-- ==============================================================================
-- [Spike 실험용 대용량 1,000건 댓글 데이터 시드 스크립트 (MySQL 8.0 전용)]
-- ==============================================================================

USE `snowthing`;

-- 0. 기존 Spike 데이터 초기화
DELETE FROM `comment` WHERE `post_id` IN (998, 999);
DELETE FROM `post` WHERE `post_id` IN (998, 999);

-- 1. 테스트용 기본 카테고리 및 회원 확인/생성
INSERT IGNORE INTO `post_category` (`category_id`, `name`, `code`) VALUES (1, '자유게시판', 'FREE');
INSERT IGNORE INTO `member` (`member_id`, `public_id`, `email`, `password`, `nickname`, `role`, `status`, `created_at`, `updated_at`)
VALUES (1, 'member-spike-001', 'spike@snowthing.com', '$2a$10$dummyHashValueForSpikeTestingOnly1234567890', '스파이크테스터', 'ROLE_USER', 'ACTIVE', NOW(), NOW());

-- 2. 테스트용 게시글 2개 생성
-- Post 998: 시나리오 A (분산 1,000건용)
INSERT INTO `post` (`post_id`, `public_id`, `member_id`, `category_id`, `title`, `content`, `writer_ip`, `is_anonymous`, `comment_count`, `created_at`, `updated_at`)
VALUES (998, 'post-spike-distributed-998', 1, 1, 'Spike [시나리오 A] 분산 1,000건 테스트 글', '내용', '127.0.0.1', FALSE, 1000, NOW(), NOW());

-- Post 999: 시나리오 B (집중 핫스팟 1,000건용)
INSERT INTO `post` (`post_id`, `public_id`, `member_id`, `category_id`, `title`, `content`, `writer_ip`, `is_anonymous`, `comment_count`, `created_at`, `updated_at`)
VALUES (999, 'post-spike-hotspot-999', 1, 1, 'Spike [시나리오 B] 핫스팟 500건 집중 테스트 글', '내용', '127.0.0.1', FALSE, 1000, NOW(), NOW());

-- ==============================================================================
-- [시나리오 A] Post 998 : 루트 댓글 100개 + 각 루트당 대댓글 9개 = 총 1,000개
-- ==============================================================================
DROP PROCEDURE IF EXISTS InsertDistributedComments;
DELIMITER $$
CREATE PROCEDURE InsertDistributedComments()
BEGIN
    DECLARE root_idx INT DEFAULT 1;
    DECLARE reply_idx INT DEFAULT 1;
    DECLARE current_root_id BIGINT;

    -- 1. 루트 댓글 100개 생성
    WHILE root_idx <= 100 DO
        INSERT INTO `comment` (`post_id`, `member_id`, `parent_id`, `content`, `writer_ip`, `is_anonymous`, `is_deleted`, `created_at`, `updated_at`)
        VALUES (998, 1, NULL, CONCAT('루트 댓글 #', root_idx), '127.0.0.1', FALSE, FALSE, NOW() + INTERVAL root_idx SECOND, NOW());
        
        SET current_root_id = LAST_INSERT_ID();
        
        -- 2. 각 루트당 대댓글 9개씩 생성 (총 900개)
        SET reply_idx = 1;
        WHILE reply_idx <= 9 DO
            INSERT INTO `comment` (`post_id`, `member_id`, `parent_id`, `content`, `writer_ip`, `is_anonymous`, `is_deleted`, `created_at`, `updated_at`)
            VALUES (998, 1, current_root_id, CONCAT('대댓글 #', reply_idx, ' (부모:', current_root_id, ')'), '127.0.0.1', FALSE, FALSE, NOW() + INTERVAL (root_idx * 10 + reply_idx) SECOND, NOW());
            SET reply_idx = reply_idx + 1;
        END WHILE;

        SET root_idx = root_idx + 1;
    END WHILE;
END$$
DELIMITER ;

CALL InsertDistributedComments();
DROP PROCEDURE IF EXISTS InsertDistributedComments;


-- ==============================================================================
-- [시나리오 B] Post 999 : 루트 댓글 500개 + 1번 루트에 대댓글 500개 집중 = 총 1,000개
-- ==============================================================================
DROP PROCEDURE IF EXISTS InsertHotspotComments;
DELIMITER $$
CREATE PROCEDURE InsertHotspotComments()
BEGIN
    DECLARE root_idx INT DEFAULT 1;
    DECLARE reply_idx INT DEFAULT 1;
    DECLARE hotspot_root_id BIGINT;

    -- 1. 루트 댓글 500개 생성
    WHILE root_idx <= 500 DO
        INSERT INTO `comment` (`post_id`, `member_id`, `parent_id`, `content`, `writer_ip`, `is_anonymous`, `is_deleted`, `created_at`, `updated_at`)
        VALUES (999, 1, NULL, CONCAT('루트 댓글 #', root_idx), '127.0.0.1', FALSE, FALSE, NOW() + INTERVAL root_idx SECOND, NOW());
        
        IF root_idx = 1 THEN
            SET hotspot_root_id = LAST_INSERT_ID();
        END IF;

        SET root_idx = root_idx + 1;
    END WHILE;

    -- 2. 1번 루트 댓글에 대댓글 500개 집중 생성
    WHILE reply_idx <= 500 DO
        INSERT INTO `comment` (`post_id`, `member_id`, `parent_id`, `content`, `writer_ip`, `is_anonymous`, `is_deleted`, `created_at`, `updated_at`)
        VALUES (999, 1, hotspot_root_id, CONCAT('핫스팟 대댓글 #', reply_idx), '127.0.0.1', FALSE, FALSE, NOW() + INTERVAL (500 + reply_idx) SECOND, NOW());
        SET reply_idx = reply_idx + 1;
    END WHILE;
END$$
DELIMITER ;

CALL InsertHotspotComments();
DROP PROCEDURE IF EXISTS InsertHotspotComments;

-- 최종 생성 건수 확인
SELECT `post_id`, COUNT(*) AS total_comments, 
       SUM(CASE WHEN `parent_id` IS NULL THEN 1 ELSE 0 END) AS root_count,
       SUM(CASE WHEN `parent_id` IS NOT NULL THEN 1 ELSE 0 END) AS reply_count
FROM `comment` 
WHERE `post_id` IN (998, 999) 
GROUP BY `post_id`;
