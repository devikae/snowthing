# 동호회 및 시즌방 홍보 게시판 API 명세서

기존 게시글 API(`/api/v1/posts`)를 그대로 사용하며, 카테고리 코드(`categoryCode`) 파라미터로 구분합니다.

## 1. 게시글 목록 조회

- **엔드포인트**: `GET /api/v1/posts`
- **쿼리 파라미터**:
  - `categoryCode` (string, optional): `CREW` (동호회 모집), `SEASON_ROOM` (시즌방 모집), 또는 빈 값 (전체글)
  - `page` (int, optional): 페이지 번호 (1부터 시작, 기본값 1)
  - `size` (int, optional): 페이지 크기 (기본값 20, 최대 100)
  - `keyword` (string, optional): 검색어
  - `searchType` (string, optional): `TITLE`, `WRITER`, `CONTENT`, `TITLE_CONTENT`
- **응답 (200 OK)**:
  ```json
  {
    "content": [
      {
        "publicId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
        "categoryName": "동호회 모집",
        "categoryCode": "CREW",
        "title": "[휘닉스파크] 26/27 스노우보드 주말 라이딩 크루원 모집합니다",
        "writerNickname": "파우더보더",
        "thumbnailImageUrl": null,
        "hasImage": false,
        "viewCount": 15,
        "commentCount": 3,
        "likeCount": 2,
        "dislikeCount": 0,
        "status": "NORMAL",
        "isDeleted": false,
        "createdAt": "2026-10-06T16:00:00"
      }
    ],
    "pageInfo": {
      "currentPage": 1,
      "size": 20,
      "totalElements": 1,
      "totalPages": 1,
      "hasNext": false
    }
  }
  ```

---

## 2. 게시글 등록

- **엔드포인트**: `POST /api/v1/posts`
- **인증**: 필수 (로그인 세션 필요)
- **요청 Body**:
  ```json
  {
    "categoryCode": "CREW", // 또는 "SEASON_ROOM"
    "title": "동호회/시즌방 홍보 제목",
    "content": "홍보 본문 내용",
    "isAnonymous": false,
    "anonymousPassword": null,
    "imageUrls": []
  }
  ```
- **예외 응답**:
  - **비로그인 사용자가 요청 시**:
    - HTTP 401 Unauthorized (`ErrorCode.INVALID_CREDENTIALS` / `AUTH_001`)
  - **`isAnonymous = true`로 요청 시**:
    - HTTP 400 Bad Request (`ErrorCode.ANONYMOUS_POST_NOT_ALLOWED` / `POST_006`)
    ```json
    {
      "code": "POST_006",
      "message": "해당 게시판은 익명 작성을 지원하지 않습니다."
    }
    ```

---

## 3. 게시글 상세 조회 / 수정 / 삭제

기존 `GET /api/v1/posts/{publicId}`, `PUT /api/v1/posts/{publicId}`, `DELETE /api/v1/posts/{publicId}` 엔드포인트를 그대로 사용합니다.
- 수정 시에도 익명 게시글이 `CREW` 또는 `SEASON_ROOM`으로 카테고리 변경을 시도하면 `POST_006` 예외가 발생합니다.
