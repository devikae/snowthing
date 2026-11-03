-- Migration 027: Admin system dashboard, feature flags, visitor stats, chat IP ban, and banners
CREATE TABLE IF NOT EXISTS system_config (
    config_key VARCHAR(50) NOT NULL PRIMARY KEY,
    config_value VARCHAR(255) NOT NULL,
    value_type VARCHAR(20) NOT NULL DEFAULT 'BOOLEAN',
    description VARCHAR(200) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    updated_by VARCHAR(50) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO system_config (config_key, config_value, value_type, description, updated_at, updated_by)
SELECT 'CHAT_ANONYMOUS_ENABLED', 'false', 'BOOLEAN', '실시간 라이브톡 비로그인 사용자 발신 허용 여부', NOW(6), 'SYSTEM'
WHERE NOT EXISTS (
    SELECT 1 FROM system_config WHERE config_key = 'CHAT_ANONYMOUS_ENABLED'
);

CREATE TABLE IF NOT EXISTS daily_visitor_log (
    visit_date DATE NOT NULL,
    visitor_hash CHAR(64) NOT NULL,
    is_member BOOLEAN NOT NULL DEFAULT FALSE,
    first_visited_at DATETIME(6) NOT NULL,
    PRIMARY KEY (visit_date, visitor_hash),
    INDEX idx_visitor_date (visit_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS daily_visitor_stats (
    stat_date DATE NOT NULL PRIMARY KEY,
    total_visitors INT NOT NULL DEFAULT 0,
    member_visitors INT NOT NULL DEFAULT 0,
    guest_visitors INT NOT NULL DEFAULT 0,
    pv_count INT NOT NULL DEFAULT 0,
    updated_at DATETIME(6) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS chat_banned_ip (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    banned_ip VARCHAR(45) NOT NULL,
    reason VARCHAR(255) NOT NULL,
    banned_until DATETIME(6) NULL,
    created_by VARCHAR(50) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    INDEX idx_chat_banned_ip (banned_ip)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS banner (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(100) NOT NULL,
    image_url VARCHAR(500) NOT NULL,
    link_url VARCHAR(500) NULL,
    display_order INT NOT NULL DEFAULT 0,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    start_at DATETIME(6) NULL,
    end_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    INDEX idx_banner_active_order (is_active, display_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DELIMITER $$

DROP PROCEDURE IF EXISTS `apply_post_blinded_column`$$
CREATE PROCEDURE `apply_post_blinded_column`()
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'post'
          AND column_name = 'is_blinded'
    ) THEN
        ALTER TABLE `post`
            ADD COLUMN `is_blinded` BOOLEAN NOT NULL DEFAULT FALSE AFTER `is_deleted`,
            ADD INDEX `idx_post_blinded` (`is_blinded`);
    END IF;
END$$

DELIMITER ;

CALL `apply_post_blinded_column`();
DROP PROCEDURE IF EXISTS `apply_post_blinded_column`;

DELIMITER $$

DROP PROCEDURE IF EXISTS `apply_post_deleted_by_column`$$
CREATE PROCEDURE `apply_post_deleted_by_column`()
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'post'
          AND column_name = 'deleted_by_member_id'
    ) THEN
        ALTER TABLE `post`
            ADD COLUMN `deleted_by_member_id` BIGINT NULL AFTER `deleted_at`,
            ADD INDEX `idx_post_deleted_by` (`deleted_by_member_id`);
    END IF;
END$$

DELIMITER ;

CALL `apply_post_deleted_by_column`();
DROP PROCEDURE IF EXISTS `apply_post_deleted_by_column`;
