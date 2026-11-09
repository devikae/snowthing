-- IKA-65 웹 푸시 구독.
-- push_subscription: 브라우저(기기) 하나의 푸시 구독이 한 행이다. 회원 한 명이 여러 기기에서 켤 수 있다.
--   endpoint는 푸시 서비스(FCM, Apple, Mozilla 등)가 준 주소다. 길이가 일정하지 않아 utf8mb4 유니크 인덱스 한도(3072바이트)를
--   넘을 수 있으므로, endpoint의 SHA-256 16진수(endpoint_hash)에 유니크를 건다. 같은 기기에서 다른 계정으로 다시 켜면 그 행의
--   회원이 바뀐다(INSERT ... ON DUPLICATE KEY UPDATE).
--   p256dh, auth는 브라우저가 준 공개키와 인증 비밀값(base64url)이다. 본문 암호화에 쓴다.
--   last_used_at은 마지막으로 등록을 갱신했거나 푸시를 보낸 시각이다. 회원당 최근 10개만 남긴다.
--   푸시 서비스가 404, 410으로 답하면 그 구독을 지운다. 회원 탈퇴 때도 지운다.
-- 시각은 KST DATETIME(6)이다.
-- 새 테이블이라 이전 앱 이미지에는 영향이 없다. 앱 배포(ddl-auto validate) 전에 적용한다. 두 번 실행해도 안전하다.
-- 설계: docs/conception/029_web_push/01_design.md

CREATE TABLE IF NOT EXISTS push_subscription (
    push_subscription_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    member_id BIGINT NOT NULL COMMENT '구독한 회원',
    endpoint VARCHAR(1024) NOT NULL COMMENT '푸시 서비스 주소',
    endpoint_hash CHAR(64) NOT NULL COMMENT 'endpoint의 SHA-256 16진수',
    p256dh VARCHAR(128) NOT NULL COMMENT '브라우저 공개키(base64url)',
    auth VARCHAR(64) NOT NULL COMMENT '브라우저 인증 비밀값(base64url)',
    created_at DATETIME(6) NOT NULL,
    last_used_at DATETIME(6) NOT NULL COMMENT '마지막 등록 갱신 또는 발송 성공 시각',
    CONSTRAINT uk_push_subscription_endpoint_hash UNIQUE (endpoint_hash),
    CONSTRAINT fk_push_subscription_member FOREIGN KEY (member_id) REFERENCES member (member_id),
    INDEX idx_push_subscription_member (member_id, last_used_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='웹 푸시 구독';
