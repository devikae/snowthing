CREATE TABLE IF NOT EXISTS `member_registration_consent` (
    `member_id` BIGINT NOT NULL PRIMARY KEY,
    `privacy_version` VARCHAR(50) NOT NULL,
    `age_confirmed` BOOLEAN NOT NULL,
    `marketing_email_agreed` BOOLEAN NOT NULL,
    `confirmed_at` DATETIME NOT NULL,
    CONSTRAINT `fk_registration_consent_member` FOREIGN KEY (`member_id`) REFERENCES `member` (`member_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
