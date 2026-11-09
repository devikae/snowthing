# 글 스크랩 설계 (IKA-62)

## 목표

로그인한 회원이 게시판 글을 스크랩하고, 내 정보 화면에서 스크랩한 글을 최근 스크랩 순으로 다시 찾는다.

- 같은 글을 여러 번 스크랩해도 한 줄만 남는다. 추가와 취소 모두 멱등하다.
- 비로그인은 401이다.
- 삭제되거나 임시조치(블라인드)된 글은 목록에서 빠진다.
- 범위 밖: 판매글(MARKET), 카풀(CARPOOL) 스크랩, 스크랩 수 집계와 표시, 폴더 분류.

## 기존 설계와 대조

- `sprint01/03.domain-model.md`, `04.erd.md`, `05.api-spec.md`에는 스크랩이 없다. 새 테이블과 API를 이 문서에서 정한다.
- 회원과 글의 N:M 관계를 중계 테이블 `post_scrap`으로 둔다. 추천(`post_reaction`), 차단(`member_block`)과 같은 모양이다.
- 회원 탈퇴 처리표(`025_member_withdrawal/01_design.md`)에 `post_scrap` 행 삭제를 추가한다.

## 데이터 모델

마이그레이션: `database/production/042_migration_post_scrap.sql`

| 컬럼 | 타입 | 설명 |
|---|---|---|
| `post_scrap_id` | BIGINT PK | |
| `member_id` | BIGINT NOT NULL | FK `member` |
| `post_id` | BIGINT NOT NULL | FK `post`, `ON DELETE CASCADE` |
| `created_at` | DATETIME(6) NOT NULL | 스크랩한 시각, KST. 앱이 `Clock`으로 넣는다 |

- `UNIQUE (member_id, post_id)`: 중복 스크랩을 DB가 막는다. 앱은 `INSERT ... ON DUPLICATE KEY UPDATE`로 넣어 동시 요청에도 예외 없이 한 줄만 남긴다. 회원 FK 인덱스도 겸한다.
- `INDEX (member_id, created_at DESC, post_scrap_id DESC)`: 내 스크랩 목록 정렬과 커서 조건을 인덱스로 처리한다.
- `INDEX (post_id)`: 글 FK용.
- `updated_at`은 두지 않는다. 행은 넣고 지우기만 한다.

### 글이 삭제될 때

글 삭제는 소프트 삭제(`post.is_deleted`)라 행이 그대로 있다. 스크랩 행도 남기고 **조회할 때 제외**한다.

- 글이 복구되거나 임시조치가 풀리면 스크랩도 다시 보인다.
- 글을 실제로 지우는 코드는 지금 없다. 나중에 생겨도 `ON DELETE CASCADE`가 스크랩 행을 함께 지워 FK 오류가 나지 않는다. `post_reaction`도 같은 방식이다.

### 회원이 탈퇴할 때

스크랩 목록은 본인만 보는 개인 기록이다. 탈퇴 트랜잭션(`MemberWithdrawalRepository`)에서 `DELETE FROM post_scrap WHERE member_id = ?`로 지운다. 회원 행은 지우지 않으므로 회원 FK에는 CASCADE를 두지 않는다.

탈퇴한 회원이 쓴 글을 다른 회원이 스크랩한 행은 남는다. 글이 남기 때문이다. 목록에서 작성자는 `탈퇴한 회원`으로 보인다.

## API

모두 세션 인증. 쓰기 요청은 CSRF 토큰이 필요하다.

### 스크랩 추가 `PUT /api/v1/posts/{publicId}/scrap`

### 스크랩 취소 `DELETE /api/v1/posts/{publicId}/scrap`

응답 200

```json
{ "scrapped": true, "changed": false }
```

`changed`는 이번 요청으로 상태가 바뀌었는지다. 이미 스크랩한 글을 다시 추가하거나, 스크랩하지 않은 글을 취소해도 200이다.

| 경우 | 상태 | error |
|---|---|---|
| 비로그인, API 토큰, 봇 위임 요청 | 401 | `SCRAP_LOGIN_REQUIRED` |
| 없는 글, 삭제, 임시조치, 1:1 문의 (추가) | 404 | `POST_NOT_FOUND` |
| 판매글, 카풀 (추가) | 400 | `SCRAP_NOT_ALLOWED` |

취소는 글 상태를 보지 않는다. 이미 삭제된 글이나 없는 글이어도 내 행만 지우고 200을 준다.

`/api/v1/posts/**`는 비회원도 쓰는 경로라 SecurityConfig가 열어 둔다. 로그인 확인은 서비스가 한다(알림과 같은 방식).

### 내 스크랩 목록 `GET /api/v1/members/me/scraps?cursor=&size=20`

기존 글 목록의 커서 응답(`CursorPageResponse`, `/api/v1/posts/scroll`)을 따른다. 정렬은 스크랩한 시각 최신순, 같은 시각이면 `post_scrap_id` 내림차순. 커서는 이 두 값이다.

```json
{
  "content": [
    {
      "postPublicId": "...",
      "categoryCode": "FREE",
      "categoryName": "자유게시판",
      "title": "...",
      "writerNickname": "...",
      "commentCount": 3,
      "likeCount": 5,
      "postCreatedAt": "2026-11-09T21:00:00",
      "scrappedAt": "2026-11-09T22:00:00"
    }
  ],
  "pageInfo": { "nextCursor": "...", "hasNext": true, "pageSize": 20 }
}
```

- `size`는 1~50, 범위를 벗어나면 기본값 20 또는 50으로 맞춘다.
- 제외: `post.is_deleted = true`, `post.status != NORMAL`, 판매글, 카풀, 1:1 문의 카테고리. 조건을 SQL에 넣어 페이지 크기가 줄지 않는다.
- 작성자 표시는 글 목록과 같은 `WriterDisplayFormatter`를 쓴다. 익명 글은 마스킹된 IP, 탈퇴 회원은 `탈퇴한 회원`.
- 이 경로는 SecurityConfig의 나머지 규칙(authenticated)에 걸려 비로그인은 401이다.

### 글 상세 `GET /api/v1/posts/{publicId}`

응답에 `scrapped: boolean`을 더한다. 추천 여부(`activeReactionTypes`)처럼 컨트롤러가 상세 응답을 만든 뒤 붙인다. 비로그인은 항상 `false`. 쿼리 하나(`post.publicId`로 조인한 exists)가 더 나간다.

## 화면

- 글 상세(`/posts/{publicId}`): 추천 버튼 줄에 `스크랩` 버튼. 스크랩한 상태면 `스크랩됨`으로 표시하고 누르면 취소한다. 비로그인이 누르면 로그인 화면으로 보낸다(돌아올 주소 포함). 1:1 문의 글에는 버튼을 두지 않는다.
- 내 정보(`/profile`): `스크랩한 글` 구역. 최근 스크랩 20개를 보여 주고 `더 보기`로 다음 커서를 불러온다. 각 줄은 글 제목 링크, 게시판, 작성자, 스크랩한 날짜, `스크랩 취소` 버튼.

## 결정과 대안

| 결정 | 고른 것 | 대안과 버린 이유 |
|---|---|---|
| 중복 방지 | UNIQUE + `ON DUPLICATE KEY UPDATE` | 존재 확인 후 insert: 동시 요청에서 둘 다 통과해 UNIQUE 위반 500이 난다. 예외를 잡는 방식은 트랜잭션이 롤백 전용이 된다 |
| 추가 API 메서드 | `PUT` (멱등) | `POST /scraps`: 리소스 생성 의미가 맞지만 두 번째 요청을 409로 줄지 고민이 생긴다. 추천 API가 이미 `PUT/DELETE .../reaction`이라 맞췄다 |
| 삭제 글 처리 | 행 유지, 조회 시 제외 | 글 삭제 시 스크랩 행 삭제: 복구 시 스크랩이 사라지고, 삭제 로직에 도메인 의존이 늘어난다 |
| 목록 페이징 | 커서(스크랩 시각 + ID) | 페이지 번호: 프로필 구역은 `더 보기`만 있으면 되고 count 쿼리가 필요 없다. 스크랩 중간 취소에도 중복, 누락이 없다 |
| 정렬 기준 | 스크랩한 시각 | 글 작성 시각: "최근에 담은 글"을 찾는 용도라 스크랩 시각이 자연스럽다 |
| 시각 | 앱 `Clock`(KST)으로 넣음 | DB `NOW()`: DB 세션 시간대에 따라 달라진다 |
| 상세 응답 | `scrapped` 필드 추가 | 별도 조회 API: 상세 화면이 요청을 하나 더 보내야 한다 |
| 쓰기 횟수 제한 | 두지 않음 | 추천처럼 IP 제한: 회원 전용이고 행이 회원당 글 하나로 묶여 남용 효과가 작다 |
