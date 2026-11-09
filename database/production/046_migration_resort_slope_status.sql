-- IKA-66 리조트 슬로프 운영 현황판.
-- resort_slope_status: 리조트당 한 줄. 공식 현황 페이지를 하루 몇 번 읽어 마지막 성공 스냅샷을 둔다.
--   수집에 성공하면 slopes_json, 열린/전체 슬로프 수, collected_at을 통째로 바꾼다.
--   실패하면 마지막 성공 값은 두고 last_attempt_at, last_result='FAILED', failure_reason만 바꾼다.
--   slopes_json은 [{name, difficulty, day, night, lateNight, note}] 배열이다. 리조트마다 슬로프 이름과 칸이 달라 행으로 나누지 않았다.
--   failure_reason은 운영 확인용이며 API로 내보내지 않는다.
-- 시각은 KST DATETIME(6)이다.
-- 새 테이블이라 이전 앱 이미지에는 영향이 없다. 앱 배포(ddl-auto validate) 전에 적용한다. 두 번 실행해도 안전하다.
-- 설계: docs/conception/030_slope_status/01_design.md

CREATE TABLE IF NOT EXISTS resort_slope_status (
    resort_slope_status_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    resort_id BIGINT NOT NULL,
    source_url VARCHAR(500) NOT NULL COMMENT '사용자에게 보여 줄 공식 현황 페이지 주소',
    slopes_json MEDIUMTEXT NULL COMMENT '마지막 성공 슬로프 목록 JSON. 한 번도 성공하지 못했으면 NULL',
    open_slope_count INT NULL COMMENT '마지막 성공 기준 한 시간대라도 연 슬로프 수',
    total_slope_count INT NULL COMMENT '마지막 성공 기준 전체 슬로프 수',
    operating_note VARCHAR(300) NULL COMMENT '출처가 적은 운영 시간 문구',
    collected_at DATETIME(6) NULL COMMENT '마지막 성공 시각 (KST)',
    last_attempt_at DATETIME(6) NOT NULL COMMENT '마지막 시도 시각 (KST)',
    last_result VARCHAR(20) NOT NULL COMMENT 'SUCCESS, FAILED',
    failure_reason VARCHAR(200) NULL COMMENT '마지막 실패 사유. 운영 확인용',
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT uk_resort_slope_status_resort UNIQUE (resort_id),
    CONSTRAINT fk_resort_slope_status_resort FOREIGN KEY (resort_id) REFERENCES resort (resort_id),
    CONSTRAINT chk_resort_slope_status_result CHECK (last_result IN ('SUCCESS', 'FAILED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='리조트별 슬로프 운영 현황 최신 스냅샷';
