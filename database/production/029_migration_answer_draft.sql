-- IKA-12 제한 API 토큰과 답변 초안 승인 게시 흐름.
-- 1) api_token: 회원(관리자)에 묶인 제한 토큰. 평문은 저장하지 않고 SHA-256 해시와 표시용 접두사만 둔다.
--    범위는 READ, DRAFT뿐이다. 게시(PUBLISH) 범위는 없다.
-- 2) answer_draft: AI가 저장하고 사람이 승인해 댓글로 게시하는 초안. 상태는 REVIEW_REQUIRED, APPROVED, PUBLISHED, REJECTED.
--    (DRAFT는 저장 검증 중의 시작 상태라 저장된 행에는 남지 않지만 CHECK에는 넣어 둔다.)
--    published_comment_id 유일 제약으로 한 댓글이 두 초안의 게시 결과가 되지 않는다.
-- 3) answer_draft_evidence: 초안이 인용한 근거와 저장 시점의 구간 해시. 게시 직전에 같은 해시인지 다시 확인한다.
-- 4) answer_draft_audit: 저장, 승인, 거절, 게시, 게시 거절 기록. 본문과 토큰 값은 넣지 않는다.
-- 모두 새 테이블이라 이전 앱 이미지에는 영향이 없다. 앱 배포(ddl-auto validate) 전에 적용한다. 두 번 실행해도 안전하다.

CREATE TABLE IF NOT EXISTS api_token (
    api_token_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    public_id VARCHAR(36) NOT NULL,
    member_id BIGINT NOT NULL COMMENT '토큰 소유자. 인증할 때마다 활성 관리자인지 다시 확인한다',
    name VARCHAR(50) NOT NULL,
    token_prefix VARCHAR(16) NOT NULL COMMENT '화면 표시용 앞부분(snt_ + 8자). 인증에는 쓰지 않는다',
    token_hash CHAR(64) NOT NULL COMMENT '평문 토큰의 SHA-256 (hex). 평문은 저장하지 않는다',
    scopes VARCHAR(50) NOT NULL COMMENT '쉼표 구분 READ, DRAFT',
    expires_at DATETIME(6) NOT NULL COMMENT '발급 후 최대 30일',
    revoked_at DATETIME(6) NULL,
    last_used_at DATETIME(6) NULL COMMENT '1분 간격으로만 갱신',
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT uk_api_token_hash UNIQUE (token_hash),
    CONSTRAINT uk_api_token_public_id UNIQUE (public_id),
    INDEX idx_api_token_member_created (member_id, created_at),
    CONSTRAINT fk_api_token_member FOREIGN KEY (member_id) REFERENCES member (member_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='제한 API 토큰';

CREATE TABLE IF NOT EXISTS answer_draft (
    answer_draft_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    public_id VARCHAR(36) NOT NULL,
    target_post_id BIGINT NOT NULL,
    target_post_public_id VARCHAR(36) NOT NULL,
    body VARCHAR(1000) NOT NULL COMMENT '게시될 댓글 본문. 댓글 제한과 같은 1,000자',
    status VARCHAR(20) NOT NULL,
    idempotency_key VARCHAR(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '작성자별 유일, 대소문자 구분',
    request_hash CHAR(64) NOT NULL COMMENT '같은 키로 다른 내용을 보냈는지 판별하는 요청 해시',
    created_by_member_id BIGINT NOT NULL,
    created_by_token_id BIGINT NULL COMMENT '토큰으로 저장했으면 api_token_id, 관리자 세션이면 NULL',
    reviewed_by_member_id BIGINT NULL,
    reviewed_at DATETIME(6) NULL,
    approved_by_member_id BIGINT NULL,
    approved_at DATETIME(6) NULL,
    approved_post_content_version BIGINT NULL COMMENT '승인 시점 post.content_version. 게시 직전 값과 다르면 게시 거절',
    reject_reason VARCHAR(255) NULL,
    published_comment_id BIGINT NULL,
    published_by_member_id BIGINT NULL,
    published_at DATETIME(6) NULL,
    version BIGINT NOT NULL DEFAULT 0 COMMENT '낙관적 잠금(@Version)',
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT uk_answer_draft_public_id UNIQUE (public_id),
    CONSTRAINT uk_answer_draft_creator_idempotency UNIQUE (created_by_member_id, idempotency_key),
    CONSTRAINT uk_answer_draft_published_comment UNIQUE (published_comment_id),
    INDEX idx_answer_draft_status_created (status, created_at, answer_draft_id),
    INDEX idx_answer_draft_target_post (target_post_id),
    CONSTRAINT chk_answer_draft_status
        CHECK (status IN ('DRAFT', 'REVIEW_REQUIRED', 'APPROVED', 'PUBLISHED', 'REJECTED')),
    CONSTRAINT chk_answer_draft_published
        CHECK (status <> 'PUBLISHED' OR published_comment_id IS NOT NULL),
    CONSTRAINT fk_answer_draft_target_post FOREIGN KEY (target_post_id) REFERENCES post (post_id),
    CONSTRAINT fk_answer_draft_created_by FOREIGN KEY (created_by_member_id) REFERENCES member (member_id),
    CONSTRAINT fk_answer_draft_created_token FOREIGN KEY (created_by_token_id) REFERENCES api_token (api_token_id),
    CONSTRAINT fk_answer_draft_reviewed_by FOREIGN KEY (reviewed_by_member_id) REFERENCES member (member_id),
    CONSTRAINT fk_answer_draft_approved_by FOREIGN KEY (approved_by_member_id) REFERENCES member (member_id),
    CONSTRAINT fk_answer_draft_published_by FOREIGN KEY (published_by_member_id) REFERENCES member (member_id),
    CONSTRAINT fk_answer_draft_published_comment FOREIGN KEY (published_comment_id) REFERENCES comment (comment_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='답변 초안';

CREATE TABLE IF NOT EXISTS answer_draft_evidence (
    answer_draft_evidence_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    answer_draft_id BIGINT NOT NULL,
    evidence_id VARCHAR(80) NOT NULL COMMENT 'P:{post_id}:{chunk_no} 또는 C:{comment_id}:0',
    chunk_hash CHAR(64) NOT NULL COMMENT '저장 시점에 확인한 구간 SHA-256 (hex)',
    sort_order INT NOT NULL,
    CONSTRAINT uk_answer_draft_evidence UNIQUE (answer_draft_id, evidence_id),
    CONSTRAINT fk_answer_draft_evidence_draft FOREIGN KEY (answer_draft_id) REFERENCES answer_draft (answer_draft_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='답변 초안 근거';

CREATE TABLE IF NOT EXISTS answer_draft_audit (
    answer_draft_audit_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    answer_draft_id BIGINT NOT NULL,
    action VARCHAR(30) NOT NULL,
    actor_member_id BIGINT NULL,
    actor_token_id BIGINT NULL COMMENT '토큰으로 한 작업이면 api_token_id',
    reason_code VARCHAR(50) NULL COMMENT '게시 거절 사유 등 짧은 코드. 본문을 넣지 않는다',
    created_at DATETIME(6) NOT NULL,
    INDEX idx_answer_draft_audit_draft_created (answer_draft_id, created_at),
    CONSTRAINT chk_answer_draft_audit_action
        CHECK (action IN ('SAVE', 'APPROVE', 'REJECT', 'PUBLISH', 'PUBLISH_REJECTED')),
    CONSTRAINT fk_answer_draft_audit_draft FOREIGN KEY (answer_draft_id) REFERENCES answer_draft (answer_draft_id),
    CONSTRAINT fk_answer_draft_audit_actor FOREIGN KEY (actor_member_id) REFERENCES member (member_id),
    CONSTRAINT fk_answer_draft_audit_token FOREIGN KEY (actor_token_id) REFERENCES api_token (api_token_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='답변 초안 감사 기록';
