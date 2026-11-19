-- IKA-98 국내와 일본 슬로프 제원 비교와 코스 맵. 설계: docs/conception/033_slope_compare/01_design.md
-- 1) resort.country: 리조트가 있는 나라. 기존 행은 모두 KR이다.
--    프로필, 카풀, 설질 제보, 시즌 로그, 운영 현황판, 날씨처럼 커뮤니티 기능에서 쓰는 리조트 목록은 KR만 쓴다.
--    JP 리조트는 슬로프 제원 비교 화면에서만 보인다.
-- 2) 일본 리조트 10곳 추가. 코드는 기존 규칙(대문자, 밑줄)을 따른다. display_order는 국내와 겹치지 않게 101부터 쓴다.
-- 3) slope: 리조트 공식 자료로 정리한 슬로프 제원. 공식 값이 없으면 NULL이고 추정하지 않는다.
--    운영 현황판(046 resort_slope_status)은 운영 여부 스냅샷이고, 이 테이블은 바뀌지 않는 제원이다.
-- 4) slope_connection: 슬로프 사이 관계(합류, 갈라짐, 이어짐, 갈라져 시작, 출발점 공유, 도착점 공유, 교차, 나란히).
--    방향이 있어 양쪽 줄을 따로 두고, 한 쌍의 두 줄은 서로 맞물리게 넣는다(MERGE와 CONTINUE, BRANCH와 STARTS_FROM).
--    OSM 선으로 자동 계산한 초안을 검수해 확정한 값만 넣는다.
-- 실행 순서: 이 마이그레이션을 적용한 뒤 새 애플리케이션을 배포한다(ddl-auto validate).
--   country는 기본값이 있어 이전 애플리케이션과 호환된다. 일본 리조트는 is_active=FALSE로 넣어
--   country를 모르는 이전 애플리케이션의 활성 목록(프로필, 카풀 등)에 섞이지 않는다.
--   새 애플리케이션은 슬로프 제원 화면에서 해외 리조트를 활성 여부와 상관없이 보여 준다.
-- 여러 번 실행해도 안전하다 (컬럼 존재 확인, IF NOT EXISTS, INSERT IGNORE).
SET NAMES utf8mb4;


SET @country_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'resort' AND COLUMN_NAME = 'country'
);
SET @add_country := IF(
    @country_exists = 0,
    'ALTER TABLE `resort` ADD COLUMN `country` VARCHAR(2) NOT NULL DEFAULT ''KR'' COMMENT ''리조트가 있는 나라 (KR, JP). 커뮤니티 기능은 KR만 쓴다'' AFTER `is_active`',
    'SELECT 1'
);
PREPARE add_country_stmt FROM @add_country;
EXECUTE add_country_stmt;
DEALLOCATE PREPARE add_country_stmt;

INSERT IGNORE INTO `resort` (`name`, `region`, `code`, `display_order`, `is_active`, `country`)
VALUES
    ('니세코 그랑히라후', '홋카이도 니세코', 'NISEKO_HIRAFU', 101, FALSE, 'JP'),
    ('루스츠', '홋카이도 루스츠', 'RUSUTSU', 102, FALSE, 'JP'),
    ('키로로', '홋카이도 아카이가와', 'KIRORO', 103, FALSE, 'JP'),
    ('토마무', '홋카이도 시무캇푸', 'TOMAMU', 104, FALSE, 'JP'),
    ('후라노', '홋카이도 후라노', 'FURANO', 105, FALSE, 'JP'),
    ('삿포로 테이네', '홋카이도 삿포로', 'SAPPORO_TEINE', 106, FALSE, 'JP'),
    ('갈라유자와', '니가타 유자와', 'GALA_YUZAWA', 107, FALSE, 'JP'),
    ('나에바', '니가타 유자와', 'NAEBA', 108, FALSE, 'JP'),
    ('하쿠바 핫포오네', '나가노 하쿠바', 'HAKUBA_HAPPO', 109, FALSE, 'JP'),
    ('노자와온센', '나가노 노자와온센', 'NOZAWA_ONSEN', 110, FALSE, 'JP');

CREATE TABLE IF NOT EXISTS `slope` (
    `slope_id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `resort_id` BIGINT NOT NULL COMMENT '리조트 ID',
    `name` VARCHAR(100) NOT NULL COMMENT '화면에 보이는 이름. 공식 한국어 이름이 있으면 그 이름',
    `name_local` VARCHAR(100) NULL COMMENT '현지 공식 이름 (일본 리조트의 일본어, 영어 이름)',
    `official_difficulty` VARCHAR(30) NULL COMMENT '리조트가 표기한 난이도 그대로',
    `difficulty` VARCHAR(20) NOT NULL COMMENT '색 표시용 난이도 묶음 (BEGINNER, INTERMEDIATE, ADVANCED, EXPERT)',
    `length_m` INT NULL COMMENT '공식 길이 (m). 미공개면 NULL',
    `avg_gradient` DECIMAL(4,1) NULL COMMENT '공식 평균 경사 (도). 미공개면 NULL',
    `max_gradient` DECIMAL(4,1) NULL COMMENT '공식 최대 경사 (도). 미공개면 NULL',
    `width_m` INT NULL COMMENT '공식 폭 (m). 미공개면 NULL',
    `lift_name` VARCHAR(100) NULL COMMENT '이 슬로프로 올라가는 리프트 이름',
    `osm_way_ids` VARCHAR(300) NULL COMMENT '코스 맵 선과 연결하는 OSM way ID 목록 (쉼표 구분). 선이 없으면 NULL',
    `source_url` VARCHAR(500) NOT NULL COMMENT '제원 출처 공식 페이지',
    `checked_at` DATE NOT NULL COMMENT '출처를 확인한 날짜 (KST)',
    `display_order` INT NOT NULL COMMENT '리조트 안 표시 순서',
    `created_at` DATETIME(6) NOT NULL,
    `updated_at` DATETIME(6) NOT NULL,
    UNIQUE KEY `uk_slope_resort_name` (`resort_id`, `name`),
    KEY `idx_slope_resort_order` (`resort_id`, `display_order`, `slope_id`),
    CONSTRAINT `fk_slope_resort` FOREIGN KEY (`resort_id`) REFERENCES `resort` (`resort_id`),
    CONSTRAINT `chk_slope_difficulty` CHECK (`difficulty` IN ('BEGINNER', 'INTERMEDIATE', 'ADVANCED', 'EXPERT')),
    CONSTRAINT `chk_slope_gradient` CHECK (
        (`avg_gradient` IS NULL OR `avg_gradient` BETWEEN 0 AND 60)
        AND (`max_gradient` IS NULL OR `max_gradient` BETWEEN 0 AND 60)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='슬로프 공식 제원';

CREATE TABLE IF NOT EXISTS `slope_connection` (
    `slope_connection_id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `slope_id` BIGINT NOT NULL COMMENT '기준 슬로프 ID',
    `related_slope_id` BIGINT NOT NULL COMMENT '관계가 있는 슬로프 ID',
    `relation` VARCHAR(20) NOT NULL COMMENT '기준 슬로프에서 본 관계 (MERGE, BRANCH, CONTINUE, STARTS_FROM, SHARE_START, SHARE_END, CROSS, PARALLEL)',
    `distance_m` INT NULL COMMENT 'PARALLEL일 때 두 코스 사이 최소 거리 (m)',
    `created_at` DATETIME(6) NOT NULL,
    `updated_at` DATETIME(6) NOT NULL,
    UNIQUE KEY `uk_slope_connection_pair` (`slope_id`, `related_slope_id`),
    KEY `idx_slope_connection_related` (`related_slope_id`),
    CONSTRAINT `fk_slope_connection_slope` FOREIGN KEY (`slope_id`) REFERENCES `slope` (`slope_id`) ON DELETE CASCADE,
    CONSTRAINT `fk_slope_connection_related` FOREIGN KEY (`related_slope_id`) REFERENCES `slope` (`slope_id`) ON DELETE CASCADE,
    CONSTRAINT `chk_slope_connection_relation` CHECK (`relation` IN ('MERGE', 'BRANCH', 'CONTINUE', 'STARTS_FROM', 'SHARE_START', 'SHARE_END', 'CROSS', 'PARALLEL')),
    CONSTRAINT `chk_slope_connection_self` CHECK (`slope_id` <> `related_slope_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='슬로프 사이 관계 (검수한 값)';
