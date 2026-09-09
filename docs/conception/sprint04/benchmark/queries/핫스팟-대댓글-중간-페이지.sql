SELECT c.comment_id, c.post_id, c.parent_id, c.content, c.is_deleted,
       c.created_at
FROM comment c WHERE c.parent_id = :rootCommentId
  AND (c.created_at > :cursorCreatedAt
       OR (c.created_at = :cursorCreatedAt AND c.comment_id > :cursorId))
ORDER BY c.created_at ASC, c.comment_id ASC LIMIT 21;
