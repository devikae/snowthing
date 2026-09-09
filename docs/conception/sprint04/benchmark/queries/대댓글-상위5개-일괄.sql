SELECT c.comment_id, c.post_id, c.parent_id, c.content, c.is_deleted,
       c.created_at, ROW_NUMBER() OVER (
           PARTITION BY c.parent_id ORDER BY c.created_at ASC, c.comment_id ASC
       ) AS rn
FROM comment c
WHERE c.parent_id IN (:rootCommentIds);
