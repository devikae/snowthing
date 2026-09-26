-- ====================================================================
-- Snowthing Production Migration 003 (MySQL 8.0)
-- Reconcile post reaction counters and enforce non-negative counters.
-- ====================================================================

SELECT
    COUNT(*) AS mismatch_post_count
FROM `post` p
LEFT JOIN (
    SELECT
        r.post_id,
        SUM(r.type = 'LIKE') AS like_count,
        SUM(r.type = 'DISLIKE') AS dislike_count
    FROM `post_reaction` r
    GROUP BY r.post_id
) actual ON actual.post_id = p.post_id
WHERE p.like_count <> COALESCE(actual.like_count, 0)
   OR p.dislike_count <> COALESCE(actual.dislike_count, 0);
UPDATE `post` p
LEFT JOIN (
    SELECT
        r.post_id,
        SUM(r.type = 'LIKE') AS like_count,
        SUM(r.type = 'DISLIKE') AS dislike_count
    FROM `post_reaction` r
    GROUP BY r.post_id
) actual ON actual.post_id = p.post_id
SET p.like_count = COALESCE(actual.like_count, 0),
    p.dislike_count = COALESCE(actual.dislike_count, 0),
    p.updated_at = p.updated_at
WHERE p.like_count <> COALESCE(actual.like_count, 0)
   OR p.dislike_count <> COALESCE(actual.dislike_count, 0);

DELIMITER $$

DROP PROCEDURE IF EXISTS `apply_post_reaction_count_checks`$$
CREATE PROCEDURE `apply_post_reaction_count_checks`()
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.table_constraints
        WHERE constraint_schema = DATABASE()
          AND table_name = 'post'
          AND constraint_name = 'chk_post_like_count_non_negative'
          AND constraint_type = 'CHECK'
    ) THEN
        ALTER TABLE `post`
            ADD CONSTRAINT `chk_post_like_count_non_negative`
            CHECK (`like_count` >= 0);
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.table_constraints
        WHERE constraint_schema = DATABASE()
          AND table_name = 'post'
          AND constraint_name = 'chk_post_dislike_count_non_negative'
          AND constraint_type = 'CHECK'
    ) THEN
        ALTER TABLE `post`
            ADD CONSTRAINT `chk_post_dislike_count_non_negative`
            CHECK (`dislike_count` >= 0);
    END IF;
END$$

CALL `apply_post_reaction_count_checks`()$$
DROP PROCEDURE `apply_post_reaction_count_checks`$$

DELIMITER ;

SELECT
    COUNT(*) AS remaining_mismatch_post_count
FROM `post` p
LEFT JOIN (
    SELECT
        r.post_id,
        SUM(r.type = 'LIKE') AS like_count,
        SUM(r.type = 'DISLIKE') AS dislike_count
    FROM `post_reaction` r
    GROUP BY r.post_id
) actual ON actual.post_id = p.post_id
WHERE p.like_count <> COALESCE(actual.like_count, 0)
   OR p.dislike_count <> COALESCE(actual.dislike_count, 0);
