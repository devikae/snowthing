SELECT c.comment_id, c.post_id, c.parent_id, c.content, c.is_deleted,
       c.created_at
FROM comment c WHERE c.parent_id = :rootCommentId
ORDER BY c.created_at ASC, c.comment_id ASC LIMIT 21;
