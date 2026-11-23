-- ====================================================================
-- Snowthing Production Migration: 065_migration_column_repost.sql
-- Version: 065
-- Description: IKA-112 휘구리 칼럼 예약 게시 대기열. 설계: docs/conception/038_column_repost/01_design.md
-- 칸
--   sequence_no: 게시 순서(작을수록 먼저, 오래된 원문부터)
--   source_no: 디시 원문 글 번호(같은 글이 두 번 들어가지 않게 UNIQUE)
--   title, content: 게시할 제목과 마크다운 본문. 사진은 디시 주소로 두고 게시하는 순간 눈팅 사진으로 바꾼다.
--   status: PENDING(대기), PUBLISHED(게시됨)
--   post_public_id, published_at: 게시된 글의 공개 ID와 시각
--   attempts, last_error: 게시 실패 횟수와 마지막 오류(정해진 횟수를 넘으면 그 글에서 멈춘다)
-- 인덱스: 다음 글은 status와 sequence_no로 찾는다. 50줄 안쪽 표라 그 밖의 인덱스는 두지 않는다.
-- 데이터: 원문 본문은 공개 저장소에 두지 않는다. 대기열 줄은 수집 스크립트가 만든 SQL로 운영 DB에 따로 넣는다.
-- 실행 순서: 064 다음. 새 표만 만들므로 이전 애플리케이션과도 호환된다.
-- 여러 번 실행해도 안전하다 (IF NOT EXISTS).
-- ====================================================================
SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS `column_repost_queue` (
    `id`           BIGINT       NOT NULL AUTO_INCREMENT,
    `sequence_no`  INT          NOT NULL,
    `source_no`    BIGINT       NOT NULL,
    `title`        VARCHAR(200) NOT NULL,
    `content`      MEDIUMTEXT   NOT NULL,
    `status`       VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    `post_public_id` VARCHAR(36) NULL,
    `published_at` DATETIME(6)  NULL,
    `attempts`     INT          NOT NULL DEFAULT 0,
    `last_error`   VARCHAR(500) NULL,
    `created_at`   DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_column_repost_sequence` (`sequence_no`),
    UNIQUE KEY `uk_column_repost_source` (`source_no`),
    KEY `idx_column_repost_status_sequence` (`status`, `sequence_no`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
