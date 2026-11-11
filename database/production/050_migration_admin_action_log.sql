-- IKA-78 관리자 운영 기능 확장. 설계: docs/conception/017_admin_dashboard/01_design.md 7절
-- 1) admin_action_log: 관리자 조치 기록. 조치와 같은 트랜잭션에서 한 줄씩 쓴다. 글 본문은 남기지 않고 대상 종류와 식별자, 조치, 사유만 둔다.
--    탈퇴 회원이 한 조치도 남기려고 actor_member_id에 FK를 두지 않는다.
-- 2) member, post, comment의 created_at 인덱스: 대시보드의 최근 30일 일별 집계가 표 전체를 읽지 않고 기간 범위만 읽게 한다.
--    post의 기존 목록 인덱스는 is_deleted, status가 앞이라 삭제 글까지 세는 기간 집계에 쓸 수 없다. comment와 member에는 created_at 인덱스가 없다.
-- 실행 순서: 이 마이그레이션을 적용한 뒤 새 애플리케이션을 배포한다. 추가만 하므로 이전 애플리케이션과도 호환된다.
-- 인덱스 추가는 InnoDB 온라인 DDL(ALGORITHM=INPLACE, LOCK=NONE)이라 쓰기를 막지 않는다.
-- 여러 번 실행해도 안전하다 (IF NOT EXISTS, 인덱스 존재 확인).

CREATE TABLE IF NOT EXISTS `admin_action_log` (
    `admin_action_log_id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `actor_member_id` BIGINT NULL COMMENT '조치한 관리자 회원 ID (스케줄러 등 사람이 아닌 조치는 NULL)',
    `action` VARCHAR(40) NOT NULL COMMENT '조치 종류 (MEMBER_SUSPEND, POST_BLIND 등)',
    `target_type` VARCHAR(30) NOT NULL COMMENT '대상 종류 (MEMBER, POST, COMMENT 등)',
    `target_id` VARCHAR(64) NOT NULL COMMENT '대상 식별자 (공개 ID 또는 번호)',
    `reason` VARCHAR(255) NULL COMMENT '사유나 바뀐 상태. 글 본문은 넣지 않는다',
    `created_at` DATETIME(6) NOT NULL,
    INDEX `idx_admin_action_log_action` (`action`, `admin_action_log_id`),
    INDEX `idx_admin_action_log_target` (`target_type`, `target_id`, `admin_action_log_id`),
    INDEX `idx_admin_action_log_actor` (`actor_member_id`, `admin_action_log_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='관리자 조치 기록';

DELIMITER $$

DROP PROCEDURE IF EXISTS `apply_050_created_at_index`$$
CREATE PROCEDURE `apply_050_created_at_index`(IN p_table VARCHAR(64), IN p_index VARCHAR(64))
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = p_table
          AND index_name = p_index
    ) THEN
        SET @ddl = CONCAT('ALTER TABLE `', p_table, '` ADD INDEX `', p_index, '` (`created_at`), ALGORITHM=INPLACE, LOCK=NONE');
        PREPARE stmt FROM @ddl;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END$$

DELIMITER ;

CALL `apply_050_created_at_index`('member', 'idx_member_created_at');
CALL `apply_050_created_at_index`('post', 'idx_post_created_at');
CALL `apply_050_created_at_index`('comment', 'idx_comment_created_at');
DROP PROCEDURE IF EXISTS `apply_050_created_at_index`;
