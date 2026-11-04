# 1단계 키워드 기준선: search_document와 근거 검색·조회 API

- 상태: 로컬 구현·검증 완료, 운영 미적용
- 기준일: 2026-11-04 (KST)
- Linear: IKA-7 (부모 IKA-5)
- 상위 설계: `01_design.md` (IKA-6 브랜치에서 작성). 이 문서는 D3, D4, D5, D7을 구현한 결과다.

## 1. 범위

| 포함 | 제외 (다른 티켓) |
|---|---|
| `post.content_version`, `search_document` 테이블과 FULLTEXT ngram 인덱스 (027) | 원문 변경 시 증분 갱신, outbox, tombstone (2단계 IKA-10) |
| 전체 재구축 API | MCP 도구·Skill 연결 (IKA-8) |
| 근거 검색 API, 근거 조회 API (관리자 전용) | 평가 자료와 Recall@k 기준선 측정 (IKA-9) |
| 구간 나누기·정규화·해시 | 의미 검색·재정렬 (3단계 IKA-11) |
| 권한 필터·삭제 제외 테스트 | API 토큰 (4단계 IKA-12) |

## 2. 스키마 (027_migration_search_document.sql)

### post.content_version

- `BIGINT NOT NULL DEFAULT 0`. 기존 행은 0에서 시작한다.
- `Post` 엔티티 메서드에서 올린다.
  - `update(title, content, category)`: 셋 중 하나라도 실제로 바뀔 때만 +1. 카테고리는 ID로 비교한다.
  - `changeStatus(status)`: 상태가 바뀔 때만 +1.
  - `softDelete()`: 삭제되지 않은 글을 삭제할 때만 +1.
- 조회수·댓글 수·반응 수·이미지 변경은 버전과 무관하다. `@Version`이 아니므로 기존 비관적 락 경로와 카운터 갱신 쿼리에는 영향이 없다(D5).
- 게시글 상태·삭제를 바꾸는 코드는 모두 위 엔티티 메서드를 거친다(`PostService`, `CarpoolWriterService`, `MarketListingService`). 엔티티를 거치지 않는 직접 UPDATE는 반응 수 재계산 쿼리뿐이고 내용과 무관하다.
- 1단계 API는 이 값을 `sourceVersion`으로 보여 주기만 한다. 오래된 갱신을 버리는 비교는 2단계에서 쓴다.

### search_document

| 컬럼 | 타입 | 설명 |
|---|---|---|
| document_id | BIGINT PK AUTO_INCREMENT | 정렬 보조 기준 |
| evidence_id | VARCHAR(80) UNIQUE | `P:{post_id}:{chunk_no}`, `C:{comment_id}:0` |
| source_type | VARCHAR(20), CHECK POST/COMMENT | |
| source_id | BIGINT | post_id 또는 comment_id |
| chunk_no | INT | 게시글 0 = 제목, 1부터 본문. 댓글은 0 |
| post_id | BIGINT | 댓글도 부모 게시글 ID를 가진다 |
| post_public_id | VARCHAR(36) | URL용 |
| parent_comment_id | BIGINT NULL | 답글의 부모 댓글 |
| category_code | VARCHAR(50) | 색인 시점 값. 검색 필터는 현재 카테고리로 한다 |
| source_version | BIGINT | `post.content_version` 또는 `comment.version` |
| chunk_hash | CHAR(64) | 정규화한 구간 텍스트의 SHA-256 hex |
| title | VARCHAR(200) NULL | 게시글 0번 구간에만 제목을 둔다 |
| body | TEXT | 본문 구간·댓글. 0번 구간은 빈 문자열 |
| source_created_at, source_updated_at | DATETIME(6) | 원문 작성·수정 시각 |
| indexed_at | DATETIME(6) | 재구축 시각 |

인덱스: `uk_search_document_evidence(evidence_id)`, `idx_search_document_post(post_id)`, `idx_search_document_source(source_type, source_id)`, `ftx_search_document_title_body(title, body) WITH PARSER ngram`.

- 제목을 0번 구간에만 두는 이유: 모든 본문 구간에 제목을 넣으면 제목 단어가 구간 수만큼 중복 집계된다. 제목만 맞는 글은 0번 구간으로 찾힌다.
- FK를 두지 않는다. 이 테이블은 원문에서 다시 만들 수 있는 복제본이고, 조회할 때마다 원문 테이블과 조인해 공개 범위를 다시 거른다. 원문이 하드 삭제되면 조인에서 빠진다. 2단계에서 tombstone을 남길 때 FK가 걸림돌이 되는 점도 고려했다.
- 불용어: ngram 파서는 InnoDB 기본 불용어(`a`, `i` 등)가 들어간 토큰을 버린다. 로컬 MySQL 8.0.46에서 기본 설정으로 만든 인덱스는 `wax`를 찾지 못했다. 027은 인덱스를 만드는 동안만 `SET SESSION innodb_ft_enable_stopword = OFF`를 적용하고 이전 값으로 돌린다. 이 설정은 인덱스 생성 시점에 인덱스에 묶인다. 서버 전역 설정(RDS 파라미터 그룹)은 바꾸지 않는다.
- `ngram_token_size`는 서버 기본값 2를 그대로 쓴다(로컬 확인 값 2). 027은 다른 변경을 적용하기 전에 `@@GLOBAL.ngram_token_size = 2`를 검사하고, 다르면 `SIGNAL`로 멈춘다(CodeRabbit 지적 반영). 로컬에서 조건을 바꿔 실패시켰을 때 컬럼·테이블이 하나도 만들어지지 않는 것을 확인했다.

### 마이그레이션 적용

- 컬럼 추가는 024와 같은 `information_schema` 확인 프로시저로, 테이블은 `CREATE TABLE IF NOT EXISTS`로 두 번 실행해도 안전하다.
- 운영은 `ddl-auto: validate`다. 새 앱은 `content_version`과 `search_document`가 없으면 기동하지 못하므로 027이 앱 배포보다 먼저 적용돼야 한다. 기존 배포 파이프라인의 마이그레이션 선행 단계를 그대로 쓴다.
- 이전 앱 이미지는 새 컬럼·테이블을 읽지 않으므로 027 적용 후에도 그대로 동작한다(추가만 하는 변경).
- 운영 post 행 수는 2026-11-03 기준 86개라 `ALTER TABLE ... ADD COLUMN` 비용은 작다. MySQL 8.0.12 이상에서는 맨 뒤 컬럼 추가가 INSTANT 방식으로 처리될 수 있다(운영 버전에서 확인하지 않음).

## 3. 구간 나누기와 정규화

구현: `SearchTextChunker`.

- 게시글 본문은 Toast UI Editor의 `getMarkdown()` 결과(Markdown, 일부 인라인 HTML)로 저장된다(`frontend/app/posts/create/page.tsx`).
- 게시글 정규화 순서
  1. 유니코드 NFC, 줄바꿈을 `\n`으로 통일
  2. Markdown 이미지 `![alt](url)` → alt, 링크 `[text](url)` → text
  3. `<br>` → 줄바꿈, 블록 태그(p, div, li, h1~h6 등) → 문단 구분, 나머지 태그 제거
  4. 줄 앞 제목·인용 표시(`#`, `>`), 강조 표시(`**`, `__`, `~~`, `` ` ``) 제거
  5. HTML 엔티티(`&nbsp;`, `&lt;` 등) 해제. 태그 제거 뒤에 해제해야 본문에 글자로 쓴 `&lt;b&gt;`가 태그로 오인돼 사라지지 않는다
  6. 빈 줄 기준으로 문단을 나누고 문단 안의 공백·줄바꿈을 공백 하나로 합친다
- 게시글 구간
  - 0번: 정규화한 제목
  - 1번부터: 문단을 순서대로 `\n\n`으로 이어 붙여 800자 이하로 묶는다. 다음 문단을 붙이면 800자를 넘는 경계에서 새 구간을 시작한다.
  - 한 문단이 800자를 넘으면 그 문단만 따로 800자 단위로 자른다. 뒤쪽 절반(400자 이후)에 공백이 있으면 그 공백에서 자르고, 없으면 그대로 자른다. 서로게이트 쌍(이모지 등)은 반으로 나누지 않는다.
  - 길이는 Java `String.length()` 기준(UTF-16 단위)이다. 한글은 1글자 = 1단위다.
- 댓글: 일반 텍스트로 저장·표시되므로 Markdown·태그 제거를 하지 않는다. NFC, 줄바꿈 통일, 문단 공백 정리만 하고 1건 = 0번 구간 하나로 둔다. 정규화 결과가 비면 색인하지 않는다.
- 해시: 정규화한 구간 텍스트의 UTF-8 바이트에 SHA-256, 소문자 hex 64자. 같은 원문이면 줄바꿈 방식(CRLF/LF), 연속 공백, NFC/NFD 차이와 관계없이 같은 해시가 나온다.
- 정규화 규칙을 바꾸면 모든 해시가 바뀐다. 바꿀 때는 재구축을 함께 하고, 근거 조회의 `stale`이 일시적으로 true가 될 수 있음을 알린다.

## 4. 공개 범위 (권한 필터)

게시판 목록(`PostRepositoryCustomImpl`)과 같은 기준을 `SearchVisibilitySql` 한 곳에 두고 세 곳에서 쓴다.

- 게시글: `status = 'NORMAL' AND is_deleted = false AND post_category.code <> 'MARKET'`
- 댓글: `comment.is_deleted = false` + 부모 게시글이 위 조건을 만족

| 지점 | 적용 방식 |
|---|---|
| 재구축 | 위 조건을 만족하는 원문만 읽어 색인한다. 숨김·삭제·MARKET 글과 그 댓글, 삭제 댓글은 들어가지 않는다 |
| 검색 (후보 생성) | `search_document`를 `post`·`post_category`·`comment`와 조인하고 같은 조건을 WHERE에 둔다. 색인 뒤에 숨김·삭제된 원문은 재구축 없이도 결과에서 빠진다 |
| 근거 조회 | 원문을 다시 읽으며 같은 조건을 검사한다. 볼 수 없으면 404 |

- `evidence_id`는 위치만 가리키며 권한을 대신하지 않는다(D4).
- 카테고리 필터는 색인 시점 `category_code`가 아니라 현재 `post_category.code`로 건다. 글이 다른 게시판으로 옮겨진 뒤에도 현재 게시판 기준으로 거른다.
- 익명 게시판 글도 공개 글이므로 색인한다. 응답에는 작성자 정보가 없다.

## 5. API 계약

모두 `/api/v1/admin/**` 아래에 있어 `SecurityConfig`에서 ROLE_ADMIN만 허용한다(D7 1단계). 비로그인은 401, 일반 회원은 403(기존 인증 진입점·접근 거부 처리기 응답). 라이브톡 위임 토큰 필터는 관리자 경로를 거부한다. 세션 인증이므로 POST에는 기존 CSRF 토큰이 필요하다.

### POST /api/v1/admin/search/rebuild

전체 재구축. 200 응답:

```json
{
  "postCount": 0,
  "postChunkCount": 0,
  "commentCount": 0,
  "documentCount": 0,
  "removedCount": 0,
  "indexedAt": "2026-11-04T10:00:00.123456"
}
```

- `documentCount = postChunkCount + commentCount`, `removedCount`는 재구축 전에 지운 행 수.
- 한 트랜잭션에서 `DELETE FROM search_document` 후 배치 INSERT(200건 단위)한다. FULLTEXT는 커밋된 행만 보므로 재구축 중에도 검색은 이전 색인을 본다.
- 원문 전체를 한 번에 메모리로 읽는다. 현재 규모(전체 게시글 86개, 2026-11-03 운영 조회)에서는 문제가 없지만 게시글이 크게 늘면 2단계 증분 갱신·세대 전환 재인덱싱으로 바꿔야 한다.
- 동시 재구축: 색인에 행이 있으면 뒤 요청이 DELETE 행 잠금에서 기다려 순서대로 실행된다. 색인이 빈 상태에서 두 요청이 겹치면 InnoDB가 한쪽을 교착 상태로 되돌릴 수 있다(500). 관리자 수동 실행이라 별도 잠금을 두지 않았다.

### GET /api/v1/admin/search/evidence

| 파라미터 | 규칙 |
|---|---|
| q | 필수. 앞뒤 공백 제거 후 2~100자(코드 포인트 기준) |
| size | 선택. 1~20, 기본 10 |
| categoryCode | 선택. 대문자로 맞춘 뒤 `^[A-Z][A-Z0-9_]{0,49}$` |

- 규칙 위반, 숫자가 아닌 size, q 누락은 400 `SEARCH_001`. 파라미터를 문자열로 받아 직접 검증한다. Spring 기본 변환에 맡기면 타입 오류가 전역 처리기에서 500이 되기 때문이다.
- 검색: `MATCH(title, body) AGAINST (? IN NATURAL LANGUAGE MODE)`. 정렬은 관련도 내림차순, 같으면 `document_id` 오름차순. 검색어는 바인딩 파라미터로만 전달한다.
- 응답:

```json
{
  "query": "바인딩 각도",
  "size": 10,
  "items": [
    {
      "evidenceId": "P:12:1",
      "sourceType": "POST",
      "postPublicId": "uuid",
      "commentId": null,
      "parentCommentId": null,
      "chunkNo": 1,
      "sourceVersion": 0,
      "chunkHash": "64자 hex",
      "url": "/posts/uuid",
      "title": "현재 게시글 제목",
      "excerpt": "색인된 구간 앞부분 최대 200자",
      "excerptTruncated": false,
      "sourceCreatedAt": "...",
      "sourceUpdatedAt": "...",
      "indexedAt": "..."
    }
  ]
}
```

- 검색 점수는 반환하지 않는다. 순위에만 쓴다(D4).
- 게시글 내부 ID, `source_id`, 작성자·회원 정보는 반환하지 않는다. `commentId`, `parentCommentId`는 기존 댓글 API가 이미 공개하는 값이며, 기존 댓글 응답과 같이 문자열로 직렬화한다. 근거 ID 안의 `post_id`는 노출되지만 관리자 전용 API이고, 4단계에서 외부 공개 범위를 넓힐 때 다시 검토한다.
- `title`은 현재 게시글 제목(표시용), `excerpt`·`chunkHash`·`sourceVersion`은 색인 시점 값이다. 재구축 전까지 원문 수정이 반영되지 않는다. 최신 여부는 근거 조회로 확인한다.
- `excerpt`는 구간 앞부분이다. 검색어 주변을 잘라 보여 주지는 않는다.
- `url`: 카풀(CARPOOL)은 `/carpool/{publicId}`, 나머지는 `/posts/{publicId}`. 게시글 상세 화면(`frontend/app/posts/[publicId]/page.tsx`)에 댓글 앵커가 없어 댓글 근거도 게시글 주소를 쓴다.

### GET /api/v1/admin/search/evidence/{evidenceId}?expectedHash=

- `evidenceId` 형식: `^([PC]):([1-9][0-9]{0,18}):(0|[1-9][0-9]{0,3})$`, 댓글은 구간 번호 0만 허용. 위반·long 범위 초과는 400 `SEARCH_001`.
- `expectedHash`: 선택. 64자 hex(대소문자 무관). 형식 위반은 400 `SEARCH_001`.
- 처리 순서
  1. 원문을 DB에서 다시 읽고 공개 범위를 검사한다. 원문이 없거나 볼 수 없거나 해당 구간 번호가 없으면 404 `EVIDENCE_001`.
  2. 현재 원문으로 구간을 다시 나눠 해시를 계산한다.
  3. `expectedHash`가 있고 현재 해시와 다르면 409 `EVIDENCE_002`.
  4. 색인 행을 `evidence_id`로 찾아 `stale`을 계산한다.
- 공개 범위 검사를 해시 비교보다 먼저 한다. 볼 수 없는 원문에 대해 해시 일치 여부를 알려주지 않기 위해서다.
- 응답 필드: `evidenceId`, `sourceType`, `postPublicId`, `commentId`, `parentCommentId`, `chunkNo`, `sourceVersion`(현재 원문 버전), `chunkHash`(현재), `indexedChunkHash`(색인 값, 없으면 null), `stale`, `url`, `title`, `text`(현재 구간, 최대 1,000자), `textTruncated`, `sourceCreatedAt`, `sourceUpdatedAt`, `indexedAt`(색인에 없으면 null).
- `stale = true`: 색인 해시와 현재 해시가 다르거나, 원문 구간은 있는데 색인에 행이 없는 경우.

### 오류 코드

| 코드 | HTTP | 의미 |
|---|---|---|
| SEARCH_001 | 400 | 검색어·결과 수·카테고리·근거 ID·해시 형식 오류 |
| EVIDENCE_001 | 404 | 근거 원문이 없거나 볼 수 없음 |
| EVIDENCE_002 | 409 | expectedHash와 현재 원문 해시가 다름 |

## 6. 구현 선택

- 쓰기와 검색은 JDBC(`SearchDocumentJdbcRepository`, `SearchSourceRepository`)로 한다. `MATCH ... AGAINST`는 JPQL·QueryDSL로 표현할 수 없다. 원문 읽기도 엔티티로 하면 게시글 이미지·작성자 연관과 LONGTEXT가 영속성 컨텍스트에 올라가므로 필요한 컬럼만 JDBC로 읽는다. 기존 `MarketThumbnailService`, `SharedRequestLimiter`도 JdbcTemplate을 쓴다.
- `SearchDocument` 엔티티는 둔다. 테스트 스키마(create-drop)가 엔티티에서 만들어지고, 운영 `validate`가 027과 엔티티가 어긋나면 기동을 막아 준다. `evidence_id` 단건 조회에도 쓴다.
- FULLTEXT 인덱스는 JPA로 표현할 수 없어 테스트에서는 `SearchDocumentFullTextIndex.ensure()`가 027과 같은 인덱스를 같은 불용어 설정으로 만든다.
- 다른 선택지: 재구축을 JPA `saveAll`로 하면 IDENTITY 키 때문에 행마다 INSERT가 나간다. JDBC 배치가 더 단순하다.

## 7. 검증

- 단위: 구간 경계(800자 정확히·초과), 긴 문단 강제 분할·공백 분할, 서로게이트 쌍, Markdown·HTML 정규화, 해시 안정성(CRLF·공백·NFD), 댓글 1구간, 입력 검증, `content_version` 증가 조건.
- 통합(MockMvc, 실제 MySQL 8.0, 테스트 트랜잭션 없이 커밋 후 정리): 비로그인 401·일반 회원 403, 재구축 대상 제외(숨김·삭제·MARKET 글, 삭제 댓글, 숨김 글의 댓글), 한국어 검색어 "바인딩 각도" ngram 일치, 점수·내부 ID 미반환, 색인 뒤 숨김·댓글 삭제의 조회 시 제외, 삭제 후 근거 조회 404, 재구축 없이 수정한 뒤 `stale = true`와 이전 해시 409, 재구축 후 `stale = false`, DB의 `content_version` 증가.
- 마이그레이션: 001 + 002 + 전체 마이그레이션을 빈 스키마에 적용하고 다시 실행(CI 스크립트 `verify-production-migration-007-015.sh`에 027 검사 추가), 027 파일 자체를 두 번 더 직접 실행, 마이그레이션 결과 스키마에 대해 `ddl-auto: validate`로 앱 컨텍스트 기동.
- 수치(검색 품질·지연)는 측정하지 않았다. 기준선 측정은 IKA-9에서 한다.

## 8. 하지 않은 것과 남은 위험

- 증분 갱신 없음: 원문 수정·삭제는 재구축 전까지 색인에 반영되지 않는다. 숨김·삭제는 조회 시 조인으로 걸러지지만, 수정된 내용은 검색되지 않고 이전 내용으로 검색될 수 있다. 근거 조회의 `stale`·`expectedHash`로 확인한다(2단계 IKA-10).
- 측정 없음: Recall@k, 지연, 색인 크기는 IKA-9.
- ngram 오탐: 2글자 토큰 단위라 짧은 단어 오탐이 생긴다(D3 부작용). 측정 대상이다.
- 운영 적용 안 함: 027 운영 적용과 배포는 사용자 확인 후 진행한다.
