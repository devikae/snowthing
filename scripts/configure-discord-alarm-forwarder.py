"""Forward the monitoring SNS topic to Discord through a Lambda. Defaults to a read-only plan.

--apply reads the webhook from the DISCORD_WEBHOOK_ALERT environment variable only, never from arguments,
so it stays out of shell history and process lists. Existing topic subscriptions (email) are left untouched.
"""
import argparse
import importlib.util
import io
import json
import os
import pathlib
import subprocess
import tempfile
import time
import zipfile

helper = importlib.util.spec_from_file_location('image_inspection', pathlib.Path(__file__).with_name('inspect-image-cleanup.py'))
inspection = importlib.util.module_from_spec(helper)
helper.loader.exec_module(inspection)

NAME = 'snowthing-discord-alarm-forwarder'
SOURCE = pathlib.Path(__file__).with_name('lambda') / 'discord_alarm_forwarder.py'
BASIC_EXECUTION = 'arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole'
TRUST = {'Version': '2012-10-17', 'Statement': [{
    'Effect': 'Allow', 'Principal': {'Service': 'lambda.amazonaws.com'}, 'Action': 'sts:AssumeRole'}]}


def plan(region, topic):
    return {'role': {'RoleName': NAME, 'AssumeRolePolicyDocument': TRUST, 'ManagedPolicy': BASIC_EXECUTION},
            'function': {'FunctionName': NAME, 'Runtime': 'python3.12', 'Handler': 'discord_alarm_forwarder.handler',
                         'Timeout': 10, 'MemorySize': 128, 'Source': SOURCE.name,
                         'Environment': ['DISCORD_WEBHOOK_ALERT (from local environment, not printed)']},
            'logRetentionDays': 14,
            'permission': {'StatementId': 'snowthing-monitoring-sns', 'Principal': 'sns.amazonaws.com', 'SourceArn': topic},
            'subscription': {'TopicArn': topic, 'Protocol': 'lambda'}, 'region': region}


def package():
    buffer = io.BytesIO()
    with zipfile.ZipFile(buffer, 'w', zipfile.ZIP_DEFLATED) as archive:
        archive.write(SOURCE, SOURCE.name)
    return buffer.getvalue()


def try_aws(region, *args):
    try:
        return inspection.aws(region, *args)
    except subprocess.CalledProcessError as error:
        if 'NotFound' in (error.stderr or '') or 'NoSuchEntity' in (error.stderr or ''):
            return None
        raise


def ensure_role(region):
    role = try_aws(region, 'iam', 'get-role', '--role-name', NAME)
    if role is None:
        role = inspection.aws(region, 'iam', 'create-role', '--role-name', NAME,
                              '--assume-role-policy-document', json.dumps(TRUST))
        inspection.aws(region, 'iam', 'attach-role-policy', '--role-name', NAME, '--policy-arn', BASIC_EXECUTION)
        time.sleep(10)  # 새 역할이 Lambda에서 보일 때까지 기다린다(IAM 전파 지연).
    return role['Role']['Arn']


def ensure_function(region, role_arn, webhook, workdir):
    archive = workdir / 'function.zip'
    archive.write_bytes(package())
    environment = workdir / 'environment.json'
    environment.write_text(json.dumps({'Variables': {'DISCORD_WEBHOOK_ALERT': webhook}}), encoding='utf-8')
    common = ['--timeout', '10', '--memory-size', '128', '--environment', 'file://' + environment.as_posix()]
    existing = try_aws(region, 'lambda', 'get-function', '--function-name', NAME)
    if existing is None:
        created = inspection.aws(region, 'lambda', 'create-function', '--function-name', NAME, '--runtime', 'python3.12',
                                 '--handler', 'discord_alarm_forwarder.handler', '--role', role_arn,
                                 '--zip-file', 'fileb://' + archive.as_posix(), *common)
        inspection.aws(region, 'lambda', 'wait', 'function-active-v2', '--function-name', NAME)
        return created['FunctionArn']
    inspection.aws(region, 'lambda', 'update-function-code', '--function-name', NAME,
                   '--zip-file', 'fileb://' + archive.as_posix())
    inspection.aws(region, 'lambda', 'wait', 'function-updated-v2', '--function-name', NAME)
    inspection.aws(region, 'lambda', 'update-function-configuration', '--function-name', NAME, *common)
    inspection.aws(region, 'lambda', 'wait', 'function-updated-v2', '--function-name', NAME)
    return existing['Configuration']['FunctionArn']


def ensure_log_retention(region):
    group = '/aws/lambda/' + NAME
    groups = inspection.aws(region, 'logs', 'describe-log-groups', '--log-group-name-prefix', group)['logGroups']
    if not any(item['logGroupName'] == group for item in groups):
        inspection.aws(region, 'logs', 'create-log-group', '--log-group-name', group)
    inspection.aws(region, 'logs', 'put-retention-policy', '--log-group-name', group, '--retention-in-days', '14')


def ensure_permission(region, topic):
    try:
        inspection.aws(region, 'lambda', 'add-permission', '--function-name', NAME,
                       '--statement-id', 'snowthing-monitoring-sns', '--action', 'lambda:InvokeFunction',
                       '--principal', 'sns.amazonaws.com', '--source-arn', topic)
    except subprocess.CalledProcessError as error:
        if 'ResourceConflictException' not in (error.stderr or ''):
            raise


def subscriptions(region, topic):
    return inspection.aws(region, 'sns', 'list-subscriptions-by-topic', '--topic-arn', topic)['Subscriptions']


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--region', default='ap-northeast-2')
    parser.add_argument('--sns-topic-arn', required=True)
    parser.add_argument('--apply', action='store_true')
    parser.add_argument('--verify', action='store_true', help='Read back the function and topic subscriptions')
    args = parser.parse_args()
    if not args.sns_topic_arn.startswith(f'arn:aws:sns:{args.region}:'):
        raise ValueError('SNS topic region must match the region')
    if args.verify:
        function = inspection.aws(args.region, 'lambda', 'get-function-configuration', '--function-name', NAME)
        subscribed = [item for item in subscriptions(args.region, args.sns_topic_arn)
                      if item['Protocol'] == 'lambda' and item['Endpoint'] == function['FunctionArn']]
        print(json.dumps({'readOnly': True, 'function': {key: function.get(key) for key in ('FunctionArn', 'Runtime', 'State', 'LastUpdateStatus')},
                          'webhookConfigured': bool(function.get('Environment', {}).get('Variables', {}).get('DISCORD_WEBHOOK_ALERT')),
                          'subscribed': bool(subscribed),
                          'otherSubscriptions': [item['Protocol'] for item in subscriptions(args.region, args.sns_topic_arn)
                                                 if item['Protocol'] != 'lambda']}, indent=2))
        return
    if not args.apply:
        print(json.dumps({'apply': False, **plan(args.region, args.sns_topic_arn)}, indent=2))
        return
    webhook = os.environ.get('DISCORD_WEBHOOK_ALERT', '').strip()
    if not webhook.startswith(('https://discord.com/api/webhooks/', 'https://discordapp.com/api/webhooks/')):
        raise ValueError('Set DISCORD_WEBHOOK_ALERT to a Discord webhook URL before --apply')
    before = subscriptions(args.region, args.sns_topic_arn)
    with tempfile.TemporaryDirectory(prefix='snowthing-discord-forwarder-') as temporary:
        role_arn = ensure_role(args.region)
        function_arn = ensure_function(args.region, role_arn, webhook, pathlib.Path(temporary))
    ensure_log_retention(args.region)
    ensure_permission(args.region, args.sns_topic_arn)
    subscription = inspection.aws(args.region, 'sns', 'subscribe', '--topic-arn', args.sns_topic_arn,
                                  '--protocol', 'lambda', '--notification-endpoint', function_arn)
    print(json.dumps({'applied': True, 'functionArn': function_arn, 'subscriptionArn': subscription['SubscriptionArn'],
                      'subscriptionsBefore': [{key: item[key] for key in ('Protocol', 'SubscriptionArn')} for item in before]},
                     indent=2))


if __name__ == '__main__':
    main()
