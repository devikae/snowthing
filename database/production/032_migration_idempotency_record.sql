-- IKA-21 작성 API Idempotency-Key 기록.
-- 게시글, 댓글(대댓글 포함), 설질 제보, 카풀, 마켓 작성 요청이 Idempotency-Key 헤더를 보내면 글 저장과 같은 트랜잭션에서 이 표에 먼저 insert한다.
-- UNIQUE(author_key, target_type, idempotency_key)가 같은 키의 두 번째 저장을 막고, 재전송에는 response_body(처음 201 응답 JSON)를 그대로 돌려준다.
-- author_key는 회원이면 member:{회원 ID}, 비로그인이면 ip:{IP SHA-256 앞 32자}다. IP 원문은 남기지 않는다.
-- 24시간이 지난 기록은 앱 스케줄러가 created_at 순으로 1,000건씩 지운다.
-- 새 표라 이전 앱 이미지에는 영향이 없다. 앱 배포(ddl-auto validate) 전에 적용한다. 두 번 실행해도 안전하다.

CREATE TABLE IF NOT EXISTS idempotency_record (
    idempotency_record_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    author_key VARCHAR(80) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT 'member:{회원 ID} 또는 ip:{IP 해시}',
    target_type VARCHAR(30) NOT NULL COMMENT 'POST, COMMENT, RESORT_REPORT, CARPOOL, MARKET_LISTING',
    idempotency_key VARCHAR(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '소문자로 맞춘 UUID',
    request_hash CHAR(64) NOT NULL COMMENT '같은 키로 다른 내용을 보냈는지 판별하는 요청 SHA-256',
    response_body MEDIUMTEXT NULL COMMENT '처음 201 응답 JSON. 같은 트랜잭션 끝에서 채우므로 커밋된 행에는 항상 있다',
    created_at DATETIME(6) NOT NULL COMMENT 'KST',
    CONSTRAINT uk_idempotency_record_author_target_key UNIQUE (author_key, target_type, idempotency_key),
    INDEX idx_idempotency_record_created (created_at, idempotency_record_id),
    CONSTRAINT chk_idempotency_record_target
        CHECK (target_type IN ('POST', 'COMMENT', 'RESORT_REPORT', 'CARPOOL', 'MARKET_LISTING'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='작성 API Idempotency-Key 기록';
