"""Configure bounded EMF collection and per-action alarms. Defaults to a read-only plan."""
import argparse
import importlib.util
import json
import pathlib
import tempfile

helper = importlib.util.spec_from_file_location('image_inspection', pathlib.Path(__file__).with_name('inspect-image-cleanup.py'))
inspection = importlib.util.module_from_spec(helper)
helper.loader.exec_module(inspection)

LOG_GROUP = '/snowthing/images/budget'
LOG_PATH = '/var/log/snowthing-chat/image_budget.jsonl'


def agent_config():
    return {'logs': {'force_flush_interval': 5, 'logs_collected': {'files': {'collect_list': [{
        'file_path': LOG_PATH, 'log_group_name': LOG_GROUP, 'log_stream_name': '{instance_id}',
        'timezone': 'UTC'}]}}}}


def alarm(action, topic):
    return {'AlarmName': 'snowthing-image-global-limit-' + action.lower().replace('_', '-'),
            'AlarmDescription': 'Image upload global budget rejected requests; inspect reservations and abuse before changing caps.',
            'Namespace': 'Snowthing/Images', 'MetricName': 'GlobalBudgetRejected',
            'Dimensions': [{'Name': 'Action', 'Value': action}], 'Statistic': 'Sum',
            'Period': 300, 'EvaluationPeriods': 1, 'DatapointsToAlarm': 1, 'Threshold': 1,
            'ComparisonOperator': 'GreaterThanOrEqualToThreshold', 'TreatMissingData': 'notBreaching',
            'ActionsEnabled': True, 'AlarmActions': [topic], 'OKActions': [topic]}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--region', default='ap-northeast-2')
    parser.add_argument('--instance-id', required=True)
    parser.add_argument('--sns-topic-arn', required=True)
    parser.add_argument('--apply', action='store_true')
    parser.add_argument('--verify', action='store_true', help='Read back alarms, retention and merged Agent configuration')
    args = parser.parse_args()
    plans = [alarm(action, args.sns_topic_arn) for action in ('PHOTO', 'POST_IMAGE')]
    if args.verify:
        response = inspection.aws(args.region, 'cloudwatch', 'describe-alarms', '--alarm-names', *[item['AlarmName'] for item in plans])
        groups = inspection.aws(args.region, 'logs', 'describe-log-groups', '--log-group-name-prefix', LOG_GROUP)['logGroups']
        source = '''
import json, pathlib, subprocess, tomllib
directory = pathlib.Path('/opt/aws/amazon-cloudwatch-agent/etc')
merged = tomllib.loads((directory / 'amazon-cloudwatch-agent.toml').read_text())
inputs = merged.get('inputs', {})
print(json.dumps({'status': json.loads(subprocess.check_output(['/opt/aws/amazon-cloudwatch-agent/bin/amazon-cloudwatch-agent-ctl', '-a', 'status'], text=True)),
    'retainsMemoryMetric': any('used_percent' in item.get('fieldpass', []) for item in inputs.get('mem', [])),
    'retainsDiskMetric': any('used_percent' in item.get('fieldpass', []) for item in inputs.get('disk', [])),
    'includesImageLog': any(config.get('file_path') == '/var/log/snowthing-chat/image_budget.jsonl'
        for item in inputs.get('logfile', []) for config in item.get('file_config', []))}))
'''
        agent = json.loads(inspection.ssm_python(args.region, args.instance_id, source))
        observed = response['MetricAlarms']
        if len(observed) != 2 or not all(agent[key] for key in ('retainsMemoryMetric', 'retainsDiskMetric', 'includesImageLog')):
            raise RuntimeError('Monitoring readback differs from the expected merged configuration: ' + json.dumps(agent))
        print(json.dumps({'readOnly': True, 'alarms': [{key: item[key] for key in ('AlarmName', 'StateValue', 'AlarmActions', 'TreatMissingData')} for item in observed],
                          'retentionDays': next(group['retentionInDays'] for group in groups if group['logGroupName'] == LOG_GROUP), 'agent': agent}, indent=2))
        return
    if not args.apply:
        print(json.dumps({'apply': False, 'agent': agent_config(), 'alarms': plans}, indent=2))
        return
    if not args.sns_topic_arn.startswith(f'arn:aws:sns:{args.region}:'):
        raise ValueError('SNS topic region must match the monitoring region')
    subscriptions = inspection.aws(args.region, 'sns', 'list-subscriptions-by-topic', '--topic-arn', args.sns_topic_arn)
    if not any(item['SubscriptionArn'].startswith('arn:') for item in subscriptions['Subscriptions']):
        raise RuntimeError('Monitoring topic has no confirmed subscription')
    backup = pathlib.Path(tempfile.mkdtemp(prefix='snowthing-image-monitoring-'))
    existing = inspection.aws(args.region, 'cloudwatch', 'describe-alarms', '--alarm-names', *[item['AlarmName'] for item in plans])
    (backup / 'alarms-before.json').write_text(json.dumps(existing), encoding='utf-8')
    groups = inspection.aws(args.region, 'logs', 'describe-log-groups', '--log-group-name-prefix', LOG_GROUP)['logGroups']
    (backup / 'log-groups-before.json').write_text(json.dumps(groups), encoding='utf-8')
    if not any(group['logGroupName'] == LOG_GROUP for group in groups):
        inspection.aws(args.region, 'logs', 'create-log-group', '--log-group-name', LOG_GROUP)
    inspection.aws(args.region, 'logs', 'put-retention-policy', '--log-group-name', LOG_GROUP, '--retention-in-days', '7')
    for plan in plans:
        request = backup / (plan['AlarmName'] + '.json')
        request.write_text(json.dumps(plan), encoding='utf-8')
        inspection.aws(args.region, 'cloudwatch', 'put-metric-alarm', '--cli-input-json', 'file://' + request.as_posix())
    remote = '''
import datetime, json, pathlib, shutil, subprocess
config = CONFIG
ctl = '/opt/aws/amazon-cloudwatch-agent/bin/amazon-cloudwatch-agent-ctl'
if not pathlib.Path(ctl).is_file(): raise RuntimeError('Existing CloudWatch Agent is required')
backup = pathlib.Path('/var/lib/snowthing-monitoring-backups') / datetime.datetime.now(datetime.timezone.utc).strftime('image-budget-%Y%m%dT%H%M%SZ')
backup.mkdir(parents=True, exist_ok=False, mode=0o700)
directory = pathlib.Path('/opt/aws/amazon-cloudwatch-agent/etc')
target = directory / 'snowthing-image-budget.json'
for source in [target, directory / 'amazon-cloudwatch-agent.json']:
    if source.is_file(): shutil.copy2(source, backup / source.name)
target.write_text(json.dumps(config), encoding='utf-8')
result = subprocess.run([ctl, '-a', 'append-config', '-m', 'ec2', '-s', '-c', 'file:' + str(target)], text=True, capture_output=True)
if result.returncode: raise RuntimeError(result.stderr + result.stdout)
status = subprocess.check_output([ctl, '-a', 'status'], text=True)
print(json.dumps({'agentStatus': json.loads(status), 'backup': str(backup), 'logFileExists': pathlib.Path('/var/log/snowthing-chat/image_budget.jsonl').exists()}))
'''.replace('CONFIG', repr(agent_config()), 1)
    status = inspection.ssm_python(args.region, args.instance_id, remote)
    print(json.dumps({'applied': True, 'logGroup': LOG_GROUP, 'retentionDays': 7,
                      'alarmNames': [item['AlarmName'] for item in plans], 'backup': str(backup),
                      'remote': json.loads(status), 'activation': 'New app emits metrics after the PR is deployed; no application deployment is performed.'}, indent=2))


if __name__ == '__main__':
    main()
