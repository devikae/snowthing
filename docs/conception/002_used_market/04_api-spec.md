# 중고장터 REST API 명세

- 문서 번호: `SPEC-API-USED-MARKET-001`
- 상태: Draft
- 작성일: 2026-09-28
- 기반 문서: [`01_requirements.md`](./01_requirements.md)
- Base URL: `/api/v1`
- 인증 방식: Session Cookie (`JSESSIONID`)
- 데이터 형식: `application/json`

## 1. 기본 원칙

중고장터 화면과 상세 데이터는 로그인 회원에게만 제공한다. 예외적으로 홈 공개 미리보기 API는 최신 판매글의 `publicId`와 썸네일 URL만 반환한다. 제목·가격·상태·판매자·연락처는 공개하지 않는다.

판매글은 기존 `Post`와 중고거래 확장 정보를 함께 사용하지만, 외부 API에서는 하나의 `market-listing` 자원으로 다룬다. 일반 게시글 API로 중고 판매글을 생성하거나 수정하지 않는다.

- 판매글은 익명으로 작성할 수 없다.
- 추천과 비추천은 지원하지 않는다.
- 댓글과 대댓글은 기존 댓글 API를 재사용한다.
- 이미지 업로드는 기존 이미지 API를 재사용한다.
- 목록은 최신순으로만 정렬한다.
- 목록형과 앨범형은 같은 목록 응답을 사용한다.
- 모든 요청·응답의 컬렉션 DTO는 방어적 복사를 적용한다.

## 2. 엔드포인트 요약

| 기능 | Method | Endpoint | 권한 |
|---|---:|---|---|
| 홈 썸네일 미리보기 | `GET` | `/api/v1/market-listings/preview` | 공개 |
| 상품 분류 조회 | `GET` | `/api/v1/market/categories` | 로그인 회원 |
| 판매글 목록 | `GET` | `/api/v1/market-listings` | 로그인 회원 |
| 판매글 등록 | `POST` | `/api/v1/market-listings` | 로그인 회원 |
| 판매글 상세 | `GET` | `/api/v1/market-listings/{publicId}` | 로그인 회원 |
| 판매글 수정 | `PUT` | `/api/v1/market-listings/{publicId}` | 작성자 |
| 거래 상태 변경 | `PATCH` | `/api/v1/market-listings/{publicId}/trade-status` | 작성자 |
| 판매글 삭제 | `DELETE` | `/api/v1/market-listings/{publicId}` | 작성자·관리자 |
| 이미지 업로드 | `POST` | `/api/v1/images` | 로그인 회원 |
| 댓글 목록 | `GET` | `/api/v1/posts/{publicId}/comments` | 로그인 회원 |
| 댓글 작성 | `POST` | `/api/v1/posts/{publicId}/comments` | 로그인 회원 |
| 대댓글 목록 | `GET` | `/api/v1/comments/{commentId}/replies` | 로그인 회원 |
| 댓글 수정 | `PUT` | `/api/v1/comments/{commentId}` | 작성자 |
| 댓글 삭제 | `DELETE` | `/api/v1/comments/{commentId}` | 작성자·관리자 |
| 관리자 상태 변경 | `PATCH` | `/api/v1/admin/market-listings/{publicId}/moderation-status` | 관리자 |

## 3. 공통 코드

### 3.1. 상품 상태 `productCondition`

| 코드 | 표시명 |
|---|---|
| `NEW` | 새 상품 |
| `LIKE_NEW` | 거의 새 상품 |
| `GOOD` | 사용감 적음 |
| `USED` | 사용감 있음 |
| `DAMAGED` | 수리·하자 있음 |

`DAMAGED`를 선택해도 별도 하자 설명 필드는 받지 않는다. 필요한 설명은 판매자가 본문에 작성하며 서버는 구체성까지 판별하지 않는다.

### 3.2. 거래 방식 `transactionMethod`

| 코드 | 표시명 |
|---|---|
| `DIRECT` | 직거래 |
| `DELIVERY` | 택배 |
| `BOTH` | 모두 가능 |

### 3.3. 거래 상태 `tradeStatus`

| 코드 | 표시명 |
|---|---|
| `ON_SALE` | 판매 중 |
| `RESERVED` | 예약 중 |
| `SOLD` | 판매 완료 |

최초 등록 상태는 서버가 `ON_SALE`로 정한다. 클라이언트는 등록 요청에서 거래 상태를 지정하지 않는다.

### 3.4. 게시글 운영 상태 `moderationStatus`

거래 상태와 게시글 운영 상태는 서로 다른 값이다.

| 코드 | 의미 |
|---|---|
| `NORMAL` | 정상 노출 |
| `HIDDEN` | 관리자 숨김 |
| `BLOCKED` | 관리자 차단 |
| `DELETED` | 작성자 또는 관리자 삭제, DELETE 요청으로만 전환 |

일반 회원의 목록과 상세 조회에는 `NORMAL`인 판매글만 노출한다.

## 4. 상품 분류 조회

### `GET /api/v1/market/categories`

판매글 작성 폼과 목록 필터에서 사용할 상품 분류를 조회한다.

#### Response `200 OK`

```json
{
  "categories": [
    {
      "code": "SNOWBOARD",
      "name": "스노보드",
      "sortOrder": 1
    },
    {
      "code": "SKI",
      "name": "스키",
      "sortOrder": 2
    }
  ]
}
```

- 일반 회원 응답에는 `isActive = true`인 분류만 포함한다.
- 판매글 응답에는 분류가 비활성화됐더라도 저장된 코드와 표시명을 반환한다.
- 정렬은 `sortOrder ASC, categoryId ASC`로 고정한다.

### 4.1. 홈 썸네일 공개 미리보기

#### `GET /api/v1/market-listings/preview`

홈 중고장터 위젯에서 사용할 최신 썸네일을 최대 4건 반환한다. 이 API만 비로그인 접근을 허용한다.

```json
{
  "items": [
    {
      "publicId": "0a4dd90c-9937-4c45-b58b-38f9ea44b33a",
      "thumbnailImageUrl": "https://images.snowthing.org/public/posts/thumbnails/image-id.jpg"
    }
  ]
}
```

- `ON_SALE`, `RESERVED`이면서 운영 상태가 `NORMAL`인 최신 판매글만 대상이다.
- 제목, 가격, 상품 상태, 거래 상태, 판매자, 연락처, 등록 시각은 반환하지 않는다.
- 이미지가 없는 판매글의 `thumbnailImageUrl`은 `null`이며 홈에서 기본 이미지를 표시한다.
- 썸네일 클릭 시 비로그인은 로그인 화면으로, 로그인 회원은 `/market/{publicId}`로 이동한다.

## 5. 판매글 목록

### `GET /api/v1/market-listings`

#### Query Parameters

| 이름 | 타입 | 필수 | 기본값 | 설명 |
|---|---|---:|---|---|
| `page` | Integer | 선택 | `1` | 1부터 시작하는 페이지 번호, 최대 100 |
| `size` | Integer | 선택 | `20` | 페이지 크기, 1~50 |
| `keyword` | String | 선택 | 없음 | 제목 검색어, 앞뒤 공백 제거 |
| `categoryCode` | String | 선택 | 없음 | 상품 분류 코드 |
| `productCondition` | Enum | 선택 | 없음 | 상품 상태 |
| `tradeStatus` | Enum | 선택 | 없음 | 거래 상태 |

정렬 파라미터는 제공하지 않는다. 항상 `createdAt DESC, marketListingId DESC`로 조회한다.

#### 판매 완료 포함 규칙

1. `keyword`와 `tradeStatus`가 모두 없으면 `ON_SALE`, `RESERVED`만 조회한다.
2. `keyword`가 있고 `tradeStatus`가 없으면 `ON_SALE`, `RESERVED`, `SOLD`를 모두 검색한다.
3. `tradeStatus`가 있으면 해당 상태만 조회한다. 이 규칙은 `keyword` 유무와 관계없이 우선한다.
4. `HIDDEN`, `BLOCKED`, `DELETED` 글은 검색 조건과 관계없이 일반 회원 응답에서 제외한다.

#### Request Example

```http
GET /api/v1/market-listings?page=1&size=20&categoryCode=SNOWBOARD&productCondition=GOOD
```

#### Response `200 OK`

```json
{
  "content": [
    {
      "publicId": "0a4dd90c-9937-4c45-b58b-38f9ea44b33a",
      "title": "2425 데크 판매합니다",
      "category": {
        "code": "SNOWBOARD",
        "name": "스노보드"
      },
      "productCondition": "GOOD",
      "transactionMethod": "BOTH",
      "tradeStatus": "ON_SALE",
      "price": 350000,
      "negotiable": true,
      "free": false,
      "thumbnailImageUrl": "https://images.snowthing.org/public/posts/thumbnails/image-id.jpg",
      "seller": {
        "publicId": "d98ae1cb-49b7-4105-aec2-2aa99818d28a",
        "nickname": "휘팍보더",
        "profileImageUrl": "https://images.snowthing.org/profiles/profile-id.jpg"
      },
      "viewCount": 31,
      "commentCount": 4,
      "createdAt": "2026-09-28T14:10:00",
      "updatedAt": "2026-09-28T14:10:00"
    }
  ],
  "pageInfo": {
    "page": 1,
    "totalPages": 7,
    "totalElements": 135,
    "nextCursor": null,
    "hasNext": true,
    "pageSize": 20
  }
}
```

사진이 없는 판매글의 `thumbnailImageUrl`은 `null`로 반환한다. 기본 이미지 선택은 프런트엔드가 담당한다.

## 6. 판매글 등록

### `POST /api/v1/market-listings`

#### Request Body

```json
{
  "categoryCode": "SNOWBOARD",
  "title": "2425 데크 판매합니다",
  "content": "상판에 가벼운 사용감이 있습니다. 직거래 장소는 연락 후 협의하겠습니다.",
  "productCondition": "GOOD",
  "transactionMethod": "BOTH",
  "price": 350000,
  "negotiable": true,
  "free": false,
  "contact": "카카오톡 snowthing_board",
  "imageUrls": [
    "https://images.snowthing.org/public/posts/originals/image-1.jpg",
    "https://images.snowthing.org/public/posts/originals/image-2.jpg"
  ]
}
```

#### 필드 규칙

| 필드 | 필수 | 규칙 |
|---|---:|---|
| `categoryCode` | 필수 | 활성 상품 분류 코드 |
| `title` | 필수 | 공백 제외 1~200자 |
| `content` | 필수 | 공백만 입력할 수 없음 |
| `productCondition` | 필수 | 상품 상태 코드 |
| `transactionMethod` | 필수 | 거래 방식 코드 |
| `price` | 조건부 | 무료 나눔이 아니면 1~20,000,000원의 정수 |
| `negotiable` | 필수 | 가격 협의 가능 여부 |
| `free` | 필수 | 무료 나눔 여부 |
| `contact` | 필수 | 앞뒤 공백 제거 후 1~30자 일반 문자열 |
| `imageUrls` | 선택 | 0~5개, 요청 순서가 표시 순서 |

교차 필드 검증은 다음과 같다.

- `free = true`: `price`는 생략하거나 0만 허용하고, 서버는 `price = 0`, `negotiable = false`로 저장한다.
- `free = false`: `price`는 1 이상의 정수여야 한다.
- `imageUrls`는 Snowthing 이미지 저장 경로만 허용하며 중복 URL을 허용하지 않는다.

#### Response `201 Created`

```json
{
  "publicId": "0a4dd90c-9937-4c45-b58b-38f9ea44b33a",
  "tradeStatus": "ON_SALE",
  "moderationStatus": "NORMAL",
  "version": 0,
  "createdAt": "2026-09-28T14:10:00"
}
```

판매글과 중고거래 확장 정보는 하나의 DB 트랜잭션에서 함께 저장한다. 어느 한쪽 저장이 실패하면 전체 요청을 롤백한다.

## 7. 판매글 상세

### `GET /api/v1/market-listings/{publicId}`

정상 판매글만 조회한다. 조회수 중복 방지 규칙은 기존 게시글 상세와 같은 `viewed_posts` 쿠키 정책을 사용한다.

#### Response `200 OK`

```json
{
  "publicId": "0a4dd90c-9937-4c45-b58b-38f9ea44b33a",
  "title": "2425 데크 판매합니다",
  "content": "상판에 가벼운 사용감이 있습니다. 직거래 장소는 연락 후 협의하겠습니다.",
  "category": {
    "code": "SNOWBOARD",
    "name": "스노보드"
  },
  "productCondition": "GOOD",
  "transactionMethod": "BOTH",
  "tradeStatus": "ON_SALE",
  "price": 350000,
  "negotiable": true,
  "free": false,
  "contact": "카카오톡 snowthing_board",
  "seller": {
    "publicId": "d98ae1cb-49b7-4105-aec2-2aa99818d28a",
    "nickname": "휘팍보더",
    "profileImageUrl": "https://images.snowthing.org/profiles/profile-id.jpg"
  },
  "images": [
    "https://images.snowthing.org/public/posts/originals/image-1.jpg",
    "https://images.snowthing.org/public/posts/originals/image-2.jpg"
  ],
  "viewCount": 32,
  "commentCount": 4,
  "version": 0,
  "canEdit": true,
  "canDelete": true,
  "createdAt": "2026-09-28T14:10:00",
  "updatedAt": "2026-09-28T14:10:00"
}
```

- `canEdit`는 작성자 본인일 때만 `true`다.
- `canDelete`는 작성자 또는 관리자일 때 `true`다.
- 판매자 이메일과 회원가입 정보는 응답하지 않는다.
- 연락처는 목록 응답에 포함하지 않고 로그인 후 상세 응답에서만 제공한다.

## 8. 판매글 수정

### `PUT /api/v1/market-listings/{publicId}`

작성자 본인만 수정할 수 있다. 전체 수정 API이므로 현재 값을 포함한 모든 필드를 전송한다.

#### Request Body

```json
{
  "version": 0,
  "categoryCode": "SNOWBOARD",
  "title": "2425 데크 가격 내립니다",
  "content": "상판에 가벼운 사용감이 있습니다.",
  "productCondition": "GOOD",
  "transactionMethod": "DIRECT",
  "price": 320000,
  "negotiable": false,
  "free": false,
  "contact": "카카오톡 snowthing_board",
  "imageUrls": [
    "https://images.snowthing.org/public/posts/originals/image-1.jpg"
  ]
}
```

- 등록과 같은 필드 검증을 적용한다.
- `version`은 필수다.
- 요청 버전과 현재 버전이 다르면 아무 값도 저장하지 않고 `409 MARKET_LISTING_UPDATE_CONFLICT`를 반환한다.
- 제목·본문·이미지만 바뀌어도 중고거래 확장 엔티티의 버전을 증가시킨다.
- 이미지 목록은 요청값 전체로 교체한다. 빈 배열이면 첨부 이미지를 모두 제거한다.

#### Response `200 OK`

```json
{
  "publicId": "0a4dd90c-9937-4c45-b58b-38f9ea44b33a",
  "version": 1,
  "updatedAt": "2026-09-28T15:00:00"
}
```

## 9. 거래 상태 변경

### `PATCH /api/v1/market-listings/{publicId}/trade-status`

본문 전체를 수정하지 않고 거래 상태만 바꾼다. 작성자 본인만 요청할 수 있다.

#### Request Body

```json
{
  "tradeStatus": "RESERVED",
  "version": 1
}
```

#### Response `200 OK`

```json
{
  "publicId": "0a4dd90c-9937-4c45-b58b-38f9ea44b33a",
  "tradeStatus": "RESERVED",
  "version": 2,
  "updatedAt": "2026-09-28T15:10:00"
}
```

- 상태 변경도 낙관적 락 검사를 적용한다.
- `ON_SALE`, `RESERVED`, `SOLD` 사이의 변경은 모두 허용한다. 잘못 판매 완료로 바꾸거나 거래가 취소된 경우 다시 판매 중으로 되돌릴 수 있다.
- 같은 상태로 변경하는 요청은 오류로 처리하지 않고 현재 상태와 버전을 반환한다. 실제 변경이 없으면 버전도 증가시키지 않는다.

## 10. 판매글 삭제

### `DELETE /api/v1/market-listings/{publicId}`

작성자 또는 관리자가 요청할 수 있다. 판매글과 중고거래 확장 정보를 물리 삭제하지 않는다.

#### Response `204 No Content`

- 게시글 운영 상태를 `DELETED`로 변경한다.
- `isDeleted = true`, `deletedAt = 현재 시각`을 기록한다.
- 이후 목록·검색·상세 조회에서 제외한다.
- 연결된 댓글과 이미지 DB 행은 보존한다.
- S3 원본과 썸네일 객체의 정리 정책은 별도 수명 주기 설계에서 다룬다.

## 11. 이미지 업로드

### `POST /api/v1/images`

기존 게시글 이미지 업로드 API를 그대로 사용한다.

```http
Content-Type: multipart/form-data
```

| 규칙 | 값 |
|---|---|
| 파일당 최대 크기 | 5MB |
| 허용 확장자 | JPG, JPEG, PNG, WebP |
| 원본 | 비율과 원본 형식 유지 |
| 썸네일 | 최대 320×320, JPEG 품질 0.8 |
| 원본 저장 경로 | `public/posts/originals/{UUID}.{ext}` |
| 썸네일 저장 경로 | `public/posts/thumbnails/{UUID}.jpg` |

업로드 API는 파일 한 개를 처리한다. 판매글 등록·수정 요청에서 최대 5개의 이미지 URL을 허용한다.

#### Response `201 Created`

```json
{
  "imageUrl": "https://images.snowthing.org/public/posts/originals/image-1.jpg",
  "imageKey": "public/posts/originals/image-1.jpg"
}
```

업로드 후 판매글 등록을 취소하면 연결되지 않은 원본과 썸네일이 남을 수 있다. 임시 업로드와 고아 객체 정리는 이번 API 범위에 포함하지 않고 운영 부채로 기록한다.

## 12. 댓글과 대댓글

댓글 경로는 기존 게시글 API를 재사용한다. 중고장터 판매글의 `publicId`도 연결된 `Post.publicId`다.

### 적용 차이

- 일반 게시글 댓글은 기존 공개 정책을 유지한다.
- 중고장터 판매글의 댓글 조회·작성은 로그인 회원만 허용한다.
- 중고장터 댓글은 익명으로 작성할 수 없다.
- 중고장터 댓글 요청에서 `isAnonymous = true`를 보내면 `400 MARKET_ANONYMOUS_COMMENT_NOT_ALLOWED`를 반환한다.
- 댓글 수정·삭제·2단계 대댓글·루트당 대댓글 100개 제한은 기존 정책을 그대로 사용한다.
- 댓글 응답의 작성자 정보는 닉네임과 프로필 이미지를 제공한다.

기존 댓글 서비스는 대상 게시글이 중고장터인지 확인한 뒤 조건부 인증 정책을 적용해야 한다. 컨트롤러에서만 검사하면 대댓글 분리 API나 다른 호출 경로가 정책을 우회할 수 있으므로 서비스 계층에서도 검증한다.

## 13. 추천 API 차단

다음 기존 추천 API에 중고장터 판매글의 `publicId`를 전달하면 요청을 거부한다.

```http
PUT /api/v1/posts/{publicId}/reaction?type=LIKE
DELETE /api/v1/posts/{publicId}/reaction?type=LIKE
```

#### Response `400 Bad Request`

```json
{
  "code": "MARKET_007",
  "error": "MARKET_REACTION_NOT_ALLOWED",
  "message": "중고장터 판매글은 추천과 비추천을 지원하지 않습니다."
}
```

화면에서 추천 버튼을 숨기는 것만으로 끝내지 않고 `ReactionService`가 게시글 종류를 검사해야 한다.

## 14. 관리자 운영 상태 변경

### `PATCH /api/v1/admin/market-listings/{publicId}/moderation-status`

`ROLE_ADMIN`만 사용할 수 있다.

1차 범위에서는 신고 접수 기능을 만들지 않는다. 관리자는 운영상 확인한 판매글을 직접 숨김·차단·삭제할 수 있다.

- `HIDDEN`: 일반 회원에게 노출하지 않으며 관리자가 복원할 수 있다.
- `BLOCKED`: 정책 위반 글로 차단하며 일반 회원에게 노출하지 않는다.
- `NORMAL`: 숨김 또는 차단 상태에서 정상 상태로 복원한다.

삭제는 운영 상태 PATCH가 아니라 `DELETE /api/v1/market-listings/{publicId}`를 사용한다. 작성자와 관리자가 같은 Soft Delete 경로를 사용한다.

운영 사유의 DB 저장 여부와 관리자 이력 테이블은 관리자 기능 설계에서 확정한다. 중고장터 1차 구현의 요청은 상태만 받는다.

```json
{
  "moderationStatus": "HIDDEN"
}
```

## 15. 오류 응답

모든 오류는 현재 공통 형식을 사용한다.

```json
{
  "code": "MARKET_006",
  "error": "MARKET_LISTING_UPDATE_CONFLICT",
  "message": "다른 화면에서 판매글이 먼저 수정됐습니다. 최신 내용을 다시 확인해 주세요."
}
```

| HTTP | ErrorCode Enum | 코드 | 발생 조건 |
|---:|---|---|---|
| `400` | `MARKET_CATEGORY_INACTIVE` | `MARKET_003` | 비활성 상품 분류로 등록·수정 |
| `400` | `MARKET_INVALID_PRICE` | `MARKET_004` | 무료 여부와 가격 조합이 잘못됨 |
| `400` | `MARKET_IMAGE_LIMIT_EXCEEDED` | `MARKET_005` | 이미지 URL이 5개를 초과함 |
| `400` | `MARKET_REACTION_NOT_ALLOWED` | `MARKET_007` | 중고장터 글에 추천·비추천 요청 |
| `400` | `MARKET_ANONYMOUS_COMMENT_NOT_ALLOWED` | `MARKET_008` | 중고장터 댓글을 익명으로 작성 |
| `400` | `MARKET_INVALID_TRADE_STATUS` | `MARKET_009` | 지원하지 않는 거래 상태 요청 |
| `400` | `INVALID_INPUT` | `COMMON_001` | 필수값·길이·Enum 등 입력 검증 실패 |
| `400` | `MARKET_PAGE_LIMIT_EXCEEDED` | `MARKET_011` | 100페이지 초과 요청 |
| `400` | `MARKET_INVALID_PAGE_SIZE` | `MARKET_010` | 페이지 크기가 1~50 범위를 벗어남 |
| `401` | `INVALID_CREDENTIALS` | `AUTH_001` | 비로그인 사용자의 중고장터 요청 |
| `403` | `ACCESS_DENIED` | `AUTH_002` | 다른 회원의 판매글 수정·삭제 |
| `404` | `MARKET_LISTING_NOT_FOUND` | `MARKET_001` | 없거나 일반 회원에게 보이지 않는 판매글 |
| `404` | `MARKET_CATEGORY_NOT_FOUND` | `MARKET_002` | 존재하지 않는 상품 분류 코드 |
| `409` | `MARKET_LISTING_UPDATE_CONFLICT` | `MARKET_006` | 낙관적 락 버전 불일치 |

문자열 메시지를 비교하거나 직접 예외 메시지를 만들어 분기하지 않는다. 모든 오류는 `ErrorCode` Enum과 `CustomException` 계열의 타입 기반 예외로 처리한다.

## 16. 권한 행렬

| 기능 | 비로그인 | 로그인 회원 | 작성자 | 관리자 |
|---|---:|---:|---:|---:|
| 홈 썸네일 미리보기 | 허용 | 허용 | 허용 | 허용 |
| 분류 조회 | 거부 | 허용 | 허용 | 허용 |
| 목록·검색 | 거부 | 허용 | 허용 | 허용 |
| 상세 조회 | 거부 | 허용 | 허용 | 허용 |
| 판매글 등록 | 거부 | 허용 | 허용 | 허용 |
| 판매글 수정 | 거부 | 거부 | 허용 | 거부 |
| 거래 상태 변경 | 거부 | 거부 | 허용 | 거부 |
| 판매글 삭제 | 거부 | 거부 | 허용 | 허용 |
| 댓글 조회·작성 | 거부 | 허용 | 허용 | 허용 |
| 관리자 숨김·차단·복원 | 거부 | 거부 | 거부 | 허용 |

관리자는 운영 상태와 삭제를 다룰 수 있지만 판매자의 가격, 연락처, 제목, 본문을 대신 수정하지 않는다.

## 17. 구현 시 확인할 계약

1. `MarketListingCreateRequest`, `MarketListingUpdateRequest`의 `imageUrls`는 생성자에서 `List.copyOf()`로 방어적 복사한다.
2. 목록과 상세 응답의 이미지 컬렉션도 `List.copyOf()`로 반환한다.
3. 컨트롤러는 Repository를 직접 호출하지 않는다.
4. 서비스는 `HttpSession`, `SecurityContextHolder`를 직접 사용하지 않고 인증 주체를 인자로 받는다.
5. 생성·수정·상태 변경은 `Post`와 `MarketListing`을 같은 트랜잭션에서 변경한다.
6. 제목·본문만 수정해도 `MarketListing.version`을 증가시키고 충돌을 검사한다.
7. 기본 목록과 검색 목록의 판매 완료 포함 규칙을 Repository 쿼리 테스트로 고정한다.
8. 일반 게시글 API, 댓글 API, 추천 API를 통한 정책 우회를 통합 테스트한다.
9. 필터 조합별 COUNT 쿼리와 목록 쿼리가 같은 조건을 사용하도록 검증한다.
10. OFFSET 100페이지 제한과 안정적인 보조 정렬을 검증한다.

## 18. 후속 결정

- 판매글 이미지 요청을 `imageUrls`에서 `imageKey`로 바꿀지 여부
- 판매 완료 글의 장기 보관 및 아카이브 정책
- 관리자 조치 사유와 감사 이력 저장 구조
- 고아 이미지 정리와 임시 업로드 수명 주기
- 중고장터 전용 쪽지 기능 연결
- 운영 데이터 증가 시 커서 조회 API 추가 여부
