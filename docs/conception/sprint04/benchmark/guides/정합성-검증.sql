-- 실행 전 USE를 대상 benchmark 스키마로 변경한다.
SELECT COUNT(*) AS total_comments,
       SUM(parent_id IS NULL) AS roots,
       SUM(parent_id IS NOT NULL) AS replies,
       SUM(is_deleted = FALSE) AS active,
       SUM(is_deleted = TRUE) AS deleted
FROM comment;

SELECT COUNT(*) AS post_comment_count_mismatch
FROM post p
WHERE p.public_id LIKE 'benchmark-sprint04-post-%'
  AND p.comment_count <> (SELECT COUNT(*) FROM comment c WHERE c.post_id = p.post_id AND c.is_deleted = FALSE);

SELECT COUNT(*) AS reply_limit_violations
FROM (SELECT parent_id FROM comment WHERE parent_id IS NOT NULL AND is_deleted = FALSE GROUP BY parent_id HAVING COUNT(*) > 100) x;

SELECT COUNT(*) - COUNT(DISTINCT comment_id) AS duplicate_comment_ids
FROM comment;

SELECT COUNT(*) AS same_timestamp_rows
FROM (SELECT created_at FROM comment GROUP BY created_at HAVING COUNT(*) > 1) x;
