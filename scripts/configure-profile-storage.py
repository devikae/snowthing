"""Apply narrowly scoped profile access; preserve backups outside the repository."""
import json
import pathlib
import subprocess
import tempfile

BUCKET = 'snowthing-media-00001'
DISTRIBUTION = 'EJ0LUYNZLPFPQ'
ROLE = 'snowthing-rds-connect-policy'
backup = pathlib.Path(tempfile.mkdtemp(prefix='snowthing-storage-backup-'))


def aws(*args):
    result = subprocess.run(['aws', *args, '--output', 'json'], capture_output=True, text=True)
    if result.returncode:
        raise RuntimeError(result.stderr)
    return json.loads(result.stdout) if result.stdout.strip() else {}


def document(name, value):
    path = backup / name
    path.write_text(json.dumps(value), encoding='utf-8')
    return 'file://' + path.as_posix()


bucket = json.loads(aws('s3api', 'get-bucket-policy', '--bucket', BUCKET)['Policy'])
document('bucket-before.json', bucket)
sid = 'AllowCloudFrontProfileRead'
bucket['Statement'] = [s for s in bucket['Statement'] if s.get('Sid') != sid]
bucket['Statement'].append({
    'Sid': sid, 'Effect': 'Allow', 'Principal': {'Service': 'cloudfront.amazonaws.com'},
    'Action': 's3:GetObject', 'Resource': f'arn:aws:s3:::{BUCKET}/public/profiles/*',
    'Condition': {'StringEquals': {'AWS:SourceArn': f'arn:aws:cloudfront::917286218765:distribution/{DISTRIBUTION}'}}
})
aws('s3api', 'put-bucket-policy', '--bucket', BUCKET, '--policy', document('bucket-after.json', bucket))
policy = {'Version': '2012-10-17', 'Statement': [
    {'Effect': 'Allow', 'Action': 's3:DeleteObject', 'Resource': f'arn:aws:s3:::{BUCKET}/public/profiles/*'},
    {'Effect': 'Allow', 'Action': 'cloudfront:CreateInvalidation', 'Resource': f'arn:aws:cloudfront::917286218765:distribution/{DISTRIBUTION}'}
]}
names = aws('iam', 'list-role-policies', '--role-name', ROLE).get('PolicyNames', [])
if 'SnowThingProfileCleanup' in names:
    document('role-before.json', aws('iam', 'get-role-policy', '--role-name', ROLE, '--policy-name', 'SnowThingProfileCleanup'))
aws('iam', 'put-role-policy', '--role-name', ROLE, '--policy-name', 'SnowThingProfileCleanup', '--policy-document', document('role-policy.json', policy))
response = aws('cloudfront', 'get-distribution-config', '--id', DISTRIBUTION)
document('distribution-before.json', response)
config = response['DistributionConfig']
behaviors = config.get('CacheBehaviors', {}).get('Items', [])
if not any(x['PathPattern'] == 'public/profiles/*' for x in behaviors):
    default = config['DefaultCacheBehavior']
    behavior = {k: v for k, v in default.items() if k not in ('MinTTL', 'DefaultTTL', 'MaxTTL', 'ForwardedValues')}
    behavior['PathPattern'] = 'public/profiles/*'
    behavior['CachePolicyId'] = '4135ea2d-6df8-44a3-9df3-4b5a84be39ad'
    behaviors.insert(0, behavior)
    config['CacheBehaviors'] = {'Quantity': len(behaviors), 'Items': behaviors}
    aws('cloudfront', 'update-distribution', '--id', DISTRIBUTION, '--if-match', response['ETag'], '--distribution-config', document('distribution-after.json', config))
print('Applied profile read/delete permissions and disabled profile caching.')
print('Backups:', backup)
