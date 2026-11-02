CREATE TABLE IF NOT EXISTS profile_image_cleanup (
    image_key VARCHAR(100) NOT NULL PRIMARY KEY,
    image_url VARCHAR(500) NOT NULL,
    not_before DATETIME(6) NOT NULL,
    attempts INT NOT NULL DEFAULT 0,
    INDEX idx_profile_image_cleanup_due (not_before)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
