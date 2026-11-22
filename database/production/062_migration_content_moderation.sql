-- IKA-80 AI 자동 검열 1차. 설계: docs/conception/031_ai_moderation/01_design.md
-- content_moderation: 글, 댓글, 장터 글, 카풀 글, 설질 제보를 쓰거나 고칠 때 같은 트랜잭션에서 한 줄을 넣는 검사 대기열이자 판정 기록이다.
--   스케줄러가 1분마다 PENDING 줄을 묶어 처리한다 (1단계 규칙, 2단계 OpenAI Moderation API).
--   본문은 저장하지 않는다. 정리한 제목과 본문의 SHA-256(content_hash)과 판정 근거(reasons)만 남긴다.
--   member_id, writer_ip는 "같은 작성자나 같은 IP가 같은 내용을 짧은 시간에 반복" 규칙에 쓴다. 원문 표에도 있는 값이다.
--   탈퇴 회원 기록도 남기려고 FK를 두지 않는다.
-- 인덱스
--   idx_content_moderation_due: 처리할 줄 고르기 (status, next_attempt_at, ID)
--   idx_content_moderation_review: 관리자 검토 목록 (verdict, reviewed_at, ID)
--   idx_content_moderation_target: 같은 대상의 이전 줄, 새 줄 확인 (target_type, target_id)
--   idx_content_moderation_hash: 같은 내용 반복과 관리자가 문제없음으로 본 내용 확인 (content_hash, created_at)
--   idx_content_moderation_processed: 일일 요약과 시간당 자동 숨김 수 (processed_at)
-- 실행 순서: 이 마이그레이션을 적용한 뒤 새 애플리케이션을 배포한다. 새 표만 만들므로 이전 애플리케이션과도 호환된다.
-- 기능 스위치(SNOWTHING_MODERATION_ENABLED)는 기본 꺼짐이라 배포만으로는 줄이 쌓이지 않는다.
-- 여러 번 실행해도 안전하다 (IF NOT EXISTS).
SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS `content_moderation` (
    `content_moderation_id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `target_type` VARCHAR(20) NOT NULL COMMENT '검사 대상 (POST, COMMENT, MARKET, CARPOOL, RESORT_REPORT)',
    `target_id` BIGINT NOT NULL COMMENT '글 번호, 댓글 번호, 설질 제보 번호 (장터와 카풀은 글 번호)',
    `member_id` BIGINT NULL COMMENT '작성 회원 (비회원이면 NULL)',
    `writer_ip` VARCHAR(45) NULL COMMENT '작성자 IP (설질 제보는 NULL)',
    `content_hash` CHAR(64) NOT NULL COMMENT '정리한 제목과 본문의 SHA-256. 본문은 저장하지 않는다',
    `status` VARCHAR(10) NOT NULL COMMENT '처리 상태 (PENDING, DONE, FAILED, SKIPPED)',
    `stage` VARCHAR(20) NULL COMMENT '판정한 단계 (RULE, MODERATION_API, LLM)',
    `verdict` VARCHAR(10) NULL COMMENT '판정 (PASS, REVIEW, HIDE)',
    `reasons` VARCHAR(1000) NULL COMMENT '걸린 규칙, 카테고리와 점수 (JSON, 본문은 넣지 않는다)',
    `attempt_count` INT NOT NULL DEFAULT 0 COMMENT '처리 시도 횟수 (가져갈 때 먼저 올린다)',
    `next_attempt_at` DATETIME(6) NOT NULL COMMENT '다음 처리 시각',
    `lease_until` DATETIME(6) NULL COMMENT '처리 중 임대 만료 시각. 지나면 다시 가져간다',
    `processed_at` DATETIME(6) NULL COMMENT '판정을 마친 시각',
    `reviewed_by` BIGINT NULL COMMENT '검토한 관리자 회원 번호',
    `reviewed_at` DATETIME(6) NULL COMMENT '검토 시각',
    `review_result` VARCHAR(20) NULL COMMENT '관리자 처리 (PASS 문제없음, KEEP 숨김 유지, DELETE 삭제, SUPERSEDED 새 수정본으로 대체)',
    `created_at` DATETIME(6) NOT NULL,
    `updated_at` DATETIME(6) NOT NULL,
    INDEX `idx_content_moderation_due` (`status`, `next_attempt_at`, `content_moderation_id`),
    INDEX `idx_content_moderation_review` (`verdict`, `reviewed_at`, `content_moderation_id`),
    INDEX `idx_content_moderation_target` (`target_type`, `target_id`),
    INDEX `idx_content_moderation_hash` (`content_hash`, `created_at`),
    INDEX `idx_content_moderation_processed` (`processed_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI 자동 검열 대기열과 판정 기록 (본문 없음)';
