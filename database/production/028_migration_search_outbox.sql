-- IKA-10 근거 검색 2단계(정합성).
-- 1) search_outbox: 게시글·댓글 변경과 같은 트랜잭션에 쓰는 색인 작업 이벤트. 처리기는 이벤트 내용이 아니라 현재 원문을 다시 읽는다.
--    원문 내용은 담지 않는다. last_error_code에는 예외 종류 같은 짧은 코드만 둔다.
-- 2) search_index_state: 원문별 마지막 적용 버전과 tombstone. 삭제·비공개 원문은 ID·버전·삭제 시각만 남기고 내용은 남기지 않는다.
-- 3) search_reindex_checkpoint: 무중단 재인덱싱(공개 게시글을 ID 순으로 나눠 outbox에 넣기)의 진행 위치. 중간에 멈추면 이어서 한다.
-- 모두 새 테이블이라 이전 앱 이미지에는 영향이 없다. 두 번 실행해도 안전하다.

CREATE TABLE IF NOT EXISTS search_outbox (
    event_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    source_type VARCHAR(20) NOT NULL COMMENT 'POST, COMMENT',
    source_id BIGINT NOT NULL COMMENT 'post_id 또는 comment_id',
    operation VARCHAR(20) NOT NULL COMMENT 'UPSERT, DELETE (기록용. 처리기는 현재 원문 상태로 판단한다)',
    source_version BIGINT NOT NULL COMMENT '이벤트를 쓴 시점의 post.content_version 또는 comment.version',
    status VARCHAR(20) NOT NULL COMMENT 'PENDING, DONE, FAILED',
    attempts INT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    due_at DATETIME(6) NOT NULL,
    lease_until DATETIME(6) NULL,
    processed_at DATETIME(6) NULL,
    last_error_code VARCHAR(100) NULL COMMENT '예외 종류 등 짧은 코드. 원문 내용을 넣지 않는다',
    INDEX idx_search_outbox_status_due (status, due_at),
    INDEX idx_search_outbox_source (source_type, source_id),
    CONSTRAINT chk_search_outbox_source_type CHECK (source_type IN ('POST', 'COMMENT')),
    CONSTRAINT chk_search_outbox_operation CHECK (operation IN ('UPSERT', 'DELETE')),
    CONSTRAINT chk_search_outbox_status CHECK (status IN ('PENDING', 'DONE', 'FAILED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='근거 검색 색인 outbox';

CREATE TABLE IF NOT EXISTS search_index_state (
    source_type VARCHAR(20) NOT NULL,
    source_id BIGINT NOT NULL,
    applied_version BIGINT NOT NULL COMMENT '마지막으로 색인에 반영한 원문 버전. -1은 아직 반영 전',
    tombstoned BOOLEAN NOT NULL DEFAULT FALSE COMMENT 'true면 원문이 없거나 볼 수 없어 문서를 지운 상태',
    document_count INT NOT NULL DEFAULT 0 COMMENT '마지막 반영 때 넣은 이 원문의 문서(구간) 수. 대조에서 빠진 문서를 찾는 데 쓴다',
    tombstoned_at DATETIME(6) NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (source_type, source_id),
    CONSTRAINT chk_search_index_state_source_type CHECK (source_type IN ('POST', 'COMMENT'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='근거 검색 원문별 적용 버전과 tombstone';

CREATE TABLE IF NOT EXISTS search_reindex_checkpoint (
    job_name VARCHAR(40) NOT NULL PRIMARY KEY,
    status VARCHAR(20) NOT NULL COMMENT 'RUNNING, DONE',
    last_source_id BIGINT NOT NULL DEFAULT 0 COMMENT '마지막으로 outbox에 넣은 post_id',
    enqueued_count BIGINT NOT NULL DEFAULT 0,
    started_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    finished_at DATETIME(6) NULL,
    CONSTRAINT chk_search_reindex_status CHECK (status IN ('RUNNING', 'DONE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='근거 검색 재인덱싱 진행 위치';
