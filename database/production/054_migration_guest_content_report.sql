-- 비회원도 게시글과 댓글을 신고할 수 있게 한다.
-- 1) reporter_id를 NULL 허용으로 바꾼다. 비회원 신고는 reporter_id가 NULL이다.
-- 2) reporter_ip를 추가한다. 비회원 신고의 중복 판단과 관리자 확인에 쓴다. 회원 신고는 NULL이다.
--    unique 제약은 NULL끼리 중복으로 보지 않으므로 비회원 중복은 애플리케이션에서 (대상, IP)로 막는다.
-- 3) (target_type, target_id, reporter_ip) 인덱스로 비회원 중복 조회를 빠르게 한다.
-- 실행 순서: 이 마이그레이션을 적용한 뒤 새 애플리케이션을 배포한다. 이전 애플리케이션과도 호환된다.

ALTER TABLE `content_report`
    MODIFY COLUMN `reporter_id` BIGINT NULL COMMENT '신고한 회원. 비회원 신고는 NULL',
    ADD COLUMN `reporter_ip` VARCHAR(45) NULL COMMENT '비회원 신고자의 IP. 회원 신고는 NULL' AFTER `reporter_id`,
    ADD INDEX `idx_content_report_target_ip` (`target_type`, `target_id`, `reporter_ip`);
