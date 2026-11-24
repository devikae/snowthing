-- IKA-123 받은 쪽지 신고. 설계: docs/conception/039_message_report/01_design.md
-- content_report.target_type에 MESSAGE(쪽지)가 추가된다. 컬럼이 VARCHAR(20)이라 구조는 그대로이고 설명만 고친다.
-- content_moderation_action.target_type도 쪽지 신고 처리 완료 이력에 MESSAGE를 담는다. 038이 VARCHAR(20)으로 만들었지만,
-- 로컬처럼 Hibernate(ddl-auto: update)가 먼저 만든 DB는 ENUM('COMMENT','POST')라 MESSAGE를 넣으면 실패한다. 같은 정의로 다시 맞춘다.
-- 운영은 이미 VARCHAR(20)이라 두 문장 모두 타입이 바뀌지 않고, MySQL 8은 설명만 바꾸는 메타데이터 변경으로 처리한다(테이블 재작성 없음).
-- 여러 번 실행해도 결과가 같다. 이전 애플리케이션과도 호환된다. 적용 순서는 상관없다.
SET NAMES utf8mb4;

ALTER TABLE `content_report`
    MODIFY COLUMN `target_type` VARCHAR(20) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'POST, COMMENT, MESSAGE',
    MODIFY COLUMN `target_id` BIGINT NOT NULL COMMENT 'post_id, comment_id 또는 direct_message_id. 삭제되어도 이력이 남도록 FK를 두지 않는다';

ALTER TABLE `content_moderation_action`
    MODIFY COLUMN `target_type` VARCHAR(20) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'POST, COMMENT, MESSAGE';
