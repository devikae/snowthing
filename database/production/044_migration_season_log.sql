-- IKA-64 시즌 방문 기록과 시즌 결산 공유.
-- 1) season_visit: 회원이 리조트를 다녀온 날. UNIQUE(member_id, visit_date)로 하루 한 번만 기록한다.
--    같은 키가 시즌 범위 조회(member_id, visit_date BETWEEN)에도 쓰인다. visit_date는 KST 날짜다.
-- 2) season_share: 회원이 시즌 결산 공유를 켠 기록. 행이 있으면 공개, 없으면 비공개다.
--    share_token은 무작위 UUID다. 공유를 끄면 행을 지워 예전 링크가 다시 살아나지 않는다.
-- 회원 탈퇴 때 두 테이블의 행을 지운다(MemberWithdrawalRepository). 시각은 KST DATETIME(6)이다.
-- 새 테이블이라 이전 앱 이미지에는 영향이 없다. 앱 배포(ddl-auto validate) 전에 적용한다. 두 번 실행해도 안전하다.
-- 설계: docs/conception/028_season_log/01_design.md

CREATE TABLE IF NOT EXISTS season_visit (
    season_visit_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    member_id BIGINT NOT NULL,
    resort_id BIGINT NOT NULL,
    visit_date DATE NOT NULL COMMENT 'KST 날짜',
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT uk_season_visit_member_date UNIQUE (member_id, visit_date),
    INDEX idx_season_visit_resort (resort_id),
    CONSTRAINT fk_season_visit_member FOREIGN KEY (member_id) REFERENCES member (member_id) ON DELETE CASCADE,
    CONSTRAINT fk_season_visit_resort FOREIGN KEY (resort_id) REFERENCES resort (resort_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='시즌 방문 기록';

CREATE TABLE IF NOT EXISTS season_share (
    season_share_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    member_id BIGINT NOT NULL,
    season_start_year INT NOT NULL COMMENT '2026이면 2026-27 시즌',
    share_token CHAR(36) NOT NULL COMMENT '공유 링크용 무작위 UUID',
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT uk_season_share_member_season UNIQUE (member_id, season_start_year),
    CONSTRAINT uk_season_share_token UNIQUE (share_token),
    CONSTRAINT fk_season_share_member FOREIGN KEY (member_id) REFERENCES member (member_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='시즌 결산 공유';
