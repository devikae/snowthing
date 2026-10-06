-- Migration 015: 오늘의 설질 (Resort Report) 테이블 및 인덱스 추가

CREATE TABLE IF NOT EXISTS resort_report (
    report_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    resort_id BIGINT NOT NULL,
    member_id BIGINT NOT NULL,
    content VARCHAR(100) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_resort_report_resort FOREIGN KEY (resort_id) REFERENCES resort (resort_id) ON DELETE CASCADE,
    CONSTRAINT fk_resort_report_member FOREIGN KEY (member_id) REFERENCES member (member_id) ON DELETE CASCADE,
    INDEX idx_resort_report_created_resort (created_at DESC, resort_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
