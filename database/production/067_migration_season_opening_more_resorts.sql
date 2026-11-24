-- 개장일 관리 표(season_opening)에 빠진 리조트 4곳을 넣는다.
-- resort 테이블에는 13곳이 있는데 052 마이그레이션은 9곳만 넣어서, 리조트 정보 페이지에서 이 4곳의 개장일이 "아직 정보가 없습니다"로 보였다.
-- 날짜는 2026-11-24 기사 기준 예상이다. 알펜시아 11-28 목표, 엘리시안 강촌 12-04 예고, 지산과 에덴밸리는 미정이라 비워 둔다.
-- 모두 확정 아님, 배너 표시 꺼짐으로 넣는다. 날짜와 배너 여부, 순서는 관리자 화면에서 바꾼다.
-- 코드 줄만 추가하므로 애플리케이션 변경은 없다. 저장 API는 요청 목록과 DB 목록이 같은지만 본다.
-- 여러 번 실행해도 안전하다 (INSERT IGNORE, resort_code UNIQUE). 이미 있는 줄은 덮어쓰지 않는다.
SET NAMES utf8mb4;

INSERT IGNORE INTO `season_opening`
    (`resort_code`, `resort_name`, `opening_date`, `confirmed`, `show_on_banner`, `banner_order`, `created_at`, `updated_at`)
VALUES
    ('ALPENSIA', '알펜시아', '2026-11-28', FALSE, FALSE, 10, NOW(6), NOW(6)),
    ('ELYSIAN', '엘리시안 강촌', '2026-12-04', FALSE, FALSE, 11, NOW(6), NOW(6)),
    ('JISAN', '지산', NULL, FALSE, FALSE, 12, NOW(6), NOW(6)),
    ('EDEN_VALLEY', '에덴밸리', NULL, FALSE, FALSE, 13, NOW(6), NOW(6));
