-- IKA-63 카풀 탑승 확인과 후기.
-- 1) carpool_rider: 출발 뒤 탑승자가 요청하고 운전자(글 작성자)가 확정하거나 거절한다.
--    같이 탄 사람 = 글 작성자 + CONFIRMED 탑승자. UNIQUE (post_id, member_id)로 한 번만 요청한다(거절 행도 남긴다).
--    (member_id, status)는 회원별 참여 횟수에 쓴다.
-- 2) carpool_review: 같이 탄 사람끼리 남기는 후기. GOOD(좋았어요) 또는 BAD(아쉬웠어요)와 100자 이하 글.
--    UNIQUE (post_id, reviewer_id, reviewee_id)로 한 카풀에서 한 사람에게 한 번만 쓴다. 본인 후기는 CHECK로 막는다.
--    (reviewee_id, rating)은 회원별 요약에 쓴다.
-- 3) carpool_review_tag: GOOD 후기의 좋았던 점. 태그별 개수를 세려고 행으로 둔다.
-- 시각은 KST DATETIME(6)이다.
-- 모두 새 테이블이라 이전 앱 이미지에는 영향이 없다. 앱 배포(ddl-auto validate) 전에 적용한다. 두 번 실행해도 안전하다.
-- 설계: docs/conception/027_carpool_review/01_design.md

CREATE TABLE IF NOT EXISTS carpool_rider (
    carpool_rider_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    post_id BIGINT NOT NULL COMMENT '카풀 글',
    member_id BIGINT NOT NULL COMMENT '탑승자',
    status VARCHAR(20) NOT NULL COMMENT 'REQUESTED, CONFIRMED, REJECTED',
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT uk_carpool_rider_post_member UNIQUE (post_id, member_id),
    INDEX idx_carpool_rider_member_status (member_id, status),
    CONSTRAINT fk_carpool_rider_post FOREIGN KEY (post_id) REFERENCES carpool_detail (post_id),
    CONSTRAINT fk_carpool_rider_member FOREIGN KEY (member_id) REFERENCES member (member_id),
    CONSTRAINT chk_carpool_rider_status CHECK (status IN ('REQUESTED', 'CONFIRMED', 'REJECTED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='카풀 탑승 확인';

CREATE TABLE IF NOT EXISTS carpool_review (
    carpool_review_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    post_id BIGINT NOT NULL COMMENT '카풀 글',
    reviewer_id BIGINT NOT NULL COMMENT '작성자',
    reviewee_id BIGINT NOT NULL COMMENT '대상',
    rating VARCHAR(10) NOT NULL COMMENT 'GOOD, BAD',
    comment VARCHAR(100) NULL COMMENT '대상 본인에게만 보이는 짧은 글',
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT uk_carpool_review_post_reviewer_reviewee UNIQUE (post_id, reviewer_id, reviewee_id),
    INDEX idx_carpool_review_reviewee_rating (reviewee_id, rating),
    INDEX idx_carpool_review_reviewer (reviewer_id),
    CONSTRAINT fk_carpool_review_post FOREIGN KEY (post_id) REFERENCES carpool_detail (post_id),
    CONSTRAINT fk_carpool_review_reviewer FOREIGN KEY (reviewer_id) REFERENCES member (member_id),
    CONSTRAINT fk_carpool_review_reviewee FOREIGN KEY (reviewee_id) REFERENCES member (member_id),
    CONSTRAINT chk_carpool_review_not_self CHECK (reviewer_id <> reviewee_id),
    CONSTRAINT chk_carpool_review_rating CHECK (rating IN ('GOOD', 'BAD'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='카풀 후기';

CREATE TABLE IF NOT EXISTS carpool_review_tag (
    carpool_review_id BIGINT NOT NULL,
    tag VARCHAR(30) NOT NULL COMMENT 'ON_TIME, KIND, FAIR_SETTLEMENT, SAFE_DRIVING, CLEAN_CAR',
    PRIMARY KEY (carpool_review_id, tag),
    CONSTRAINT fk_carpool_review_tag_review FOREIGN KEY (carpool_review_id) REFERENCES carpool_review (carpool_review_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='카풀 후기의 좋았던 점';
