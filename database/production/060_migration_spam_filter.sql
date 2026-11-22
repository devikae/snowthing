-- IKA-96 금칙어 조용한 보류와 IP 대역 차단. 설계: docs/conception/035_spam_filter/01_design.md
-- 1) banned_word: 관리자가 관리하는 금칙어. normalized_word는 공백과 기호를 빼고 소문자로 바꾼 비교 값이라 "카 지 노"와 "카지노"를 한 번만 등록한다.
--    초기값은 광고에만 쓰는 말로 둔다. 일상 대화와 겹치는 짧은 말(토토, 먹튀, 카지노 단독)은 넣지 않는다.
--    (하이원 카지노, 카풀비 먹튀, 토토로 같은 정상 글이 숨지 않게 하려는 선택이다. 필요하면 관리자 화면에서 추가한다.)
-- 2) content_filter_hold: 금칙어로 보류 중인 글과 댓글 기록. 관리자 검토 목록과 비회원 작성자 확인(같은 IP, 보류 뒤 1시간)에 쓴다.
--    공개 여부의 기준은 이 표가 아니라 post.status = 'FILTER_HOLD', comment.filter_state = 'HELD'다.
-- 3) comment.filter_state: 댓글 금칙어 보류 상태 (NULL, HELD, REJECTED). 끝에 붙이는 NULL 컬럼이라 MySQL 8.0 INSTANT 추가로 표를 다시 쓰지 않는다.
--    post.status는 VARCHAR(20)이라 새 값 FILTER_HOLD를 그대로 저장한다. 스키마 변경이 없다.
-- 4) chat_banned_ip: 라이브톡 전용이던 IP 차단을 글, 댓글, 라이브톡 공통 차단 목록으로 쓴다. banned_ip에 CIDR 대역(예: 2001:db8:1:2::/64)도 넣는다.
--    VARCHAR(45)는 가장 긴 IPv6 대역(39자 + /128)도 담는다. 기존 단일 IP 차단은 그대로 /32, /128로 읽혀 계속 동작한다. 표 설명만 바꾼다.
-- 실행 순서: 이 마이그레이션을 적용한 뒤 새 애플리케이션을 배포한다. 추가만 하므로 이전 애플리케이션과도 호환된다.
-- 여러 번 실행해도 안전하다 (IF NOT EXISTS, INSERT IGNORE, 컬럼 존재 확인).
-- 접속 클라이언트의 기본 문자셋과 관계없이 한글 초기값이 깨지지 않게 한다.
SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS `banned_word` (
    `banned_word_id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `word` VARCHAR(50) NOT NULL COMMENT '관리자가 입력한 금칙어 표기',
    `normalized_word` VARCHAR(50) NOT NULL COMMENT '비교 값 (HTML 제거, NFKC, 소문자, 글자와 숫자만)',
    `created_at` DATETIME(6) NOT NULL,
    UNIQUE KEY `uk_banned_word_normalized` (`normalized_word`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='금칙어 (걸리면 글과 댓글을 조용히 보류)';

INSERT IGNORE INTO `banned_word` (`word`, `normalized_word`, `created_at`)
VALUES
    ('카지노사이트', '카지노사이트', NOW(6)),
    ('온라인카지노', '온라인카지노', NOW(6)),
    ('바카라', '바카라', NOW(6)),
    ('토토사이트', '토토사이트', NOW(6)),
    ('슬롯사이트', '슬롯사이트', NOW(6)),
    ('홀덤사이트', '홀덤사이트', NOW(6)),
    ('먹튀검증', '먹튀검증', NOW(6)),
    ('가입머니', '가입머니', NOW(6));

CREATE TABLE IF NOT EXISTS `content_filter_hold` (
    `content_filter_hold_id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `target_type` VARCHAR(10) NOT NULL COMMENT '보류 대상 (POST, COMMENT)',
    `target_id` BIGINT NOT NULL COMMENT '글 번호 또는 댓글 번호',
    `post_id` BIGINT NOT NULL COMMENT '글 번호 (댓글이면 댓글이 달린 글)',
    `member_id` BIGINT NULL COMMENT '작성 회원 (비회원이면 NULL). 탈퇴 회원 기록도 남기려고 FK를 두지 않는다',
    `writer_ip` VARCHAR(45) NOT NULL COMMENT '작성자 IP',
    `matched_word` VARCHAR(50) NOT NULL COMMENT '걸린 금칙어',
    `held_at` DATETIME(6) NOT NULL COMMENT '보류한 시각 (금칙어가 든 내용으로 고치면 다시 쓴다)',
    UNIQUE KEY `uk_content_filter_hold_target` (`target_type`, `target_id`),
    INDEX `idx_content_filter_hold_list` (`held_at`, `content_filter_hold_id`),
    INDEX `idx_content_filter_hold_member` (`member_id`),
    INDEX `idx_content_filter_hold_ip` (`writer_ip`, `held_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='금칙어 보류 중인 글과 댓글';

DELIMITER $$

DROP PROCEDURE IF EXISTS `apply_060_comment_filter_state`$$
CREATE PROCEDURE `apply_060_comment_filter_state`()
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'comment'
          AND column_name = 'filter_state'
    ) THEN
        ALTER TABLE `comment`
            ADD COLUMN `filter_state` VARCHAR(10) NULL
                COMMENT '금칙어 보류 상태 (NULL 보통, HELD 보류, REJECTED 보류 뒤 삭제)',
            ALGORITHM=INSTANT;
    END IF;
END$$

DELIMITER ;

CALL `apply_060_comment_filter_state`();
DROP PROCEDURE IF EXISTS `apply_060_comment_filter_state`;

ALTER TABLE `chat_banned_ip` COMMENT = 'IP 차단 목록 (라이브톡, 게시글, 댓글 작성 공통). banned_ip는 단일 IP 또는 CIDR 대역';
