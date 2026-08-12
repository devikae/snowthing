# 🏔️ Snowthing (스노우띵)

> **전국 스키장 정보와 라이딩 경험을 나누고, 함께 탈 유저를 찾는 윈터스포츠 전용 커뮤니티 플랫폼**

---

## 📌 1. 프로젝트 개요 (Overview)

* **서비스명**: Snowthing (눈팅)
* **목적**: 기존 노후화된 윈터스포츠 커뮤니티의 불편함(http 접속, 보안 취약, 이미지 첨부 오류 등)을 극복하고, 스노보더/스키어를 위한 커뮤니티 플랫폼을 제공합니다.
* **1차 MVP 핵심 타겟**:
  1. **숙련 이용자 (시즌 보더)**: 실시간 설질/현장 정보 공유, 프로필 N:M 중계(베이스 스키장/라이딩 성향) 등록
  2. **초보 이용자 (입문 보더)**: 부담 없는 비회원 익명 작성 및 정제된 Q&A 지식 탐색

---

## 🛠️ 2. 기술 스택 (Tech Stack)

### Frontend
* **Core**: Next.js 14+ (App Router), TypeScript, React 18
* **State & Fetching**: React Query, Axios
* **Styling**: Vanilla CSS, TailwindCSS

### Backend
* **Core**: Java 17, Spring Boot 3.x, Spring Data JPA
* **Security & Auth**: Spring Security, Spring Session Redis, BCrypt
* **Build & Test**: Gradle, JUnit5, Mockito, Postman Runner

### Database & Cache
* **RDBMS**: MySQL 8.0 (InnoDB Engine, Docker Container)
* **In-Memory Cache**: Redis 7.x (Spring Session, Write-Behind Buffer, Voter Set)

### Infrastructure & DevOps
* **Container**: Docker, Docker Compose
* **Proxy**: Nginx (Reverse Proxy, SSL Termination)

---

## 📂 3. 프로젝트 물리 디렉토리 구조 (Project Structure)

```
snowthing/ (프로젝트 최상위 루트)
├── backend/                  # Spring Boot 3.x 백엔드 애플리케이션
│   ├── src/
│   └── build.gradle
├── frontend/                 # Next.js 14+ 프론트엔드 애플리케이션
│   ├── src/
│   └── package.json
├── database/                 # DB 초기화 및 DDL 관리 폴더
│   └── ddl.sql               # 테이블 DDL (UUID v7, N:M 대리키 id 등)
├── docs/                     # 기획, 아키텍처, ERD, API 명세 문서
│   ├── conception/           # 기획 및 아키텍처 명세서 (sprint01/)
│   └── project/              # work.md 작업 이력 관리
└── docker-compose.yml        # MySQL 8.0 컨테이너 및 initdb 자동 마운트
```

---

## 🏛️ 4. 핵심 아키텍처 설계 특징 (Architecture Highlights)

1. **PK dual 전략 (내부성능 vs 외부보안)**:
   * DB 내부 조인/클러스터드 인덱스용: 8바이트 정수 `BIGINT AUTO_INCREMENT id` (100% Append-Only)
   * 외부 API/URL 노출용: `public_id` **`UUID v7 (Time-ordered Epoch)`** 세컨더리 인덱스 파편화(Page Split) 방지
2. **댓글 계층형 N+1 회피전략 (Single Query + In-Memory Tree)**:
   * `WHERE post_id = :postId` 단 1번의 JPQL DTO 직접 조회로 한번에 조회 ➔ 서버 메모리에서 `HashMap` $O(1)$ 포인터 계층 파싱 (`children: []`)
3. **6대 쿠키 보안 속성 & 세션 고정 방어**:
   * `HttpOnly`, `Secure`, `SameSite=Lax`, `Path=/api`, `Domain`, `No PII` 6대 쿠키 속성
   * 로그인 성공 시 `request.changeSessionId()` 호출하여 세션 고정 공격 차단
4. **N:M 중계 테이블 식별자 전략**:
   * `member_resort`, `member_riding_style` 중계 테이블에 복합키 대신 단일 대리키 `id` PK + `UNIQUE KEY`를 두어 JPA 지연 로딩 및 식별자 복잡성 완화

---

## 🚀 5. 아키텍처 의사결정 비하인드 (Architecture Decision Rationale)

> 💡 **왜 Redis 도입이 무조건 필수적이었는가? (세션 & DB 락 & 멱등성 & Scale-out)**

### 1. DB 락(Lock)을 잡는 방식의 치명적 한계와 Write-Behind 도입
* 처음에는 단순하게 DB 테이블에 `comment_count`, `like_count`, `view_count`를 두고 원자적 쿼리(`UPDATE post SET count = count + 1`)나 DB 비관적 락(Pessimistic Lock)을 걸어 처리하려 했습니다.
* 하지만 수많은 유저가 동시에 추천을 누르거나 댓글을 달 때, DB 메모리의 `Lock Wait Queue` 대기 줄이 길어지면서 Spring의 DB 커넥션 풀 고갈로 서비스 전체가 마비 가능성이 있다는것을 파악했습니다.
* 또한 조회수/추천수/댓글수의 멱등성(Idempotency - 중복 요청 시 카운트 정합성 수호)과 가용성 보장을 위해, DB 락을 잡는 방식이 아닌 **`Redis Write-Behind 패턴`** (Redis `INCR`/`SADD` 메모리 처리 ➔ 10초 주기 DB 배치 일괄 UPDATE)을 채택하게 되었습니다.

### 2. 서버 Scale-out 확장을 위한 Redis 세션 저장소 (Spring Session Redis)의 필연성
* 위에서 언급한 조회수 같은 부분의 멱등성과 DB 부하 감소를 위해 Redis 인프라를 도입해야 한다면, **세션 저장소 역시 DB나 단일 서버 RAM 메모리가 아닌 Redis로 통합 관리하는 것이 유리**하다고 판단했습니다.
* DB에 `SPRING_SESSION` 테이블을 만들어 세션을 저장하면 유저 요청 세션 테이블을 조회해야 하다면
* 인메모리인 **`Spring Session Redis`**를 사용하여 `spring:session:sessions:JSESSIONID` 키로 세션을 검증함으로써, **DB I/O 부하 낮추고, 동시에 분산 확장(Scale-out)해도 세션이 끊기지 않는 인메모리 세션중앙저장 방식을 채택했습니다.
