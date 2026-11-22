-- IKA-109 쪽지 도착 메일을 관리자 화면에서 켜고 끄는 설정을 추가한다.
-- system_config 행 두 개만 추가한다. 스키마 변경은 없다.
--   DM_MAIL_ENQUEUE_ENABLED: 쪽지가 오면 발송 요청(dm_mail_outbox)을 기록할지
--   DM_MAIL_SEND_ENABLED: 쌓인 발송 요청을 메일로 보낼지
-- 기본값은 둘 다 'false'다. 이 마이그레이션과 새 애플리케이션을 배포해도 메일은 나가지 않는다.
-- 실제 동작은 환경변수(SNOWTHING_DM_MAIL_ENQUEUE_ENABLED, SNOWTHING_DM_MAIL_SEND_ENABLED)와 이 설정이 모두 true일 때만 켜진다.
-- 실행 순서: 이 마이그레이션을 적용한 뒤 새 애플리케이션을 배포한다. 행이 없으면 애플리케이션은 'false'로 본다.
-- 여러 번 실행해도 안전하다 (NOT EXISTS). 이미 관리자가 바꾼 값은 덮어쓰지 않는다.
-- 설계: docs/conception/032_dm_email_notification/04_admin_switch.md

SET NAMES utf8mb4;

INSERT INTO `system_config` (`config_key`, `config_value`, `value_type`, `description`, `updated_at`, `updated_by`)
SELECT 'DM_MAIL_ENQUEUE_ENABLED', 'false', 'BOOLEAN', '쪽지 도착 메일 발송 요청 기록 여부', NOW(6), 'SYSTEM'
WHERE NOT EXISTS (
    SELECT 1 FROM `system_config` WHERE `config_key` = 'DM_MAIL_ENQUEUE_ENABLED'
);

INSERT INTO `system_config` (`config_key`, `config_value`, `value_type`, `description`, `updated_at`, `updated_by`)
SELECT 'DM_MAIL_SEND_ENABLED', 'false', 'BOOLEAN', '쪽지 도착 메일 발송 여부', NOW(6), 'SYSTEM'
WHERE NOT EXISTS (
    SELECT 1 FROM `system_config` WHERE `config_key` = 'DM_MAIL_SEND_ENABLED'
);
