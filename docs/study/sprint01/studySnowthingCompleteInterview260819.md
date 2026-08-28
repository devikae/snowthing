# 🏔️ [Single Pure-Backend Master Guide] Snowthing 백엔드·MySQL 8 인덱스·JPA 10대 기술 딥다이브·50대 면접 대본 집대성 (2026-08-19)

> **노션(Notion) 복사 전용 - 프론트엔드 내용 제거 / 순수 백엔드 & MySQL 8 전용 마스터 가이드**  
> 본 문서는 Snowthing 프로젝트의 백엔드 설계 (`build.gradle` 명세: Spring Boot 4.0.0, Spring Dependency Management 1.1.6, Java 21, Spring Security 7), MySQL 8.0 인덱스(Clustered/Secondary/Composite Index) 물리적 구조, 10대 백엔드 기술 7대 서술 체계 해설, REST API 명세, DDL 제약조건, 그리고 **순수 백엔드 9대 영역 총 50개 면접 질문 및 꼬리 질문 답변 대본**을 중복 없이 정확한 엔지니어링 근거를 바탕으로 정리한 단 하나의 마스터 파일입니다.

---

# 📑 CHAPTER 1. Snowthing 백엔드 아키텍처 & MySQL 8.0 DB 인프라

## 1.1 시스템 모노레포 구조 (Pure Backend)
```text
snowthing/
└── backend/               # Spring Boot 4.0.0 (Java 21, Spring Security 7, MySQL 8.0)
    ├── build.gradle       # org.springframework.boot:4.0.0, io.spring.dependency-management:1.1.6
    ├── src/main/java/com/ikae/snowthing/
    │   ├── domain/auth/   # 인증/로그인 (Controller, Service, DTO)
    │   ├── domain/member/ # 회원/프로필/마스터데이터 (Controller, Service, Repository, Entity)
    │   └── global/        # SecurityConfig, GlobalExceptionHandler, BaseTimeEntity
    └── src/test/java/     # 25개 통합/단위 테스트 수트
```

## 1.2 서블릿 컨테이너(Tomcat) & Security Filter Chain 데이터 흐름

```text
[HTTP Client Request]
       │ (Cookie: JSESSIONID=32자리랜덤키)
       ▼
[Tomcat Servlet Container]
       │ DelegatingFilterProxy ➔ FilterChainProxy
       ▼
[Spring Security Filter Chain]
       │ 1. CorsFilter (http://localhost:3000 검증)
       │ 2. LogoutFilter (/api/auth/logout 감지 시 세션 무효화 및 쿠키 파기)
       │ 3. SecurityContextHolderFilter (HttpSession에서 SecurityContext 읽어 ThreadLocal 복원)
       │ 4. ExceptionTranslationFilter (401/403 예외 감지)
       │ 5. AuthorizationFilter (URL 및 Role 권한 검증)
       ▼
[Controller Layer (AuthController, MemberController)]
       │ @Valid DTO 파라미터 1차 입력값 검증 ➔ Service 호출
       ▼
[Service Layer (AuthService, MemberService)]
       │ @Transactional 트랜잭션 경계 ➔ BCrypt 검증 ➔ 비즈니스 규칙 처리
       ▼
[Repository Layer (MemberRepository)]
       │ Spring Data JPA & JPQL JOIN FETCH ➔ MySQL 8.0 InnoDB DB 쿼리 실행
```

---

# 📑 CHAPTER 2. MySQL 8.0 DB 인덱스(INDEX) 물리적 설계 & DDL 명세

## 2.1 MySQL 8.0 InnoDB 인덱스 물리적 구조 & 걸려있는 이유

MySQL 8.0 InnoDB 엔진에서는 **클러스터드 인덱스(Clustered Index)**와 **세컨더리 인덱스(Secondary Index)** B-Tree 구조로 인덱스가 관리됩니다.

### 1. `member` 회원 테이블 인덱스 구조
* **`PRIMARY KEY (member_id)` (Clustered Index / BIGINT 8바이트 정수)**:
  - **이유**: InnoDB 데이터 레코드가 `member_id` 순서대로 물리적 디스크 블록에 정렬 저장됩니다. DB 내부 JOIN 연산 시 B-Tree 이진 탐색을 통해 최상의 조인 속도를 제공합니다.
* **`UNIQUE INDEX uk_member_public_id (public_id)` (Secondary Index / VARCHAR(36) UUID)**:
  - **이유**: 외부 REST API URL (`GET /api/members/me` 등)로 유저 단건 조회 시 `public_id` B-Tree 인덱스를 통해 O(1) 수준으로 리프 노드 주소를 탐색합니다.
* **`UNIQUE INDEX uk_member_email (email)` (Secondary Index / VARCHAR(255))**:
  - **이유**: 로그인 시 이메일로 유저를 빠르게 검색하고, **동시성 환경 이메일 중복 가입 락(Race Condition)을 방어**합니다.
* **`UNIQUE INDEX uk_member_nickname (nickname)` (Secondary Index / VARCHAR(100))**:
  - **이유**: 회원가입 및 프로필 수정 시 닉네임 중복 검사 쿼리 속도를 최적화합니다.

### 2. `member_resort` & `member_riding_style` N:M 중계 테이블 인덱스 구조
* **`PRIMARY KEY (member_resort_id)` (Clustered Index)**: 단일 대리키 PK.
* **`UNIQUE INDEX uk_member_resort (member_id, resort_id)` (Composite Secondary Index / 복합 인덱스)**:
  - **이유**: 1. 동일 회원이 동일한 스키장을 중복 등록하는 무결성 파손을 차단합니다. 2. `member_id` 선두 컬럼 기반 복합 인덱스이므로 `WHERE member_id = ?` 조회 시 별도 인덱스 생성 없이 인덱스 레인지 스캔(Index Range Scan)으로 즉시 조회합니다.

## 2.2 MySQL 8.0 DDL 문법

```sql
-- 1. 회원 마스터 테이블 (member)
CREATE TABLE member (
    member_id BIGINT AUTO_INCREMENT PRIMARY KEY,          -- [Clustered Index PK]
    public_id VARCHAR(36) NOT NULL,                       -- [Secondary Unique Index]
    email VARCHAR(255) NOT NULL,                          -- [Secondary Unique Index]
    password VARCHAR(255) NOT NULL,                       -- [NOT NULL] BCrypt 60자리 해시
    nickname VARCHAR(100) NOT NULL,                       -- [Secondary Unique Index]
    role VARCHAR(20) NOT NULL DEFAULT 'ROLE_USER',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME(6) NOT NULL,                      -- JPA Auditing
    updated_at DATETIME(6) NOT NULL,                      -- JPA Auditing
    CONSTRAINT uk_member_public_id UNIQUE (public_id),
    CONSTRAINT uk_member_email UNIQUE (email),
    CONSTRAINT uk_member_nickname UNIQUE (nickname)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 2. N:M 선호 스키장 중계 테이블 (member_resort)
CREATE TABLE member_resort (
    member_resort_id BIGINT AUTO_INCREMENT PRIMARY KEY,  -- [Clustered Index PK]
    member_id BIGINT NOT NULL,                           -- [FK]
    resort_id BIGINT NOT NULL,                           -- [FK]
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_member_resort_member FOREIGN KEY (member_id) REFERENCES member(member_id) ON DELETE CASCADE,
    CONSTRAINT fk_member_resort_resort FOREIGN KEY (resort_id) REFERENCES resort(resort_id) ON DELETE CASCADE,
    CONSTRAINT uk_member_resort UNIQUE (member_id, resort_id) -- [Composite Secondary Index]
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
```

---

# 📑 CHAPTER 3. 백엔드 10대 핵심 기술 7대 서술 요소 체계 해설 (7 Core Elements)

AGENTS.md 규칙 21에 의거하여 백엔드에 사용된 10대 핵심 기술을 7대 서술 체계로 명확히 해설합니다.

---

## 3.1 Bean Validation (`@Valid`, `@NotBlank`, `@Email`) 입력값 검증

### ① 개념 (명확한 정의)
자바 표준 스펙(JSR-380 / Hibernate Validator) 어노테이션을 통해 Controller 계층으로 들어오는 DTO 파라미터의 유효성을 도메인 로직 진입 전에 1차적으로 검증하는 기술입니다.

### ② 왜 사용하는지 (Why - 도입 목적)
잘못된 데이터(공백 이메일, 8자리 미만 비밀번호)가 DB나 Service 레이어로 침투하여 비즈니스 로직 에러를 일으키는 것을 Controller 입구에서 차단하기 위함입니다.

### ③ 어떨 때 사용하는지 (When - 사용 상황)
회원가입(`MemberSignUpRequest`), 로그인(`MemberLoginRequest`), 프로필 수정(`MemberProfileUpdateRequest`) 등 외부 입력값을 수신하는 모든 `@PostMapping`, `@PutMapping` DTO 파라미터 선언 시 적용합니다.

### ④ 어떻게 사용하는지 (How - 구체적 구현 예시)
```java
public record MemberSignUpRequest(
        @NotBlank(message = "이메일은 필수 입력값입니다.")
        @Email(message = "올바른 이메일 형식이 아닙니다.")
        String email,

        @NotBlank(message = "비밀번호는 필수 입력값입니다.")
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[@$!%*#?&])[A-Za-z\\d@$!%*#?&]{8,20}$", 
                 message = "비밀번호는 8~20자 영문, 숫자, 특수문자를 포함해야 합니다.")
        String password,

        @NotBlank(message = "닉네임은 필수 입력값입니다.")
        @Size(min = 2, max = 10, message = "닉네임은 2~10자 이내여야 합니다.")
        String nickname
) {}

// Controller 적용
@PostMapping
public ResponseEntity<MemberSignUpResponse> signUp(@Valid @RequestBody MemberSignUpRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED).body(memberService.signUp(request));
}
```

### ⑤ 장점은 무엇인지 (Pros)
1. Controller 내부의 보일러플레이트 검증 코드가 제거됩니다.
2. 검증 실패 시 `MethodArgumentNotValidException`이 발생하여 `GlobalExceptionHandler`에서 `400 Bad Request` JSON 응답으로 일관되게 변환 가능합니다.

### ⑥ 다른 기술/대안은 무엇이 있는지 (Alternatives)
* **Service 내 수동 `if` 검증**: 개발자가 일일이 자바 코드로 검증하는 방식으로 코드 중복과 가독성 저하가 발생합니다.
* **DB Constraints에만 의존**: DB 쿼리가 실행된 후에 에러가 발생하여 서버 자원이 낭비됩니다.

### ⑦ 트레이드오프 및 극복 방안 (Trade-off & Mitigation)
* **트레이드오프**: `@Valid` 어노테이션 누락 시 검증이 동작하지 않고 통과하는 위험이 존재합니다.
* **극복 방안**: Controller 단위 테스트 수트에서 바인딩 예외 발생 여부를 명확히 테스트 검증합니다.

---

## 3.2 JPA / Spring Data JPA 영속성 메커니즘

### ① 개념 (명확한 정의)
자바 객체(Entity)와 RDBMS 테이블을 매핑해 주는 ORM(Object-Relational Mapping) 표준 기술로, 영속성 컨텍스트 1차 캐시, 변경 감지(Dirty Checking), 지연 로딩(Lazy Loading)을 통해 데이터베이스를 자바 객체처럼 다루게 해줍니다.

### ② 왜 사용하는지 (Why - 도입 목적)
SQL CRUD 작성을 자동화하고, 1차 캐시와 Dirty Checking을 통해 DB 커넥션 및 쿼리 실행을 최적화하기 위함입니다.

### ③ 어떨 때 사용하는지 (When - 사용 상황)
백엔드 데이터베이스의 모든 C.R.U.D 데이터 조작 및 객체 그래프 탐색 시 사용합니다.

### ④ 어떻게 사용하는지 (How - 구체적 구현 예시)
```java
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberService {
    private final MemberRepository memberRepository;

    @Transactional // 👈 Dirty Checking을 위한 트랜잭션 경계
    public void updateProfile(String email, String newNickname) {
        Member member = memberRepository.findByEmail(email).orElseThrow();
        member.updateNickname(newNickname); // 👈 수동 save() 없이 Dirty Checking으로 UPDATE SQL 자동 실행!
    }
}
```

### ⑤ 장점은 무엇인지 (Pros)
1. Dirty Checking을 통해 수동 UPDATE 쿼리 생략이 가능합니다.
2. 1차 캐시 메모리 조회를 통해 동일 트랜잭션 내 DB 조회 쿼리가 생략됩니다.

### ⑥ 다른 기술/대안은 무엇이 있는지 (Alternatives)
* **MyBatis / JdbcTemplate**: SQLMapper 방식으로 객체 지향적 접근이 불가능하며 N+1 수동 해결이 필요합니다.

### ⑦ 트레이드오프 및 극복 방안 (Trade-off & Mitigation)
* **트레이드오프**: N+1 쿼리 문제 및 `MultipleBagFetchException` 발생 위험이 있습니다.
* **극복 방안**: `FetchType.LAZY`로 설정하고 필요 시 3회 개별 `JOIN FETCH` 쿼리로 조회합니다.

---

## 3.3 세션 고정 방어 (`changeSessionId()`)
* **개념**: 로그인 성공 시 세션 데이터는 유지하면서 세션 ID만 무작위 새 32자리 문자열로 재발급하는 보안 메커니즘.
* **Why**: 공격자가 비회원 세션 ID를 유저에게 쥐여준 뒤 로그인 시 계정을 탈취하는 **세션 고정 공격을 차단**.
* **When**: 로그인 성공 직후 실행.
* **How**: `httpRequest.changeSessionId()` 호출.
* **Pros**: 세션 탈취 차단 및 기존 장바구니/임시 세션 데이터 유지.
* **Alternatives**: `newSession()`(데이터 소실), `none()`(보안 무방비).
* **Trade-off & Mitigation**: 세션 맵 재갱신 오버헤드가 있으나 로그인 시 단 1회 실행되어 성능 영향이 미미합니다.

---

## 3.4 이중 식별자 전략 (Dual PK: BIGINT AUTO_INCREMENT + public_id UUID)
* **개념**: DB 내부 조인용으로는 `id BIGINT`를 사용하고, API URL 외부 노출용으로는 `public_id UUID`를 분리 적용하는 아키텍처.
* **Why**: **API 보안성**(ID 추측 공격 차단)과 **DB 조인 성능**(8바이트 정수 B-Tree 연산)을 동시 달성.
* **When**: REST API URL에 식별자가 노출되는 모든 엔티티 설계 시.
* **How**: `@PrePersist`로 UUID 자동 생성 및 `findByPublicId()` 조회.
* **Pros**: 개인정보 무단 스크랩 차단, 서비스 가입자 수 지표 유출 방지, 최상 조인 속도.
* **Alternatives**: UUID 단일 PK(인덱스 크기 증가, Page Split 디스크 I/O 급증), TSID.
* **Trade-off & Mitigation**: DB 용량이 소량 증가하므로 `public_id`에 UNIQUE INDEX를 걸어 O(1) 조회를 보장합니다.

---

## 3.5 JPA Auditing (`BaseTimeEntity`) 기반 자동 시간 주입
* **개념**: 엔티티 생성/수정 시 시간을 JPA 라이프사이클 이벤트로 자동 주입하는 기술.
* **Why**: DB `sysdate` 사용 시 발생하는 **1차 캐시 메모리 엔티티 `createdAt=null` 데이터 불일치 방지**.
* **When**: 모든 엔티티 공통 추상 클래스 작성 시.
* **How**: `@MappedSuperclass`, `@EntityListeners(AuditingEntityListener.class)`.
* **Pros**: `save()` 즉시 1차 캐시 엔티티에 시간이 반영되어 데이터 무결성 보장.
* **Alternatives**: DB `DEFAULT NOW()`(1차 캐시 null 남음), 수동 `LocalDateTime.now()`(코드 중복).
* **Trade-off & Mitigation**: 메인 클래스 어노테이션 누락 위험이 있으므로 별도 `@Configuration`으로 분리 관리합니다.

---

## 3.6 다중 1:N 조인 시 `MultipleBagFetchException` 및 3회 `JOIN FETCH` 최적화
* **개념**: 다중 일대다 List 컬렉션 동시 `JOIN FETCH` 시 발생하는 예외와, 이를 피하기 위한 3회 개별 `JOIN FETCH` 분리 조회 기법.
* **Why**: N+1 쿼리 폭발을 막으면서 서버 다운을 차단.
* **When**: 한 엔티티가 2개 이상의 N:M 중계 컬렉션을 가지고 한 번에 조회해야 할 때.
* **How**: `findAllByMemberIdWithResort`, `findAllByMemberIdWithRidingStyle` 2개 JPQL 분리 작성.
* **Pros**: 회원 수가 10,000명이어도 프로필 조회 쿼리는 항상 고정 3회만 실행.
* **Alternatives**: `Set` 사용(순서 미보장, 카테시안 곱 중복 오버헤드), `default_batch_fetch_size`.
* **Trade-off & Mitigation**: 단 1회가 아닌 3회 SQL이 실행되지만 N+1(101회) 대비 네트워크 커넥션 비용이 대폭 절감됩니다.

---

## 3.7 BCrypt Password Encoder (Salt & Work Factor=10)
* **개념**: Blowfish 암호 기반의 단방향 해시 함수로 솔트(Salt)와 피트니스 비용(Work Factor)을 적용한 비밀번호 암호화 기술.
* **Why**: DB 해킹 시에도 원본 비밀번호 복호화를 불가능하게 하고 무차별 대입 공격을 물리적으로 지연시키기 위함.
* **When**: 회원가입 시 비밀번호 암호화 및 로그인 시 비밀번호 검증 시.
* **How**: `passwordEncoder.encode(rawPassword)`, `passwordEncoder.matches(raw, encoded)`.
* **Pros**: 솔트 무작위 추가로 무지개 테이블 사전 공격 차단.
* **Alternatives**: SHA-256 (연산 속도가 빨라 무차별 대입 공격에 취약함).
* **Trade-off & Mitigation**: Cost=10 적용 시 1,024회 연산으로 CPU 소모가 발생하지만 로그인 시 단 1회 실행되어 적절한 타협점입니다.

---

## 3.8 Spring Transactional (`@Transactional(readOnly = true)`)
* **개념**: 선언적 트랜잭션 관리 어노테이션으로, 메서드 시작 시 DB 커넥션을 획득하고 정상 종료 시 `commit()`, 예외 발생 시 `rollback()`을 자동 수행합니다.
* **Why**: 수동 `commit/rollback` 코드를 없애고 `readOnly = true` 설정으로 JPA 영속성 컨텍스트의 스냅샷 생성을 생략하여 메모리를 최적화하기 위함.
* **When**: 서비스 레이어의 모든 읽기/쓰기 메서드에 적용.
* **How**: 클래스 상단에 `@Transactional(readOnly = true)` 선언 후, C.U.D 메서드에만 `@Transactional` 덮어쓰기.
* **Pros**: JPA 하이버네이트 스냅샷 미생성으로 메모리 절약 및 쿼리 플러시 오버헤드 방지.
* **Alternatives**: 수동 `TransactionTemplate` (코드 복잡).
* **Trade-off & Mitigation**: 트랜잭션 내부에서 외부 API 호출 시 DB 커넥션을 오래 쥐고 있는 현상이 발생하므로 외부 API 호출은 트랜잭션 밖으로 분리합니다.

---

## 3.9 Jackson JSON Serializer & Java 17 `record` DTO
* **개념**: Java 17 불변 데이터 객체인 `record`와 Jackson 라이브러리를 통해 자바 객체와 JSON 텍스트 간 변환을 수행하는 기술.
* **Why**: DTO 객체의 Thread-Safety 불변성을 보장하고 보일러플레이트 코드를 줄이기 위함.
* **When**: API Request/Response DTO 정의 시.
* **How**: `public record ResortResponse(Long id, String name, String regionName) {}`.
* **Pros**: Getter, equals, hashCode 자동 생성 및 안전한 직렬화.
* **Alternatives**: Lombok `@Getter`/`@AllArgsConstructor` 클래스 (가변성 위험).
* **Trade-off & Mitigation**: Java 17 이상에서는 Jackson이 record를 기본 공식 지원합니다.

---

## 3.10 HikariCP Connection Pool & MySQL 8.0 InnoDB Engine
* **개념**: 미리 DB 커넥션을 생성하여 풀(Pool)에 보관해 두고 재사용하는 고성능 JDBC 커넥션 풀 라이브러리입니다.
* **Why**: 매 요청마다 DB 커넥션을 맺고 끊는 3-Way Handshake RTT 오버헤드를 없애기 위함.
* **When**: Spring Boot 실행 시 데이터소스 연동 시.
* **How**: `spring.datasource.hikari.maximum-pool-size: 10`.
* **Pros**: O(1) 수준의 커넥션 획득 속도 및 CPU 오버헤드 최소화.
* **Alternatives**: Tomcat DBCP (HikariCP가 속도 측면에서 수배 이상 빠름).
* **Trade-off & Mitigation**: 커넥션 풀 고갈 가능성이 있으므로 OSIV 옵션을 끄고(`open-in-view: false`) 커넥션 반환 속도를 극대화합니다.

---

# 📑 CHAPTER 4. 9대 영역 50대 백엔드 기술 질문 & 꼬리 질문 대본 (Pure Backend)

---

## 영역 1. Spring Security & 인증/인가 흐름

### Q1. Spring Security Filter Chain의 동작 원리와 주요 필터들의 역할은 무엇인가요?
* **정확한 대답**: 서블릿 컨테이너(Tomcat)의 `DelegatingFilterProxy`가 요청을 받아 Spring Bean인 `FilterChainProxy`에 위임하면 순서대로 보안 필터들이 실행됩니다. `CorsFilter`(Cors 검증), `LogoutFilter`(로그아웃 처리), `SecurityContextHolderFilter`(세션에서 SecurityContext 복원), `ExceptionTranslationFilter`(401/403 예외 감지), `AuthorizationFilter`(URL 권한 검증)가 핵심 역할을 수행합니다.
* **[면접관 꼬리 질문]**: "DelegatingFilterProxy와 FilterChainProxy의 물리적 차이는 무엇인가요?"
* **[면접자 답변 대본]**: "Tomcat은 서블릿 스펙으로 동작하여 Spring Bean을 인식하지 못하므로, 서블릿 필터인 `DelegatingFilterProxy`가 요청을 받아 Spring Context 내의 `FilterChainProxy` Bean으로 위임해 줍니다. 이를 통해 스프링의 DI 기능으로 보안 필터들을 관리할 수 있게 됩니다."

### Q2. Authentication(인증)과 Authorization(인가)의 명확한 차이는 무엇인가요?
* **정확한 대답**: Authentication(인증)은 "이 사용자가 누구인가?"를 검증하는 신원 확인 과정(로그인)이고, Authorization(인가)은 "인증된 사용자가 특정 리소스에 접근할 권한이 있는가?"를 검증하는 권한 확인 과정(Role 검증)입니다.
* **[면접관 꼬리 질문]**: "인증 객체인 Authentication은 어디에 저장되나요?"
* **[면접자 답변 대본]**: "`SecurityContextHolder` 내부의 ThreadLocal에 저장되어, 동일한 스레드 내에서는 Controller, Service 등 어느 레이어에서나 `SecurityContextHolder.getContext().getAuthentication()`으로 접근할 수 있습니다."

### Q3. 세션 기반 인증의 전체 흐름을 설명해 주세요.
* **정확한 대답**: `POST /api/auth/login` 요청 ➔ BCrypt 비밀번호 검증 ➔ `SecurityContext` 생성 ➔ `request.changeSessionId()` 세션 고정 방어 ➔ 세션 저장 ➔ `Set-Cookie: JSESSIONID=32자리랜덤키; Path=/; HttpOnly; SameSite=Lax` 발급 ➔ 후속 요청 시 쿠키 전송 ➔ `SecurityContextHolderFilter`가 세션 복원.
* **[면접관 꼬리 질문]**: "`request.getSession(true)`와 `request.getSession(false)`의 차이는 무엇인가요?"
* **[면접자 답변 대본]**: "`true`는 세션이 없으면 새로 생성하고, `false`는 세션이 없으면 `null`을 반환합니다. 로그인 시에는 세션을 생성해야 하므로 `true`, 단순 조회 시에는 `false`를 사용해 불필요한 세션 생성을 막습니다."

### Q4. 401 Unauthorized와 403 Forbidden 예외 처리 분리 방식은 무엇인가요?
* **정확한 대답**: 401(미인증)은 `AuthenticationEntryPoint`가 동작하고, 403(권한부족)은 `AccessDeniedHandler`가 동작합니다. `SecurityConfig`에서 JSON 응답(`SC_UNAUTHORIZED`, `SC_FORBIDDEN`)을 반환하도록 설정했습니다.
* **[면접관 꼬리 질문]**: "`ExceptionTranslationFilter`는 체인의 어느 위치에서 예외를 잡나요?"
* **[면접자 답변 대본]**: "`AuthorizationFilter` 바로 앞에 위치하여 하위 필터나 Controller에서 던져진 AuthenticationException 및 AccessDeniedException을 try-catch로 감싸 캐치한 뒤 처리합니다."

### Q5. Session 방식과 JWT 토큰 방식의 장단점을 비교하고 세션을 선택한 이유는 무엇인가요?
* **정확한 대답**: 세션(Stateful)은 서버에서 세션을 즉시 파기할 수 있어 보안성이 뛰어나지만 서버 메모리를 사용합니다. JWT(Stateless)는 확장이 쉬우나 토큰 탈취 시 강제 제어가 불가능합니다. 본 프로젝트는 즉각적인 강제 로그아웃 통제와 보안성, Remember-Me(30일) UX를 위해 세션을 선택했습니다.
* **[면접관 꼬리 질문]**: "JWT에서 세션처럼 강제 로그아웃을 구현하려면 어떻게 해야 하나요?"
* **[면접자 답변 대본]**: "Redis에 만료된 Access Token을 Blacklist로 등록하거나 Refresh Token을 Redis에서 삭제해야 합니다. 하지만 이 경우 결국 Redis 상태를 관리하게 되어 JWT의 순수 Stateless 장점이 희석됩니다."

---

## 영역 2. 세션 & 쿠키 보안

### Q6. 세션 고정 공격(Session Fixation)과 `changeSessionId()`의 역할은 무엇인가요?
* **정확한 대답**: 공격자가 발급받은 비회원 세션 ID를 유저에게 쥐여준 뒤 로그인 시 계정을 탈취하는 공격입니다. `changeSessionId()`는 로그인 성공 직후 기존 세션 데이터는 유지하면서 세션 ID만 무작위 새 문자열로 재발급하여 공격자의 세션 ID를 무효화시킵니다.
* **[면접관 꼬리 질문]**: "Spring Security 7의 기본 세션 고정 방어 전략은 무엇인가요?"
* **[면접자 답변 대본]**: "Servlet 3.1+ 컨테이너 환경을 감지하여 기본적으로 `changeSessionId()` 방식이 자동으로 동작합니다."

### Q7. 쿠키 5대 보안 속성(HttpOnly, Secure, SameSite, Path, Max-Age)의 역할은 무엇인가요?
* **정확한 대답**: `HttpOnly`(자바스크립트 접근 차단/XSS 방어), `Secure`(HTTPS 채널만 전송), `SameSite=Lax`(Cross-Site 전송 제어/CSRF 방어), `Path=/`(전체 API 쿠키 전송), `Max-Age`(만료 시간 지정).
* **[면접관 꼬리 질문]**: "SameSite 속성 중 Strict, Lax, None의 차이는 무엇인가요?"
* **[면접자 답변 대본]**: "`Strict`는 모든 Cross-Site 전송 차단, `Lax`는 안전한 GET 탑레벨 이동 시 전송 허용, `None`은 모든 전송 허용(단, `Secure=true` 필수)을 의미합니다."

### Q8. 개발 환경과 운영 환경의 쿠키 보안 설정 차이점은 무엇인가요?
* **정확한 대답**: 개발(`http://localhost:3000`)은 SSL이 없으므로 `Secure=false`, 운영(`https`)은 `Secure=true`를 적용해야 쿠키 전송이 거부되지 않습니다.
* **[면접관 꼬리 질문]**: "도메인이 다를 때 SameSite 설정은 어떻게 하나요?"
* **[면접자 답변 대본]**: "도메인이 완전히 다르면 `SameSite=None`과 `Secure=true`를 적용하고, CORS 응답 헤더에 `Access-Control-Allow-Credentials: true`를 명시해야 합니다."

### Q9. 세션 만료 시간(Timeout) 정책은 어떻게 설정했나요?
* **정확한 대답**: 일반 세션은 1시간 슬라이딩 세션(활동 시 연장)으로 세션 쿠키를 사용하고, Remember-Me 체크 시 30일(`2,592,000초`) 만료 쿠키를 발급합니다.
* **[면접관 꼬리 질문]**: "슬라이딩 세션은 톰캣 내부에서 어떻게 작동하나요?"
* **[면접자 답변 대본]**: "요청이 들어올 때마다 `session.getLastAccessedTime()`이 현재 시간으로 갱신되며 만료 카운트다운(3600초)이 처음부터 리셋되는 원리입니다."

### Q10. 세션 객체에 회원 엔티티 전체 대신 식별자(ID)와 Role만 저장해야 하는 이유는 무엇인가요?
* **정확한 대답**: 1. 서버 RAM 메모리 절약(OOM 방지), 2. 정보 변경 시 세션-DB 데이터 불일치 방지, 3. JPA 지연 로딩 프록시 직렬화 에러(`NotSerializableException`) 예방을 위함입니다.
* **[면접관 꼬리 질문]**: "세션에 식별자만 넣었을 때 매 요청 DB 조회 오버헤드는 어떻게 해결하나요?"
* **[면접자 답변 대본]**: "Spring Data JPA 1차 캐시 메모리와 PK 인덱스 조회를 통해 O(1) 성능으로 접근하므로 DB 오버헤드는 거의 없습니다."

### Q11. 다중 로그인(동시 로그인) 제어 정책은 어떻게 설계하나요?
* **정확한 대답**: `SecurityConfig`에서 `.maximumSessions(1)`을 설정하여 기존 세션을 만료시키거나 새 로그인을 차단합니다.
* **[면접관 꼬리 질문]**: "기존 세션 만료 시 유저에게 어떤 응답을 내려주나요?"
* **[면접자 답변 대본]**: "`sessionInformationExpiredStrategy()`를 구현하여 401 JSON 응답과 함께 '다른 기기에서 로그인되었습니다'라는 메시지를 내보냅니다."

---

## 영역 3. 도메인 설계 & 식별자(PK/UUID)

### Q12. Dual PK Strategy(`BIGINT AUTO_INCREMENT` + `public_id UUID`)를 사용하는 이유는 무엇인가요?
* **정확한 대답**: 내부 조인은 8바이트 정수 `id`로 실행하여 B-Tree 인덱스 조인 성능을 극대화하고, 외부 REST API URL 노출용으로는 36자리 UUID `public_id`를 사용하여 보안성을 둘 다 잡기 위함입니다.
* **[면접관 꼬리 질문]**: "UUID를 DB PK로 직접 쓸 때 발생하는 B-Tree 인덱스 파편화(Fragmentation) 문제는 무엇인가요?"
* **[면접자 답변 대본]**: "무작위 UUID는 순서가 없어 INSERT 시 B-Tree 인덱스 중간에 무작위로 위치하며 페이지 분할(Page Split)이 빈번하게 발생해 디스크 I/O가 급증합니다. 정수 PK는 순차 추가되어 파편화가 없습니다."

### Q13. Auto-Increment ID를 외부에 노출할 때의 취약점은 무엇인가요?
* **정확한 대답**: ID 추측 공격(Enumeration Attack)을 통한 개인정보 무단 스크랩과 비즈니스 가입자 수 지표 유출 위험이 있습니다.
* **[면접관 꼬리 질문]**: "UUID 외에 고려해 볼 대안 식별자는 무엇이 있나요?"
* **[면접자 답변 대본]**: "정렬 가능한 64비트 정수형 식별자인 **TSID**나 Twitter의 **Snowflake ID**를 고려할 수 있습니다."

### Q14. 내부 JOIN 연산 시 숫자형 PK 사용의 성능적 이점은 무엇인가요?
* **정확한 대답**: BIGINT(8바이트)는 UUID(36바이트)보다 메모리가 훨씬 작아 인덱스 탑재량이 늘어나고, CPU 정수 비교 연산 속도가 문자열 비교보다 빠릅니다.
* **[면접관 꼬리 질문]**: "BIGINT PK 오버플로우가 발생할 가능성은 없나요?"
* **[면접자 답변 대본]**: "BIGINT는 64비트 정수로 약 922경 개의 데이터를 저장할 수 있어 일반적인 서비스 환경에서는 물리적으로 오버플로우가 발생하지 않습니다."

### Q15. 회원 상태(정상, 탈퇴, 정지) 검증 위치는 어디가 적절한가요?
* **정확한 대답**: `AuthService.authenticate()` 서비스 레이어에서 비밀번호 검증 직후 검사하여 `CustomAuthException("SUSPENDED_MEMBER")`을 던집니다.
* **[면접관 꼬리 질문]**: "이미 로그인된 정지 회원의 후속 요청은 어떻게 차단하나요?"
* **[면접자 답변 대본]**: "관리자가 정지 처리하는 시점에 서블릿 세션을 즉시 무효화하고, Security Custom Filter에서 유저 Status를 확인해 `SUSPENDED` 시 403 Forbidden을 반환합니다."

---

## 영역 4. 데이터베이스, MySQL 8 인덱스 & 동시성

### Q16. 이메일 중복 체크 시 애플리케이션 검증 외에 DB UNIQUE 제약조건이 필수인 이유는 무엇인가요?
* **정확한 대답**: 동시성 환경에서 두 유저가 동시 가입 요청 시 자바 검사를 둘 다 통과하는 경쟁 상태(Race Condition)가 발생합니다. DB `UNIQUE` 제약조건이 있어야 DB 레벨에서 `DataIntegrityViolationException`을 뿜으며 무결성을 방어합니다.
* **[면접관 꼬리 질문]**: "DB UNIQUE 위반 예외 시 사용자에게 어떻게 응답하나요?"
* **[면접자 답변 대본]**: "`GlobalExceptionHandler`에서 캐치하여 `400 Bad Request`와 함께 '이미 사용 중인 이메일입니다'라는 메시지를 반환합니다."

### Q17. N:M 다대다 중계 테이블 분리 이유는 무엇인가요?
* **정확한 대답**: RDBMS 제1정규형(원자성) 준수 및 JPA Direct `@ManyToMany`가 유발하는 기존 관계 전체 `DELETE` 후 `INSERT` 하는 비효율을 방지하기 위함입니다.
* **[면접관 꼬리 질문]**: "복합키 대신 단일 대리키(id) + 복합 UNIQUE를 쓴 이유는 무엇인가요?"
* **[면접자 답변 대본]**: "JPA에서 복합키(`@IdClass`) 구현 시 Entity 코드가 복잡해집니다. 단일 대리키(`id`)에 `(member_id, resort_id)` `복합 UNIQUE`를 거는 것이 JPA 생산성과 DB 무결성을 둘 다 얻는 베스트 기법입니다."

### Q18. MySQL 8 InnoDB B-Tree 인덱스의 동작 원리와 클러스터드 인덱스의 차이는 무엇인가요?
* **정확한 대답**: Clustered Index는 PK 순서로 실제 데이터 레코드가 물리 정렬 저장되는 인덱스이며, Secondary Index는 B-Tree 리프 노드에 PK 값을 쥐고 있어 Secondary Index 조회의 경우 '인덱스 탐색 ➔ PK 탐색' 2번의 탐색 과정을 거칩니다.
* **[면접관 꼬리 질문]**: "복합 인덱스(Composite Index) 생성 시 컬럼 순서가 왜 중요한가요?"
* **[면접자 답변 대본]**: "B-Tree 인덱스는 첫 번째 선두 컬럼 기준으로 정렬된 후 두 번째 컬럼이 정렬됩니다. 따라서 선두 컬럼이 `WHERE` 절 조건에 포함되지 않으면 인덱스를 타지 못하고 Full Table Scan이 터지므로 카디널리티(기억 선택도)가 높은 컬럼을 선두로 두어야 합니다."

### Q19. BCrypt 단방향 해시와 Salt/Work Factor의 역할은 무엇인가요?
* **정확한 대답**: BCrypt는 복호화가 불가능한 해시 암호화입니다. Salt는 무작위 문자로 사전 공격을 막고, Work Factor(Cost=10)는 1,024회 연쇄 연산으로 무차별 대입 공격(Brute Force)을 물리적으로 지연시킵니다.
* **[면접관 꼬리 질문]**: "Work Factor를 10에서 14로 올리면 어떤 변화가 생기나요?"
* **[면접자 답변 대본]**: "Cost 10(1,024회) 대비 Cost 14(16,384회)는 연산 시간이 16배 증가하여 보안은 강해지나 로그인 시 서버 CPU 부하가 증가하는 트레이드오프가 발생합니다."

---

## 영역 5. 아키텍처 & 확장성

### Q20. Scale-out 시 세션 문제 해결 방안(Sticky Session vs Redis 클러스터링)은 무엇인가요?
* **정확한 대답**: Sticky Session은 로드밸런서가 특정 서버로만 트래픽 고정(서버 다운 시 세션 소실)합니다. Redis 공유 세션 클러스터링(Spring Session Data Redis)은 중앙 In-Memory DB인 Redis에 세션을 공유 저장하여 완벽한 Stateless 수준의 확장성을 제공합니다.
* **[면접관 꼬리 질문]**: "Redis 장애 시 대비책은 무엇인가요?"
* **[면접자 답변 대본]**: "Redis Sentinel이나 Redis Cluster를 구축하여 Primary 노드 장애 시 Secondary 노드가 1~2초 이내에 자동 승격되는 Failover 시스템을 구축합니다."

### Q21. Controller와 Service, Entity 간의 책임 분리 기준은 무엇인가요?
* **정확한 대답**: Controller(HTTP 요청 검증, 쿠키/세션 제어, DTO 반환), Service(트랜잭션 경계, 비즈니스 규칙 제어 - 서블릿 API 참조 금지), Entity(도메인 핵심 비즈니스 메서드 직접 보유 및 검증).
* **[면접관 꼬리 질문]**: "Service에서 HttpServletRequest를 파라미터로 받으면 왜 안 되나요?"
* **[면접자 답변 대본]**: "Service 계층이 Web 서블릿 스펙에 결합되어 단위 테스트 시 Web 객체를 Mocking해야 하고, gRPC나 메시지 큐 등 타 전송 프로토콜로 변경 시 Service 전체를 재작성해야 하는 단일 책임 원칙(SRP) 위반이 생깁니다."

### Q22. 로그아웃 연계 처리 방식은 무엇인가요?
* **정확한 대답**: 서버 측 `session.invalidate()` 실행 ➔ 응답 헤더 `Set-Cookie: JSESSIONID=; Max-Age=0` 전달로 브라우저 쿠키를 즉시 파기합니다.
* **[면접관 꼬리 질문]**: "로그아웃 없이 탭을 닫아버리면 서버 세션은 어떻게 되나요?"
* **[면접자 답변 대본]**: "세션 타임아웃(1시간) 동안 요청이 없으면 톰캣 세션 스캐너가 만료된 세션을 메모리에서 자동으로 정리합니다."

---

## 영역 6. 검증 & 입력값 Validation (`@Valid`)

### Q23. Bean Validation 어노테이션(`@NotBlank`, `@Email`, `@Pattern`)의 차이점은 무엇인가요?
* **정확한 대답**: `@NotNull`은 `null`만 거부하고 공백문자열(`""`)은 허용합니다. `@NotEmpty`는 `null`과 빈 문자열(`""`)을 거부합니다. `@NotBlank`는 `null`, 빈 문자열(`""`), 그리고 띄어쓰기 공백(`" "`)까지 거부하는 가장 엄격한 검증 어노테이션입니다.
* **[면접관 꼬리 질문]**: "비밀번호 검증 정규식 `@Pattern`을 사용한 이유는 무엇인가요?"
* **[면접자 답변 대본]**: "영문, 숫자, 특수문자를 최소 1개 이상 포함하고 8~20자 이내인지 자바 정규식 룩어라운드(`(?=.*[A-Za-z])`)로 일관되게 검증하여 취약한 비밀번호 생성을 차단하기 위함입니다."

### Q24. `@WebMvcTest`와 `@SpringBootTest` 단위/통합 테스트의 차이점은 무엇인가요?
* **정확한 대답**: `@WebMvcTest`는 Controller 및 웹 레이어 관련 빈만 가볍게 로딩하여 빠르게 테스트하고, `@SpringBootTest`는 전체 스프링 컨테이너 빈과 DB를 모두 띄워 엔드-투-엔드 통합 검증을 수행합니다.
* **[면접관 꼬리 질문]**: "`@SpringBootTest` 속도 지연을 극복하는 팁은 무엇인가요?"
* **[면접자 답변 대본]**: "공통 추상 테스트 클래스를 정의하여 스프링 컨테이너를 단 1번만 띄우고 재사용하는 방식을 사용하여 테스트 실행 속도를 극대화합니다."

### Q25. 동시성 회원가입 테스트(`MemberConcurrencyTest`)는 어떻게 검증했나요?
* **정확한 대답**: `ExecutorService`와 `CountDownLatch`를 활용해 10개의 멀티스레드가 동일한 이메일로 동시 가입 요청을 동시에 쏘도록 시뮬레이션하고, 단 1건만 성공하고 9건은 예외 처리됨을 검증했습니다.
* **[면접관 꼬리 질문]**: "`CountDownLatch`는 동시성 테스트에서 어떤 역할을 하나요?"
* **[면접자 답변 대본]**: "`latch.await()`를 통해 10개의 스레드가 준비될 때까지 대기시켰다가 `latch.countDown()`으로 10개 스레드를 한순간에 동시에 출발시켜 정밀한 동시 경합 상태를 만듭니다."

---

## 영역 7. JPA 영속성 메커니즘 & ORM 딥다이브

### Q26. 영속성 컨텍스트 1차 캐시와 DB 조회 쿼리 생략 원리는 무엇인가요?
* **정확한 대답**: JPA는 엔티티 조회 시 영속성 컨텍스트 내부의 1차 캐시 `Map<Id, Entity>`를 먼저 검색합니다. 동일 트랜잭션 내 이미 존재하는 식별자 조회의 경우 DB SQL을 전송하지 않고 1차 캐시 객체를 즉시 반환하여 성능을 최적화합니다.
* **[면접관 꼬리 질문]**: "1차 캐시의 생명주기(Scope)는 언제까지 유지되나요?"
* **[면접자 답변 대본]**: "1차 캐시는 트랜잭션 범위와 1:1로 일치합니다. HTTP 요청 하나가 들어와 트랜잭션이 끝나고 `EntityManager`가 종료되면 1차 캐시도 즉시 소멸하므로 메모리 낭비가 없습니다."

### Q27. 변경 감지(Dirty Checking) 동작 원리와 `@Transactional`의 역할은 무엇인가요?
* **정확한 대답**: JPA는 엔티티를 영속성 컨텍스트에 담을 때 초기 상태의 '스냅샷'을 떠둡니다. 트랜잭션이 커밋되는 시점에 엔티티 필드와 스냅샷을 비교하여 변경된 부분이 있으면 수동 `update()` 없이도 `UPDATE` SQL을 자동 생성하여 DB에 반영합니다.
* **[면접관 꼬리 질문]**: "Dirty Checking으로 생성되는 UPDATE 쿼리는 기본적으로 모든 필드를 변경하나요?"
* **[면접자 답변 대본]**: "기본적으로는 모든 필드를 갱신하는 쿼리가 나가며, 이는 쿼리 재사용성을 높여줍니다. 변경된 필드만 동적으로 갱신하고 싶다면 엔티티 클래스에 `@DynamicUpdate` 어노테이션을 붙여 처리할 수 있습니다."

### Q28. 지연 로딩(LAZY) vs 즉시 로딩(EAGER)과 실무에서 LAZY만 써야 하는 이유는 무엇인가요?
* **정확한 대답**: `EAGER`는 엔티티 조회 시 연관 엔티티까지 무조건 조인하여 가져오는 방식이고, `LAZY`는 실제 연관 객체의 필드를 사용할 때 프록시를 통해 쿼리를 날리는 방식입니다. 실무에서는 `EAGER` 설정 시 예상치 못한 JPQL N+1 쿼리 폭발이 터지므로 무조건 `LAZY`만 설정해야 합니다.
* **[면접관 꼬리 질문]**: "지연 로딩 상태의 연관 객체를 트랜잭션 밖에서 접근하면 어떤 에러가 발생하나요?"
* **[면접자 답변 대본]**: "영속성 컨텍스트가 이미 종료되었으므로 프록시를 초기화할 수 없어 `LazyInitializationException` 예외가 발생합니다."

### Q29. OSIV (Open Session In View) 옵션의 작동 원리와 실무 설정 방안은 무엇인가요?
* **정확한 대답**: `spring.jpa.open-in-view: true` 설정 시 HTTP 요청 시작부터 뷰/컨트롤러까지 DB 커넥션과 영속성 컨텍스트를 유지하는 기능입니다. 하지만 이 경우 컨트롤러까지 DB 커넥션을 오래 잡고 있어 커넥션 풀 고갈을 유발하므로, 실무에서는 **`OSIV: false`로 설정**하고 서비스 레이어 안에서 DTO로 변환하여 반환해야 합니다.
* **[면접관 꼬리 질문]**: "OSIV를 켰을 때 지연 로딩 예외를 막으려면 어떻게 해야 하나요?"
* **[면접자 답변 대본]**: "서비스 레이어의 `@Transactional` 범위 내에서 JPQL `JOIN FETCH` 또는 DTO Projection을 사용해 필요한 데이터를 한 번에 영속화한 뒤 DTO로 변환하여 컨트롤러로 넘겨주면 됩니다."

### Q30. `@Modifying(clearAutomatically = true, flushAutomatically = true)`의 필요성은 무엇인가요?
* **정확한 대답**: JPQL 벌크 연산(`DELETE`, `UPDATE`)은 영속성 컨텍스트 1차 캐시를 거치지 않고 DB로 직접 SQL을 날립니다. 이 때문에 1차 캐시와 DB 데이터 간 불일치가 발생하므로, 벌크 연산 후 `clearAutomatically = true`를 붙여 1차 캐시를 자동으로 비워주어야 무결성이 유지됩니다.
* **[면접관 꼬리 질문]**: "만약 clearAutomatically를 안 쓰면 어떤 버그가 생기나요?"
* **[면접자 답변 대본]**: "DB에는 수정/삭제된 데이터가 반영되었지만, 영속성 컨텍스트 1차 캐시에는 옛날 데이터가 남아있어 이후 `findById()` 호출 시 옛날 데이터가 조회되는 버그가 발생합니다."

### Q31. JPA N+1 문제의 근본 원인과 `JOIN FETCH` vs `@EntityGraph` vs `@BatchSize` 차이는 무엇인가요?
* **정확한 대답**: N+1은 JPQL이 연관관계를 고려하지 않고 주 엔티티 쿼리(1회)만 날린 뒤, 로딩된 연관 객체 N개를 탐색할 때마다 N번의 추가 SQL이 나가는 원인입니다. `JOIN FETCH`는 JPQL 조인, `@EntityGraph`는 어노테이션 기반 조인, `@BatchSize`는 `IN (?, ?, ?)` 묶음 처리로 N+1을 해결합니다.
* **[면접관 꼬리 질문]**: "컬렉션 페이징 처리 시 JOIN FETCH를 쓰면 발생하는 심각한 문제는 무엇인가요?"
* **[면접자 답변 대본]**: "1:N 관계에서 JOIN FETCH 후 페이징을 시도하면 Hibernate가 경고 로그를 남기며 **모든 DB 데이터를 메모리로 들고 와서 메모리 페이징(In-Memory Paging)**을 수행하므로 OOM 장애가 터집니다. 컬렉션 페이징 시에는 `@BatchSize`를 써야 합니다."

### Q32. 영속성 전이(`CascadeType.ALL`)와 외딴 객체 제거(`orphanRemoval = true`)의 차이는 무엇인가요?
* **정확한 대답**: `CascadeType.ALL`은 부모 엔티티의 저장/삭제 이벤트를 자식 엔티티로 전가하는 기능이고, `orphanRemoval = true`는 부모 엔티티의 컬렉션에서 자식 객체 요소만 제거했을 때 DB에서 해당 자식 DELETE 쿼리가 나가는 기능입니다.
* **[면접관 꼬리 질문]**: "CascadeType.ALL과 orphanRemoval = true를 둘 다 켜면 어떤 이점이 있나요?"
* **[면접자 답변 대본]**: "부모 엔티티가 자식 엔티티의 생명주기를 100% 관리하는 Aggregate Root 구조가 되어, Repository 생성 없이 부모 엔티티 하나만으로 자식의 추가/삭제/수정을 완벽히 제어할 수 있습니다."

### Q33. `saveAndFlush()` vs `save()`의 차이는 무엇인가요?
* **정확한 대답**: `save()`는 영속성 컨텍스트 1차 캐시에 엔티티를 담아두고 트랜잭션 커밋 시점에 `flush()` 되며, `saveAndFlush()`는 즉시 DB에 `flush()`를 실행하여 SQL을 반영하되 트랜잭션 커밋은 여전히 트랜잭션 끝에서 처리됩니다.
* **[면접관 꼬리 질문]**: "`saveAndFlush()`를 쓰면 DB 트랜잭션이 커밋된 것인가요?"
* **[면접자 답변 대본]**: "아닙니다. `flush()`는 단지 영속성 컨텍스트의 변경 내용을 DB에 SQL로 전송하는 것뿐이며, 실제 DB 트랜잭션 커밋(Commit)은 트랜잭션이 종료되는 시점에 실행됩니다."

---

## 영역 8. Spring Transactional (`@Transactional`) & 예외 처리

### Q34. `@Transactional(readOnly = true)`를 읽기 전용 메서드에 적용하는 성능적 이점은 무엇인가요?
* **정확한 대답**: 하이버네이트 영속성 컨텍스트가 엔티티의 변경 감지를 위한 **'스냅샷(Snapshot)'을 생성하지 않으므로 메모리가 절약**되고, 트랜잭션 커밋 시점에 플러시(Flush) 검사를 생략하여 CPU 오버헤드가 크게 줄어듭니다.
* **[면접관 꼬리 질문]**: "readOnly = true 메서드 안에서 엔티티 필드를 수정하면 DB에 반영되나요?"
* **[면접자 답변 대본]**: "플러시(Flush)가 실행되지 않으므로 DB에 UPDATE 쿼리가 나가지 않으며 데이터 변경이 방지됩니다."

### Q35. `@Transactional` 적용 시 언체크 예외(RuntimeException)와 체크 예외(Checked Exception)의 롤백 방식 차이는 무엇인가요?
* **정확한 대답**: 기본적으로 스프링 트랜잭션은 `RuntimeException` 및 `Error` 발생 시에만 롤백을 수행하고, Checked Exception(`Exception` 상위 클래스)이 발생하면 롤백을 수행하지 않고 커밋합니다.
* **[면접관 꼬리 질문]**: "Checked Exception 발생 시에도 롤백시키려면 어떻게 해야 하나요?"
* **[면접자 답변 대본]**: `@Transactional(rollbackFor = Exception.class)` 속성을 명시적으로 지정하여 모든 예외에 대해 롤백이 동작하도록 설정해야 합니다."

### Q36. 동일 클래스 내부에서 `@Transactional` 메서드를 일반 메서드가 호출할 때 트랜잭션이 적용되지 않는 문제(Self-Invocation)의 원인은 무엇인가요?
* **정확한 대답**: 스프링 트랜잭션은 CGLIB 프록시 객체 기반으로 동작합니다. 동일 클래스 내부의 메서드 호출(`this.method()`)은 프록시를 거치지 않고 실제 객체의 타겟 메서드를 직접 호출하므로 AOP 트랜잭션 어드바이스가 적용되지 않습니다.
* **[면접관 꼬리 질문]**: "Self-Invocation 문제를 해결하려면 어떻게 설계해야 하나요?"
* **[면접자 답변 대본]**: "트랜잭션이 필요한 로직을 별도의 Spring Bean 서비스 클래스로 분리하여 외부에서 해당 Bean의 메서드를 호출하도록 객체 구조를 리팩토링해야 합니다."

---

## 영역 9. 백엔드 인프라, MySQL 8 & CI/CD 딥다이브

### Q37. Spring Boot 4.0.0 (`build.gradle` 명세) 세션 타임아웃 및 HikariCP 설정 방안은 무엇인가요?
* **정확한 대답**: `server.servlet.session.timeout: 60m`으로 1시간 슬라이딩 세션을 설정하고, `spring.datasource.hikari.maximum-pool-size: 10`으로 적절한 커넥션 풀을 할당합니다.
* **[면접관 꼬리 질문]**: "HikariCP 커넥션 풀 크기를 너무 크게 잡으면 어떤 문제가 생기나요?"
* **[면접자 답변 대본]**: "커넥션 풀이 너무 크면 DB 서버의 메모리 부하와 Context Switching 오버헤드가 증가하여 오히려 전체 시스템의 쿼리 처리 속도가 저하됩니다."

### Q38. Controller-Service 계층 분리와 Java 17 `record` DTO 적용의 이점은 무엇인가요?
* **정확한 대답**: Controller는 Web 요청/응답 변환에만 집중하고 Service는 서블릿 API 의존성 없이 비즈니스 로직에만 집중하게 분리하며, `record` DTO를 사용해 스키마 유출과 무한 순환 참조를 차단합니다.
* **[면접관 꼬리 질문]**: "DTO 변환 시 자바 17+ record를 쓰면 어떤 이점이 있나요?"
* **[면접자 답변 대본]**: "`record`는 불변(Immutable) 데이터 객체로 `equals`, `hashCode`, `toString`, Getter가 자동 생성되어 코드 라인을 획기적으로 줄이고 thread-safe 무결성을 보장합니다."

### Q39. 동시성 환경에서의 Optimistic Lock (낙관적 락) vs Pessimistic Lock (비관적 락) 차이는 무엇인가요?
* **정확한 대답**: 낙관적 락은 충돌이 적을 것으로 가정하여 JPA `@Version` 필드로 커밋 시점에 충돌을 감지하고, 비관적 락은 충돌이 잦을 것으로 가정하여 DB `SELECT ... FOR UPDATE` 락을 실제로 거는 방식입니다.
* **[면접관 꼬리 질문]**: "충돌이 자주 발생하는 포인트에서는 둘 중 무엇을 선택해야 하나요?"
* **[면접자 답변 대본]**: "충돌이 빈번한 경우 낙관적 락은 롤백 및 재시도 로직 오버헤드가 크므로, DB 수준에서 즉시 순차 처리를 보장하는 비관적 락(Pessimistic Write)을 선택해야 합니다."

### Q40. GitHub Actions CI 파이프라인 및 테스트 자동화 검증 흐름은 무엇인가요?
* **정확한 대답**: `pull_request` 이벤트 발생 시 GitHub Actions가 우분투 러너에서 레포를 체크아웃하고 `.\gradlew.bat test` 25개 테스트 수트를 자동 빌드/검증한 뒤 결과를 PR에 통보하는 흐름입니다.
* **[면접관 꼬리 질문]**: "CI 빌드 속도를 향상시키기 위해 적용할 수 있는 기법은 무엇인가요?"
* **[면접자 답변 대본]**: "GitHub Actions의 `actions/cache`를 활용해 Gradle 의존성 패키지(`~/.gradle/caches`)를 캐싱하여 매 빌드마다 패키지를 재다운로드하지 않도록 최적화합니다."

### Q41. MySQL 8.0 InnoDB의 MVCC(Multi-Version Concurrency Control) 동작 원리는 무엇인가요?
* **정확한 대답**: MVCC는 트랜잭션 격리 수준을 보장하기 위해 Undo Log에 데이터의 이전 버전을 보관해 두어, `READ COMMITTED`나 `REPEATABLE READ` 환경에서 락(Lock)을 걸지 않고도 일관된 읽기(Consistent Read)를 제공하는 인노DB 핵심 동시성 기술입니다.
* **[면접관 꼬리 질문]**: "MVCC 덕분에 조회의 성능적 이점은 무엇인가요?"
* **[면접자 답변 대본]**: "읽기 작업이 쓰기 작업의 락을 기다리지 않고(Non-blocking Read), 쓰기 작업 역시 읽기 작업의 락을 기다리지 않아 동시 조회 성능이 극대화됩니다."

### Q42. MySQL 8.0의 기본 트랜잭션 격리 수준(Isolation Level)과 Phantom Read는 무엇인가요?
* **정확한 대답**: MySQL InnoDB의 기본 격리 수준은 `REPEATABLE READ`입니다. Phantom Read는 한 트랜잭션 내에서 동일한 쿼리를 두 번 실행했을 때, 다른 트랜잭션의 `INSERT`에 의해 첫 번째 쿼리에서 없던 유령(Phantom) 레코드가 나타나는 현상입니다.
* **[면접관 꼬리 질문]**: "MySQL InnoDB는 REPEATABLE READ에서 Phantom Read를 어떻게 막나요?"
* **[면접자 답변 대본]**: "InnoDB는 갭 락(Gap Lock)과 넥스트 키 락(Next-Key Lock)을 사용하여 조건 범위 사이의 빈 공간에 새로운 레코드가 `INSERT` 되는 것을 물리적으로 차단하여 Phantom Read를 예방합니다."

### Q43. Spring Security `SecurityContextHolder`의 기본 Strategy(ThreadLocal)와 Async 스레드 전파 방식은 무엇인가요?
* **정확한 대답**: 기본 전략은 `MODE_THREADLOCAL`로, 요청을 처리하는 동일 스레드 내에서만 보안 컨텍스트가 공유됩니다. `@Async` 비동기 스레드로 보안 컨텍스트를 전파하려면 `MODE_INHERITABLETHREADLOCAL` 설정이나 `DelegatingSecurityContextExecutor`를 사용해야 합니다.
* **[면접관 꼬리 질문]**: "ThreadLocal 사용 후 cleanup을 하지 않으면 어떤 문제가 생기나요?"
* **[면접자 답변 대본]**: "톰캣의 스레드 풀(Thread Pool) 재사용 특성 때문에 이전 유저의 `SecurityContext` 정보가 톰캣 스레드에 그대로 남아있어, 다음 유저 요청 시 타인의 세션으로 인증되는 심각한 보안 누수가 발생합니다. 시큐리티 필터 끝에서 반드시 clear 해야 합니다."

### Q44. Jackson Serializer의 `ObjectMapper` 사용 시 `JavaTimeModule` 등록 이유는 무엇인가요?
* **정확한 대답**: Java 8의 `LocalDateTime`, `LocalDate` 객체는 기본 Jackson `ObjectMapper`로 직렬화 시 배열 형태(`[2026, 8, 19]`)로 변환됩니다. `JavaTimeModule`을 등록해야 ISO-8601 표준 문자열(`"2026-08-19T17:56:00"`)로 정상 직렬화됩니다.
* **[면접관 꼬리 질문]**: "Spring Boot 4.0.0에서는 JavaTimeModule이 기본 등록되어 있나요?"
* **[면접자 답변 대본]**: "네, Spring Boot 4.0.0 / Spring MVC의 `Jackson2ObjectMapperBuilder`가 자동으로 `JavaTimeModule`을 감지하여 등록해 줍니다."

### Q45. REST API 응답 포맷 일관성을 위한 `ResponseEntity<T>` 및 Common Response Wrapper 구조는 무엇인가요?
* **정확한 대답**: API 응답의 HTTP Status Code, Header, Body 데이터를 타입 안정하게 캡슐화하기 위해 `ResponseEntity<T>`를 사용하며, 에러 발생 시에도 `GlobalExceptionHandler`를 통해 동일한 에러 JSON 구조(`{"error": "MESSAGE"}`)를 반환하는 일관성 전략입니다.
* **[면접관 꼬리 질문]**: "성공 응답에 200 OK 대신 201 Created를 사용하는 기준은 무엇인가요?"
* **[면접자 답변 대본]**: "`POST /api/members` 회원가입처럼 서버에 새로운 자원이 정상적으로 생성된 요청에는 `201 Created`와 함께 생성된 자원의 위치(Location)를 반환하는 것이 RESTful 스펙입니다."

### Q46. Spring Security `CorsFilter`와 Custom Interceptor / Filter의 실행 순서는 어떻게 되나요?
* **정확한 대답**: `CorsFilter`는 Spring Security Filter Chain의 최상단(1번 필터) 부근에 위치하여, Custom Interceptor나 Controller에 요청이 도착하기 훨씬 전에 브라우저의 OPTIONS Preflight 요청을 미리 검증하고 응답합니다.
* **[면접관 꼬리 질문]**: "만약 CorsFilter보다 Custom Filter가 먼저 실행되면 어떤 에러가 발생하나요?"
* **[면접자 답변 대본]**: "Custom Filter에서 미인증 유저의 OPTIONS 예비 요청을 401 Unauthorized로 차단해 버려, 브라우저가 실제 요청(POST/GET)을 보내기도 전에 CORS 에러가 발생합니다."

### Q47. 데이터베이스 커넥션 풀(HikariCP)의 `connectionTimeout`과 `maxLifetime` 속성의 역할은 무엇인가요?
* **정확한 대답**: `connectionTimeout`은 애플리케이션이 풀에서 커넥션을 얻기 위해 대기하는 최대 시간(기본 30초)이며, `maxLifetime`은 커넥션이 풀 안에서 유휴 상태로 존재할 수 있는 최대 수명(기본 30분)입니다.
* **[면접관 꼬리 질문]**: "HikariCP maxLifetime 설정 시 DB의 `wait_timeout`보다 크게 설정하면 어떻게 되나요?"
* **[면접자 답변 대본]**: "DB가 먼저 커넥션을 끊어버렸는데 애플리케이션 풀은 살아있다고 착각하여, 해당 커넥션을 가져와 쿼리를 날릴 때 `CommunicationsException` 끊김 장애가 터집니다. 반드시 maxLifetime을 DB wait_timeout보다 2~3분 짧게 설정해야 합니다."

### Q48. `MemberRepository` 테스트 시 `@DataJpaTest` vs `@SpringBootTest` 선택 기준은 무엇인가요?
* **정확한 대답**: `@DataJpaTest`는 JPA 관련 컴포넌트만 슬라이스 로딩하고 기본적으로 `@Transactional` 롤백이 자동 적용되어 순수 Repository 쿼리 테스트에 최적이며, `@SpringBootTest`는 전체 의존성을 띄워 통합 검증 시 사용합니다.
* **[면접관 꼬리 질문]**: "`@DataJpaTest` 실행 시 실제 MySQL DB를 바라보게 하려면 어떻게 하나요?"
* **[면접자 답변 대본]**: "`@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)` 어노테이션을 붙여 임베디드 DB 대체 기능을 끄면 실제 설정된 MySQL DB 환경에서 레포지토리 테스트가 가능합니다."

### Q49. JPA 엔티티 설계 시 `@EqualsAndHashCode`를 함부로 사용하면 안 되는 이유는 무엇인가요?
* **정확한 대답**: Lombok의 `@EqualsAndHashCode`를 엔티티 전체 필드에 걸면, 지연 로딩 필드가 호출되어 불필요한 SQL이 쏟아지거나 영속성 컨텍스트의 객체 동일성(`==`) 원칙이 깨집니다. PK(`id`) 필드만 기준으로 구현해야 합니다.
* **[면접관 꼬리 질문]**: "엔티티의 equals & hashCode 구현 시 id가 null인 비영속 상태 엔티티는 어떻게 처리하나요?"
* **[면접자 답변 대본]**: "비영속 엔티티는 ancora id가 없어 항상 `false`가 반환될 수 있으므로, 객체 동일성(`this == o`) 또는 비즈니스 키(public_id 등)를 비교하도록 안전하게 구현해야 합니다."

### Q50. Spring Boot 4.0.0 기반 Snowthing 백엔드의 최종 아키텍처 및 품질 검증 요약은 무엇인가요?
* **정확한 대답**: Snowthing 백엔드는 Spring Boot 4.0.0 (Java 21), Spring Security 7, MySQL 8.0 인프라 기반 위에 **Dual PK 보안, 세션 고정 방어, 3회 개별 JOIN FETCH 성능 최적화, DTO record 캡슐화, Bean Validation `@Valid` 검증**을 적용하고 **25개 통합 테스트 통과**로 검증된 최상 품질의 백엔드 시스템입니다.
* **[면접관 꼬리 질문]**: "프로젝트를 진행하면서 얻은 최고의 아키텍처적 레슨은 무엇인가요?"
* **[면접자 답변 대본]**: "단순히 기능을 만드는 것에 그치지 않고, DB 인덱스 B-Tree 구조, 영속성 컨텍스트 1차 캐시, 경쟁 상태(Race Condition) 락 방어, N+1 쿼리 최적화 등 물리적 원리와 트레이드오프를 명확히 이해하고 아키텍처를 설계하는 것의 중요성을 체득한 점입니다."
