-- 엔티티가 없어 create-drop이 만들지 않는 요청 제한 테이블. 운영 정의는 database/production/022_migration_shared_request_budget.sql
CREATE TABLE IF NOT EXISTS request_budget_window (
    budget_key VARCHAR(100) NOT NULL PRIMARY KEY,
    used_count INT NOT NULL DEFAULT 0,
    expires_at_millis BIGINT NOT NULL,
    INDEX idx_request_budget_expiry (expires_at_millis)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 엔티티가 없어 create-drop이 만들지 않는 로그인 유지 토큰 테이블. 운영 정의는 database/production/056_migration_persistent_logins.sql
CREATE TABLE IF NOT EXISTS persistent_logins (
    series VARCHAR(64) NOT NULL PRIMARY KEY,
    member_public_id VARCHAR(36) NOT NULL,
    token_hash CHAR(64) NOT NULL,
    previous_token_hash CHAR(64) NULL,
    last_used_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    INDEX idx_persistent_logins_member (member_public_id),
    INDEX idx_persistent_logins_last_used (last_used_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
