"""python -I -m unittest discover -s scripts/lambda"""
import json
import os
import unittest
from unittest import mock

import discord_alarm_forwarder as forwarder


class FormatMessageTest(unittest.TestCase):

    def test_alarm_message_in_kst(self):
        message = json.dumps({
            'AlarmName': 'snowthing-image-global-limit-photo',
            'NewStateValue': 'ALARM',
            'NewStateReason': 'Threshold Crossed',
            'StateChangeTime': '2026-11-04T05:03:12.123+0000',
        })

        content = forwarder.format_message('ALARM: x', message)

        self.assertEqual(content.splitlines(), [
            '🚨 CloudWatch ALARM: snowthing-image-global-limit-photo',
            '시각: 2026-11-04 14:03:12 KST',
            '이유: Threshold Crossed',
        ])

    def test_ok_state_icon(self):
        content = forwarder.format_message(None, json.dumps({'AlarmName': 'a', 'NewStateValue': 'OK'}))

        self.assertTrue(content.startswith('✅ CloudWatch OK: a'))

    def test_non_alarm_message_is_shortened(self):
        content = forwarder.format_message('Budget', 'x' * 2000)

        self.assertTrue(content.startswith('📣 Budget\n'))
        self.assertEqual(len(content), len('📣 Budget\n') + forwarder.MAX_RAW_LENGTH)

    def test_truncates_to_discord_limit(self):
        content = forwarder.format_message(None, json.dumps({'AlarmName': 'a', 'NewStateReason': 'r' * 5000}))

        self.assertEqual(len(content), forwarder.MAX_CONTENT_LENGTH)


class HandlerTest(unittest.TestCase):

    def test_skips_without_webhook(self):
        with mock.patch.dict(os.environ, {'DISCORD_WEBHOOK_ALERT': ''}), \
                mock.patch.object(forwarder, 'post') as post:
            result = forwarder.handler({'Records': [{'Sns': {'Message': '{}'}}]}, None)

        self.assertEqual(result, {'sent': 0})
        post.assert_not_called()

    def test_posts_each_record_without_mentions(self):
        captured = []

        def fake_urlopen(request, timeout):
            captured.append(json.loads(request.data))
            return mock.MagicMock()

        with mock.patch.dict(os.environ, {'DISCORD_WEBHOOK_ALERT': 'https://discord.test/hook'}), \
                mock.patch('urllib.request.urlopen', side_effect=fake_urlopen):
            result = forwarder.handler({'Records': [
                {'Sns': {'Message': json.dumps({'AlarmName': 'a', 'NewStateValue': 'ALARM'})}},
                {'Sns': {'Subject': 's', 'Message': '@everyone'}},
            ]}, None)

        self.assertEqual(result, {'sent': 2})
        self.assertEqual([item['allowed_mentions'] for item in captured], [{'parse': []}] * 2)


if __name__ == '__main__':
    unittest.main()
