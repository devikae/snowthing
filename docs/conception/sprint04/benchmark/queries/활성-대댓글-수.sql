SELECT COUNT(*) AS active_reply_count
FROM comment WHERE parent_id = :rootCommentId AND is_deleted = FALSE;
