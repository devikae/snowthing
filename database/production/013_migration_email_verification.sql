ALTER TABLE `member`
    ADD COLUMN `email_verified_at` DATETIME NULL AFTER `departure_region`;

CREATE TABLE `email_verification` (
    `verification_id` BIGINT NOT NULL AUTO_INCREMENT,
    `public_id` VARCHAR(36) NOT NULL,
    `email` VARCHAR(100) NOT NULL,
    `purpose` VARCHAR(30) NOT NULL,
    `code_digest` CHAR(64) NOT NULL,
    `token_digest` CHAR(64) NULL,
    `status` VARCHAR(20) NOT NULL,
    `failed_attempt_count` INT NOT NULL DEFAULT 0,
    `send_count` INT NOT NULL DEFAULT 1,
    `send_window_started_at` DATETIME NOT NULL,
    `request_ip` VARCHAR(45) NOT NULL,
    `sent_at` DATETIME NULL,
    `expires_at` DATETIME NOT NULL,
    `verified_at` DATETIME NULL,
    `token_expires_at` DATETIME NULL,
    `consumed_at` DATETIME NULL,
    `ses_message_id` VARCHAR(255) NULL,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`verification_id`),
    UNIQUE KEY `uk_email_verification_public_id` (`public_id`),
    UNIQUE KEY `uk_email_verification_email_purpose` (`email`, `purpose`),
    KEY `idx_email_verification_token_purpose` (`token_digest`, `purpose`),
    KEY `idx_email_verification_status_expires_at` (`status`, `expires_at`),
    KEY `idx_email_verification_expires_at` (`expires_at`),
    CONSTRAINT `chk_email_verification_purpose`
        CHECK (`purpose` IN ('SIGN_UP', 'PASSWORD_RESET')),
    CONSTRAINT `chk_email_verification_status`
        CHECK (`status` IN ('PENDING', 'SENT', 'VERIFIED', 'CONSUMED', 'SEND_FAILED', 'FAILED', 'EXPIRED')),
    CONSTRAINT `chk_email_verification_failed_attempt_count`
        CHECK (`failed_attempt_count` BETWEEN 0 AND 5),
    CONSTRAINT `chk_email_verification_send_count`
        CHECK (`send_count` >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='회원가입 및 비밀번호 재설정 이메일 인증 상태';
