-- 로그인 상태 유지(remember-me) 토큰. 세션은 서버 메모리에 있어 60분 쉬거나 배포하면 사라진다.
-- 세션이 없을 때 브라우저의 로그인 유지 쿠키로 다시 인증해 새 세션을 만들고, 그 토큰을 이 표에 둔다.
-- series는 기기 하나를 가리키고 쿠키에 그대로 들어간다. token은 자동 로그인 때마다 바뀌며 SHA-256 해시만 저장한다.
-- previous_token_hash는 바로 전 token의 해시다. 동시에 들어온 요청이 바뀌기 전 token을 내밀어도 잠깐 받아 준다.
-- 회원은 공개 ID로 묶는다. 탈퇴하면 이메일이 익명값으로 바뀌어 이메일로는 지울 수 없다.
-- 회원 삭제와 상관없이 앱이 지우므로 FK를 두지 않는다. 30일 넘게 쓰이지 않은 행은 매일 새벽 지운다.
-- 실행 순서: 이 마이그레이션을 적용한 뒤 새 애플리케이션을 배포한다. 표 추가만 하므로 이전 애플리케이션과도 호환된다.
-- 여러 번 실행해도 안전하다 (IF NOT EXISTS).

CREATE TABLE IF NOT EXISTS `persistent_logins` (
    `series` VARCHAR(64) NOT NULL PRIMARY KEY COMMENT '기기별 토큰 묶음 식별자 (쿠키에 담김)',
    `member_public_id` VARCHAR(36) NOT NULL COMMENT '회원 공개 ID',
    `token_hash` CHAR(64) NOT NULL COMMENT '현재 token의 SHA-256 해시',
    `previous_token_hash` CHAR(64) NULL COMMENT '바로 전 token의 SHA-256 해시',
    `last_used_at` DATETIME(6) NOT NULL COMMENT '발급 또는 마지막 자동 로그인 시각 (KST)',
    `created_at` DATETIME(6) NOT NULL COMMENT '발급 시각 (KST)',
    INDEX `idx_persistent_logins_member` (`member_public_id`),
    INDEX `idx_persistent_logins_last_used` (`last_used_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='로그인 상태 유지 토큰';
