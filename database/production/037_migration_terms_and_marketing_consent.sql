-- IKA-31, IKA-33 이용약관 동의 버전과 광고성 이메일 수신 동의 변경 이력.
-- 1) member_registration_consent.terms_version: 가입 때 동의한 이용약관 버전.
--    약관이 생기기 전에 가입한 행은 동의한 적이 없으므로 NULL로 둔다. 임의로 채우지 않는다.
-- 2) member_marketing_consent_history: 내 정보 수정에서 바꾼 수신 동의 이력. 추가만 하고 수정하지 않는다.
--    현재 값은 가장 최근 행이고, 행이 없으면 가입 때 값, 그것도 없으면 미동의다.
-- 실행 순서: 이 마이그레이션을 적용한 뒤 새 애플리케이션을 배포한다. 이전 애플리케이션과도 호환된다.

ALTER TABLE `member_registration_consent`
    ADD COLUMN `terms_version` VARCHAR(50) NULL COMMENT '동의한 이용약관 버전. 약관 도입 전 가입자는 NULL' AFTER `privacy_version`;

CREATE TABLE IF NOT EXISTS `member_marketing_consent_history` (
    `member_marketing_consent_history_id` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    `member_id` BIGINT NOT NULL,
    `agreed` BOOLEAN NOT NULL,
    `changed_at` DATETIME NOT NULL,
    INDEX `idx_marketing_consent_member_id` (`member_id`, `member_marketing_consent_history_id`),
    CONSTRAINT `fk_marketing_consent_member` FOREIGN KEY (`member_id`) REFERENCES `member` (`member_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
