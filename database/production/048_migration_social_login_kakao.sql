-- IKA-72 카카오 로그인. member_social_account.provider에 KAKAO를 허용한다.
-- CHECK 제약을 지우고 같은 이름으로 다시 만든다. 기존 행(GOOGLE, NAVER)은 새 제약도 만족한다.
-- 실행 순서: 이 마이그레이션을 적용한 뒤 새 애플리케이션을 배포한다. 허용 값을 늘리기만 하므로 이전 애플리케이션과도 호환된다.
-- 여러 번 실행해도 안전하다 (지운 뒤 같은 정의로 다시 만든다).

ALTER TABLE `member_social_account`
    DROP CHECK `chk_member_social_provider`,
    MODIFY COLUMN `provider` VARCHAR(20) NOT NULL COMMENT '소셜 제공자 (GOOGLE, NAVER, KAKAO)',
    ADD CONSTRAINT `chk_member_social_provider` CHECK (`provider` IN ('GOOGLE', 'NAVER', 'KAKAO'));
