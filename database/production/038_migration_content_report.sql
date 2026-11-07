-- IKA-32 게시물 신고와 임시조치. 설계: docs/conception/025_content_report/01_design.md
-- 1) content_report: 회원의 신고. 같은 회원이 같은 대상을 두 번 신고하지 못한다.
-- 2) content_moderation_action: 관리자와 자동 복구의 조치 이력. 추가만 하고, 임시조치 행만 해제 시각을 기록한다.
--    (action_type, released_at, expires_at) 인덱스는 만료된 임시조치를 찾는 정기 작업용이다.
-- 3) comment.moderation_held: 임시조치로 가린 댓글 표시. 삭제 상태를 재사용해 검색과 AI 조회에서 자동으로 빠진다.
-- 4) notification.comment_id를 NULL 허용으로 바꾼다. 게시글 조치 알림에는 댓글이 없다.
-- 글의 임시조치는 post.status = 'TEMPORARY_HOLD'로 표시한다. 컬럼이 VARCHAR라 변경이 필요 없다.
-- 실행 순서: 이 마이그레이션을 적용한 뒤 새 애플리케이션을 배포한다. 이전 애플리케이션과도 호환된다.
-- 되돌릴 때 주의: TEMPORARY_HOLD 글이 있으면 이전 애플리케이션이 상태 값을 읽지 못한다. 먼저 복구한다.

CREATE TABLE IF NOT EXISTS `content_report` (
    `content_report_id` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    `target_type` VARCHAR(20) NOT NULL COMMENT 'POST, COMMENT',
    `target_id` BIGINT NOT NULL COMMENT 'post_id 또는 comment_id. 삭제되어도 이력이 남도록 FK를 두지 않는다',
    `reporter_id` BIGINT NOT NULL,
    `reason` VARCHAR(30) NOT NULL,
    `detail` VARCHAR(500) NULL,
    `status` VARCHAR(20) NOT NULL COMMENT 'PENDING, RESOLVED',
    `created_at` DATETIME NOT NULL,
    `resolved_at` DATETIME NULL,
    UNIQUE KEY `uk_content_report_target_reporter` (`target_type`, `target_id`, `reporter_id`),
    INDEX `idx_content_report_status_id` (`status`, `content_report_id`),
    CONSTRAINT `fk_content_report_reporter` FOREIGN KEY (`reporter_id`) REFERENCES `member` (`member_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `content_moderation_action` (
    `content_moderation_action_id` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    `target_type` VARCHAR(20) NOT NULL,
    `target_id` BIGINT NOT NULL,
    `action_type` VARCHAR(20) NOT NULL COMMENT 'TEMPORARY_HOLD, RESTORE, DELETE, DISMISS',
    `memo` VARCHAR(500) NULL,
    `admin_id` BIGINT NULL COMMENT '기간 만료 자동 복구는 NULL',
    `expires_at` DATETIME NULL COMMENT '임시조치 만료 시각',
    `released_at` DATETIME NULL COMMENT '임시조치 해제 시각',
    `created_at` DATETIME NOT NULL,
    INDEX `idx_moderation_action_target` (`target_type`, `target_id`, `content_moderation_action_id`),
    INDEX `idx_moderation_action_expiry` (`action_type`, `released_at`, `expires_at`),
    CONSTRAINT `fk_moderation_action_admin` FOREIGN KEY (`admin_id`) REFERENCES `member` (`member_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE `comment`
    ADD COLUMN `moderation_held` BOOLEAN NOT NULL DEFAULT FALSE COMMENT '임시조치로 가린 댓글이면 TRUE';

ALTER TABLE `notification`
    MODIFY COLUMN `comment_id` BIGINT NULL COMMENT '알림을 만든 댓글. 게시글 조치 알림은 NULL. FK 없음';
