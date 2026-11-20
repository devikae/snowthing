-- 중고장터를 로그인하지 않은 사람도 볼 수 있게 할지 관리자가 정하는 설정을 추가한다.
-- system_config 행 하나만 추가한다. 기본값은 'true'(비회원 열람 허용)다.
-- 글쓰기, 수정, 삭제, 거래 상태 변경은 이 설정과 관계없이 로그인이 필요하다.
-- 실행 순서: 이 마이그레이션을 적용한 뒤 새 애플리케이션을 배포한다. 행이 없으면 애플리케이션은 'true'로 본다.
-- 여러 번 실행해도 안전하다 (NOT EXISTS).

INSERT INTO `system_config` (`config_key`, `config_value`, `value_type`, `description`, `updated_at`, `updated_by`)
SELECT 'MARKET_GUEST_ACCESS_ENABLED', 'true', 'BOOLEAN', '중고장터 비로그인 열람 허용 여부', NOW(6), 'SYSTEM'
WHERE NOT EXISTS (
    SELECT 1 FROM `system_config` WHERE `config_key` = 'MARKET_GUEST_ACCESS_ENABLED'
);
