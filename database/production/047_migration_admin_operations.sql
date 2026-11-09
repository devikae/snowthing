-- IKA-68 관리자 운영 제어 (PR #48의 027을 047로 옮김). 설계: docs/conception/017_admin_dashboard/01_design.md
-- 1) system_config: 관리자가 켜고 끄는 운영 설정. 지금은 비회원 라이브톡 발신 여부 하나다.
-- 2) chat_banned_ip: 라이브톡 IP 차단 목록. 애플리케이션 시작 때 유효한 차단을 메모리에 올린다.
-- 3) banner: 관리자 배너. 공개 조회는 활성이고 기간 안인 것만 내보낸다.
-- 4) post.deleted_by_member_id: 글을 지운 회원. 작성자 삭제와 관리자 삭제를 관리자 화면에서 구분한다.
--    탈퇴 회원이 지운 기록도 남기려고 FK를 두지 않는다. 이 컬럼으로 찾는 조회가 없어 인덱스도 두지 않는다 (PR #48에서 뺌).
-- PR #48의 post.is_blinded 컬럼은 만들지 않는다. 관리자 블라인드는 main의 시장 글 관리자 조치처럼 post.status = 'BLOCKED'로 표시한다.
-- 실행 순서: 이 마이그레이션을 적용한 뒤 새 애플리케이션을 배포한다. 추가만 하므로 이전 애플리케이션과도 호환된다.
-- 여러 번 실행해도 안전하다 (IF NOT EXISTS, 컬럼 존재 확인).

CREATE TABLE IF NOT EXISTS `system_config` (
    `config_key` VARCHAR(50) NOT NULL PRIMARY KEY,
    `config_value` VARCHAR(255) NOT NULL,
    `value_type` VARCHAR(20) NOT NULL DEFAULT 'BOOLEAN',
    `description` VARCHAR(200) NOT NULL,
    `updated_at` DATETIME(6) NOT NULL,
    `updated_by` VARCHAR(50) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO `system_config` (`config_key`, `config_value`, `value_type`, `description`, `updated_at`, `updated_by`)
SELECT 'CHAT_ANONYMOUS_ENABLED', 'false', 'BOOLEAN', '실시간 라이브톡 비로그인 사용자 발신 허용 여부', NOW(6), 'SYSTEM'
WHERE NOT EXISTS (
    SELECT 1 FROM `system_config` WHERE `config_key` = 'CHAT_ANONYMOUS_ENABLED'
);

CREATE TABLE IF NOT EXISTS `chat_banned_ip` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `banned_ip` VARCHAR(45) NOT NULL,
    `reason` VARCHAR(255) NOT NULL,
    `banned_until` DATETIME(6) NULL,
    `created_by` VARCHAR(50) NOT NULL,
    `created_at` DATETIME(6) NOT NULL,
    INDEX `idx_chat_banned_ip` (`banned_ip`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `banner` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `title` VARCHAR(100) NOT NULL,
    `image_url` VARCHAR(500) NOT NULL,
    `link_url` VARCHAR(500) NULL,
    `display_order` INT NOT NULL DEFAULT 0,
    `is_active` BOOLEAN NOT NULL DEFAULT TRUE,
    `start_at` DATETIME(6) NULL,
    `end_at` DATETIME(6) NULL,
    `created_at` DATETIME(6) NOT NULL,
    INDEX `idx_banner_active_order` (`is_active`, `display_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DELIMITER $$

DROP PROCEDURE IF EXISTS `apply_047_post_deleted_by_column`$$
CREATE PROCEDURE `apply_047_post_deleted_by_column`()
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'post'
          AND column_name = 'deleted_by_member_id'
    ) THEN
        ALTER TABLE `post`
            ADD COLUMN `deleted_by_member_id` BIGINT NULL AFTER `deleted_at`;
    END IF;
END$$

DELIMITER ;

CALL `apply_047_post_deleted_by_column`();
DROP PROCEDURE IF EXISTS `apply_047_post_deleted_by_column`;
