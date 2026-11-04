"""Read-only QNA demand inspection (stage 0 of IKA-5). Returns aggregate numbers only.

Production runs through the existing EC2 role and SSM, like inspect-image-cleanup.py.
--local runs the same queries against a local MySQL container for verification.
No title, content, member or post identifier leaves the database host.
"""
import argparse
import base64
import datetime
import json
import pathlib
import subprocess
import tempfile
import time

KST = datetime.timezone(datetime.timedelta(hours=9))

# Shared by the remote and local runners. Expects `query(statement)` and `NOW_KST` to be defined.
COMMON = r'''
import json, math, re, time
assert re.fullmatch(r'\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}', NOW_KST)
WINDOW_DAYS = 90
VISIBLE_QNA = ("FROM post p JOIN post_category pc ON pc.category_id = p.category_id "
               "WHERE pc.code = 'QNA' AND p.status = 'NORMAL' AND p.is_deleted = 0")
# An answer is a live comment by someone other than the question author.
# Guest-to-guest pairs cannot be told apart and are counted separately.
OTHER = ("c.post_id = p.post_id AND c.is_deleted = 0 AND ("
         "(p.member_id IS NOT NULL AND (c.member_id IS NULL OR c.member_id <> p.member_id)) OR "
         "(p.member_id IS NULL AND c.member_id IS NOT NULL))")
now = f"'{NOW_KST}'"
recent_from = f"DATE_SUB({now}, INTERVAL {WINDOW_DAYS} DAY)"
statements = {
    'publicPostsByCategory': (
        "SELECT pc.code, COUNT(*) FROM post p JOIN post_category pc ON pc.category_id = p.category_id "
        "WHERE p.status = 'NORMAL' AND p.is_deleted = 0 AND pc.code <> 'MARKET' "
        "GROUP BY pc.code ORDER BY pc.code"),
    'qnaByMonth': f"SELECT DATE_FORMAT(p.created_at, '%Y-%m'), COUNT(*) {VISIBLE_QNA} GROUP BY 1 ORDER BY 1",
    'qnaRows': (
        f"SELECT p.created_at >= {recent_from}, "
        f"p.created_at <= DATE_SUB({now}, INTERVAL 1 DAY), "
        f"(SELECT TIMESTAMPDIFF(MINUTE, p.created_at, MIN(c.created_at)) FROM comment c WHERE {OTHER}), "
        "EXISTS (SELECT 1 FROM comment c WHERE c.post_id = p.post_id AND c.is_deleted = 0 "
        "AND p.member_id IS NULL AND c.member_id IS NULL), "
        f"COALESCE((SELECT m.role = 'ROLE_ADMIN' FROM comment c LEFT JOIN member m ON m.member_id = c.member_id "
        f"WHERE {OTHER} ORDER BY c.created_at, c.comment_id LIMIT 1), 0) "
        f"{VISIBLE_QNA}"),
}
elapsed = {}
raw = {}
for label, statement in statements.items():
    start = time.monotonic()
    raw[label] = [line.split('\t') for line in query(statement).splitlines() if line]
    elapsed[label] = round((time.monotonic() - start) * 1000, 2)

def percentile(values, ratio):
    if not values:
        return None
    ordered = sorted(values)
    return ordered[max(0, math.ceil(len(ordered) * ratio) - 1)]

def summarize(rows):
    minutes = [int(r[2]) for r in rows if r[2] != 'NULL']
    undetermined = sum(1 for r in rows if r[2] == 'NULL' and r[3] == '1')
    mature = [r for r in rows if r[1] == '1' and not (r[2] == 'NULL' and r[3] == '1')]
    return {
        'questions': len(rows),
        'answered': len(minutes),
        'undetermined': undetermined,
        'questionsOlderThan24h': len(mature),
        'answeredWithin24h': sum(1 for r in mature if r[2] != 'NULL' and int(r[2]) <= 1440),
        'notAnsweredWithin24h': sum(1 for r in mature if r[2] == 'NULL' or int(r[2]) > 1440),
        'firstAnswerMinutes': {'n': len(minutes), 'p50': percentile(minutes, 0.5),
                               'p90': percentile(minutes, 0.9), 'max': max(minutes) if minutes else None},
        'adminFirstAnswers': sum(1 for r in rows if r[4] == '1'),
    }

rows = raw['qnaRows']
report = {
    'readOnly': True,
    'queryMaxExecutionTimeMs': 5000,
    'measuredAtKst': NOW_KST,
    'recentWindowDays': WINDOW_DAYS,
    'scope': "post.status = 'NORMAL' AND post.is_deleted = 0; answers are live comments by another writer",
    'observations': {
        'publicPostsByCategory': {code: int(count) for code, count in raw['publicPostsByCategory']},
        'qnaByMonth': {month: int(count) for month, count in raw['qnaByMonth']},
        'qnaAllTime': summarize(rows),
        'qnaRecent': summarize([r for r in rows if r[0] == '1']),
    },
    'elapsedMs': elapsed,
}
print(json.dumps(report))
'''

REMOTE_CONNECT = r'''
import os, subprocess, urllib.parse
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
    return subprocess.check_output(base + ["SET SESSION TRANSACTION READ ONLY; "
                                           "SET SESSION MAX_EXECUTION_TIME=5000; " + statement],
                                   env=environment, text=True).strip()
'''


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
                       '--comment', 'Snowthing QNA demand inspection')
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


def run_local(container, database, user, now_kst):
    def query(statement):
        # MYSQL_PWD is passed through from the caller's environment, never on the command line.
        return subprocess.check_output(
            ['docker', 'exec', '-e', 'MYSQL_PWD', container, 'mysql', '-u', user, database,
             '--batch', '--skip-column-names', '--raw', '-e',
             'SET SESSION TRANSACTION READ ONLY; SET SESSION MAX_EXECUTION_TIME=5000; ' + statement],
            text=True, encoding='utf-8').strip()
    output = []
    scope = {'query': query, 'NOW_KST': now_kst, 'print': output.append}
    exec(COMMON, scope)
    return json.loads(output[-1])


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--region', default='ap-northeast-2')
    parser.add_argument('--instance-id')
    parser.add_argument('--local', action='store_true', help='run against a local MySQL container')
    parser.add_argument('--container', default='snowthing-mysql')
    parser.add_argument('--database', default='snowthing')
    parser.add_argument('--user', default='snowuser')
    parser.add_argument('--now-kst', help='fixed KST time "YYYY-MM-DD HH:MM:SS" (default: now)')
    parser.add_argument('--output', type=pathlib.Path, required=True)
    args = parser.parse_args()
    now_kst = args.now_kst or datetime.datetime.now(KST).strftime('%Y-%m-%d %H:%M:%S')
    if args.local:
        report = run_local(args.container, args.database, args.user, now_kst)
    elif args.instance_id:
        source = REMOTE_CONNECT + f'NOW_KST = {now_kst!r}\n' + COMMON
        report = json.loads(ssm_python(args.region, args.instance_id, source))
    else:
        parser.error('--instance-id is required unless --local is given')
    args.output.write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    print(json.dumps(report, ensure_ascii=False, indent=2))


if __name__ == '__main__':
    main()
