SELECT c.comment_id, c.post_id, c.parent_id, c.content, c.is_deleted,
       c.is_anonymous, c.writer_ip, c.created_at,
       m.public_id AS member_public_id, m.nickname, m.profile_image_url
FROM comment c LEFT JOIN member m ON m.member_id = c.member_id
WHERE c.post_id = :postId AND c.parent_id IS NULL
ORDER BY c.created_at ASC, c.comment_id ASC LIMIT 21;
