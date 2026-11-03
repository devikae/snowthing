CREATE TABLE IF NOT EXISTS market_thumbnail (
    post_public_id VARCHAR(36) NOT NULL PRIMARY KEY,
    source_key VARCHAR(500) NULL,
    ready_source_key VARCHAR(500) NULL,
    revision BIGINT NOT NULL DEFAULT 0,
    attempts INT NOT NULL DEFAULT 0,
    due_at DATETIME(6) NULL,
    lease_until DATETIME(6) NULL,
    INDEX idx_market_thumbnail_due (due_at)
);
