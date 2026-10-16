# 인앱 알림 도메인 모델

## 1. 도메인 경계

알림 도메인은 게시글·댓글·추천의 원본 상태를 소유하지 않는다. 다른 도메인에서 확정된 사건을 받아 수신자별 알림을 영속화하고 읽음 상태와 보관 수명주기만 관리한다.

```text
Post / Comment / Reaction
          │ 동기식 도메인 사건
          ▼
Notification 생성 정책
          │
          ▼
MySQL notification
```

댓글 서비스가 알림 Repository를 직접 호출하지 않는다. 알림 생성용 애플리케이션 인터페이스 또는 동기식 트랜잭션 이벤트로 경계를 분리한다.

## 2. Notification 엔티티

| 필드 | 의미 |
|---|---|
| `id` | 내부 BIGINT 식별자 |
| `publicId` | API에 노출할 UUID 문자열 |
| `recipient` | 알림을 소유한 회원 |
| `type` | `POST_COMMENT`, `COMMENT_REPLY`, `POST_BEST` |
| `post` | 이동 대상 게시글 |
| `comment` | 원인이 된 댓글. 베스트 알림은 `null` |
| `idempotencyKey` | 같은 원인의 중복 생성을 막는 고유 키 |
| `readAt` | 읽은 시각. `null`이면 미읽음 |
| `createdAt` | 생성 시각 |

댓글 본문, 게시글 제목, 작성자 닉네임은 알림 테이블에 복제하지 않는다. 민감한 익명 정보와 수정 전 내용을 장기간 남기지 않고, 조회 시 원본의 현재 공개 가능 상태만 사용한다.

## 3. NotificationType

```java
public enum NotificationType {
    POST_COMMENT,
    COMMENT_REPLY,
    POST_BEST
}
```

문자열 메시지를 비교해 분기하지 않는다. 잘못된 타입과 상태는 `ErrorCode`와 `CustomException` 계열로 처리한다.

## 4. 생성 사건

### CommentCreated

```text
- commentId
- postId
- rootCommentId nullable
- actorMemberId nullable
```

- `rootCommentId == null`: 게시글 작성자를 수신자로 계산한다.
- `rootCommentId != null`: 루트 댓글 작성자를 수신자로 계산한다.
- 대댓글에서 게시글 작성자는 수신자 계산에 참여하지 않는다.

### PostBecameBest

```text
- postId
- actorMemberId nullable
- resultingLikeCount
```

추천 수가 실제 변경됐고 결과가 정확히 30일 때 발행한다. `idempotencyKey`가 최종 중복 방지 장치다.

## 5. 불변식

1. `recipient`는 반드시 존재한다.
2. `post`는 반드시 존재한다.
3. 댓글 계열 알림은 `comment`가 반드시 존재한다.
4. 베스트 알림은 `comment`가 없어야 한다.
5. 행 생성 시 `readAt`은 `null`이다.
6. 이미 읽은 알림을 다시 읽음 처리해도 결과가 달라지지 않는다.
7. `idempotencyKey`는 전체 테이블에서 유일하다.
8. 행 소유자만 목록 조회와 읽음 처리를 할 수 있다.

## 6. 멱등 키

```text
POST_COMMENT:{commentId}:{recipientMemberId}
COMMENT_REPLY:{commentId}:{recipientMemberId}
POST_BEST:{postId}:{recipientMemberId}
```

애플리케이션에서 먼저 중복 여부를 확인하더라도 DB UNIQUE 제약을 최종 방어선으로 둔다.

## 7. 트랜잭션 경계

1차 구현은 원본 변경과 알림 저장을 같은 트랜잭션에서 처리한다.

```text
댓글 저장 + 댓글 수 증가 + 알림 저장 = 하나의 커밋
추천 row 변경 + 추천 수 변경 + 베스트 알림 저장 = 하나의 커밋
```

동기식 처리는 알림 저장 실패가 원본 요청까지 실패시키는 대가가 있다. 현재 알림 INSERT는 단일 행이고 외부 네트워크 호출이 없으므로 정합성 이점을 우선한다. 처리량이나 독립 배포 요구가 커지면 Transactional Outbox로 교체한다.
