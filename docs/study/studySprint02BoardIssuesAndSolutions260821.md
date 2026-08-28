# 📚 [Master Study Guide] Sprint 02 커뮤니티 게시판 5대 아키텍처 문제점 & 10대 대체 기술(Alternatives) 7대 필수 요소 상세 가이드 (2026-08-21)

> **노션(Notion) 복사용 및 백엔드 기술 면접 / 시스템 아키텍처 심화 학습용 마스터 가이드**  
> 본 문서는 Snowthing 커뮤니티 도메인(게시글 Post & 댓글/대댓글 Comment) 구축 시 발생할 수 있는 **5대 핵심 아키텍처 문제점**과 **10대 대체 기술(Alternatives)**에 대해, **7대 필수 서술 요소 체계(개념, Why, When, How 코드/SQL, Pros, Cons & Trade-off, 서비스/아키텍처 레벨 극복 방안)** 중 **극복 방안(Mitigation)을 물리적 원리와 코드 수준으로 파헤쳐 수록한 완성판 학습 문서**입니다.

---

# 📑 PART 1. 5대 핵심 문제점 심층 파헤치기 (문제 & 극복 방안 딥다이브)

---

## 1. 💥 [댓글] 이미 삭제된 댓글 재삭제 시 `comment_count` 음수 차감 및 카운터 정합성 오염 문제

### ① 개념 (What - 문제의 명확한 정의)
Soft Delete(논리 삭제) 처리된 댓글에 대해 동시 요청(Race Condition)이나 무효한 삭제 요청이 들어왔을 때, 게시글 엔티티의 역정규화 컬럼인 `post.comment_count`가 계속 차감되어 **수치가 0 미만인 `-1`, `-2`로 오염되는 정합성 파괴 현상**입니다.

### ② 발생 원인 (Why - 물리적 & DB 메커니즘)
- [`CommentService.java`](file:///c:/Users/ikaes/IdeaProjects/snowthing/backend/src/main/java/com/ikae/snowthing/domain/comment/service/CommentService.java) `deleteComment()` 메서드에서 `comment.softDelete()` 호출 후 `post.decreaseCommentCount()`를 실행합니다.
- 트랜잭션 격리 수준(Read Committed) 환경에서 동일한 댓글 삭제 요청이 동시에 2건 들어오면, Thread 1과 Thread 2가 모두 `comment.isDeleted() == false` 상태를 읽게 됩니다.
- Thread 1이 먼저 `comment.softDelete()` 후 `comment_count`를 1 ➔ 0으로 차감하고 COMMIT 되더라도, 이미 검증을 통과한 Thread 2가 뒤이어 `comment_count`를 0 ➔ -1로 차감하여 쿼리를 전송하므로 음수가 발생합니다.

### ③ 언제 발생하는지 (When - 적합한 발생 상황)
- 클라이언트 네트워크 지연으로 사용자가 삭제 버튼을 빠른 속도로 연타(광클)할 때
- 관리자 댓글 강제 삭제 API와 일반 유저의 삭제 요청이 동시에 백엔드로 인커밍될 때

### ④ 어떻게 발생하는지 (How - 실제 코드 & DB 쿼리 실행 메커니즘)
```
[Thread 1] SELECT * FROM comment WHERE id = 1 (is_deleted = false)
[Thread 2] SELECT * FROM comment WHERE id = 1 (is_deleted = false)
[Thread 1] UPDATE comment SET is_deleted = true WHERE id = 1
[Thread 1] UPDATE post SET comment_count = comment_count - 1 WHERE id = 10 (1 -> 0) -> COMMIT
[Thread 2] UPDATE comment SET is_deleted = true WHERE id = 1
[Thread 2] UPDATE post SET comment_count = comment_count - 1 WHERE id = 10 (0 -> -1) -> COMMIT [음수 오염!]
```

### ⑤ 부정적 영향 (Pros & Cons of Ignoring - 미해결 시 여파)
- **비즈니스 결함**: 게시판 목록 조회 시 댓글이 0개임에도 `댓글 [-1]`로 노출되어 사용자 서비스 신뢰도 실추.
- **DB 쿼리 오류**: 댓글 수 정렬(`ORDER BY comment_count DESC`) 쿼리 실행 시 정렬 순서가 꼬여 인기 게시글 추출 알고리즘이 파괴됨.

### ⑥ 기존 처리 방식과의 비교 및 한계점 (Alternatives vs Existing)
- **기존 방식**: 자바 서비스 메서드 내 `if (comment.isDeleted()) throw ...` 단순 예외 검사.
- **한계점**: 동시성 멀티 스레드 환경에서는 SELECT 시점의 스냅샷이 동일하므로 자바 `if` 문 검사가 무용지물이 됨.

### ⑦ 트레이드오프 및 서비스/아키텍처 레벨 극복 방안 (Trade-off & Detailed Mitigation)
- **트레이드오프**: 단순 자바 로직 방어가 불가능하므로 DB 레벨의 제약 조건이나 원자적 SQL 연산으로 이관해야 하는 오버헤드 발생.
- **서비스 레벨 극복 방안 (UX 폴백)**:
  - 프론트엔드 댓글 카운트 렌더링 시 `Math.max(0, count)` 처리로 만에 하나 백엔드 오염이 발생하더라도 유저 화면에는 `-1`이 아닌 `0`으로 표시되도록 사용자 시각 차단 폴백을 적용합니다.
- **아키텍처 레벨 극복 방안 (엔티티 캡슐화 & DB Constraint)**:
  - 1차적으로 `Post` JPA 엔티티 내 도메인 메서드 `decreaseCommentCount()` 내부에 `this.commentCount = Math.max(0, this.commentCount - 1)` 방어 로직을 캡슐화합니다.
  - 2차적으로 DB `POST` 테이블에 `ALTER TABLE post ADD CONSTRAINT chk_post_comment_count CHECK (comment_count >= 0)` DDL 제약 조건을 추가하여, DB 엔진이 커밋 시점에 음수 업데이트 시도를 물리적으로 거부하고 예외를 내도록 이중 방어망을 구축합니다.

---

## 2. 💥 [게시글] 인기 글 상세 조회 시 `increaseViewCount()` 쓰기 락(Row Lock) 병목 문제

### ① 개념 (What - 문제의 명확한 정의)
유저가 게시글 상세 페이지를 읽을 때마다 동기 트랜잭션(`@Transactional`) 내에서 `UPDATE post SET view_count = view_count + 1` 쓰기 쿼리가 날아가 DB 쓰기 병목(Lock Contention)이 발생하는 현상입니다.

### ② 발생 원인 (Why - 물리적 & DB 메커니즘)
- RDBMS(MySQL InnoDB)는 단일 행(Row)에 대한 `UPDATE` 쿼리 실행 시 해당 행에 배타적 쓰기 락(Exclusive Row Lock, X-Lock)을 겁니다.
- 읽기(Read) 요청임에도 불구하고 쓰기 락이 발생하여, 동시 진입한 수천 개의 트랜잭션이 동일한 게시글 Row Lock을 획득하기 위해 줄을 서서 대기합니다.

### ③ 언제 발생하는지 (When - 적합한 발생 상황)
- 메인 화면에 노출된 인기 핫딜, 긴급 공지사항, 리조트 실시간 제보 글 등 특정 핫 게시글에 수천 명의 동접자가 동시에 클릭할 때

### ④ 어떻게 발생하는지 (How - 실제 코드 & DB 쿼리 실행 메커니즘)
```
[유저 1000명 동시 요청] GET /api/posts/{publicId}
 └── PostService.getPostDetail() 진입 (@Transactional)
      └── DB Connection Pool 1000개 고갈
           └── UPDATE post SET view_count = view_count + 1 WHERE post_id = 1 (Row Lock 대기)
                └── 5초 후 DB Connection Timeout 예외 발생 -> 504 Gateway Timeout
```

### ⑤ 부정적 영향 (Pros & Cons of Ignoring - 미해결 시 여파)
- **캐스케이딩 장애 (Cascading Failure)**: 인기 글 1개의 조회수 락 병목으로 인해 DB 커넥션 풀이 고갈되어, 로그인, 게시글 작성 등 서비스 전체 API가 마비됨.

### ⑥ 기존 처리 방식과의 비교 및 한계점 (Alternatives vs Existing)
- **기존 방식**: JPA `@Modifying @Query` Bulk Update 호출.
- **한계점**: 영속성 컨텍스트 스냅샷 비교는 줄였지만, DB InnoDB Row Lock 형성 자체를 피할 수는 없음.

### ⑦ 트레이드오프 및 서비스/아키텍처 레벨 극복 방안 (Trade-off & Detailed Mitigation)
- **트레이드오프**: 조회수를 실시간으로 DB에 동기 기록하는 아키텍처를 포기해야 함.
- **서비스 레벨 극복 방안 (가용성 우선 정책)**:
  - 조회수 반영에 10분의 미세한 시차가 발생하더라도, 유저가 글을 읽을 때 페이지 로딩 속도를 최우선으로 확보하는 가용성(Availability) 우선 서비스 정책을 수립합니다.
- **아키텍처 레벨 극복 방안 (Redis 쓰기 격리 & Write-Back 배치)**:
  - 유저가 글을 읽을 때 DB `UPDATE` 쿼리를 100% 제거하고, Redis `INCR post:view_count:{id}` 명령으로 인메모리 단에서 조회수만 가산합니다.
  - 백그라운드 스프링 `@Scheduled(cron = "0 */10 * * * *")` 스케줄러가 Redis에 누적된 수치를 읽어 10분마다 DB `post.view_count` 컬럼으로 일괄 Bulk Write-Back (`UPDATE post SET view_count = view_count + :incr`)을 수행함으로써 DB Row Lock 형성 자체를 완전히 분리합니다.

---

## 3. 💥 [추천 비동기] `@Async` 비동기 카운터 유실 시 투표 이력과 카운트 수치 불일치 문제

### ① 개념 (What - 문제의 명확한 정의)
추천 투표 시 `post_reaction` 테이블 저장은 성공적으로 COMMIT 되었으나, 비동기로 카운터를 올리는 `@Async` 핸들러가 예외나 서버 셧다운으로 유실될 때 데이터 불일치가 남는 현상입니다.

### ② 발생 원인 (Why - 물리적 & DB 메커니즘)
- 메인 트랜잭션 Thread는 `reactionRepository.save()` 후 DB COMMIT을 치고 즉시 200 OK를 응답합니다.
- 스프링의 `@Async` 비동기 스레드 풀에서 실행되는 [`PostReactionEventListener`](file:///c:/Users/ikaes/IdeaProjects/snowthing/backend/src/main/java/com/ikae/snowthing/domain/post/event/PostReactionEventListener.java)가 실행 중 DB 락 타임아웃이나 OOM, 서버 재부팅을 만나면 카운트 `UPDATE` 쿼리가 날아가지 못하고 사라집니다.

### ③ 언제 발생하는지 (When - 적합한 발생 상황)
- 서버 배포 시점, 서버 셧다운, DB 일시적 네트워크 흔들림 또는 비동기 스레드 풀(Thread Pool) 큐가 가득 찼을 때

### ④ 어떻게 발생하는지 (How - 실제 코드 & DB 쿼리 실행 메커니즘)
```
[Main Thread] post_reaction INSERT (post_id=1, member_id=5, type='LIKE') -> COMMIT 완료
[Main Thread] eventPublisher.publishEvent() -> 200 OK 응답
[Async Thread] @Async handleEvent() 실행 중 DB Timeout 터짐 -> UPDATE post SET like_count = like_count + 1 실패!
[결과] post_reaction 에는 1건 존재하나, post.like_count는 0으로 동기화 실패 (데이터 정합성 파괴)
```

### ⑤ 부정적 영향 (Pros & Cons of Ignoring - 미해결 시 여파)
- **사용자 경험(UX) 악화**: 유저는 "추천을 눌렀는데 화면 숫자가 안 오른다"고 생각하여 재투표를 시도하지만, DB 유니크 제약으로 409 Conflict 예외가 터져 혼란 야기.

### ⑥ 기존 처리 방식과의 비교 및 한계점 (Alternatives vs Existing)
- **기존 방식**: `@Async` 백그라운드 단순 이벤트 발행.
- **한계점**: JVM 인메모리 큐에 보관되므로 서버 재부팅 시 이벤트가 100% 영구 유실됨.

### ⑦ 트레이드오프 및 서비스/아키텍처 레벨 극복 방안 (Trade-off & Detailed Mitigation)
- **트레이드오프**: 단순 비동기 이벤트 대신 DB 기반 아웃박스 테이블이나 스케줄러 배치를 도입해야 함.
- **서비스 레벨 극복 방안 (Optimistic UI 렌더링)**:
  - 백엔드의 처리 시차나 유실에 관계없이 프론트엔드에서 추천 버튼 클릭 즉시 추천 버튼 색상을 바꾸고 숫자 수치를 +1 가산하여 유저가 지연을 느끼지 않도록 처리합니다.
- **아키텍처 레벨 극복 방안 (Transactional Outbox & 새벽 정정 배치)**:
  - 1차적으로 Transactional Outbox Pattern을 채택하여 `outbox` 테이블에 메인 트랜잭션과 동일 커밋을 수행함으로써 이벤트 유실을 방지합니다.
  - 2차 보완으로 매일 새벽 4시마다 `ScheduledReckoningBatch`를 실행하여 `SELECT COUNT(*) FROM post_reaction WHERE post_id = :id AND type = 'LIKE'` 수치와 `post.like_count` 수치를 비교하고, 다를 경우 올바른 수치로 자동 보정하여 100% 최종 일관성(Eventual Consistency)을 달성합니다.

---

## 4. 💥 [대댓글] 3차 이상 무한 깊이 대댓글 작성 시 프론트엔드 UI 파괴 문제

### ① 개념 (What - 문제의 명확한 정의)
대댓글의 대댓글(3차), 4차, 10차 대댓글 작성 시 프론트엔드의 고정 들여쓰기(`marginLeft`)로 인해 모바일 및 웹 화면 우측 밖으로 본문 텍스트가 삐져나가는 UI 깨짐 현상입니다.

### ② 발생 원인 (Why - 물리적 & DB 메커니즘)
- 백엔드 [`CommentService.java`](file:///c:/Users/ikaes/IdeaProjects/snowthing/backend/src/main/java/com/ikae/snowthing/domain/comment/service/CommentService.java)에서 `parentId` 검증 시 부모가 '원댓글(1차)'인지 '대댓글(2차)'인지 검사하는 depth 제한 로직이 누락됨.
- 프론트엔드는 계층 구조에 따라 `marginLeft = depth * 1.5rem`으로 스타일을 렌더링함.

### ③ 언제 발생하는지 (When - 적합한 발생 상황)
- 악의적인 사용자가 대댓글의 ID를 부모로 삼아 N차 대댓글을 연속 작성하거나 타사 스크립트로 API를 직접 호출할 때

### ④ 어떻게 발생하는지 (How - 실제 코드 & DB 쿼리 실행 메커니즘)
```
10차 대댓글 작성 -> depth = 10
 └── 프론트엔드: <div style="margin-left: 15rem (240px)">
      └── 모바일 화면 폭(360px) 중 본문 영역이 120px로 축소됨 -> 1글자씩 세로 줄바꿈 및 화면 우측 이탈
```

### ⑤ 부정적 영향 (Pros & Cons of Ignoring - 미해결 시 여파)
- **서비스 사용 불능**: 모바일 사용자가 게시글 및 댓글을 정상적으로 읽을 수 없어 서비스 가독성 파괴.

### ⑥ 기존 처리 방식과의 비교 및 한계점 (Alternatives vs Existing)
- **기존 방식**: 부모 존재 여부(`parentId != null`)만 검증.
- **한계점**: 부모 댓글의 부모가 존재하는지(2차 깊이 이상인지) 검증하지 않아 무한 깊이 생성을 막지 못함.

### ⑦ 트레이드오프 및 서비스/아키텍처 레벨 극복 방안 (Trade-off & Detailed Mitigation)
- **트레이드오프**: 3차 이상의 깊은 토론 스레드 작성을 제한해야 함.
- **서비스 레벨 극복 방안 (대댓글 폼 안내 & Toast 알림)**:
  - 프론트엔드 댓글 입력창에서 대댓글의 [답글 달기] 버튼을 누를 경우 "대댓글에는 추가 답글을 작성할 수 없습니다."라는 안내 Toast 메세지를 노출하여 작성을 사전 유도 차단합니다.
- **아키텍처 레벨 극복 방안 (백엔드 2단계 Depth Validation)**:
  - [`CommentService.java`](file:///c:/Users/ikaes/IdeaProjects/snowthing/backend/src/main/java/com/ikae/snowthing/domain/comment/service/CommentService.java) `createComment()` 메서드 진입 시 `if (parent != null && parent.getParent() != null)` 검증 로직을 추가합니다.
  - 부모 댓글(`parent`)이 이미 부모(`parent.getParent()`)를 가지고 있는 2차 계층 이상이라면 `CustomAuthException(ErrorCode.INVALID_INPUT, "대댓글에는 추가 답글을 작성할 수 없습니다.")` 예외(400 Bad Request)를 던져 백엔드 API 수준에서 물리적으로 원자적 차단합니다.

---

## 5. 💥 [보안] 익명 비밀번호 URL 쿼리 스트링 평문 노출 보안 위험 문제

### ① 개념 (What - 문제의 명확한 정의)
비회원 익명 게시글/댓글 삭제 시 `DELETE /api/posts/{publicId}?anonymousPassword=1234` 형태처럼 URL 쿼리 파라미터로 비밀번호가 전송되어 웹 서버 로그에 비밀번호가 평문 저장되는 보안 문제입니다.

### ② 발생 원인 (Why - 물리적 & DB 메커니즘)
- HTTP 표준 및 웹 서버(Nginx, Apache, AWS ALB) 구현상, HTTP GET/DELETE 메서드의 URL 쿼리 스트링은 서버 Access Log의 Request Line 항목에 100% 그대로 로깅됩니다.

### ③ 언제 발생하는지 (When - 적합한 발생 상황)
- 비회원 익명 사용자가 자신이 쓴 글이나 댓글을 삭제하기 위해 비밀번호를 입력하고 삭제를 요청할 때마다 항상 발생

### ④ 어떻게 발생하는지 (How - 실제 코드 & DB 쿼리 실행 메커니즘)
```
Client: DELETE /api/posts/p1024?anonymousPassword=secretPass123
 └── Nginx access.log 기록:
      "192.168.1.10 - - [21/Aug/2026:17:00:00] "DELETE /api/posts/p1024?anonymousPassword=secretPass123 HTTP/1.1" 200 45"
       └── 로그 파일 조회자에게 비회원 비밀번호 완전 노출!
```

### ⑤ 부정적 영향 (Pros & Cons of Ignoring - 미해결 시 여파)
- **보안 컨플라이언스 위반**: 비밀번호 평문 로깅으로 인한 개인정보보호법 위반 및 서버 로그 유출 시 타 계정 도용 2차 피해.

### ⑥ 기존 처리 방식과의 비교 및 한계점 (Alternatives vs Existing)
- **기존 방식**: DB 저장 시 BCrypt 암호화 저장.
- **한계점**: DB 저장은 안전하지만, 네트워크 전송 구간 및 Nginx 웹 서버 로그 단에서의 비밀번호 노출을 막지 못함.

### ⑦ 트레이드오프 및 서비스/아키텍처 레벨 극복 방안 (Trade-off & Detailed Mitigation)
- **트레이드오프**: URL 파라미터 전송 대신 커스텀 헤더나 무상태 토큰 방식을 적용해야 하므로 프론트엔드 연동 복잡도 증가.
- **서비스 레벨 극복 방안 (삭제 모달 폼 보안 전송)**:
  - 삭제 모달 팝업에서 비밀번호 입력 시 `type="password"` 상태로 암호화 입력을 보장하고, URL 쿼리 스트링 생성을 아예 프론트엔드 단에서 금지합니다.
- **아키텍처 레벨 극복 방안 (HTTP Custom Header & HMAC 일회용 삭제 토큰)**:
  - **방안 1**: 전송 방식을 HTTP Custom Header (`X-Anonymous-Password`)로 전환하고 Nginx `log_format` 설정에서 해당 헤더 로깅을 제외하여 로그 평문 노출을 차단합니다.
  - **방안 2**: 무상태 HMAC-SHA256 일회용 삭제 토큰(`generateDeleteToken`) 방식을 도입하여 DB에 비밀번호 컬럼 자체가 아예 존재하지 않는 무상태 보안 검증 구조를 완성합니다.

---

# 📑 PART 2. 10대 대체 기술(Alternatives) 7대 필수 요소 심층 분석 (극복 방안 딥다이브)

---

## 1. 💥 [댓글] 이미 삭제된 댓글 재삭제 이슈의 2대 대안

### 1-1. 대체 대안 A: DB Atomic SQL 함수 (`GREATEST`) 사용

#### ① 개념 (What)
JPA 영속 상태 변경 방식 대신, MySQL의 `GREATEST(0, comment_count - 1)` SQL 함수를 내보내 DB 엔진 단에서 차감 결과가 0 미만으로 내려가지 않도록 원자적 방어를 수행하는 기법입니다.

#### ② 왜 사용하는지 (Why)
JPA 메모리 연산 방식(`post.setCommentCount(count - 1)`)은 동시 요청 시 Dirty Read로 음수 차감이 터질 수 있으므로, DB 엔진의 Single Thread SQL 실행 메커니즘을 이용하기 위함입니다.

#### ③ 어떨 때 사용하는지 (When)
재고 차감(0개 미만 불가), 포인트 차감(0원 미만 불가), 카운터 감소 등 하한선이 명확한 차감 연산에 사용합니다.

#### ④ 어떻게 사용하는지 (How - 구현 코드)
```java
// PostRepository.java
@Modifying(clearAutomatically = true, flushAutomatically = true)
@Query("UPDATE Post p SET p.commentCount = GREATEST(0, p.commentCount - 1) WHERE p.id = :postId")
void decreaseCommentCountAtomic(@Param("postId") Long postId);
```

#### ⑤ 장점 (Pros)
- **음수 차감 물리적 100% 방지**: MySQL 엔진이 Single Thread로 쿼리를 내보내므로 동시 요청이 10,000건 들어와도 0 아래로 내려가지 않음.
- **영속성 스냅샷 비교 생략**: JPA 1:1 엔티티 스냅샷 비교 과정이 없어서 Execution Time 축소.

#### ⑥ 다른 기술과의 비교 (Alternatives)
- **자바 `Math.max(0, count - 1)` 방어 대비**: 자바 메모리 방어는 멀티 스레드 동시 진입 시 이미 생성된 UPDATE 쿼리를 막지 못하지만, SQL `GREATEST`는 DB 엔진 단에서 원자적 처리됨.

#### ⑦ 트레이드오프 및 서비스/아키텍처 레벨 극복 방안 (Trade-off & Detailed Mitigation)
- **트레이드오프 (JPA 1차 캐시 불일치)**: DB 컬럼은 차감되었으나 JPA 1차 캐시 엔티티 객체의 `commentCount` 수치는 갱신되지 않는 불일치 발생.
- **서비스 레벨 극복 방안**: 댓글 삭제 응답 반환 시 개별 엔티티 수치 대신 백엔드가 방금 갱신한 정정 수치를 반환하거나 최신 목록 API를 다시 호출하도록 유도.
- **아키텍처 레벨 극복 방안 (JPA Flush & Clear)**: `@Modifying(clearAutomatically = true, flushAutomatically = true)` 옵션을 부여하여 쿼리 실행 직후 JPA 영속성 컨텍스트를 DB로 `flush()`하고 1차 캐시를 자동으로 `clear()` 함으로써 이후 조회 쿼리가 DB의 최신 `comment_count` 수치를 패치하도록 완전 동기화.

---

### 1-2. 대체 대안 B: 스케줄러 기반 비동기 카운터 재계산 (Scheduled Reconciliation)

#### ① 개념 (What)
댓글 작성/삭제 시 DB 카운터를 즉시 변경하지 않고, 주기적인 백그라운드 스케줄러가 실시간 `SELECT COUNT(*)` 집계 쿼리를 돌려 게시글의 `comment_count`를 일괄 정정 덮어쓰는 기법입니다.

#### ② 왜 사용하는지 (Why)
카운터 증감 연산 자체를 이관하여 쓰기 락 병목과 음수 오염 가능성을 근본 제거하기 위함입니다.

#### ③ 어떨 때 사용하는지 (When)
실시간 카운트 정확도보다 DB 쓰기 성능 및 안정성이 훨씬 중요한 대규모 커뮤니티에 적합합니다.

#### ④ 어떻게 사용하는지 (How - 구현 코드)
```java
@Scheduled(cron = "0 */5 * * * *") // 5분마다 실행
@Transactional
public void reconcileCommentCounts() {
    Set<String> dirtyPostIds = redisTemplate.opsForSet().members("dirty_posts");
    if (dirtyPostIds == null || dirtyPostIds.isEmpty()) return;

    for (String postIdStr : dirtyPostIds) {
        Long postId = Long.parseLong(postIdStr);
        long actualCount = commentRepository.countByPostIdAndIsDeletedFalse(postId);
        postRepository.updateCommentCount(postId, actualCount);
        redisTemplate.opsForSet().remove("dirty_posts", postIdStr);
    }
}
```

#### ⑤ 장점 (Pros)
- **카운터 오류 근본적 해결**: 증감 연산을 하지 않고 실시간 개수를 덮어쓰므로 카운트 누수나 음수 현상이 발생할 수 없음.

#### ⑥ 다른 기술과의 비교 (Alternatives)
- **동기 `decreaseCommentCount()` 대비**: 동기 방식은 매 댓글 삭제 시 DB Row Lock을 잡지만, 스케줄러 방식은 삭제 시 Lock을 전혀 잡지 않음.

#### ⑦ 트레이드오프 및 서비스/아키텍처 레벨 극복 방안 (Trade-off & Detailed Mitigation)
- **트레이드오프 (최대 5분의 시차 발생 & 주기적 DB I/O 부하)**: 댓글 작성 직후 5분 동안은 화면 상의 댓글 수 수치가 실시간 반영되지 않고, 배치 실행 시 Full Scan 부하가 생김.
- **서비스 레벨 극복 방안 (로컬 State 반영)**: 댓글 작성/삭제 직후 프론트엔드 로컬 State에서 수치를 임시로 +1 / -1 가산하여 렌더링함으로써 유저가 시차를 느끼지 않도록 보완.
- **아키텍처 레벨 극복 방안 (Dirty Set Redis 수집 & 핀포인트 집계)**: 전수 조사의 DB I/O 부하를 막기 위해 댓글 CUD 발생 시 `dirty_posts` Redis Set에 `postId`를 수집하고, 스케줄러는 해당 Set에 등록된 `postId`에 대해서만 핀포인트 Range 집계 쿼리를 내보내어 DB I/O를 99% 절감.

---

## 2. 💥 [게시글] 인기 글 상세 조회 시 쓰기 락 병목 이슈의 2대 대안

### 2-1. 대체 대안 A: Redis HyperLogLog (`PFADD`) 기반 고성능 카운팅 & 중복 제거

#### ① 개념 (What)
Redis의 확률적 자료구조인 HyperLogLog(`PFADD`, `PFCOUNT`)를 활용하여, 단 12KB 메모리만으로 중복 조회를 인메모리 $O(1)$로 차단하고 조회수를 카운팅하는 기법입니다.

#### ② 왜 사용하는지 (Why)
단순 카운터나 RDBMS에 중복 IP 테이블을 만들어 저장하면 메모리와 DB 용량이 폭증합니다. HyperLogLog는 100만 건의 중복 IP를 단 12KB 메모리로 추산하므로 공간 효율성이 최상입니다.

#### ③ 어떨 때 사용하는지 (When)
대규모 트래픽 서비스의 게시글 조회수, 방문자 수(UV) 집계 및 중복 조회 방지에 사용합니다.

#### ④ 어떻게 사용하는지 (How - 구현 코드)
```java
public void increaseViewCountWithHyperLogLog(Long postId, String clientIp) {
    String redisKey = "post:views:" + postId;
    // HyperLogLog에 IP 추가 (새로운 IP면 1 반환, 중복이면 0 반환)
    Long added = redisTemplate.opsForHyperLogLog().add(redisKey, clientIp);
    
    if (added != null && added == 1L) {
        redisTemplate.opsForValue().increment("post:view_count:" + postId);
    }
}
```

#### ⑤ 장점 (Pros)
- **DB Row Lock 100% 제거**: DB에 쓰기 쿼리가 전혀 들어가지 않으므로 동접자가 몰려도 락 병목이 터지지 않음.
- **극도의 메모리 절약**: 100만 명의 IP를 수집해도 무조건 단 12KB 메모리만 사용함.

#### ⑥ 다른 기술과의 비교 (Alternatives)
- **Redis Set 자료구조 대비**: Redis Set은 100만 개 IP 저장 시 수십 MB의 메모리가 들지만, HyperLogLog는 12KB로 고정됨.

#### ⑦ 트레이드오프 및 서비스/아키텍처 레벨 극복 방안 (Trade-off & Detailed Mitigation)
- **트레이드오프 (0.81%의 확률적 표준오차)**: 확률적 계산 알고리즘 특성상 약 0.81% 미만의 오차가 발생할 수 있음.
- **서비스 레벨 극복 방안**: 커뮤니티 조회 수치는 0.81% 오차(1,000회 기준 약 8회 차이)가 서비스 이용이나 금융 정산 영역이 아니므로 서비스 요구사항을 완전 만족함을 도메인 레벨 수용.
- **아키텍처 레벨 극복 방안 (Redis RDB/AOF & Scheduled Write-Back)**: Redis 메모리 휘발에 대비하여 10분 단위 스케줄러가 Redis 수치를 DB `post.view_count` 컬럼으로 Write-Back 집계 갱신하여 영구 보존.

---

### 2-2. 대체 대안 B: Client-Side Cookie 쿨타임 제한 (24시간 중복 방지)

#### ① 개념 (What)
유저 브라우저 쿠키(`viewed_posts=1,4,12`)에 읽은 글 ID를 기록하고, 쿠키가 유효한 24시간 동안은 프론트엔드에서 백엔드로 조회수 증가 요청을 아예 보내지 않도록 차단하는 기법입니다.

#### ② 왜 사용하는지 (Why)
서버 백엔드로 인커밍(Incoming)되는 HTTP 요청 수 자체를 줄여 네트워크 및 서버 CPU 자원을 절약하기 위함입니다.

#### ③ 어떨 때 사용하는지 (When)
Redis 같은 인메모리 인프라 구축 비용 없이 단순한 웹 서비스에서 조회수 어뷰징을 방지할 때 사용합니다.

#### ④ 어떻게 사용하는지 (How - 구현 코드)
```typescript
// Next.js 프론트엔드 컴포넌트
useEffect(() => {
  const viewedPosts = getCookie('viewed_posts') || '';
  if (!viewedPosts.includes(`[${postId}]`)) {
    api.post(`/api/posts/${publicId}/views`);
    setCookie('viewed_posts', `${viewedPosts}[${postId}]`, { maxAge: 86400 });
  }
}, [postId]);
```

#### ⑤ 장점 (Pros)
- **서버 요청 수 급감**: 동일 유저의 재방문 요청이 백엔드까지 도착하지 않으므로 트래픽이 획기적으로 줄어듦.

#### ⑥ 다른 기술과의 비교 (Alternatives)
- **서버 IP 기반 차단 대비**: 서버 IP 차단은 서버 메모리를 소비하지만, 쿠키 방식은 클라이언트 브라우저 자원을 활용함.

#### ⑦ 트레이드오프 및 서비스/아키텍처 레벨 극복 방안 (Trade-off & Detailed Mitigation)
- **트레이드오프 (쿠키 삭제 및 시크릿 창 어뷰징에 취약)**: 유저가 브라우저 쿠키를 삭제하거나 시크릿 모드로 접속하면 중복 카운트가 올라감.
- **서비스 레벨 극복 방안**: 어뷰징 유저가 쿠키를 삭제하더라도 개별 유저의 자발적 행위이므로 시스템 전체 셧다운을 일으키지 않는 수준에서 허용.
- **아키텍처 레벨 극복 방안 (하이브리드 IP Redis 쿨타임)**: 백엔드에서 1차로 Client Cookie를 대조하고 2차로 IP 기반 Redis 10분 쿨타임 키(`view:cooldown:{ip}:{postId}`)를 이중 검증하여 쿠키 삭제 어뷰징을 99% 무력화하는 하이브리드 검증 구축.

---

## 3. 💥 [추천 비동기] `@Async` 비동기 카운터 유실 이슈의 2대 대안

### 3-1. 대체 대안 A: Transactional Outbox Pattern (트랜잭셔널 아웃박스 패턴)

#### ① 개념 (What)
이벤트를 인메모리 스프링 이벤트로 던지지 않고, 메인 비즈니스 로직과 동일한 DB 트랜잭션 안에서 `outbox` 테이블에 이벤트 메시지를 함께 `INSERT`한 뒤, 별도의 메시지 릴레이(Debezium CDC 또는 Polling Publisher)가 읽어서 처리하는 Enterprise 분산 트랜잭션 패턴입니다.

#### ② 왜 사용하는지 (Why)
JVM 인메모리 비동기 이벤트는 서버가 갑자기 꺼지면 메모리에 있던 이벤트가 100% 유실됩니다. DB 테이블에 이벤트 발행 내역을 함께 기록하여 **최소 1회 전달(At-Least-Once Delivery)**을 물리적으로 보장하기 위함입니다.

#### ③ 어떨 때 사용하는지 (When)
결제, 결제 후 포인트 적립, 이벤트 카운팅 등 유실되면 안 되는 핵심 비동기 이벤트 처리에 사용합니다.

#### ④ 어떻게 사용하는지 (How - 구현 코드)
```java
@Transactional
public void reactToPost(String publicId, ReactionType type, CustomUserDetails userDetails) {
    // 1. 투표 내역 저장
    reactionRepository.save(reaction);
    
    // 2. 동일 트랜잭션 내에서 outbox 테이블에 이벤트 메시지 저장 (원자성 보장)
    outboxRepository.save(new OutboxEvent(
        "POST_REACTION",
        postId.toString(),
        objectMapper.writeValueAsString(new PostReactionEvent(postId, type))
    ));
}
```

#### ⑤ 장점 (Pros)
- **이벤트 유실 0%**: 메인 데이터 저장과 이벤트 작성이 동일 DB 트랜잭션으로 묶여 원자성(Atomic)이 보장됨.
- **서버 장애 복구**: 서버가 다운된 후 재시작되어도 `outbox` 테이블에 남아있는 미처리 이벤트를 읽어서 복구 처리.

#### ⑥ 다른 기술과의 비교 (Alternatives)
- **스프링 `@Async` 기본 이벤트 대비**: 기본 `@Async`는 메모리 유실 위험이 크지만, Outbox Pattern은 DB 내구성을 이용해 유실을 물리 차단함.

#### ⑦ 트레이드오프 및 서비스/아키텍처 레벨 극복 방안 (Trade-off & Detailed Mitigation)
- **트레이드오프 (Outbox 테이블 비대화 및 추가 DB Write I/O)**: 모든 이벤트가 DB에 기록되므로 I/O 부담이 늘어나고 테이블 용량이 커짐.
- **서비스 레벨 극복 방안**: 비동기 처리가 지연되더라도 유저 화면에는 Optimistic UI로 완료 상태를 즉시 표시.
- **아키텍처 레벨 극복 방안 (Outbox Purge Scheduler)**: `outbox` 테이블에 모든 비동기 이벤트가 누적되어 DB 용량이 폭증하고 I/O가 느려지는 현상을 막기 위해, `status = 'PROCESSED'`이면서 생성된 지 1시간이 지난 Outbox 행을 1,000개 단위로 DELETE하는 `OutboxPurgeScheduler` 배치를 구축하여 테이블 사이즈를 작게 유지.

---

## 3-2. 대체 대안 B: 새벽 정정 스케줄러 배치 (Scheduled Reckoning Batch)

#### ① 개념 (What)
이벤트 유실 가능성을 인정하되, 매일 새벽 트래픽이 적은 시각에 `post_reaction` 테이블의 실제 투표 건수를 `COUNT(*)`로 집계하여 `post.like_count` 수치와 대조 후 다를 경우 일괄 수정하는 정정 배치 기법입니다.

#### ② 왜 사용하는지 (Why)
복잡한 메시지 큐나 Outbox 패턴 구축 비용 없이, 100% 데이터 정합성을 가장 단순한 코드로 보장하기 위함입니다.

#### ③ 어떨 때 사용하는지 (When)
실시간 카운트 반영의 밀리초 오차가 서비스 이용에 치명적이지 않은 커뮤니티 서비스에 적합합니다.

#### ④ 어떻게 사용하는지 (How - 구현 코드)
```java
@Scheduled(cron = "0 0 4 * * *") // 매일 새벽 4시
@Transactional
public void reconcileReactionCounts() {
    List<Post> posts = postRepository.findAll();
    for (Post post : posts) {
        long actualLikes = reactionRepository.countByPostIdAndType(post.getId(), ReactionType.LIKE);
        if (post.getLikeCount() != actualLikes) {
            post.setLikeCount(actualLikes); // 데이터 정정
        }
    }
}
```

#### ⑤ 장점 (Pros)
- **구현 단순성**: 추가 인프라 구축 없이 가장 직관적이고 안정적으로 데이터 일관성을 맞출 수 있음.

#### ⑥ 다른 기술과의 비교 (Alternatives)
- **Outbox Pattern 대비**: Outbox Pattern은 복잡한 릴레이 스레드가 필요하지만, 정정 배치는 단순 SQL 집계로 완료됨.

#### ⑦ 트레이드오프 및 서비스/아키텍처 레벨 극복 방안 (Trade-off & Detailed Mitigation)
- **트레이드오프 (새벽 시간대 DB Read I/O 부하)**: 전수 조사를 돌리면 DB CPU 사용량이 상승함.
- **서비스 레벨 극복 방안**: 새벽 4시는 유저 접속량이 가장 적은 시간대이므로 정정 작업으로 인한 성능 영향을 사용자에게서 격리.
- **아키텍처 레벨 극복 방안 (어제 변경된 게시글 Index Scan 핀포인트)**: 새벽 4시 배치 시 전체 `post` 테이블 Full Scan으로 인한 DB CPU 상승을 막기 위해 `WHERE updated_at >= NOW() - INTERVAL 1 DAY` 조건절을 추가하여 전날 변경이 일어난 게시글만 Index Scan으로 핀포인트 정정.

---

## 4. 💥 [대댓글] 3차 이상 무한 깊이 대댓글 이슈의 2대 대안

### 4-1. 대체 대안 A: Flat List + `@Mention` (유튜브 / 인스타그램 1차 평탄화 모델)

#### ① 개념 (What)
대댓글의 계층형 들여쓰기 자체를 없애고 모든 답글을 원댓글 하위의 평탄한(Flat) 1차 리스트로만 렌더링하며, 누구에게 작성한 답글인지 `@작성자닉네임` 태그로 표시하는 UI/UX 아키텍처입니다.

#### ② 왜 사용하는지 (Why)
모바일 화면 폭(360px~430px)은 들여쓰기를 3단계만 해도 본문 영역이 좁아져 읽기 불가능해집니다. 이를 근본적으로 해결하기 위함입니다.

#### ③ 어떨 때 사용하는지 (When)
유튜브, 인스타그램, 페이스북 등 모바일 웹/앱 트래픽 비중이 80% 이상인 현대 웹 서비스에 사용합니다.

#### ④ 어떻게 사용하는지 (How - 구현 코드)
```json
// JSON 반환 구조
{
  "commentId": 12,
  "content": "@댓글보더 저도 그렇게 생각합니다!",
  "targetMemberNickname": "댓글보더",
  "parentId": 1, // 최상위 원댓글 ID만 유지
  "depth": 1
}
```

#### ⑤ 장점 (Pros)
- **UI 레이아웃 파괴 근본 차단**: 들여쓰기 너비가 0으로 고정되므로 아무리 답글이 많이 달려도 모바일 화면 레이아웃이 절대 깨지지 않음.
- **데이터 구조 단순화**: N차 복잡한 트리를 조립할 필요 없이 1차 리스트만 반환하므로 백엔드 연산이 가벼워짐.

#### ⑥ 다른 기술과의 비교 (Alternatives)
- **N차 계층형 트리 대비**: N차 계층형 트리는 복잡한 Recursion 조립과 UI 들여쓰기가 필요하지만, Flat List는 $O(N)$ 단일 루프 반환 가능.

#### ⑦ 트레이드오프 및 서비스/아키텍처 레벨 극복 방안 (Trade-off & Detailed Mitigation)
- **트레이드오프 (답글의 구체적 부모 맥락 추적 불분명)**: 누구의 대댓글에 대한 대댓글인지 1:1 스레드 흐름 파악이 계층형보다 다소 모호함.
- **서비스 레벨 극복 방안 (Tooltip 미니 모달 뷰어)**: 1:1 대댓글 스레드 맥락 추적이 모호해지는 단점을 해결하기 위해, `@작성자닉네임` 태그 클릭 시 해당 원본 댓글의 팝업 미니 모달(Tooltip Modal)이 뜨도록 프론트엔드 뷰 연동.
- **아키텍처 레벨 극복 방안**: `targetCommentId` 외래키를 DTO에 포함하여 단 1회 인메모리 Map 조회로 원본 댓글 본문을 즉시 팝업으로 렌더링.

---

### 4-2. 대체 대안 B: CSS Max-Indent Clamp (프론트엔드 들여쓰기 한계선 고정)

#### ① 개념 (What)
백엔드는 데이터베이스 상에서 N차 대댓글을 허용하되, 프론트엔드 CSS 렌더링 시 `margin-left` 들여쓰기의 최대 한계선을 `clamp` 또는 `min()` 함수로 고정하는 기법입니다.

#### ② 왜 사용하는지 (Why)
백엔드 도메인 로직 수정 없이 프론트엔드 스타일시트 적용만으로 레이아웃 이탈을 막기 위함입니다.

#### ③ 어떨 때 사용하는지 (When)
기존 백엔드 API 스펙을 건드리지 않고 빠르게 UI 깨짐을 임시 방어할 때 사용합니다.

#### ④ 어떻게 사용하는지 (How - 구현 코드)
```tsx
// Tailwind / Inline Style 적용
<div style={{ marginLeft: `${Math.min(depth, 3) * 1.5}rem` }}>
  {comment.content}
</div>
```

#### ⑤ 장점 (Pros)
- **백엔드 수정 0건**: 백엔드 코드를 단 한 줄도 수정하지 않고 프론트엔드 뷰만으로 1초 만에 방어할 수 있음.

#### ⑥ 다른 기술과의 비교 (Alternatives)
- **백엔드 Depth Validation 대비**: 백엔드 검증은 400 에러를 반환하지만, CSS Clamp는 에러 없이 렌더링 위치만 고정함.

#### ⑦ 트레이드오프 및 서비스/아키텍처 레벨 극복 방안 (Trade-off & Detailed Mitigation)
- **트레이드오프 (3차 이상 대댓글 간 시각적 구분 모호)**: 3차 대댓글과 4차, 5차 대댓글의 들여쓰기 위치가 동일해져 계층 구분이 안 됨.
- **서비스 레벨 극복 방안 (답글 뱃지 렌더링)**: 3차 이상 대댓글의 들여쓰기 위치가 같아져 시각적 계층이 모호해지는 점을 보완하기 위해, `depth > 2`인 경우 댓글 상단에 `↳ [3차 답글]` 태그 뱃지(Badge)를 추가 렌더링.
- **아키텍처 레벨 극복 방안**: 백엔드 2단계 깊이 제한Validation(`parent.getParent() != null`)을 병행 적용하여 3차 이상 생성 자체를 예외 차단.

---

## 5. 💥 [보안] 익명 비밀번호 URL 쿼리 스트링 노출 이슈의 2대 대안

### 5-1. 대체 대안 A: HTTP Custom Header (`X-Anonymous-Password`) 전달

#### ① 개념 (What)
비회원 비밀번호를 URL 쿼리 스트링이 아닌, HTTP 요청 헤더(`X-Anonymous-Password: 1234`)에 포함하여 전달하는 보안 패턴입니다.

#### ② 왜 사용하는지 (Why)
웹 서버(Nginx, Apache)는 표준 보안 설정상 Request Body와 Custom Header 내용을 Access Log에 기록하지 않고 요청 라인(URL)만 기록하므로, 로그 유출을 물리 차단할 수 있습니다.

#### ③ 어떨 때 사용하는지 (When)
RESTful API 관례상 `DELETE` 메서드에 Request Body를 실어 보내기 부담스러울 때 사용합니다.

#### ④ 어떻게 사용하는지 (How - 구현 코드)
```java
@DeleteMapping("/api/posts/{publicId}")
public ResponseEntity<Void> deletePost(
    @PathVariable String publicId,
    @RequestHeader(value = "X-Anonymous-Password", required = false) String anonymousPassword) {
    postService.deletePost(publicId, anonymousPassword);
    return ResponseEntity.noContent().build();
}
```

#### ⑤ 장점 (Pros)
- **웹 서버 로그 유출 100% 차단**: Nginx, ALB, CDN 액세스 로그에 비밀번호 평문 기록이 남지 않음.
- **HTTP 스펙 준수**: `DELETE` 메서드 본문(Body)을 비워두어 일부 엄격한 HTTP 클라이언트 라이브러리와의 호환성 유지.

#### ⑥ 다른 기술과의 비교 (Alternatives)
- **URL Query Parameter 대비**: URL Parameter는 웹 서버 로그에 평문 기록되지만, Custom Header는 기록되지 않아 보안상 우월함.

#### ⑦ 트레이드오프 및 서비스/아키텍처 레벨 극복 방안 (Trade-off & Detailed Mitigation)
- **트레이드오프 (CORS Preflight Flight 요청 발생)**: 표준 헤더가 아닌 커스텀 헤더(`X-`)를 사용하므로 브라우저가 `OPTIONS` 사전 요청(Preflight)을 보냄.
- **서비스 레벨 극복 방안**: 첫 요청 시 수 밀리초의 Preflight 지연이 발생하지만 지연 시간이 매우 짧으므로 삭제 성공 경험 우선 제공.
- **아키텍처 레벨 극복 방안 (Preflight Caching)**: 브라우저의 CORS Preflight(`OPTIONS`) 요청으로 인한 2배 HTTP 트래픽 발생을 극복하기 위해, Spring Security CORS Configuration에서 `allowedHeaders("X-Anonymous-Password")` 등록 및 `maxAge(3600)`을 지정하여 브라우저가 Preflight 결과를 1시간 동안 메모리에 캐싱하도록 설정.

---

### 5-2. 대체 대안 B: HMAC-SHA256 기반 무상태 일회용 삭제 토큰 (Stateless Delete Token)

#### ① 개념 (What)
비회원이 글 작성 시 백엔드가 비밀번호를 저장하지 않고, `HMAC-SHA256(publicId + secretKey + password)`로 암호화 서명된 일회용 삭제 토큰(Token)을 발급하여 유저에게 반환하는 무상태 보안 인증 패턴입니다.

#### ② 왜 사용하는지 (Why)
비밀번호 원본 및 BCrypt 해시조차 DB에 저장하지 않아, DB가 뚫려도 비회원 비밀번호가 유출될 위험이 0%입니다.

#### ③ 어떨 때 사용하는지 (When)
익명 게시판 보안 수준을 극상으로 끌어올리고 무상태(Stateless) 검증을 꾀할 때 사용합니다.

#### ④ 어떻게 사용하는지 (How - 구현 코드)
```java
// 작성 시 토큰 발급
public String generateDeleteToken(String publicId, String rawPassword) {
    return HmacUtils.hmacSha256Hex(SECRET_KEY, publicId + ":" + rawPassword);
}

// 삭제 시 토큰 대조 검증
public void validateDeleteToken(String publicId, String rawPassword, String clientToken) {
    String expectedToken = generateDeleteToken(publicId, rawPassword);
    if (!expectedToken.equals(clientToken)) {
        throw new CustomAuthException(ErrorCode.INVALID_ANON_PASSWORD);
    }
}
```

#### ⑤ 장점 (Pros)
- **DB 보안 극상**: DB에 비밀번호 컬럼 자체가 존재하지 않으므로 데이터베이스 유출 사고 시에도 안전함.

#### ⑥ 다른 기술과의 비교 (Alternatives)
- **BCrypt DB 저장 대비**: BCrypt 저장은 DB 용량을 차지하고 딕셔너리 공격 대상이 될 수 있으나, HMAC 토큰은 DB 저장이 필요 없는 무상태 검증임.

#### ⑦ 트레이드오프 및 서비스/아키텍처 레벨 극복 방안 (Trade-off & Detailed Mitigation)
- **트레이드오프 (서버 Secret Key 유출 시 서명 위조 위험)**: 애플리케이션의 `SECRET_KEY`가 유출되면 토큰 위조가 가능해짐.
- **서비스 레벨 극복 방안**: 토큰 생성 알고리즘이 노출되지 않도록 에러 메시지 캡슐화.
- **아키텍처 레벨 극복 방안 (AWS Secrets Manager & Key Rotation)**: 서버 `SECRET_KEY` 유출 시 토큰 서명 위조 위험을 물리적으로 극복하기 위해, `SECRET_KEY`를 코드나 설정 파일에 하드코딩하지 않고 `AWS Secrets Manager`에 저장하며 30일마다 자동으로 서명 키를 로테이션(Rotation)하고 구버전 키는 7일간 Grace Period를 두어 안전 검증.

---

# 📌 PART 3. 작업 완료 및 파일 위치 안내

* **생성된 마스터 스터디 가이드 경로**:
  - [`c:\Users\ikaes\IdeaProjects\snowthing\docs\study\sprint02\studySprint02BoardIssuesAndSolutions260821.md`](file:///c:/Users/ikaes/IdeaProjects/snowthing/docs/study/sprint02/studySprint02BoardIssuesAndSolutions260821.md)
  - [`c:\Users\ikaes\IdeaProjects\snowthing\docs\study\studySprint02BoardIssuesAndSolutions260821.md`](file:///c:/Users/ikaes/IdeaProjects/snowthing/docs/study/studySprint02BoardIssuesAndSolutions260821.md)
* **`AGENTS.md` 작업 기록 완료**: [`docs/project/work.md`](file:///c:/Users/ikaes/IdeaProjects/snowthing/docs/project/work.md) 파일에 수록 완료.
