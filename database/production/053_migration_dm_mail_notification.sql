-- IKA-88 쪽지 도착 메일 알림.
-- member_notification_setting: 회원별 알림 설정. 행이 없으면 꺼짐으로 해석한다.
--   기존 회원은 행이 없으므로 꺼짐이고, 신규 가입(일반, 소셜) 때 dm_email_enabled = TRUE로 만든다.
--   member 테이블을 UPDATE하지 않는다.
-- dm_mail_outbox: 쪽지 1개에 행 1개인 발송 요청(Transactional Outbox). 쪽지 INSERT와 같은 트랜잭션에서 쓴다.
--   이메일 주소와 쪽지 본문은 저장하지 않고 ID만 둔다. 발송 직전에 회원 상태, 설정, 읽음 여부를 다시 확인한다.
--   status: PENDING, PROCESSING, ACCEPTED, RETRY_WAIT, FAILED, UNKNOWN, SKIPPED
--   claim_token과 lease_until은 선점 소유권이다. 결과 기록은 claim_token이 맞을 때만 한다.
--   provider_message_id는 SES MessageId다. 묶음 메일 한 통이면 여러 행에 같은 값이 들어간다.
-- dm_mail_recipient_state: 받는 사람별 마지막 발송 시도 시각. 쿨다운 기준이자, 같은 받는 사람을 두 워커가
--   동시에 묶지 못하게 하는 잠금 지점(FOR UPDATE SKIP LOCKED)이다.
-- 회원 탈퇴 때 세 테이블의 해당 회원 행을 지운다.
-- 시각은 KST DATETIME(6)이다.
-- 새 테이블만 만들어 이전 앱 이미지에는 영향이 없다. 앱 배포(ddl-auto validate) 전에 적용한다. 두 번 실행해도 안전하다.
-- 설계: docs/conception/032_dm_email_notification/01_design.md
-- 접속 클라이언트의 기본 문자셋과 관계없이 한글 COMMENT가 깨지지 않게 한다.
SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS member_notification_setting (
    member_id BIGINT NOT NULL PRIMARY KEY,
    dm_email_enabled BOOLEAN NOT NULL COMMENT '쪽지 도착 메일 알림',
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_member_notification_setting_member FOREIGN KEY (member_id) REFERENCES member (member_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='회원 알림 설정';

CREATE TABLE IF NOT EXISTS dm_mail_outbox (
    event_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    direct_message_id BIGINT NOT NULL COMMENT '알릴 쪽지',
    recipient_member_id BIGINT NOT NULL COMMENT '받는 회원',
    event_type VARCHAR(30) NOT NULL COMMENT 'DM_RECEIVED',
    channel VARCHAR(20) NOT NULL COMMENT 'EMAIL',
    status VARCHAR(20) NOT NULL,
    attempts INT NOT NULL,
    due_at DATETIME(6) NOT NULL COMMENT '이 시각부터 선점 대상',
    lease_until DATETIME(6) NULL COMMENT 'PROCESSING 소유 만료 시각',
    claim_token CHAR(36) NULL COMMENT '선점 토큰(UUID)',
    provider_message_id VARCHAR(100) NULL COMMENT 'SES MessageId',
    skip_reason VARCHAR(30) NULL,
    last_error_code VARCHAR(100) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT uk_dm_mail_outbox_intent UNIQUE (direct_message_id, recipient_member_id, event_type, channel),
    INDEX idx_dm_mail_outbox_due (status, due_at, event_id),
    INDEX idx_dm_mail_outbox_recipient (recipient_member_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='쪽지 도착 메일 발송 요청';

CREATE TABLE IF NOT EXISTS dm_mail_recipient_state (
    recipient_member_id BIGINT NOT NULL PRIMARY KEY,
    last_attempt_at DATETIME(6) NULL COMMENT '마지막 SES 호출 시작 시각'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='쪽지 메일 받는 사람별 발송 상태';
