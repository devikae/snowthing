-- IKA-23 회원 간 1:1 쪽지와 차단.
-- 1) direct_message: 편지함형 쪽지. 쪽지 한 통이 행 하나이고 발신자와 수신자가 각자 자기 쪽에서만 지운다
--    (sender_deleted_at, receiver_deleted_at). read_at은 수신자가 처음 열 때만 기록한다.
--    market_listing_id는 중고장터 판매글에서 보낸 경우에만 채운다. 판매글은 소프트 삭제라 FK가 깨지지 않는다.
--    인덱스: 받은함 목록과 안 읽은 수는 (receiver_id, receiver_deleted_at, direct_message_id),
--    보낸함 목록은 (sender_id, sender_deleted_at, direct_message_id). 목록은 direct_message_id DESC 커서 페이징이다.
-- 2) member_block: 회원 차단. 쪽지 전용이 아니라 같이타요, 카풀에서도 재사용한다.
--    UNIQUE (blocker_id, blocked_id)로 중복 차단을 막고 앱은 ON DUPLICATE KEY로 멱등하게 넣는다.
--    (blocked_id, blocker_id) 인덱스는 "상대가 나를 차단했는지" 조회와 blocked_id FK에 쓴다.
-- 모두 새 테이블이라 이전 앱 이미지에는 영향이 없다. 앱 배포(ddl-auto validate) 전에 적용한다. 두 번 실행해도 안전하다.
-- 설계: docs/conception/022_direct_message/01_design.md

CREATE TABLE IF NOT EXISTS direct_message (
    direct_message_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    sender_id BIGINT NOT NULL,
    receiver_id BIGINT NOT NULL,
    market_listing_id BIGINT NULL COMMENT '판매글에서 보낸 경우의 판매글',
    content VARCHAR(1000) NOT NULL,
    read_at DATETIME(6) NULL COMMENT 'NULL이면 안 읽음. 수신자가 처음 열 때 기록',
    sender_deleted_at DATETIME(6) NULL COMMENT '발신자 쪽 삭제',
    receiver_deleted_at DATETIME(6) NULL COMMENT '수신자 쪽 삭제',
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    INDEX idx_direct_message_receiver (receiver_id, receiver_deleted_at, direct_message_id),
    INDEX idx_direct_message_sender (sender_id, sender_deleted_at, direct_message_id),
    INDEX idx_direct_message_market_listing (market_listing_id),
    CONSTRAINT fk_direct_message_sender FOREIGN KEY (sender_id) REFERENCES member (member_id),
    CONSTRAINT fk_direct_message_receiver FOREIGN KEY (receiver_id) REFERENCES member (member_id),
    CONSTRAINT fk_direct_message_market_listing FOREIGN KEY (market_listing_id) REFERENCES market_listing (market_listing_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='회원 간 1:1 쪽지';

CREATE TABLE IF NOT EXISTS member_block (
    member_block_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    blocker_id BIGINT NOT NULL COMMENT '차단한 회원',
    blocked_id BIGINT NOT NULL COMMENT '차단당한 회원',
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT uk_member_block_blocker_blocked UNIQUE (blocker_id, blocked_id),
    INDEX idx_member_block_blocked_blocker (blocked_id, blocker_id),
    CONSTRAINT fk_member_block_blocker FOREIGN KEY (blocker_id) REFERENCES member (member_id),
    CONSTRAINT fk_member_block_blocked FOREIGN KEY (blocked_id) REFERENCES member (member_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='회원 차단';
