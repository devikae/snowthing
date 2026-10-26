-- ====================================================================
-- Snowthing Production Migration 016: Social Login (OAuth 2.0)
-- Description: 회원 1:N 소셜 계정 연동 테이블(member_social_account) 생성
-- ====================================================================

CREATE TABLE IF NOT EXISTS `member_social_account` (
    `social_account_id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '소셜 계정 연동 PK',
    `member_id` BIGINT NOT NULL COMMENT '연결된 회원 ID (member.member_id)',
    `provider` VARCHAR(20) NOT NULL COMMENT '소셜 제공자 (GOOGLE, NAVER)',
    `provider_user_id` VARCHAR(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '소셜 제공자 측 고유 회원 식별자',
    `provider_email` VARCHAR(100) NOT NULL COMMENT '소셜 제공자 측 인증 이메일 스냅샷',
    `connected_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '소셜 연동 일시',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성 일시',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정 일시',
    CONSTRAINT `uk_member_social_provider_user` UNIQUE (`provider`, `provider_user_id`),
    CONSTRAINT `uk_member_social_member_provider` UNIQUE (`member_id`, `provider`),
    CONSTRAINT `chk_member_social_provider` CHECK (`provider` IN ('GOOGLE', 'NAVER')),
    CONSTRAINT `fk_member_social_member` FOREIGN KEY (`member_id`) REFERENCES `member` (`member_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='회원 소셜 계정 연동';
