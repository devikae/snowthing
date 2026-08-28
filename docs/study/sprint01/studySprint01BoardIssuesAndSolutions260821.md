# 📚 [Study Guide] Sprint 01 게시판 & 댓글 5대 아키텍처 문제점, 극복 방안 및 대체 기술 비교 가이드 (2026-08-21)

> **노션(Notion) 복사용 및 백엔드 기술 면접 대비용 마스터 가이드**  
> 본 문서는 Snowthing 스프린트 01/02 커뮤니티 도메인(게시글 Post & 댓글/대댓글 Comment)을 구축하면서 발견된 **5대 아키텍처 문제점**, **실제 적용된 물리적 해결책**, 그리고 **현업에서 사용되는 다양한 대체 대안(Alternatives) 및 트레이드오프 분석**을 7대 필수 요소 체계에 입각하여 정리한 문서입니다.

---

# 📑 PART 1. 커뮤니티 5대 문제점, 물리적 원인, 극복 방안 & 대체 대안 (Alternatives)

---

## 1. 💥 [댓글] 이미 삭제된 댓글 재삭제 시 `comment_count` 음수 차감 문제

### ① 개념 (What)
Soft Delete 처리된 댓글에 대해 동시 요청이나 무효한 삭제 요청이 들어왔을 때, 게시글의 역정규화 컬럼인 `post.comment_count`가 계속 차감되어 **수치가 음수(Negative Value)로 오염**되는 현상입니다.

### ② 물리적 원인 및 사이드 이펙트 (Why & Side-Effect)
- [`CommentService.java`](file:///c:/Users/ikaes/IdeaProjects/snowthing/backend/src/main/java/com/ikae/snowthing/domain/comment/service/CommentService.java) `deleteComment()` 메서드에서 `comment.softDelete()` 이후 `post.decreaseCommentCount()`를 실행합니다.
- 동시 2회 요청 시 `comment.isDeleted()` 예외 검사가 뚫리거나, 관리자 강제 삭제 쿼리 실행 시 `comment_count`가 0에서 차감되어 `-1`, `-2`가 되는 데이터 정합성 파괴가 발생합니다.

### ③ 현재 적용된 물리적 해결책 (How)
1. **도메인 엔티티 캡슐화 방어**: `Post` 엔티티 내부 `decreaseCommentCount()` 메서드에서 `this.commentCount = Math.max(0, this.commentCount - 1)`로 물리적 하한선(0)을 강제합니다.
2. **DB Table CHECK 제약 조건**: `POST` 테이블의 `comment_count` 컬럼에 `CHECK (comment_count >= 0)`를 추가하여 DB 엔진 단에서 음수 저장을 차단합니다.

### ④ 대체 대안 (Alternatives & Comparison)
* **대안 A: DB Atomic SQL 함수 (`GREATEST`) 사용**
  - **원리**: `UPDATE post SET comment_count = GREATEST(0, comment_count - 1) WHERE post_id = :id` SQL 쿼리 내에서 MySQL의 `GREATEST()` 함수를 사용하여 0 미만 차감을 원자적으로 물리 방지합니다.
  - **장점**: JPA 영속성 컨텍스트를 거치지 않고 DB 엔진이 1회 SQL 쿼리로 방어하므로 안전합니다.
* **대안 B: 스케줄러 기반 비동기 카운터 재계산 (Scheduled Counter Reconciliation)**
  - **원리**: 댓글 삭제 시 카운트를 즉시 차감하지 않고, 5분마다 `@Scheduled` 스케줄러가 `SELECT COUNT(*) FROM comment WHERE post_id = :id AND is_deleted = false` 쿼리로 실시간 숫자를 재계산하여 동기화합니다.
  - **장점**: 연산 오류나 음수 차감 위험이 완전히 제거됩니다.

---

## 2. 💥 [게시글] 인기 글 상세 조회 시 `increaseViewCount()` 쓰기 락(Row Lock) 병목

### ① 개념 (What)
유저가 게시글을 클릭하여 읽을 때마다 동기 트랜잭션(`@Transactional`) 내에서 `UPDATE post SET view_count = view_count + 1` 쿼리가 실행되어 DB 쓰기 병목이 발생하는 현상입니다.

### ② 물리적 원인 및 사이드 이펙트 (Why & Side-Effect)
- 단순 읽기(Read) 요청임에도 불구하고 인기 게시글에 동접자 1,000명이 한 번에 들어오면, **동일한 DB Row에 대한 배타적 쓰기 락(Exclusive Row Lock)**을 얻기 위해 트랜잭션 대기 열(Lock Contention)이 형성됩니다.
- 이로 인해 읽기 응답 속도가 급격히 느려지고 DB 커넥션 타임아웃 예외가 발생합니다.

### ③ 현재 적용된 물리적 해결책 (How)
* **DB Atomic Bulk Update 쿼리 실행**: JPA 엔티티 스냅샷 비교 대신 `@Modifying @Query("UPDATE Post p SET p.viewCount = p.viewCount + 1 WHERE p.id = :id")`를 호출하여 영속성 컨텍스트 1:1 비교 오버헤드를 줄입니다.

### ④ 대체 대안 (Alternatives & Comparison)
* **대안 A: Redis HyperLogLog (인메모리 고성능 카운팅 & 중복 제거)**
  - **원리**: Redis의 HyperLogLog 자료구조(`PFADD post:views:{id} {memberId_or_ip}`)를 사용합니다. 단 12KB의 메모리만으로 100만 명의 중복 조회를 인메모리 O(1)로 차단하고 조회수를 카운팅합니다.
  - **장점**: DB Row Lock이 100% 제거되고 중복 조회수 어뷰징까지 동시에 해결됩니다.
* **대안 B: Client-Side Cookie 쿨타임 제한 (24시간 중복 방지)**
  - **원리**: 유저 브라우저 쿠키(`viewed_posts=1,4,12`)에 읽은 글 ID를 저장하고, 쿠키가 존재하는 24시간 동안은 백엔드로 조회수 증가 API를 아예 보내지 않도록 프론트엔드에서 차단합니다.
  - **장점**: 백엔드 서버로 들어오는 HTTP 요청 수 자체를 줄여줍니다.

---

## 3. 💥 [추천 비동기] `@Async` 이벤트 유실 시 투표 이력과 카운트 수치 불일치

### ① 개념 (What)
추천/비추천 투표 시 `post_reaction` 테이블 저장은 즉시 완료(COMMIT)되었으나, 비동기로 카운트를 올리는 `@Async` 핸들러가 예외나 서버 셧다운으로 유실될 경우 데이터 불일치가 남는 현상입니다.

### ② 물리적 원리 및 사이드 이펙트 (Why & Side-Effect)
- `post_reaction` 테이블에는 투표 내역 1건이 정상 저장되어 유저는 중복 투표를 할 수 없는데, 게시글의 `like_count` 수치는 올라가지 않아 **두 테이블 간 정합성 불일치(Data Inconsistency)**가 발생합니다.

### ③ 현재 적용된 물리적 해결책 (How)
* **비동기 예외 로그 수집 & UI 낙관적 반영**: 프론트엔드 Optimistic UI로 시각적 일치감을 보장하고 백엔드 예외 로그를 수집합니다.

### ④ 대체 대안 (Alternatives & Comparison)
* **대안 A: Transactional Outbox Pattern (트랜잭셔널 아웃박스 패턴)**
  - **원리**: 이벤트를 인메모리 스프링 이벤트로 던지지 않고, 동일한 DB 트랜잭션 내에서 `outbox` 테이블에 이벤트를 함께 `INSERT`합니다. 이후 Debezium(CDC)이나 Polling Publisher가 이 이벤트를 안전하게 읽어서 비동기 처리합니다.
  - **장점**: 서버가 갑자기 꺼지거나 비동기 스레드가 죽어도 이벤트 유실이 발생하지 않는 기업급 분산 트랜잭션 패턴입니다.
* **대안 B: 정정 스케줄러 배치 (Scheduled Reckoning Batch)**
  - **원리**: 매일 새벽 4시마다 `SELECT COUNT(*) FROM post_reaction WHERE post_id = :id AND type = 'LIKE'` 쿼리와 `post.like_count` 수치를 대조하여 다를 경우 불일치를 자동으로 수정하는 배치를 돌립니다.

---

## 4. 💥 [대댓글] 3차 이상 무한 깊이 대댓글 작성 시 프론트엔드 UI 파괴 문제

### ① 개념 (What)
유저가 '대댓글(2차)'의 ID를 `parentId`로 지정하여 3차, 4차, 5차 계층의 대댓글을 계속해서 작성할 때 발생하는 문제입니다.

### ② 물리적 원리 및 사이드 이펙트 (Why & Side-Effect)
- 프론트엔드 계층형 트리 렌더링 시 대댓글 깊이(`depth`)에 비례하여 `marginLeft: depth * 1.5rem` 들여쓰기가 적용됩니다.
- N차 대댓글이 계속 작성되면 들여쓰기가 화면 우측 밖으로 삐져나가 **모바일 및 웹 UI 레이아웃이 완전히 깨지게 됩니다.**

### ③ 현재 적용된 물리적 해결책 (How)
* **2단계 깊이(Depth) 제한Validation**: `parent.getParent() != null` 조건으로 부모 댓글이 이미 대댓글인 경우 400 Bad Request 예외를 던져 3차 이상 대댓글 작성을 차단합니다.

### ④ 대체 대안 (Alternatives & Comparison)
* **대안 A: Flat List + `@Mention` (유튜브 / 인스타그램 스타일 1차 평탄화)**
  - **원리**: 대댓글 들여쓰기 깊이를 없애고 모든 대댓글을 원댓글 바로 아래 평탄한(Flat) 리스트로 렌더링하며, 누구에게 보낸 답글인지 `@닉네임` 태그로 표시합니다.
  - **장점**: UI 레이아웃이 화면 밖으로 삐져나가는 현상이 아예 구조적으로 불가능해집니다.
* **대안 B: CSS Max-Indent Clamp (프론트엔드 들여쓰기 한계선 고정)**
  - **원리**: 백엔드는 N차 대댓글을 허용하되, 프론트엔드 CSS에서 `margin-left: min(depth * 1.5rem, 4.5rem)`으로 최대 들여쓰기 한계를 4.5rem으로 고정합니다.

---

## 5. 💥 [보안] 익명 비밀번호 URL 쿼리 스트링 평문 노출 위험

### ① 개념 (What)
비회원 익명 게시글/댓글 삭제 시 `DELETE /api/posts/{publicId}?anonymousPassword=1234` 형태처럼 URL 쿼리 파라미터로 비밀번호가 전달될 때 발생하는 보안 문제입니다.

### ② 물리적 원리 및 사이드 이펙트 (Why & Side-Effect)
- HTTP URL 쿼리 스트링은 Nginx 웹 서버 Access Log, AWS ALB 액세스 로그, 브라우저 History에 **평문(Plaintext)으로 100% 그대로 기록**됩니다.
- 웹 서버 로그를 조회할 때 비회원의 비밀번호가 인가 없이 노출될 수 있습니다.

### ③ 현재 적용된 물리적 해결책 (How)
* **BCrypt 해시 암호화 검증**: DB 저장 시 비밀번호를 BCrypt 해싱하여 원본 비밀번호 유출을 차단합니다.

### ④ 대체 대안 (Alternatives & Comparison)
* **대안 A: HTTP Custom Header (`X-Anonymous-Password`) 전달**
  - **원리**: `DELETE` 요청 전송 시 URL 쿼리 스트링 대신 `X-Anonymous-Password: 1234` 커스텀 HTTP 헤더에 실어 보냅니다.
  - **장점**: Nginx 및 액세스 로그에 비밀번호가 남지 않습니다.
* **대안 B: HMAC-SHA256 일회용 삭제 토큰 (Stateless Delete Token)**
  - **원리**: 비회원이 글 작성 시 백엔드가 비밀번호를 저장하지 않고, `SHA256(publicId + secretKey + password)`로 생성된 일회용 삭제 토큰을 유저 클라이언트에 전달합니다. 삭제 시 유저는 비밀번호 대신 이 토큰을 제시하여 검증받습니다.

---

# 📌 PART 2. 작업 완료 요약

* **생성된 파일 경로**:
  - [`c:\Users\ikaes\IdeaProjects\snowthing\docs\study\sprint01\studySprint01BoardIssuesAndSolutions260821.md`](file:///c:/Users/ikaes/IdeaProjects/snowthing/docs/study/sprint01/studySprint01BoardIssuesAndSolutions260821.md)
  - [`c:\Users\ikaes\IdeaProjects\snowthing\docs\study\studySprint01BoardIssuesAndSolutions260821.md`](file:///c:/Users/ikaes/IdeaProjects/snowthing/docs/study/studySprint01BoardIssuesAndSolutions260821.md)
* **작업 이력 기록 완료**: [`docs/project/work.md`](file:///c:/Users/ikaes/IdeaProjects/snowthing/docs/project/work.md) 파일 업데이트 완료.
