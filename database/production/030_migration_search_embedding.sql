-- IKA-11 근거 검색 3단계 실험(의미 검색). MySQL 8.0에는 벡터 타입이 없으므로 float32 배열을 BLOB으로 저장하고 앱에서 코사인 유사도를 계산한다.
-- search_document 구간 하나에 모델별 벡터 하나. content_hash는 벡터를 만들 때의 search_document.chunk_hash다.
-- 검색은 content_hash = chunk_hash인 벡터만 쓰므로, 원문이 바뀌어 해시가 달라진 벡터(오래된 벡터)는 다시 만들기 전까지 쓰이지 않는다.
-- 원문 내용(제목, 본문)은 담지 않는다. 의미 검색은 기본 꺼짐(snowthing.search.semantic.enabled=false)이라 이 테이블은 비어 있다.
-- 새 테이블만 만들어 이전 앱 이미지에는 영향이 없다. 두 번 실행해도 안전하다.

CREATE TABLE IF NOT EXISTS search_embedding (
    embedding_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    evidence_id VARCHAR(80) NOT NULL COMMENT 'search_document.evidence_id',
    model_id VARCHAR(100) NOT NULL COMMENT '임베딩 제공자와 모델 식별자. 모델을 바꾸면 새 행이 생긴다',
    post_id BIGINT NOT NULL COMMENT '게시글 또는 댓글의 부모 게시글. 원문 삭제 시 벡터를 지우는 데 쓴다',
    source_type VARCHAR(20) NOT NULL COMMENT 'POST, COMMENT',
    source_id BIGINT NOT NULL COMMENT 'post_id 또는 comment_id',
    content_hash CHAR(64) NOT NULL COMMENT '벡터를 만든 구간의 chunk_hash',
    dimension INT NOT NULL,
    vector BLOB NOT NULL COMMENT 'float32 little-endian 배열 (dimension x 4 바이트)',
    created_at DATETIME(6) NOT NULL,
    UNIQUE KEY uk_search_embedding_evidence_model (evidence_id, model_id),
    INDEX idx_search_embedding_post (post_id),
    INDEX idx_search_embedding_source (source_type, source_id),
    CONSTRAINT chk_search_embedding_source_type CHECK (source_type IN ('POST', 'COMMENT')),
    CONSTRAINT chk_search_embedding_dimension CHECK (dimension BETWEEN 1 AND 4096),
    CONSTRAINT chk_search_embedding_vector_length CHECK (OCTET_LENGTH(vector) = dimension * 4)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='근거 검색 의미 검색 실험용 구간 벡터';
