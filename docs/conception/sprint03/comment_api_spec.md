# 📋 Snowthing 댓글 도메인 공식 API 명세서 (Comment API Specification)

- **문서 번호**: `SPEC-API-SPRINT03-COMMENT`
- **상태**: `Accepted`
- **적용 스프린트**: Sprint 03 (댓글 및 계층형 대댓글 도메인)
- **기반 정책 문서**: `docs/conception/sprint03/comment_policy.md`, `docs/conception/sprint03/ADR-001-comment-hierarchy-and-retrieval-architecture.md`

---

## 1. API 엔드포인트 요약

| 기능 | HTTP Method | Endpoint | 인증 (Auth) | 비고 |
| :--- | :---: | :--- | :---: | :--- |
| **1. 댓글/대댓글 작성** | `POST` | `/api/v1/posts/{publicId}/comments` | 일반회원/익명 | 2단계 평탄화, 루트당 100개 상한 |
| **2. 게시글 댓글 목록 조회** | `GET` | `/api/v1/posts/{publicId}/comments` | 불필요 (Public) | 루트 20개 Batch + 대댓글 Top-5 프리뷰 |
| **3. 대댓글 목록 분리 조회** | `GET` | `/api/v1/comments/{commentId}/replies` | 불필요 (Public) | 5개 초과 대댓글 20개 커서 페이징 |
| **4. 댓글 수정** | `PUT` | `/api/v1/comments/{commentId}` | 작성자 세션/비번 | 비회원 익명 비밀번호 검증 |
| **5. 댓글 삭제** | `DELETE` | `/api/v1/comments/{commentId}` | 작성자 세션/비번/관리자 | Soft Delete, 고아 노드 은닉 정책 |

---

## 2. 세부 API 명세

---

### 1. 댓글 및 대댓글 작성 (Create Comment / Reply)

게시글에 루트 댓글을 작성하거나, 특정 댓글 하위에 대댓글을 작성합니다.

- **HTTP Method**: `POST`
- **URI**: `/api/v1/posts/{publicId}/comments`
- **인증 요구사항**:
  - 일반 회원: 로그인 세션 쿠키 필수
  - 로그인 익명: 로그인 세션 쿠키 필수, `isAnonymous = true`
  - 비로그인 익명: 로그인 불필요, `isAnonymous = true`, `anonymousPassword` (4자리 이상) 필수

#### Request Headers
```http
Content-Type: application/json
X-XSRF-TOKEN: {csrf_token}
```

#### Request Body
```json
{
  "content": "이 스키장 설질 오늘 정말 좋네요!",
  "parentId": null,
  "isAnonymous": false,
  "anonymousPassword": null
}
```

| 필드명 | 타입 | 필수 여부 | 설명 |
| :--- | :---: | :---: | :--- |
| `content` | String | **필수** | 댓글 본문 (1자 이상 1,000자 이하) |
| `parentId` | Long | 선택 | 부모 댓글 ID. `null`이면 루트 댓글, 대댓글 작성 시 대상 댓글 ID 전달 (대댓글에 답글 시 서버에서 최상위 루트 ID로 자동 평탄화) |
| `isAnonymous` | Boolean | **필수** | 익명 작성 여부 (`true` / `false`) |
| `anonymousPassword` | String | 조건부 필수 | 비로그인 익명 작성 시 필수 (4자 이상 20자 이하) |

#### Response (201 Created)
```json
{
  "commentId": 105,
  "postId": 998,
  "parentId": null,
  "writer": {
    "publicId": "member-pub-1234",
    "nickname": "파우더매니아",
    "profileImageUrl": "https://cdn.snowthing.com/profiles/1234.jpg"
  },
  "isAnonymous": false,
  "writerIp": "127.0.0.1",
  "content": "이 스키장 설질 오늘 정말 좋네요!",
  "replyCount": 0,
  "createdAt": "2026-09-01T15:30:00"
}
```

#### 주요 예외 응답
- `400 Bad Request` (`COMMENT_004`): 루트 댓글의 활성 대댓글 수가 이미 100개에 도달한 경우
- `400 Bad Request` (`COMMON_001`): 본문이 비어있거나 비로그인 익명 비밀번호가 누락된 경우
- `404 Not Found` (`POST_001`): 존재하지 않거나 삭제된 게시글인 경우
- `404 Not Found` (`COMMENT_002`): 지정한 `parentId` 부모 댓글이 존재하지 않는 경우

---

### 2. 게시글 댓글 목록 조회 (Read Post Comments - Root Batch + Top-5 Preview)

게시글 상세 화면에서 루트 댓글 20개와 각 루트 댓글 하위의 대댓글 상위 5개를 일괄 조회합니다.

- **HTTP Method**: `GET`
- **URI**: `/api/v1/posts/{publicId}/comments`
- **인증 요구사항**: 없음 (Public)

#### Request Query Parameters
| 파라미터명 | 타입 | 기본값 | 설명 |
| :--- | :---: | :---: | :--- |
| `cursor` | Long | `null` | 커서 페이징용 마지막 루트 댓글 ID (`commentId`). 첫 페이지 조회 시 생략 |
| `size` | Integer | `20` | 조회할 루트 댓글 수 (기본 20개, 최대 50개) |

#### Response (200 OK)
```json
{
  "publicId": "post-pub-5678",
  "totalCommentCount": 42,
  "comments": [
    {
      "commentId": 101,
      "parentId": null,
      "writer": {
        "publicId": "member-pub-1234",
        "nickname": "파우더매니아",
        "profileImageUrl": "https://cdn.snowthing.com/profiles/1234.jpg"
      },
      "isAnonymous": false,
      "writerIp": "211.234.***.***",
      "content": "하이원 아테나 슬로프 오픈했나요?",
      "isDeleted": false,
      "replyCount": 8,
      "previewReplies": [
        {
          "commentId": 102,
          "parentId": 101,
          "writer": {
            "publicId": "member-pub-8888",
            "nickname": "설질감별사",
            "profileImageUrl": null
          },
          "isAnonymous": false,
          "writerIp": "175.120.***.***",
          "content": "네 오늘 오전 9시에 오픈했습니다!",
          "isDeleted": false,
          "createdAt": "2026-09-01T15:32:00"
        }
      ],
      "hasMoreReplies": true,
      "createdAt": "2026-09-01T15:30:00"
    },
    {
      "commentId": 103,
      "parentId": null,
      "writer": null,
      "isAnonymous": true,
      "writerIp": "121.160.***.***",
      "content": "삭제된 댓글입니다.",
      "isDeleted": true,
      "replyCount": 1,
      "previewReplies": [
        {
          "commentId": 104,
          "parentId": 103,
          "writer": {
            "publicId": "member-pub-9999",
            "nickname": "스노우보더",
            "profileImageUrl": null
          },
          "isAnonymous": false,
          "writerIp": "220.70.***.***",
          "content": "삭제된 질문이지만 답변 남깁니다. 야간개장은 18시부터입니다.",
          "isDeleted": false,
          "createdAt": "2026-09-01T15:35:00"
        }
      ],
      "hasMoreReplies": false,
      "createdAt": "2026-09-01T15:31:00"
    }
  ],
  "nextCursor": 103,
  "hasNext": true
}
```

---

### 3. 대댓글 목록 분리 페이징 조회 (Read Separated Replies)

특정 루트 댓글 하위에 5개를 초과하는 대댓글이 있을 때, 사용자가 "답글 더보기"를 클릭하여 20개 단위로 추가 조회합니다.

- **HTTP Method**: `GET`
- **URI**: `/api/v1/comments/{commentId}/replies`
- **인증 요구사항**: 없음 (Public)

#### Request Query Parameters
| 파라미터명 | 타입 | 기본값 | 설명 |
| :--- | :---: | :---: | :--- |
| `cursor` | Long | `null` | 커서 페이징용 마지막 대댓글 ID (`commentId`). 첫 더보기 호출 시 5번째 프리뷰 대댓글의 ID를 전달 |
| `size` | Integer | `20` | 조회할 대댓글 수 (기본 20개, 최대 50개) |

#### Response (200 OK)
```json
{
  "rootCommentId": 101,
  "totalReplyCount": 8,
  "replies": [
    {
      "commentId": 106,
      "parentId": 101,
      "writer": {
        "publicId": "member-pub-7777",
        "nickname": "카빙장인",
        "profileImageUrl": null
      },
      "isAnonymous": false,
      "writerIp": "112.180.***.***",
      "content": "빅토리아 슬로프는 다음 주 오픈 예정이랍니다.",
      "isDeleted": false,
      "createdAt": "2026-09-01T15:40:00"
    }
  ],
  "nextCursor": 106,
  "hasNext": false
}
```

---

### 4. 댓글 수정 (Update Comment)

본인이 작성한 댓글의 본문을 수정합니다.

- **HTTP Method**: `PUT`
- **URI**: `/api/v1/comments/{commentId}`
- **인증 요구사항**: 로그인 회원(본인 세션 일치) 또는 비로그인 익명(`anonymousPassword` 일치)

#### Request Body
```json
{
  "content": "수정된 댓글 본문 내용입니다.",
  "anonymousPassword": "mypassword123"
}
```

#### Response (200 OK)
```json
{
  "commentId": 105,
  "content": "수정된 댓글 본문 내용입니다.",
  "updatedAt": "2026-09-01T15:45:00"
}
```

---

### 5. 댓글 삭제 (Delete Comment - Soft Delete)

댓글을 삭제 처리합니다 (`is_deleted = true`).

- **HTTP Method**: `DELETE`
- **URI**: `/api/v1/comments/{commentId}`
- **인증 요구사항**: 로그인 작성자 본인, 최고 관리자(`ROLE_ADMIN`), 또는 비로그인 익명 비밀번호 일치

#### Request Body
```json
{
  "anonymousPassword": "mypassword123"
}
```

#### Response (200 OK)
```json
{
  "message": "댓글이 삭제되었습니다."
}
```

---

## 3. 공통 에러 코드 매핑

| HTTP Status | ErrorCode | 에러 메시지 |
| :--- | :--- | :--- |
| `400 Bad Request` | `COMMENT_004` | 루트 댓글 1개당 작성 가능한 대댓글 수는 최대 100개입니다. |
| `400 Bad Request` | `COMMENT_003` | 동일한 게시글의 댓글에만 대댓글을 달 수 있습니다. |
| `400 Bad Request` | `COMMON_001` | 잘못된 입력값입니다. (글자수 제한 위반, 비밀번호 누락 등) |
| `403 Forbidden` | `AUTH_002` | 해당 작업을 수행할 권한이 없습니다. |
| `403 Forbidden` | `POST_004` | 비회원 익명 비밀번호가 일치하지 않습니다. |
| `404 Not Found` | `COMMENT_001` | 존재하지 않거나 이미 삭제된 댓글입니다. |
| `404 Not Found` | `COMMENT_002` | 존재하지 않는 부모 댓글입니다. |
| `404 Not Found` | `POST_001` | 존재하지 않거나 삭제된 게시글입니다. |
