# 사용자 알림

## 배경

- 지금 알림은 `global/notification`의 관리자용 Discord 알림(가입, 서버 오류, 일일 요약)뿐이다. 사용자는 자기 글에 댓글이 달려도 직접 들어가 보기 전에는 알 수 없다.
- 021 1:1 문의 설계에서 답변 알림을 "별도 알림 설계"로 미뤘다.
- `PostReactionEventListener`는 로그만 남기는 상태다. 추천 알림은 이번 범위가 아니다.

## 요구사항

- 다음 세 경우에 받는 사람에게 알림을 만든다.
  - 내 글에 댓글이 달림 (`POST_COMMENT`)
  - 내 댓글에 답글이 달림 (`COMMENT_REPLY`)
  - 내 1:1 문의에 관리자가 답변함 (`INQUIRY_ANSWER`)
- 헤더에 안 읽은 알림 개수 배지를 띄우고, 누르면 최근 알림 목록을 보여준다.
- 알림은 하나씩 저장하고 읽음 여부를 둔다. 안 읽은 것만 보기와 모두 읽음을 제공한다.
- 실시간 푸시, 이메일, 알림 끄기 설정은 이번 범위가 아니다.

## 생성 방식

1:1 문의 답변은 관리자가 `INQUIRY` 글에 남기는 댓글이다(021). 그래서 세 알림 모두 댓글 작성에서 나온다. 댓글은 `CommentCommandService.createComment` 한 곳에서만 저장된다.

```
CommentCommandService.createComment (트랜잭션)
  └ 댓글 저장 → CommentCreatedEvent 발행
        ↓ 커밋 성공 후
NotificationEventListener
  @TransactionalEventListener(phase = AFTER_COMMIT)
  @Transactional(propagation = REQUIRES_NEW)
  └ 받는 사람 계산 → notification INSERT (실패하면 로그만 남김)
```

- 커밋 후에 만들기 때문에 롤백된 댓글에는 알림이 생기지 않는다.
- 알림 저장이 실패해도 댓글은 이미 커밋돼 있다. 알림은 부가 기능이라 댓글 작성을 실패시키지 않는다.
- AFTER_COMMIT 시점에는 원래 트랜잭션의 리소스가 아직 묶여 있어 그대로 쓰면 반영되지 않는다. 그래서 `REQUIRES_NEW`로 새 트랜잭션을 연다.
- 이벤트에는 엔티티 대신 ID와 필요한 값(댓글 ID, 글 ID, 글 publicId, 카테고리, 글 작성자 ID, 부모 댓글 작성자 ID, 댓글 작성자 ID, 관리자 여부, 익명 여부, 표시 이름, 본문 앞부분)을 담는다. 리스너는 새 트랜잭션이라 지연 로딩을 쓸 수 없다.

검토한 대안은 다음과 같다.

- 댓글과 같은 트랜잭션에서 바로 저장: 가장 단순하고 원자적이지만 알림 저장이 실패하면 댓글까지 롤백된다. 댓글 서비스가 알림 저장소를 직접 알게 된다.
- Outbox 테이블과 스케줄러(028 search outbox 방식): 서버가 죽어도 유실이 없지만 테이블과 스케줄러가 늘고 알림이 늦게 뜬다. 알림은 유실 비용이 낮아 과하다고 판단했다.

선택한 방식의 위험은 커밋 직후, 리스너 실행 전에 서버가 죽으면 알림 1건이 유실되는 것이다. 이 경우는 감수한다. 리스너 실패는 `log.warn`으로 남긴다.

## 받는 사람 규칙

| 상황 | 받는 사람 | 종류 |
|---|---|---|
| `INQUIRY` 글에 관리자가 댓글 또는 답글 | 문의 작성자 | `INQUIRY_ANSWER` |
| 답글 | 부모 댓글 작성자 | `COMMENT_REPLY` |
| 답글 | 글 작성자 (부모 댓글 작성자와 다를 때) | `POST_COMMENT` |
| 일반 댓글 | 글 작성자 | `POST_COMMENT` |

보내지 않는 경우:

- 받는 사람이 댓글 작성자 본인이다.
- 받는 사람이 비회원이다(`member_id`가 NULL인 비회원 익명 글이나 댓글).
- 받는 사람이 탈퇴 회원이다(`MemberStatus.WITHDRAWN`). 정지 회원(`SUSPENDED`)은 해제 후 볼 수 있도록 그대로 만든다.
- 한 댓글이 같은 사람에게 두 번 가는 경우 하나만 보낸다. 우선순위는 `INQUIRY_ANSWER`, `COMMENT_REPLY`, `POST_COMMENT` 순이다.

표시 규칙:

- 회원이 익명으로 쓴 댓글은 작성자를 "익명"으로 저장한다. 알림으로 익명 작성자의 닉네임이 드러나면 안 된다.
- 본문은 앞 50자를 잘라 `preview`에 저장한다. 원본 댓글이 수정되거나 삭제돼도 알림은 바꾸지 않는다.

## 데이터

`database/production/033_migration_notification.sql`

```sql
CREATE TABLE notification (
  notification_id BIGINT AUTO_INCREMENT PRIMARY KEY,
  receiver_id     BIGINT       NOT NULL,
  type            VARCHAR(30)  NOT NULL,
  post_id         BIGINT       NOT NULL,
  post_public_id  VARCHAR(36)  NOT NULL,
  post_category_code VARCHAR(30) NOT NULL,
  comment_id      BIGINT       NOT NULL,
  actor_name      VARCHAR(50)  NOT NULL,
  preview         VARCHAR(100) NOT NULL,
  is_read         BOOLEAN      NOT NULL DEFAULT FALSE,
  created_at      DATETIME(6)  NOT NULL,
  CONSTRAINT fk_notification_receiver FOREIGN KEY (receiver_id) REFERENCES member (member_id),
  INDEX idx_notification_receiver_read_id (receiver_id, is_read, notification_id)
);
```

- 인덱스 하나로 배지 개수(`receiver_id, is_read = false`)와 안 읽은 목록(ID 역순)을 처리한다. 정렬은 ID 기준이다.
- 전체 목록(`receiver_id = ?` + ID 역순)은 이 인덱스로 범위는 좁히지만, 중간에 `is_read`가 있어 ID 순서가 보장되지 않으므로 filesort가 생긴다. 90일 보관으로 회원당 행 수가 작아 감수한다. 구현 때 `EXPLAIN`으로 확인하고, 문제가 되면 `(receiver_id, notification_id)` 인덱스를 추가한다.
- `post_id`, `comment_id`에는 FK를 두지 않는다. 알림은 링크용 스냅샷이고, FK가 있으면 글이나 댓글 삭제가 알림 때문에 막힌다.
- `receiver_id`에는 FK를 둔다. 회원 탈퇴는 상태 변경(`WITHDRAWN`)이라 회원 행이 지워지지 않으므로 CASCADE는 두지 않는다.
- `actor_name`, `post_public_id`, `post_category_code`는 값으로 저장한다. 목록 조회에서 member, post JOIN이 없다. 닉네임을 바꿔도 지난 알림에는 예전 이름이 남는다.
- 신규 N:M 관계는 없다. 회원 1 : 알림 N이다.
- 보관 기간은 90일이다. 매일 새벽 4시(KST) 스케줄러가 `created_at` 기준으로 지난 알림을 삭제한다. 한 번에 지우는 행 수를 제한해 긴 락을 피한다.

## API

모두 로그인 필수다. 비로그인은 401이다.

| API | 설명 |
|---|---|
| `GET /api/v1/notifications?unreadOnly=false&cursor=&size=20` | 내 알림 목록. ID 내림차순 커서. `{ items, nextCursor, hasNext }` |
| `GET /api/v1/notifications/unread-count` | `{ count }` |
| `PUT /api/v1/notifications/{notificationId}/read` | 하나 읽음. 남의 알림이면 404, 이미 읽었으면 그대로 204 |
| `PUT /api/v1/notifications/read-all` | 내 안 읽은 알림 전체 읽음. 204 |

- 목록 항목: `notificationId`, `type`, `postPublicId`, `postCategoryCode`, `commentId`, `actorName`, `preview`, `read`, `createdAt`.
- `size`는 최대 50이다.
- 읽음 처리는 `WHERE notification_id = ? AND receiver_id = 본인`으로 조회한다. 남의 알림은 존재 여부를 숨기려고 404로 응답한다.
- 메서드는 프로젝트 관례인 `PUT`을 쓴다. 읽음 처리는 멱등이다.
- 기존 API는 바뀌지 않는다.

## 화면

- 헤더(`SiteChrome`) 프로필 메뉴 옆에 종 아이콘. 로그인 상태에서만 보인다. 안 읽은 개수 배지, 10개 이상은 `9+`.
- 배지는 페이지 이동(`pathname` 변경)과 탭 복귀(`visibilitychange`) 때 갱신한다. 주기 조회는 하지 않는다.
- 종을 누르면 드롭다운에 최근 10개. 상단 "모두 읽음", 하단 "전체 보기". 안 읽은 항목은 배경색으로 구분한다.
- 문구
  - `POST_COMMENT`: "{actor}님이 내 글에 댓글을 남겼어요"
  - `COMMENT_REPLY`: "{actor}님이 내 댓글에 답글을 남겼어요"
  - `INQUIRY_ANSWER`: "문의에 답변이 달렸어요"
  - 아래에 미리보기와 상대 시간(KST).
- 항목을 누르면 읽음 처리 후 `/posts/{publicId}#comments`로 이동한다. 중고장터 글(`MARKET`)은 `/posts` 상세가 404라서 `/market/{publicId}#comments`로 보낸다. 이 경로를 고르려고 `post_category_code`를 함께 저장한다(구현 중 추가). 삭제된 글이면 기존 404 화면이 뜬다.
- `/notifications` 페이지: "전체", "안 읽음" 탭, 커서 기반 더 보기. 비로그인은 로그인 화면으로 보낸다.

## 검증

- 세 종류 각각 생성, 받는 사람 정확성.
- 본인 댓글, 비회원 대상, 탈퇴 회원 대상은 생성하지 않음.
- 내 글의 내 댓글에 답글이 달리면 `COMMENT_REPLY` 하나만 생성.
- 회원 익명 댓글의 `actor_name`이 "익명".
- 댓글 트랜잭션이 롤백되면 알림 없음. 알림 저장 실패해도 댓글은 저장됨.
- 목록 ID 내림차순, `unreadOnly`, 커서, 남의 알림 읽음 처리 404, 모두 읽음 후 개수 0.
- 90일 지난 알림 삭제.
- 백엔드 `./gradlew spotlessCheck test`, 프론트 `npm run lint`, `npm run build`.

## 후속

- 해당 댓글 위치로 바로 스크롤 (답글이 지연 로딩이라 댓글 컴포넌트 수정 필요)
- 추천 알림
- 알림 끄기 설정
- 실시간 푸시 (STOMP `/user/queue` 또는 SSE)
