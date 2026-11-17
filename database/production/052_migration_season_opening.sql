-- IKA-87 리조트 개장일(D-DAY)을 관리자 화면에서 관리한다. 설계: docs/conception/032_season_opening_admin/01_design.md
-- 지금까지 개장일은 frontend/app/lib/seasonOpening.ts 상수라 날짜를 바꿀 때마다 배포해야 했다.
-- 1) season_opening: 리조트별 개장일 한 줄. 관리자는 날짜, 확정 여부, 메인 배너 표시 여부, 배너 순서만 바꾼다.
--    리조트 추가와 삭제는 화면에서 하지 않는다. 대상 리조트가 바뀌면 마이그레이션으로 넣는다.
--    banner_order에는 UNIQUE를 두지 않는다. 한 번에 순서를 맞바꾸는 저장이 중간 상태에서 충돌하기 때문이다. 겹침은 애플리케이션이 막는다.
-- 2) system_config의 SEASON_OPENING_BANNER_ENABLED: 메인 개장 배너 전체 켜기와 끄기.
-- 초기값은 26/27 시즌 프론트 상수 그대로다 (용평 11.22, 하이원 11.24는 PR #147 값). show_on_banner는 예전 confirmed 값,
-- banner_order는 개장일 순, 같은 날이면 이름 순이다.
-- 실행 순서: 이 마이그레이션을 적용한 뒤 새 애플리케이션을 배포한다. 추가만 하므로 이전 애플리케이션과도 호환된다.
-- 여러 번 실행해도 안전하다 (IF NOT EXISTS, INSERT IGNORE, 설정 존재 확인). 이미 있는 줄의 날짜는 덮어쓰지 않는다.

CREATE TABLE IF NOT EXISTS `season_opening` (
    `season_opening_id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `resort_code` VARCHAR(30) NOT NULL COMMENT '리조트 코드 (resort.code, 슬로프캠 코드와 같다)',
    `resort_name` VARCHAR(50) NOT NULL COMMENT '개장 배너에 보이는 짧은 이름',
    `opening_date` DATE NULL COMMENT '개장일 (KST 달력 날짜). NULL이면 D-DAY를 보이지 않는다',
    `confirmed` BOOLEAN NOT NULL DEFAULT FALSE COMMENT '리조트가 공식 발표한 날짜인지. 아니면 화면에 예상으로 보인다',
    `show_on_banner` BOOLEAN NOT NULL DEFAULT FALSE COMMENT '메인 개장 배너에 넣을지',
    `banner_order` INT NOT NULL COMMENT '메인 개장 배너 순서 (작을수록 앞)',
    `created_at` DATETIME(6) NOT NULL,
    `updated_at` DATETIME(6) NOT NULL,
    UNIQUE KEY `uk_season_opening_resort_code` (`resort_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='리조트 시즌 개장일';

INSERT IGNORE INTO `season_opening`
    (`resort_code`, `resort_name`, `opening_date`, `confirmed`, `show_on_banner`, `banner_order`, `created_at`, `updated_at`)
VALUES
    ('YONGPYONG', '모나용평', '2026-11-22', TRUE, TRUE, 1, NOW(6), NOW(6)),
    ('HIGH1', '하이원', '2026-11-24', TRUE, TRUE, 2, NOW(6), NOW(6)),
    ('O2_RESORT', '오투리조트', '2026-11-27', FALSE, FALSE, 3, NOW(6), NOW(6)),
    ('PHOENIX', '휘닉스파크', '2026-11-27', TRUE, TRUE, 4, NOW(6), NOW(6)),
    ('MUJU', '무주덕유산', '2026-12-04', FALSE, FALSE, 5, NOW(6), NOW(6)),
    ('VIVALDI', '비발디파크', '2026-12-04', FALSE, FALSE, 6, NOW(6), NOW(6)),
    ('WELLI_HILLI', '웰리힐리파크', '2026-12-04', FALSE, FALSE, 7, NOW(6), NOW(6)),
    ('KONJIAM', '곤지암', '2026-12-11', FALSE, FALSE, 8, NOW(6), NOW(6)),
    ('OAK_VALLEY', '오크밸리', '2026-12-11', FALSE, FALSE, 9, NOW(6), NOW(6));

INSERT INTO `system_config` (`config_key`, `config_value`, `value_type`, `description`, `updated_at`, `updated_by`)
SELECT 'SEASON_OPENING_BANNER_ENABLED', 'true', 'BOOLEAN', '메인 개장 D-DAY 배너 표시 여부', NOW(6), 'SYSTEM'
WHERE NOT EXISTS (
    SELECT 1 FROM `system_config` WHERE `config_key` = 'SEASON_OPENING_BANNER_ENABLED'
);
