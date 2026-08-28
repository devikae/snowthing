# 📚 [Master Study Guide] Sprint 02 게시글(Post) 도메인 5대 아키텍처 결함 & 물리적 극복 방안 가이드 (2026-08-21)

> **노션(Notion) 복사용 및 백엔드 게시글 도메인 심화 학습용 마스터 가이드**  
> 본 문서는 Snowthing 게시글(Post) 도메인(댓글 제외) 구축 시 발생할 수 있는 **5대 핵심 아키텍처 문제점과 결함**에 대해, **7대 필수 서술 요소 체계(개념, Why, When, How 코드/SQL, Pros & Cons, 기존 한계점, 서비스/아키텍처 레벨 극복 방안)**를 물리적 메커니즘 수준으로 전수 파헤쳐 정리한 마스터 학습 문서입니다.

---

# 📑 PART 1. 게시글(Post) 도메인 5대 결함 & 7대 필수 요소 심층 분석

---

## 1. 💥 `POST` 페이징 목록 조회 시 본문 제외 미적용으로 인한 네트워크 트래픽 폭증 및 DB I/O 병목

### ① 개념 (What - 문제의 명확한 정의)
게시글 목록 API(`GET /api/posts?page=0&size=10`)를 부를 때, 목록 화면에는 제목, 작성자, 카테고리, 추천 수만 필요한데 **게시글 본문(`content` VARCHAR 5000/TEXT) 필드까지 전부 DB에서 SELECT하여 DTO로 내보내는 문제**입니다.

### ② 발생 원인 (Why - 물리적 & DB 메커니즘)
- JPA Repository에서 목록 조회 시 `Post` 엔티티 전체를 SELECT 하거나 `PostListResponse` DTO 생성 시 본문(`content`)을 포함하는 DTO projections 미분리로 인해 발생합니다.
- 본문 내에 수천 자의 장문이 포함되어 있으면 목록 쿼리 1번당 전송 데이터 크기(Payload Size)가 **수십 KB ➔ 수 MB로 폭증**합니다.

### ③ 언제 발생하는지 (When - 적합한 발생 상황)
- 사용자가 모바일 또는 3G/4G 환경에서 게시글 목록을 스크롤(무한 스크롤 / 페이징)할 때 로딩 지연 및 모바일 데이터 소모 폭증.

### ④ 어떻게 발생하는지 (How - 실제 코드 & DB 쿼리 실행 메커니즘)
```sql
-- 목록 10개 조회 쿼리 실행 시 (불필요한 content 컬럼 포함!)
SELECT post_id, public_id, title, content, view_count, like_count, created_at FROM post WHERE category_id = 1;
-- 10개 글 본문(content) 합계 500KB 데이터가 매 페이징마다 DB -> API 서버 -> 클라이언트로 낭비 전송됨
```

### ⑤ 부정적 영향 (Pros & Cons of Ignoring - 미해결 시 여파)
- **DB 메모리/네트워크 낭비**: DB Buffer Pool 메모리 낭비 및 네트워크 대역폭(Bandwidth) 고갈.
- **클라이언트 로딩 지연**: 목록 화면을 열 뿐인데 유저 휴대폰 메모리와 데이터 소모가 급증함.

### ⑥ 기존 처리 방식과의 비교 및 한계점 (Alternatives vs Existing)
- **기존 방식**: 엔티티 전체 조회 `SELECT p FROM Post p`
- **한계점**: LOB/TEXT 컬럼의 지연 로딩이 기본 적용되지 않아 불필요한 IO가 매번 발생함.

### ⑦ 트레이드오프 및 서비스/아키텍처 레벨 극복 방안 (Trade-off & Detailed Mitigation)
- **트레이드오프**: 목록 전용 DTO (`PostListResponse`)를 별도로 정의해야 하는 DTO 파편화 오버헤드.
- **서비스 레벨 극복 방안 (목록 DTO 경량화)**: 목록 DTO에 본문을 아예 제외(`content` 제거)하고 제목은 최대 40자 자름(Truncate) 처리하여 UI 렌더링 속도 최적화.
- **아키텍처 레벨 극복 방안 (JPQL/Querydsl DTO Projections)**:
  - `SELECT new PostListResponse(p.publicId, p.title, p.likeCount...) FROM Post p` 방식을 적용하여 DB 레벨에서 `content` 컬럼 자체를 SELECT 하지 않도록 DB I/O를 원자적 차단.

---

## 2. 💥 카테고리별 게시글 목록 페이징 조회의 Count Query N+1 및 Index Scan 타임아웃

### ① 개념 (What - 문제의 명확한 정의)
게시글 목록 페이징(`Page<PostListResponse>`) 조회 시, Spring Data JPA의 `Pageable`을 사용할 때 **전체 게시글 수(`COUNT(*)`)를 세는 카운트 쿼리가 매 페이징 요청마다 DB 테이블 전체를 스캔**하여 일어나는 성능 저하 현상입니다.

### ② 발생 원인 (Why - 물리적 & DB 메커니즘)
- JPA `PageRequest` 사용 시 Hibernate는 데이터 10건 조회 쿼리 1번 + 전체 개수 계산 `SELECT COUNT(p) FROM Post p WHERE p.category = :category` 쿼리 1번을 내보냅니다.
- `post` 테이블에 `category_id + created_at` 복합 인덱스가 없으면, 카운트 쿼리가 **테이블 풀 스캔(Full Table Scan)**을 일으킵니다.

### ③ 언제 발생하는지 (When - 적합한 발생 상황)
- 게시글 데이터가 10만 건 이상 쌓인 상태에서 10페이지, 100페이지 등 높은 페이지 번호(Offset Paging)를 넘길 때.

### ④ 어떻게 발생하는지 (How - 실제 코드 & DB 쿼리 실행 메커니즘)
```sql
-- 1. 데이터 10건 조회 (Fast)
SELECT * FROM post WHERE category_code = 'FREE' ORDER BY created_at DESC LIMIT 10 OFFSET 1000;
-- 2. 전체 Count 쿼리 (Slow - 10만 건 Full Scan!)
SELECT COUNT(*) FROM post WHERE category_code = 'FREE'; -- 2초 소요!
```

### ⑤ 부정적 영향 (Pros & Cons of Ignoring - 미해결 시 여파)
- **DB CPU 100% 점유**: 100명의 유저가 탭을 전환하면 `COUNT(*)` 쿼리 100개가 DB CPU를 100% 점유하여 전체 서비스 마비.

### ⑥ 기존 처리 방식과의 비교 및 한계점 (Alternatives vs Existing)
- **기존 방식**: `Page<PostListResponse>` 기본 페이징 반환.
- **한계점**: 무조건 `COUNT(*)`를 실행하므로 데이터가 쌓일수록 성능이 선형적으로 저하됨.

### ⑦ 트레이드오프 및 서비스/아키텍처 레벨 극복 방안 (Trade-off & Detailed Mitigation)
- **트레이드오프**: 전체 페이지 번호(1, 2, 3... 10)를 보여주는 UI 대신 `더보기` 버튼(Slice 페이징)으로 전환해야 함.
- **서비스 레벨 극복 방안 (Slice 무한 스크롤 UI)**: 모바일/웹 목록 UI를 페이지 번호 방식에서 `Slice` 기반 [더보기 / 무한 스크롤] UI로 전환.
- **아키텍처 레벨 극복 방안 (Slice 페이징 & Covering Index)**:
  - `Page<T>` 대신 `Slice<T>`를 사용하여 `COUNT(*)` 쿼리 자체를 100% 제거(`limit + 1` 조회 방식).
  - DB에 `idx_category_created_at(category_id, created_at DESC)` 커버링 인덱스를 생성하여 Index Only Scan 유도.

---

## 3. 💥 게시글 수정/삭제 시 작성자 검증 인가(Authorization) 누락 및 IDOR 취약점

### ① 개념 (What - 문제의 명확한 정의)
회원이 작성한 일반 게시글을 수정/삭제할 때, 로그인된 유저가 **해당 게시글의 실제 작성자 본인인지 또는 관리자(`ROLE_ADMIN`)인지 검증하지 않고** `publicId`만 알면 타인의 글을 임의로 수정/삭제할 수 있는 보안 취약점입니다.

### ② 발생 원인 (Why - 물리적 & DB 메커니즘)
- [`PostService.java`](file:///c:/Users/ikaes/IdeaProjects/snowthing/backend/src/main/java/com/ikae/snowthing/domain/post/service/PostService.java) `updatePost()` / `deletePost()`에서 `post.getMember().getPublicId().equals(userDetails.getPublicId())` 대조 로직이 누락되거나 null 검증 조건이 뚫릴 때 발생합니다.

### ③ 언제 발생하는지 (When - 적합한 발생 상황)
- 인증된 유저 A가 Postman이나 브라우저 개발자 도구(F12)에서 유저 B가 쓴 게시글의 `publicId`를 파라미터로 넣어 `PUT /api/posts/{publicId}`를 호출할 때.

### ④ 어떻게 발생하는지 (How - 실제 코드 & DB 쿼리 실행 메커니즘)
```
[User A (Hacker)] PUT /api/posts/p9999 (User B's Post)
 └── PostService.updatePost() 진입
      └── 작성자 대조 검증 없이 post.updateTitleAndContent() 실행!
           └── User B의 글이 User A에 의해 강제 변조됨! (IDOR 보안 참사)
```

### ⑤ 부정적 영향 (Pros & Cons of Ignoring - 미해결 시 여파)
- **데이터 변조 & 악성 스팸**: 타인의 글을 삭제하거나 비하/광고성 내용으로 강제 변경하는 심각한 보안 사고 발생.

### ⑥ 기존 처리 방식과의 비교 및 한계점 (Alternatives vs Existing)
- **기존 방식**: 어노테이션 `@PreAuthorize("isAuthenticated()")` 만 사용.
- **한계점**: "로그인 여부"만 검증할 뿐 "글 작성자 본인 여부"를 검증하지 못함.

### ⑦ 트레이드오프 및 서비스/아키텍처 레벨 극복 방안 (Trade-off & Detailed Mitigation)
- **트레이드오프**: 매 수정/삭제 시마다 DB에서 작성자 ID를 대조해야 하는 인가 연산 오버헤드.
- **서비스 레벨 극복 방안 (버튼 숨김 렌더링)**: 프론트엔드 상세 페이지에서 작성자 본인 및 관리자가 아닌 경우 [수정], [삭제] 버튼 자체를 렌더링하지 않음.
- **아키텍처 레벨 극복 방안 (백엔드 도메인 인가 검증)**:
  - `PostService` 내에 `validatePostOwnerOrAdmin(post, userDetails)` 도메인 검증 메서드를 공통화하고, 불일치 시 `403 Forbidden (ErrorCode.ACCESS_DENIED)` 예외를 즉시 던져 백엔드 단에서 물리 차단.

---

## 4. 💥 회원글 ➔ 익명글 (또는 그 반대) 카테고리 변경 시 작성자 정보 정합성 오염 및 비밀번호 유실 문제

### ① 개념 (What - 문제의 명확한 정의)
게시글 수정(`PUT /api/posts/{publicId}`) 시 유저가 카테고리를 일반 카테고리(`FREE`)에서 익명 카테고리(`ANONYMOUS`)로 변경하거나 그 반대로 변경할 때, **`is_anonymous` 플래그와 `member_id`, `anonymous_password` 데이터 간의 상태 꼬임(State Corruption) 현상**입니다.

### ② 발생 원인 (Why - 물리적 & DB 메커니즘)
- 게시글 작성 시에는 `isAnonymous`에 따라 `member`가 저장되거나 `anonymousPassword`가 저장됩니다.
- 그러나 게시글 수정 시 카테고리 코드(`categoryCode`)를 바꾸면서 `isAnonymous` 상태 변경에 따른 기존 `member` 매핑 해제 처리나 `anonymousPassword` BCrypt 재암호화 처리가 캡슐화되어 있지 않으면 상태가 파괴됩니다.

### ③ 언제 발생하는지 (When - 적합한 발생 상황)
- 유저가 자유게시판(`FREE`)에 쓴 글을 나중에 익명게시판(`ANONYMOUS`)으로 수정 이동하거나, 익명글을 회원글로 수정 이동할 때.

### ④ 어떻게 발생하는지 (How - 실제 코드 & DB 쿼리 실행 메커니즘)
```
[회원글 -> 익명글 수정 시]
- is_anonymous = true 로 변경되었으나, member_id (FK) 가 여전히 연관되어 있어 DB 상에서 작성자 유저 정보가 그대로 노출됨!
[익명글 -> 회원글 수정 시]
- is_anonymous = false 로 변경되었으나, member_id 가 null 로 남아 작성자 없는 유령 글 발생!
```

### ⑤ 부정적 영향 (Pros & Cons of Ignoring - 미해결 시 여파)
- **익명성 파괴 보안 사고**: 익명글로 바꿨는데 DB에 작성자 회원의 `member_id`가 남아 익명성이 파괴되거나, 유령 글이 되어 삭제 불가능 상태 발생.

### ⑥ 기존 처리 방식과의 비교 및 한계점 (Alternatives vs Existing)
- **기존 방식**: DTO 필드를 엔티티에 덮어쓰는 `post.setTitle(...)`, `post.setCategory(...)`
- **한계점**: 엔티티 불변식(Invariant)을 지키지 못함.

### ⑦ 트레이드오프 및 서비스/아키텍처 레벨 극복 방안 (Trade-off & Detailed Mitigation)
- **트레이드오프**: 작성 후 카테고리 변경 시 익명/일반 간의 전환 제약이 필요함.
- **서비스 레벨 극복 방안 (카테고리 이동 정책 제한)**: 익명게시판(`ANONYMOUS`)과 일반게시판(`FREE`, `QNA`) 간의 카테고리 변경 작성을 서비스 정책상 금지하고 안내 문구 노출.
- **아키텍처 레벨 극복 방안 (도메인 카테고리 변경 검증)**:
  - `Post.java` 도메인 엔티티 내에 `changeCategory(PostCategory newCategory)` 메서드를 만들고, 익명 ↔ 일반 카테고리 간의 전환 시도가 들어오면 `400 Bad Request (ErrorCode.INVALID_INPUT, "익명게시판과 일반게시판 간 카테고리 변경은 불가능합니다.")` 예외를 던져 백엔드 차원에서 원자적 차단.

---

## 5. 💥 `PostReaction` 추천/비추천 투표 시 계정당 1회 독자 투표 업데이트의 Race Condition 및 DB Deadlock

### ① 개념 (What - 문제의 명확한 정의)
유저가 추천(LIKE)과 비추천(DISLIKE)을 빠르게 번갈아 누르거나 동시 클릭할 때, 복합 유니크 인덱스(`uk_post_member_type`)에도 불구하고 **DB 트랜잭션 교착 상태(Deadlock) 및 데이터 충돌**이 발생하는 동시성 문제입니다.

### ② 발생 원인 (Why - 물리적 & DB 메커니즘)
- 오늘 `PostReaction` DB 제약 조건을 `UNIQUE (post_id, member_id, type)`로 변경하여 유저 1명이 추천 1건 + 비추천 1건을 각각 가질 수 있게 만들었습니다.
- 유저가 추천 클릭과 비추천 클릭을 동시에 내보내면, MySQL InnoDB는 두 트랜잭션에서 `post_reaction` 유니크 인덱스 페이지 락(Index Page Lock)을 획득하는 과정에서 **Circular Dependency (순환 대기) Deadlock**을 유발할 수 있습니다.

### ③ 언제 발생하는지 (When - 적합한 발생 상황)
- 클라이언트 단에서 추천 버튼과 비추천 버튼을 동시에 클릭하거나, 2개의 브라우저 탭에서 동일 계정으로 추천/비추천을 연타할 때.

### ④ 어떻게 발생하는지 (How - 실제 코드 & DB 쿼리 실행 메커니즘)
```
[Tx 1 (추천)] INSERT INTO post_reaction (post_id=1, member_id=5, type='LIKE') -> Index Lock 획득 대기
[Tx 2 (비추천)] INSERT INTO post_reaction (post_id=1, member_id=5, type='DISLIKE') -> Index Lock 획득 대기
 -> MySQL InnoDB Deadlock Detector 발동 -> Deadlock found when trying to get lock; try restarting transaction (500 Server Error!)
```

### ⑤ 부정적 영향 (Pros & Cons of Ignoring - 미해결 시 여파)
- **500 Internal Server Error 발생**: DB 데드락 발생 시 사용자 화면에 500 에러 페이지가 뜸.

### ⑥ 기존 처리 방식과의 비교 및 한계점 (Alternatives vs Existing)
- **기존 방식**: `@UniqueConstraint` 선언만 적용.
- **한계점**: 동시 INSERT 시 발생하는 DB 인덱스 데드락을 100% 방지할 수 없음.

### ⑦ 트레이드오프 및 서비스/아키텍처 레벨 극복 방안 (Trade-off & Detailed Mitigation)
- **트레이드오프**: 버튼 연타 시 프론트엔드에서 클릭을 잠시 차단해야 함.
- **서비스 레벨 극복 방안 (Debounce / Throttle)**: 프론트엔드 버튼 클릭 시 300ms 디바운스(Debounce) 및 로딩 Spinner를 적용하여 동시 클릭을 시각적으로 100% 차단.
- **아키텍처 레벨 극복 방안 (CannotAcquireLockException Catch & Retry)**:
  - 백엔드 `PostService.reactToPost()`에서 `CannotAcquireLockException` 또는 `DeadlockLoserDataAccessException` 예외를 Catch 하여 409 Conflict 또는 3회 자동 재시도(Spring Retry) 로직을 적용하여 500 에러 방지.

---

# 📌 PART 2. 작업 완료 및 파일 위치 안내

* **생성된 마스터 스터디 가이드 경로**:
  - [`c:\Users\ikaes\IdeaProjects\snowthing\docs\study\sprint02\studySprint02PostDomainIssues260821.md`](file:///c:/Users/ikaes/IdeaProjects/snowthing/docs/study/sprint02/studySprint02PostDomainIssues260821.md)
  - [`c:\Users\ikaes\IdeaProjects\snowthing\docs\study\studySprint02PostDomainIssues260821.md`](file:///c:/Users/ikaes/IdeaProjects/snowthing/docs/study/studySprint02PostDomainIssues260821.md)
* **`AGENTS.md` 작업 기록 완료**: [`docs/project/work.md`](file:///c:/Users/ikaes/IdeaProjects/snowthing/docs/project/work.md) 파일에 수록 완료.
