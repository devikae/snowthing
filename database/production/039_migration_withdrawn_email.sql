-- IKA-39 회원 탈퇴 후 30일 재가입 차단.
-- withdrawn_email: 탈퇴한 이메일의 HMAC-SHA256만 보관한다. 이메일 원문은 저장하지 않는다.
--   email_hash는 EmailVerificationHasher의 서버 비밀값으로 만든 64자리 16진수다.
--   expires_at이 지나면 차단이 풀리고, 스케줄러가 idx_withdrawn_email_expires_at으로 찾아 지운다.
-- 시각은 KST DATETIME(6)이다.
-- 새 테이블이라 이전 앱 이미지에는 영향이 없다. 앱 배포(ddl-auto validate) 전에 적용한다. 두 번 실행해도 안전하다.
-- 설계: docs/conception/025_member_withdrawal/01_design.md

CREATE TABLE IF NOT EXISTS withdrawn_email (
    email_hash CHAR(64) NOT NULL PRIMARY KEY COMMENT '정규화한 이메일의 HMAC-SHA256',
    withdrawn_at DATETIME(6) NOT NULL,
    expires_at DATETIME(6) NOT NULL COMMENT '재가입 차단이 풀리는 시각',
    INDEX idx_withdrawn_email_expires_at (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='탈퇴 후 재가입 차단 이메일 해시';
