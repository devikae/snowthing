-- 수동 실험 시드 틀. {PREFIX}, {RECEIVERS}, {DMS} 를 바꿔 쓴다. 예: ev1, 40, 3
-- 쪽지 트랜잭션이 커밋된 직후와 같은 상태(회원, 알림 설정, 쪽지, 발송 요청)를 한 트랜잭션으로 만든다.
SET @now = NOW(6);
START TRANSACTION;
INSERT INTO member (public_id, email, nickname, email_verified_at, role, status, created_at, updated_at)
VALUES (UUID(), '{PREFIX}-sender@exp.snowthing.org', '{PREFIX}_sender', @now, 'ROLE_USER', 'ACTIVE', @now, @now);
SET @sender = LAST_INSERT_ID();
INSERT INTO member (public_id, email, nickname, email_verified_at, role, status, created_at, updated_at)
WITH RECURSIVE seq(n) AS (SELECT 1 UNION ALL SELECT n + 1 FROM seq WHERE n < {RECEIVERS})
SELECT UUID(), CONCAT('{PREFIX}-r', n, '@exp.snowthing.org'), CONCAT('{PREFIX}_r', n), @now, 'ROLE_USER', 'ACTIVE', @now, @now FROM seq;
INSERT INTO member_notification_setting (member_id, dm_email_enabled, updated_at)
SELECT member_id, TRUE, @now FROM member WHERE email LIKE '{PREFIX}-r%';
INSERT INTO direct_message (sender_id, receiver_id, content, created_at, updated_at)
WITH RECURSIVE k(i) AS (SELECT 1 UNION ALL SELECT i + 1 FROM k WHERE i < {DMS})
SELECT @sender, m.member_id, CONCAT('{PREFIX} 쪽지 ', k.i), @now, @now FROM member m CROSS JOIN k WHERE m.email LIKE '{PREFIX}-r%';
INSERT INTO dm_mail_outbox (direct_message_id, recipient_member_id, event_type, channel, status, attempts, due_at, created_at, updated_at)
SELECT direct_message_id, receiver_id, 'DM_RECEIVED', 'EMAIL', 'PENDING', 0, @now, @now, @now FROM direct_message WHERE sender_id = @sender;
COMMIT;
