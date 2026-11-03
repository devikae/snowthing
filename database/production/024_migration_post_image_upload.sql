CREATE TABLE IF NOT EXISTS post_image_upload (
    image_key VARCHAR(160) NOT NULL PRIMARY KEY,
    owner_email VARCHAR(255) NOT NULL,
    state VARCHAR(20) NOT NULL,
    due_at DATETIME(6) NOT NULL,
    INDEX idx_image_upload_due (due_at)
);

DELIMITER $$
DROP PROCEDURE IF EXISTS apply_post_image_url_index$$
CREATE PROCEDURE apply_post_image_url_index()
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.statistics
        WHERE table_schema = DATABASE() AND table_name = 'post_image'
          AND index_name = 'idx_post_image_url'
    ) THEN
        ALTER TABLE post_image ADD INDEX idx_post_image_url (image_url);
    END IF;
END$$
DELIMITER ;
CALL apply_post_image_url_index();
DROP PROCEDURE IF EXISTS apply_post_image_url_index;

-- Enroll existing attachments conservatively. All posts, including soft-deleted ones,
-- remain references; existing unreferenced files require a separate reviewed manifest.
INSERT IGNORE INTO post_image_upload(image_key, owner_email, state, due_at)
SELECT REPLACE(pi.image_url, 'https://images.snowthing.org/', ''),
       MIN(COALESCE(m.email, 'legacy')), 'PENDING', DATE_ADD(UTC_TIMESTAMP(6), INTERVAL 1 DAY)
FROM post_image pi
JOIN post p ON p.post_id=pi.post_id
LEFT JOIN member m ON m.member_id=p.member_id
WHERE REPLACE(pi.image_url, 'https://images.snowthing.org/', '')
      REGEXP '^public/posts/(originals/)?[0-9a-f-]{36}[.](jpg|jpeg|png|webp)$'
GROUP BY REPLACE(pi.image_url, 'https://images.snowthing.org/', '');
