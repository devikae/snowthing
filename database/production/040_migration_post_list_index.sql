-- IKA-53 게시판 목록 정렬용 인덱스.
-- 목록은 매번 count(*)와 ORDER BY created_at DESC, post_id DESC를 돌린다. post에는 FK 인덱스만 있어 글이 늘면
-- 카테고리 전체를 읽고 정렬한다. 카테고리 목록과 전체 목록 두 가지를 둔다.
-- 인덱스만 추가하므로 이전 앱 이미지에도 영향이 없다. 두 번 실행해도 안전하다.
DELIMITER $$

DROP PROCEDURE IF EXISTS `apply_post_list_indexes`$$
CREATE PROCEDURE `apply_post_list_indexes`()
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'post'
          AND index_name = 'idx_post_category_list'
    ) THEN
        ALTER TABLE `post`
            ADD INDEX `idx_post_category_list`
                (`category_id`, `is_deleted`, `status`, `created_at` DESC, `post_id` DESC);
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'post'
          AND index_name = 'idx_post_latest_list'
    ) THEN
        ALTER TABLE `post`
            ADD INDEX `idx_post_latest_list`
                (`is_deleted`, `status`, `created_at` DESC, `post_id` DESC);
    END IF;
END$$

DELIMITER ;

CALL `apply_post_list_indexes`();
DROP PROCEDURE IF EXISTS `apply_post_list_indexes`;
