-- IKA-122 주기적으로 같은 업체(세부 누스타 카지노 상담 업체)만 광고하는 글을 금칙어로도 막는다.
-- 이 업체는 문구를 바꿔도 업체 이름과 메신저 아이디는 그대로 쓴다. 연락처 보류는 비회원과 새 회원만 보므로,
-- 오래된 회원 계정을 쓰거나 연락처를 빼고 올려도 걸리게 업체 고유 표기를 금칙어로 넣는다.
-- normalized_word는 BannedWordMatcher.normalize 결과(글자와 숫자만, 소문자)와 같아야 한다.
-- "누스타"는 "누스타카지노", "누스타호텔"을, "tam999"는 "TAM999", "@Rtam999"를 함께 잡는다.
-- 관리자 화면에서 이미 넣은 단어는 normalized_word UNIQUE라 건너뛴다. 여러 번 실행해도 안전하다 (INSERT IGNORE).
SET NAMES utf8mb4;

INSERT IGNORE INTO `banned_word` (`word`, `normalized_word`, `created_at`)
VALUES
    ('누스타', '누스타', NOW(6)),
    ('세부카지노', '세부카지노', NOW(6)),
    ('로얄탐', '로얄탐', NOW(6)),
    ('tam999', 'tam999', NOW(6));
