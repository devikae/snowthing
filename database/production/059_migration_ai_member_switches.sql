-- 관리자가 AI 회원의 글, 댓글, 라이브톡 활동을 각각 켜고 끄는 설정을 추가한다 (IKA-108).
-- system_config 행 세 개만 추가한다. 기본값은 'true'로 지금처럼 모두 활동한다.
-- 환경 변수 SNOWTHING_AI_MEMBERS_ENABLED가 false면 이 설정과 관계없이 AI 회원 기능 전체가 꺼져 있다.
-- 실행 순서: 이 마이그레이션을 적용한 뒤 새 애플리케이션을 배포한다. 행이 없으면 애플리케이션은 'true'로 본다.
-- 여러 번 실행해도 안전하다 (NOT EXISTS).

INSERT INTO `system_config` (`config_key`, `config_value`, `value_type`, `description`, `updated_at`, `updated_by`)
SELECT 'AI_MEMBER_POST_ENABLED', 'true', 'BOOLEAN', 'AI 회원 글쓰기 허용 여부', NOW(6), 'SYSTEM'
WHERE NOT EXISTS (
    SELECT 1 FROM `system_config` WHERE `config_key` = 'AI_MEMBER_POST_ENABLED'
);

INSERT INTO `system_config` (`config_key`, `config_value`, `value_type`, `description`, `updated_at`, `updated_by`)
SELECT 'AI_MEMBER_COMMENT_ENABLED', 'true', 'BOOLEAN', 'AI 회원 댓글 허용 여부', NOW(6), 'SYSTEM'
WHERE NOT EXISTS (
    SELECT 1 FROM `system_config` WHERE `config_key` = 'AI_MEMBER_COMMENT_ENABLED'
);

INSERT INTO `system_config` (`config_key`, `config_value`, `value_type`, `description`, `updated_at`, `updated_by`)
SELECT 'AI_MEMBER_CHAT_ENABLED', 'true', 'BOOLEAN', 'AI 회원 라이브톡 대화 허용 여부', NOW(6), 'SYSTEM'
WHERE NOT EXISTS (
    SELECT 1 FROM `system_config` WHERE `config_key` = 'AI_MEMBER_CHAT_ENABLED'
);
