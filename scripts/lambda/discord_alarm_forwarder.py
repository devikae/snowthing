"""SNS(snowthing-monitoring-alerts) 메시지를 Discord #장애 채널로 전달하는 Lambda. 표준 라이브러리만 쓴다.

SNS HTTPS 구독은 SNS 봉투 JSON을 보내지만 Discord 웹훅은 content 형식만 받아서 이 함수가 형식을 바꿔 준다.
웹훅 URL은 Lambda 환경변수 DISCORD_WEBHOOK_ALERT에만 둔다. 로그에 남기지 않는다.
"""
import datetime
import json
import os
import urllib.error
import urllib.request

KST = datetime.timezone(datetime.timedelta(hours=9))
MAX_CONTENT_LENGTH = 1900
MAX_RAW_LENGTH = 500
STATE_ICONS = {'ALARM': '🚨', 'OK': '✅', 'INSUFFICIENT_DATA': '⚪'}


def handler(event, context):
    webhook = os.environ.get('DISCORD_WEBHOOK_ALERT', '').strip()
    if not webhook:
        print('DISCORD_WEBHOOK_ALERT is empty; skipped')
        return {'sent': 0}
    records = event.get('Records', [])
    for record in records:
        sns = record.get('Sns', {})
        post(webhook, format_message(sns.get('Subject'), sns.get('Message', '')))
    return {'sent': len(records)}


def format_message(subject, message):
    try:
        alarm = json.loads(message)
    except (TypeError, ValueError):
        alarm = None
    if not isinstance(alarm, dict) or 'AlarmName' not in alarm:
        title = subject or 'SNS 알림'
        return truncate(f'📣 {title}\n{(message or "")[:MAX_RAW_LENGTH]}')
    state = alarm.get('NewStateValue', '?')
    lines = [
        f"{STATE_ICONS.get(state, '❔')} CloudWatch {state}: {alarm['AlarmName']}",
        f"시각: {to_kst(alarm.get('StateChangeTime'))}",
        f"이유: {alarm.get('NewStateReason', '')}",
    ]
    if alarm.get('AlarmDescription'):
        lines.append(f"설명: {alarm['AlarmDescription']}")
    return truncate('\n'.join(lines))


def to_kst(value):
    if not value:
        return '?'
    try:
        parsed = datetime.datetime.strptime(value, '%Y-%m-%dT%H:%M:%S.%f%z')
    except ValueError:
        return value
    return parsed.astimezone(KST).strftime('%Y-%m-%d %H:%M:%S KST')


def truncate(content):
    return content if len(content) <= MAX_CONTENT_LENGTH else content[:MAX_CONTENT_LENGTH - 1] + '…'


def post(webhook, content):
    body = json.dumps({'content': content, 'allowed_mentions': {'parse': []}}).encode('utf-8')
    # Discord 앞단은 기본 Python User-Agent를 막을 수 있어 직접 지정한다.
    request = urllib.request.Request(webhook, data=body, method='POST', headers={
        'Content-Type': 'application/json', 'User-Agent': 'snowthing-alarm-forwarder/1.0'})
    try:
        with urllib.request.urlopen(request, timeout=5) as response:
            response.read()
    except urllib.error.HTTPError as error:
        # 실패를 던져 Lambda 비동기 재시도(최대 2회)를 받는다. 응답 본문과 URL은 남기지 않는다.
        raise RuntimeError(f'Discord webhook returned HTTP {error.code}') from None
    except urllib.error.URLError as error:
        raise RuntimeError(f'Discord webhook unreachable: {type(error.reason).__name__}') from None
