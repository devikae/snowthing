CREATE TABLE IF NOT EXISTS request_budget_window (
    budget_key VARCHAR(100) NOT NULL PRIMARY KEY,
    used_count INT NOT NULL DEFAULT 0,
    expires_at_millis BIGINT NOT NULL,
    INDEX idx_request_budget_expiry (expires_at_millis)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
