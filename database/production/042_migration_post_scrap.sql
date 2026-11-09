-- IKA-62 글 스크랩. 설계: docs/conception/026_post_scrap/01_design.md
-- post_scrap: 회원이 스크랩한 글. 회원과 글의 N:M 중계 테이블이다.
--   UNIQUE (member_id, post_id)로 중복 스크랩을 막고 앱은 ON DUPLICATE KEY로 멱등하게 넣는다. 회원 FK 인덱스도 겸한다.
--   (member_id, created_at, post_scrap_id) 인덱스는 내 스크랩 목록의 최신순 정렬과 커서 조건에 쓴다.
--   글은 소프트 삭제라 행을 남기고 조회할 때 뺀다. 실제 삭제가 생겨도 막히지 않게 post FK는 ON DELETE CASCADE다.
--   회원 탈퇴 때는 앱이 행을 지운다(member 행은 남으므로 member FK는 CASCADE 없음).
-- 새 테이블이라 이전 앱 이미지에는 영향이 없다. 앱 배포(ddl-auto validate) 전에 적용한다. 두 번 실행해도 안전하다.

CREATE TABLE IF NOT EXISTS post_scrap (
    post_scrap_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    member_id BIGINT NOT NULL COMMENT '스크랩한 회원',
    post_id BIGINT NOT NULL COMMENT '스크랩한 글',
    created_at DATETIME(6) NOT NULL COMMENT '스크랩한 시각(KST)',
    CONSTRAINT uk_post_scrap_member_post UNIQUE (member_id, post_id),
    INDEX idx_post_scrap_member_latest (member_id, created_at DESC, post_scrap_id DESC),
    INDEX idx_post_scrap_post (post_id),
    CONSTRAINT fk_post_scrap_member FOREIGN KEY (member_id) REFERENCES member (member_id),
    CONSTRAINT fk_post_scrap_post FOREIGN KEY (post_id) REFERENCES post (post_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='글 스크랩';
