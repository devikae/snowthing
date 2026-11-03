"""Read-only production backlog/EXPLAIN inspection through the existing EC2 role."""
import argparse
import base64
import json
import pathlib
import subprocess
import tempfile
import time


def aws(region, *args):
    result = subprocess.run(['aws', *args, '--region', region, '--output', 'json'],
                            capture_output=True, text=True, encoding='utf-8', check=True)
    return json.loads(result.stdout) if result.stdout.strip() else {}


def ssm_python(region, instance, source):
    encoded = base64.b64encode(source.encode()).decode()
    command = f"python3 -c 'import base64;exec(base64.b64decode(\"{encoded}\"))'"
    with tempfile.TemporaryDirectory(prefix='snowthing-inspect-') as temporary:
        parameters = pathlib.Path(temporary) / 'parameters.json'
        parameters.write_text(json.dumps({'commands': [command]}), encoding='utf-8')
        response = aws(region, 'ssm', 'send-command', '--instance-ids', instance,
                       '--document-name', 'AWS-RunShellScript', '--timeout-seconds', '120',
                       '--parameters', 'file://' + parameters.as_posix(),
                       '--comment', 'Snowthing image cleanup inspection')
    command_id = response['Command']['CommandId']
    for _ in range(90):
        try:
            response = aws(region, 'ssm', 'get-command-invocation', '--command-id', command_id,
                           '--instance-id', instance)
        except subprocess.CalledProcessError as failure:
            if 'InvocationDoesNotExist' not in failure.stderr:
                raise
            time.sleep(1)
            continue
        if response['Status'] in ('Pending', 'InProgress', 'Delayed'):
            time.sleep(1)
            continue
        if response['Status'] != 'Success':
            raise RuntimeError(response['StandardErrorContent'] or response['StandardOutputContent'])
        return response['StandardOutputContent']
    raise TimeoutError(f'SSM inspection did not finish: {command_id}')


REMOTE = r'''
import json, os, subprocess, time, urllib.parse
values = dict(line.split('=', 1) for line in subprocess.check_output(
    ['docker', 'exec', 'snowthing-backend', 'printenv'], text=True).splitlines() if '=' in line)
url = urllib.parse.urlparse('mysql://' + values['SNOWTHING_PROD_DB_URL'].split('//', 1)[1])
environment = os.environ.copy()
environment['MYSQL_PWD'] = values.get('SNOWTHING_DB_PASSWORD') or subprocess.check_output(
    ['aws', 'rds', 'generate-db-auth-token', '--hostname', url.hostname, '--port', str(url.port or 3306),
     '--region', 'ap-northeast-2', '--username', values['SNOWTHING_DB_USERNAME']], text=True).strip()
base = ['mysql', '--connect-timeout=8', '--ssl-mode=REQUIRED', '--enable-cleartext-plugin',
        '-h', url.hostname, '-P', str(url.port or 3306), '-u', values['SNOWTHING_DB_USERNAME'],
        url.path.lstrip('/'), '--batch', '--skip-column-names', '--raw', '-e']
def query(statement):
    return subprocess.check_output(base + ["SET SESSION MAX_EXECUTION_TIME=5000; " + statement],
                                   env=environment, text=True).strip()
queries = {
    'postsIncludingSoftDeleted': 'SELECT COUNT(*) FROM post',
    'attachments': 'SELECT COUNT(*) FROM post_image',
    'legacyBackfillKeys': "SELECT COUNT(DISTINCT REPLACE(image_url,'https://images.snowthing.org/','')) FROM post_image WHERE REPLACE(image_url,'https://images.snowthing.org/','') REGEXP '^public/posts/(originals/)?[0-9a-f-]{36}[.](jpg|jpeg|png|webp)$'",
    'imageTables': "SELECT table_name FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name IN ('post_image_upload','post_content_image_reference','market_thumbnail') ORDER BY table_name",
    'attachmentIndexes': "SELECT index_name,column_name FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='post_image' ORDER BY index_name,seq_in_index",
    'attachmentLookupPlan': "EXPLAIN FORMAT=JSON SELECT 1 FROM post_image WHERE image_url IN ('public/posts/originals/00000000-0000-0000-0000-000000000000.jpg','https://images.snowthing.org/public/posts/originals/00000000-0000-0000-0000-000000000000.jpg') LIMIT 1"
}
report = {'readOnly': True, 'queryMaxExecutionTimeMs': 5000, 'includesSoftDeletedPosts': True, 'observations': {}}
for label, statement in queries.items():
    start = time.monotonic()
    value = query(statement)
    if label.endswith('Plan'): value = json.loads(value)
    elif label in ('postsIncludingSoftDeleted', 'attachments', 'legacyBackfillKeys'): value = int(value)
    else: value = value.splitlines()
    report['observations'][label] = {'value': value, 'elapsedMs': round((time.monotonic() - start) * 1000, 2)}
if 'post_image_upload' in report['observations']['imageTables']['value']:
    report['observations']['dueUploadRows'] = {'value': int(query("SELECT COUNT(*) FROM post_image_upload WHERE due_at<=UTC_TIMESTAMP(6) AND state='PENDING'"))}
if 'post_content_image_reference' in report['observations']['imageTables']['value']:
    report['observations']['contentLookupPlan'] = {'value': json.loads(query("EXPLAIN FORMAT=JSON SELECT 1 FROM post_content_image_reference WHERE image_key='public/posts/originals/00000000-0000-0000-0000-000000000000.jpg' LIMIT 1"))}
print(json.dumps(report))
'''


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--region', default='ap-northeast-2')
    parser.add_argument('--instance-id', required=True)
    parser.add_argument('--output', type=pathlib.Path, required=True)
    args = parser.parse_args()
    report = json.loads(ssm_python(args.region, args.instance_id, REMOTE))
    args.output.write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    print(json.dumps(report, ensure_ascii=False, indent=2))


if __name__ == '__main__':
    main()
