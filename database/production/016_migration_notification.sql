-- 회원 인앱 알림
CREATE TABLE IF NOT EXISTS `notification` (
    `notification_id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `public_id` VARCHAR(36) NOT NULL,
    `recipient_member_id` BIGINT NOT NULL,
    `type` VARCHAR(30) NOT NULL,
    `post_id` BIGINT NOT NULL,
    `comment_id` BIGINT NULL,
    `idempotency_key` VARCHAR(150) NOT NULL,
    `read_at` DATETIME NULL,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY `uk_notification_public_id` (`public_id`),
    UNIQUE KEY `uk_notification_idempotency_key` (`idempotency_key`),
    INDEX `idx_notification_recipient_id_desc` (`recipient_member_id`, `notification_id` DESC),
    INDEX `idx_notification_recipient_read_id` (`recipient_member_id`, `read_at`, `notification_id` DESC),
    INDEX `idx_notification_retention` (`read_at`, `created_at`),
    CONSTRAINT `fk_notification_recipient` FOREIGN KEY (`recipient_member_id`) REFERENCES `member` (`member_id`),
    CONSTRAINT `fk_notification_post` FOREIGN KEY (`post_id`) REFERENCES `post` (`post_id`),
    CONSTRAINT `fk_notification_comment` FOREIGN KEY (`comment_id`) REFERENCES `comment` (`comment_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
