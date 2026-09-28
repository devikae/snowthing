-- ====================================================================
-- Snowthing Production Migration
-- Version: 004
-- Description: 중고장터 상품 분류와 판매글 확장 테이블 추가
-- ====================================================================

CREATE TABLE IF NOT EXISTS `market_category` (
    `market_category_id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `code` VARCHAR(50) NOT NULL,
    `name` VARCHAR(50) NOT NULL,
    `sort_order` INT NOT NULL,
    `is_active` BOOLEAN NOT NULL DEFAULT TRUE,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT `uk_market_category_code` UNIQUE (`code`),
    CONSTRAINT `chk_market_category_sort_order` CHECK (`sort_order` >= 0),
    INDEX `idx_market_category_active_sort` (`is_active`, `sort_order`, `market_category_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `market_listing` (
    `market_listing_id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `post_id` BIGINT NOT NULL,
    `market_category_id` BIGINT NOT NULL,
    `product_condition` VARCHAR(20) NOT NULL,
    `transaction_method` VARCHAR(20) NOT NULL,
    `trade_status` VARCHAR(20) NOT NULL DEFAULT 'ON_SALE',
    `price` BIGINT NOT NULL,
    `is_negotiable` BOOLEAN NOT NULL DEFAULT FALSE,
    `is_free` BOOLEAN NOT NULL DEFAULT FALSE,
    `contact` VARCHAR(30) NOT NULL,
    `version` BIGINT NOT NULL DEFAULT 0,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT `uk_market_listing_post` UNIQUE (`post_id`),
    CONSTRAINT `fk_market_listing_post`
        FOREIGN KEY (`post_id`) REFERENCES `post` (`post_id`),
    CONSTRAINT `fk_market_listing_category`
        FOREIGN KEY (`market_category_id`) REFERENCES `market_category` (`market_category_id`),
    CONSTRAINT `chk_market_listing_condition`
        CHECK (`product_condition` IN ('NEW', 'LIKE_NEW', 'GOOD', 'USED', 'DAMAGED')),
    CONSTRAINT `chk_market_listing_transaction_method`
        CHECK (`transaction_method` IN ('DIRECT', 'DELIVERY', 'BOTH')),
    CONSTRAINT `chk_market_listing_trade_status`
        CHECK (`trade_status` IN ('ON_SALE', 'RESERVED', 'SOLD')),
    CONSTRAINT `chk_market_listing_price_non_negative` CHECK (`price` >= 0),
    CONSTRAINT `chk_market_listing_price_mode`
        CHECK ((`is_free` = TRUE AND `price` = 0 AND `is_negotiable` = FALSE)
            OR (`is_free` = FALSE AND `price` BETWEEN 1 AND 20000000)),
    CONSTRAINT `chk_market_listing_contact`
        CHECK (CHAR_LENGTH(TRIM(`contact`)) BETWEEN 1 AND 30),
    INDEX `idx_market_listing_trade_created`
        (`trade_status`, `created_at`, `market_listing_id`),
    INDEX `idx_market_listing_category_trade_created`
        (`market_category_id`, `trade_status`, `created_at`, `market_listing_id`),
    INDEX `idx_market_listing_condition_trade_created`
        (`product_condition`, `trade_status`, `created_at`, `market_listing_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO `post_category` (`name`, `code`)
VALUES ('중고장터', 'MARKET')
ON DUPLICATE KEY UPDATE `name` = VALUES(`name`);

INSERT INTO `market_category` (`code`, `name`, `sort_order`, `is_active`) VALUES
('SNOWBOARD', '스노보드', 1, TRUE),
('SKI', '스키', 2, TRUE),
('BINDING', '바인딩', 3, TRUE),
('BOOTS', '부츠', 4, TRUE),
('APPAREL', '의류', 5, TRUE),
('PROTECTIVE_GEAR', '보호장비', 6, TRUE),
('ACCESSORY', '액세서리', 7, TRUE),
('PASS', '시즌권·이용권', 8, TRUE),
('OTHER', '기타', 9, TRUE)
ON DUPLICATE KEY UPDATE
    `name` = VALUES(`name`),
    `sort_order` = VALUES(`sort_order`);
