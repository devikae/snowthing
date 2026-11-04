-- IKA-22 사용자 알림 (내 글 댓글, 내 댓글 답글, 1:1 문의 답변).
-- 1) notification: 받는 사람 한 명당 한 행. 댓글 커밋 뒤 새 트랜잭션에서 만든다.
--    post_id, comment_id는 링크용 스냅샷이라 FK를 두지 않는다. FK가 있으면 글이나 댓글 삭제가 알림 때문에 막힌다.
--    receiver_id는 member FK. 회원 탈퇴는 상태 변경(WITHDRAWN)이라 행이 지워지지 않으므로 CASCADE는 두지 않는다.
--    actor_name, post_public_id, post_category_code는 값으로 저장해 목록 조회에서 member, post JOIN이 없다.
-- 2) idx_notification_receiver_read_id: 배지 개수(receiver_id, is_read = false)와 안 읽은 목록(ID 역순)을 이 인덱스 하나로 처리한다.
-- 보관 기간은 90일이다. 매일 04:00 KST 스케줄러가 created_at 기준으로 지난 알림을 1,000건씩 지운다.
-- 새 테이블이라 이전 앱 이미지에는 영향이 없다. 앱 배포(ddl-auto validate) 전에 적용한다. 두 번 실행해도 안전하다.

CREATE TABLE IF NOT EXISTS notification (
    notification_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    receiver_id BIGINT NOT NULL COMMENT '알림을 받는 회원',
    type VARCHAR(30) NOT NULL COMMENT 'POST_COMMENT, COMMENT_REPLY, INQUIRY_ANSWER',
    post_id BIGINT NOT NULL COMMENT '링크용 스냅샷. FK 없음',
    post_public_id VARCHAR(36) NOT NULL COMMENT '화면 이동용 글 공개 ID',
    post_category_code VARCHAR(30) NOT NULL COMMENT '화면 이동 경로 선택용. MARKET이면 /market 상세',
    comment_id BIGINT NOT NULL COMMENT '알림을 만든 댓글. FK 없음',
    actor_name VARCHAR(50) NOT NULL COMMENT '만든 시점의 댓글 작성자 이름. 익명 댓글이면 익명',
    preview VARCHAR(100) NOT NULL COMMENT '댓글 본문 앞 50자',
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_notification_receiver FOREIGN KEY (receiver_id) REFERENCES member (member_id),
    INDEX idx_notification_receiver_read_id (receiver_id, is_read, notification_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='사용자 알림';
