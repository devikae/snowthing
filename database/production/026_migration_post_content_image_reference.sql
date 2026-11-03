-- Inline references are separate from gallery order and has_image.
CREATE TABLE IF NOT EXISTS post_content_image_reference (
    post_id BIGINT NOT NULL,
    image_key VARCHAR(160) NOT NULL,
    CONSTRAINT uk_post_content_image UNIQUE (post_id, image_key),
    INDEX idx_content_image_key (image_key),
    CONSTRAINT fk_content_image_post FOREIGN KEY (post_id) REFERENCES post(post_id) ON DELETE CASCADE
);

-- One-time backfill, including soft-deleted posts. Runtime cleanup never scans content.
INSERT IGNORE INTO post_content_image_reference(post_id, image_key)
SELECT p.post_id, u.image_key
FROM post p JOIN post_image_upload u ON LOCATE(u.image_key, p.content) > 0;
