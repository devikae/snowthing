# 어드민 게시판 관리 (IKA-114, 2026-11-24)

## 왜

- 게시판 하나를 더하려면 마이그레이션과 프론트 7곳(홈 탭, 게시판 메뉴, 글 목록, 글쓰기, 게시판 설명, 사이트맵, 짧은 이름)을 고쳐 배포해야 했다(IKA-111 정보게시판).
- 글쓰기 규칙(공지는 관리자만, 익명게시판은 항상 익명, 동호회와 시즌방과 정보는 익명 금지)이 `PostService` 상수에 박혀 있어 게시판 성격을 바꾸려면 코드를 고쳐야 했다.
- 관리자가 어드민 화면에서 게시판을 추가, 수정, 열고 닫기, 삭제, 복구하고 순서와 홈 탭 노출을 정하게 한다. 배포 없이 1분 안에 사이트에 반영된다.

## 범위

- 포함: `post_category` 칸 추가와 기존 규칙 옮겨 적기, 공개 게시판 목록 API, 어드민 게시판 API, 글쓰기 검사를 DB 규칙으로 바꾸기, 프론트 하드코딩 목록을 API로 바꾸기, 어드민 화면 `/admin/boards`.
- 제외: 게시판 영구 삭제, 게시판별 운영자, AI 멤버 활동 게시판(자유, 익명, 질문으로 고정).
- 관리 대상 아님: 중고장터(MARKET), 카풀(CARPOOL), 1:1 문의(INQUIRY). 전용 화면과 전용 규칙이 있어 어드민 목록에 나오지 않고 수정, 닫기, 삭제를 막는다.

## 사용자 결정 (2026-11-23)

1. 닫기는 메뉴에서만 숨긴다. 메뉴, 홈 탭, 글쓰기 선택지에서 빠지고 새 글을 막는다. 기존 글은 주소와 검색으로 계속 보인다.
2. 글쓰기 규칙을 표로 옮기고 어드민에서 고른다.
3. 순서 번호 하나를 메뉴와 홈 탭이 같이 쓰고, 홈 탭 노출은 게시판마다 켜고 끈다.
4. 삭제는 소프트 삭제다. 사이트에서는 닫기와 같게 보이고, 어드민 기본 목록에서 빠져 "삭제한 게시판" 칸에서 복구한다. 닫기와 삭제는 따로 둔다.
5. 어드민 화면은 시안(목록 표, 순서 위아래 버튼, 추가 폼, 삭제한 게시판 접기 칸)대로 간다.

## 테이블 (064 마이그레이션)

`post_category`에 칸을 더한다.

| 칸 | 형식 | 설명 |
|---|---|---|
| short_name | VARCHAR(10) NOT NULL | 홈 탭과 글 목록 분류 표시 이름 (공지, 자유) |
| description | VARCHAR(200) NULL | 게시판 위 설명과 검색 결과 설명 |
| sort_order | INT NOT NULL | 메뉴와 홈 탭 순서, 작을수록 앞. 같은 값이면 ID 순 |
| status | VARCHAR(10) NOT NULL | OPEN, HIDDEN |
| show_on_home | BOOLEAN NOT NULL | 홈 탭에 보이기 |
| write_permission | VARCHAR(10) NOT NULL | ALL(비회원 포함), MEMBER, ADMIN |
| anonymous_mode | VARCHAR(10) NOT NULL | FORCED(항상 익명), OPTIONAL(선택), FORBIDDEN(금지) |
| deleted_at | DATETIME(6) NULL | 소프트 삭제 시각 |

- 열거값은 다른 표와 같이 VARCHAR로 둔다. ENUM은 값을 더할 때마다 ALTER가 필요하다.
- 행이 20줄 안쪽이라 정렬용 인덱스는 두지 않는다. code는 이미 UNIQUE다.

### 기존 게시판 옮겨 적기

| 코드 | 짧은 이름 | 순서 | 쓰기 | 익명 | 홈 탭 |
|---|---|---|---|---|---|
| NOTICE | 공지 | 10 | ADMIN | FORBIDDEN | 켬 |
| FREE | 자유 | 20 | MEMBER | OPTIONAL | 켬 |
| ANONYMOUS | 익명 | 30 | ALL | FORCED | 켬 |
| QNA | 질문 | 40 | MEMBER | OPTIONAL | 켬 |
| INFO | 정보 | 50 | MEMBER | FORBIDDEN | 켬 |
| FOOD | 맛집 | 60 | MEMBER | OPTIONAL | 켬 |
| CREW | 동호회 | 70 | MEMBER | FORBIDDEN | 켬 |
| SEASON_ROOM | 시즌방 | 80 | MEMBER | FORBIDDEN | 켬 |
| MARKET, CARPOOL, INQUIRY | 기존 이름 | 900번대 | 지금 코드 규칙 그대로 | | 끔 |
| 그 밖의 옛 줄 (GEAR_VS 등) | 이름 | 990 | MEMBER | OPTIONAL | 끔, HIDDEN |

설명은 지금 `posts/page.tsx`의 게시판 설명 문구를 그대로 옮긴다.

## 규칙

- 비회원 글은 익명 비밀번호로 관리하므로 쓰기가 ALL이면 익명은 FORCED만 고를 수 있다.
- 쓰기 ADMIN 게시판은 관리자만 쓰고 고친다(지금 공지와 같다).
- 닫힌(HIDDEN) 게시판과 삭제한 게시판에는 새 글을 쓰거나 다른 글을 옮겨 올 수 없다. 이미 있는 글은 고치고 지울 수 있다.
- 코드는 영문 대문자, 숫자, 밑줄 2~20자이고 영문으로 시작한다. 추가할 때만 정하고 바꿀 수 없다. 주소(`/posts?category=CODE`)와 검색 유입이 코드에 묶여 있다.
- 삭제한 게시판의 코드는 다시 쓸 수 없다(UNIQUE). 예전 글이 그 코드를 가리킨다.
- 예약 코드: MARKET, CARPOOL, INQUIRY, BEST(목록 보기 종류 이름과 겹침).
- 순서 바꾸기는 바로 위나 아래의 관리 대상 게시판(삭제 제외)과 sort_order를 맞바꾼다. 새 게시판은 맨 뒤(최댓값 + 10)에 붙는다.

## API

### 공개

`GET /api/v1/public/boards`: 열린 게시판을 sort_order, ID 순으로 준다. 관리 대상 아닌 셋은 빠진다.

```json
[{ "code": "FREE", "name": "자유게시판", "shortName": "자유", "description": "...", "showOnHome": true, "writePermission": "MEMBER", "anonymousMode": "OPTIONAL" }]
```

백엔드 캐시는 두지 않는다. 20줄 조회라 가볍고, 프론트가 60초 캐시를 둔다.

### 어드민 (`/api/v1/admin/**`, 관리자만)

| 메서드 | 경로 | 하는 일 |
|---|---|---|
| GET | /api/v1/admin/boards | 관리 대상 게시판 전부(닫힘, 삭제 포함) |
| POST | /api/v1/admin/boards | 추가 |
| PATCH | /api/v1/admin/boards/{code} | 이름, 짧은 이름, 설명, 규칙, 홈 탭 수정 |
| PATCH | /api/v1/admin/boards/{code}/status | 열기, 닫기 |
| POST | /api/v1/admin/boards/{code}/move | 위나 아래로 한 칸 |
| DELETE | /api/v1/admin/boards/{code} | 소프트 삭제 |
| POST | /api/v1/admin/boards/{code}/restore | 복구. 닫힌 상태로 돌아온다 |

바꿀 때마다 `admin_action_log`에 BOARD_CREATE, BOARD_UPDATE, BOARD_STATUS, BOARD_MOVE, BOARD_DELETE, BOARD_RESTORE로 남긴다(대상 BOARD, 대상 ID는 코드).

## 프론트

- 루트 레이아웃(서버)이 공개 API를 60초 캐시로 받아 게시판 목록 컨텍스트로 내려준다. 메뉴, 홈 탭, 글 목록, 글쓰기가 이 목록을 쓴다. 게시판 목록 페이지 제목과 설명, 사이트맵도 같은 API를 쓴다.
- API가 실패하면 지금 하드코딩 목록을 기본값으로 쓴다. 새로 추가한 게시판만 잠깐 빠진다.
- 닫힌 게시판 주소는 글 목록이 열리고 글쓰기 버튼이 없다. 사이트맵에서 빠진다.
- 모바일 홈 탭 격자 칸 수는 탭 개수(전체글 포함)를 2줄에 넣는 수로 정하고 5칸을 넘지 않는다.
- 어드민 화면 `/admin/boards`: "사이트" 메뉴 묶음. 목록 표(순서 위아래, 이름, 홈 탭 이름, 코드, 쓰기, 익명, 홈 탭, 상태, 수정과 닫기와 삭제), 추가 폼, 삭제한 게시판 접기 칸. 수정은 그 줄이 입력칸으로 바뀌고 코드만 잠긴다.

## 바뀌는 점과 대가

- 어드민 변경은 최대 1분 뒤에 보인다. 바로 반영하려면 프론트 캐시 무효화 경로와 인증이 필요해 넣지 않았다.
- 메뉴가 백엔드 응답에 기대지만 실패하면 기본 목록으로 그린다.
- 루트 레이아웃이 데이터를 받으므로 정적 페이지도 60초마다 다시 만들어진다.
- 글쓰기 검사가 코드 상수 대신 DB 값을 본다. 기존 게시판 동작은 옮겨 적은 값으로 그대로 유지하고 기존 테스트로 확인한다.

## 배포 순서

064 마이그레이션 적용 후 백엔드, 프론트 배포. 새 칸은 기본값이 있어 이전 앱과 호환된다. IKA-111(063)이 먼저 머지되어야 한다.
