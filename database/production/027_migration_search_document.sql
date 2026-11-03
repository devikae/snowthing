-- IKA-7 근거 검색 1단계(키워드 기준선).
-- 1) post.content_version: 제목·본문·카테고리·상태·삭제가 바뀔 때 애플리케이션이 1 올리는 내용 버전.
--    @Version(낙관적 락)이 아니다. 기존 행은 0에서 시작한다.
-- 2) search_document: 공개 게시글 구간과 댓글을 담는 검색 전용 복제 테이블. 원문은 post/comment가 기준이다.
--    전체 재구축 API로만 채운다. 원문 하드 삭제와 무관하게 조회 시 원문 테이블과 조인해 공개 범위를 다시 거르므로 FK를 두지 않는다.

DELIMITER $$
DROP PROCEDURE IF EXISTS apply_post_content_version$$
CREATE PROCEDURE apply_post_content_version()
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'post'
          AND column_name = 'content_version'
    ) THEN
        ALTER TABLE post ADD COLUMN content_version BIGINT NOT NULL DEFAULT 0;
    END IF;
END$$
DELIMITER ;
CALL apply_post_content_version();
DROP PROCEDURE IF EXISTS apply_post_content_version;

-- ngram 파서는 기본 불용어(a, i 등 한 글자 영어 단어 포함)가 들어간 토큰을 버린다.
-- 그대로 두면 'wax'처럼 a가 들어간 영어 2-gram이 모두 빠지므로, 인덱스를 만들 때만 세션 단위로 불용어를 끈다.
-- 이 설정은 인덱스 생성 시점에 인덱스에 묶여 저장된다.
SET @ika7_previous_ft_stopword = @@SESSION.innodb_ft_enable_stopword;
SET SESSION innodb_ft_enable_stopword = OFF;

CREATE TABLE IF NOT EXISTS search_document (
    document_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    evidence_id VARCHAR(80) NOT NULL COMMENT 'P:{post_id}:{chunk_no} 또는 C:{comment_id}:0',
    source_type VARCHAR(20) NOT NULL COMMENT 'POST, COMMENT',
    source_id BIGINT NOT NULL COMMENT 'post_id 또는 comment_id',
    chunk_no INT NOT NULL,
    post_id BIGINT NOT NULL,
    post_public_id VARCHAR(36) NOT NULL,
    parent_comment_id BIGINT NULL,
    category_code VARCHAR(50) NOT NULL COMMENT '인덱싱 시점 카테고리 (조회 필터는 현재 카테고리 기준)',
    source_version BIGINT NOT NULL COMMENT 'post.content_version 또는 comment.version',
    chunk_hash CHAR(64) NOT NULL COMMENT '정규화한 구간 텍스트의 SHA-256 (hex)',
    title VARCHAR(200) NULL COMMENT '게시글 0번 구간에만 제목을 둔다',
    body TEXT NOT NULL,
    source_created_at DATETIME(6) NOT NULL,
    source_updated_at DATETIME(6) NOT NULL,
    indexed_at DATETIME(6) NOT NULL,
    CONSTRAINT uk_search_document_evidence UNIQUE (evidence_id),
    INDEX idx_search_document_post (post_id),
    INDEX idx_search_document_source (source_type, source_id),
    FULLTEXT INDEX ftx_search_document_title_body (title, body) WITH PARSER ngram,
    CONSTRAINT chk_search_document_source_type CHECK (source_type IN ('POST', 'COMMENT'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='근거 검색용 문서 (재구축 가능한 복제본)';

SET SESSION innodb_ft_enable_stopword = @ika7_previous_ft_stopword;
