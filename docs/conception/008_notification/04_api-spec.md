# 인앱 알림 REST API 명세

## 1. 공통 원칙

- 모든 API는 로그인 세션이 필요하다.
- 수신 회원은 세션의 `CustomUserDetails`로 결정하며 요청 본문에서 회원 ID를 받지 않는다.
- Entity를 직접 반환하지 않고 전용 DTO를 사용한다.
- DTO가 컬렉션을 받으면 `List.copyOf()`로 방어적 복사한다.
- 알림 식별자는 내부 BIGINT가 아닌 `publicId`를 노출한다.

## 2. 알림 목록

### `GET /api/v1/notifications?cursor={publicId}&size=20`

- `cursor`가 없으면 최신 알림부터 조회한다.
- `size` 기본값은 20, 허용 범위는 1~50이다.
- 커서는 직전 응답의 `nextCursor`를 사용한다.

```json
{
  "notifications": [
    {
      "publicId": "4c0b4d8d-5d5d-4d4d-8c7a-879ba9b9f31a",
      "type": "COMMENT_REPLY",
      "message": "작성한 댓글에 새 답글이 달렸습니다.",
      "postPublicId": "a1385ff3-43e8-49ce-96e3-1642b74c28ba",
      "postTitle": "휘닉스파크 같이 가실 분",
      "targetAvailable": true,
      "read": false,
      "createdAt": "2026-10-16T20:10:00"
    }
  ],
  "nextCursor": "54c88cec-3517-4f29-a2c6-a39a9780d81a",
  "hasNext": true
}
```

원본이 공개 가능하지 않으면 `postTitle=null`, `targetAvailable=false`로 반환한다. 알림 메시지는 서버가 `NotificationType`에 따라 생성하며 댓글 본문과 작성자 신원을 포함하지 않는다.

## 3. 미읽음 개수

### `GET /api/v1/notifications/unread-count`

```json
{
  "count": 12
}
```

서버는 실제 개수를 반환한다. 화면에서만 100 이상을 `99+`로 표시한다.

## 4. 한 건 읽음

### `PATCH /api/v1/notifications/{publicId}/read`

- 본인 알림만 처리한다.
- 이미 읽은 알림에 다시 요청해도 성공한다.
- 응답은 `204 No Content`다.
- 존재하지 않거나 다른 회원의 알림은 동일한 `NOTIFICATION_NOT_FOUND` 응답을 사용해 소유 여부를 노출하지 않는다.

## 5. 전체 읽음

### `PATCH /api/v1/notifications/read-all`

- 현재 회원의 `read_at IS NULL` 행만 한 번에 갱신한다.
- 응답은 `204 No Content`다.
- 이미 모두 읽은 상태에서도 성공한다.

## 6. 오류 코드

| ErrorCode | HTTP | 조건 |
|---|---:|---|
| `INVALID_CREDENTIALS` | 401 | 로그인 세션 없음 |
| `NOTIFICATION_NOT_FOUND` | 404 | 알림이 없거나 현재 회원 소유가 아님 |
| `NOTIFICATION_INVALID_PAGE_SIZE` | 400 | `size` 범위 위반 |
| `NOTIFICATION_INVALID_CURSOR` | 400 | 현재 회원 목록에 없는 커서 |

예외 생성과 분기는 문자열 리터럴이 아닌 `ErrorCode`와 정적 타입의 커스텀 예외로 처리한다.
