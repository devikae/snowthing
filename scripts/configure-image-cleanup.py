"""Configure tagged cleanup only; never expire all post images."""
import json
import pathlib
import subprocess
import tempfile

bucket = 'snowthing-media-00001'
role = 'snowthing-rds-connect-policy'
backup = pathlib.Path(tempfile.mkdtemp(prefix='snowthing-image-cleanup-backup-'))

def aws(*args, missing=False):
    r = subprocess.run(['aws', *args, '--output', 'json'], capture_output=True, text=True)
    if r.returncode:
        if missing and 'NoSuchLifecycleConfiguration' in r.stderr: return {'Rules': []}
        raise RuntimeError(r.stderr)
    return json.loads(r.stdout) if r.stdout.strip() else {}

def doc(name, data):
    p=backup/name
    p.write_text(json.dumps(data), encoding='utf-8')
    return 'file://'+p.as_posix()

policy_name = 'SnowThingManagedPostCleanup'
names = aws('iam', 'list-role-policies', '--role-name', role).get('PolicyNames', [])
if policy_name in names:
    doc('role-before.json', aws('iam','get-role-policy','--role-name',role,'--policy-name',policy_name))
policy = {'Version':'2012-10-17','Statement':[{'Effect':'Allow',
    'Action':['s3:DeleteObject','s3:PutObjectTagging'],
    'Resource':[f'arn:aws:s3:::{bucket}/public/posts/*']}]}
aws('iam','put-role-policy','--role-name',role,'--policy-name',policy_name,'--policy-document',doc('role-after.json',policy))
old = aws('s3api','get-bucket-lifecycle-configuration','--bucket',bucket,missing=True)
doc('lifecycle-before.json',old)
rule_id = 'SnowThingConfirmedUnusedImages'
rules = [x for x in old['Rules'] if x['ID'] != rule_id]
rules.append({'ID':rule_id,'Status':'Enabled','Filter':{'And':{'Prefix':'public/posts/',
    'Tags':[{'Key':'snowthing-cleanup','Value':'ready'}]}},'Expiration':{'Days':1}})
aws('s3api','put-bucket-lifecycle-configuration','--bucket',bucket,'--lifecycle-configuration',doc('lifecycle-after.json',{'Rules':rules}))
print('Configured managed-image cleanup permissions and tagged-only one-day lifecycle fallback.')
print('Backups:',backup)
