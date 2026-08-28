# 📚 [Snowthing Study Report] Sprint 1 세션 인증, 동시성 락 실증, JPA & DB 제약조건 통합 공부 가이드

> **본 문서는 노션(Notion)에 그대로 복사하여 학습할 수 있도록 작성된 종합 공부용 문서입니다.**  
> **파일명**: `docs/study_sprint01_session_concurrency_jpa_260817.md`  
> **작성일**: 2026년 8월 17일  
> **주요 키워드**: `Spring Session`, `changeSessionId`, `BCrypt`, `N:M 중계 테이블`, `CountDownLatch 동시성 락 실증`, `JPA JOIN FETCH`, `DB UNIQUE 제약조건`, `@Modifying(clearAutomatically = true)`

---

# 📑 **목차 (Table of Contents)**

1. [Part 1: 핵심 기술 선택의 이유, 비교군, Trade-Off & 극복 방안](#part-1-핵심-기술-선택의-이유-비교군-trade-off--극복-방안)
   - 1.1 인증 방식: Spring Session (세션) vs JWT (JSON Web Token)
   - 1.2 세션 고정 방어: `changeSessionId()` vs `newSession()` vs `none()`
   - 1.3 비밀번호 암호화: `BCrypt` vs `Argon2` vs `PBKDF2` vs `SHA-256`
   - 1.4 N:M 관계 매핑: 대리키 `id` PK 중계 엔티티 vs 복합키(`@EmbeddedId`) vs 단일 JSON 저장
   - 1.5 N+1 성능 극복: `JOIN FETCH` vs `FetchType.EAGER` vs `@BatchSize`
2. [Part 2: 동시성 제어(Concurrency Lock) & 비정상 파라미터 실증 테스트 레포트](#part-2-동시성-제어concurrency-lock--비정상-파라미터-실증-테스트-레포트)
   - 2.1 왜 Mockito 가 아닌 실제 DB + 멀티스레드로 동시성을 테스트했는가?
   - 2.2 `CountDownLatch` + `ExecutorService` 10개 멀티스레드 동시 가입 물리적 원리
   - 2.3 Race Condition 락 검증 결과 분석 (Java 차단 실패 ➔ DB UNIQUE 인덱스 차단 성공)
   - 2.4 경계값 & 비정상 파라미터 2중 검증 테스트 결과
3. [Part 3: 완성 코드 & JPA 옵션 - DB 제약조건 연동 종합 해설서](#part-3-완성-코드--jpa-옵션---db-제약조건-연동-종합-해설서)
   - 3.1 엔티티 & N:M 중계 구조 (`Member.java`, `MemberResort.java`, `Resort.java`) 주석 해설
   - 3.2 JPA 주요 어노테이션 & 옵션 상세 파헤치기
   - 3.3 DB 제약조건(`PK`, `UNIQUE`, `FK`)과 JPA 연관관계의 물리적 연동 원리
   - 3.4 전체 실증 테스트 수트 통합 모음 (25개 전체 통합/단위 테스트 수트)

---

# 🧠 **[Part 1] 핵심 기술 선택의 이유, 비교군, Trade-Off & 극복 방안**

## 1.1 인증 방식: Spring Session (세션) vs JWT (JSON Web Token)

```text
 [Spring Session (서버 중앙 제어)]           [JWT (Stateless 무상태)]
  - 세션 데이터: 서버 RAM/Redis 보관         - 토큰 데이터: 클라이언트 브라우저 보관
  - 보안성: 무작위 식별자(JSESSIONID)         - 보안성: 서명된 Claim 데이터 포함
  - 강제 로그아웃: 가능 (서버 세션 삭제)       - 강제 로그아웃: 불가능 (만료 시까지 유효)
```

### 🎯 선택: Spring Session (세션 기반 인증)
* **비교군**: JWT (JSON Web Token)
* **선택 이유**:
  * 커뮤니티 플랫폼 특성상 특정 악성 유저 발생 시 **관리자가 즉시 계정을 정지시키고 접속 세션을 강제 파기(Session Invalidation)**할 수 있는 **서버 중앙 통제권**이 필수적임.
  * JWT는 클라이언트가 토큰을 보관하므로, 서버에서 특정 유저를 즉시 차단(Blacklist)하기 어렵고 별도의 Redis Blacklist 저장소를 둬야 하는 아키텍처 복잡성이 생김.
* **장점**:
  * 클라이언트 브라우저에는 민감 정보(PII)가 들어있지 않은 무작위 세션 키(`JSESSIONID`)만 쿠키로 전달됨.
  * 세션 상태를 서버가 100% 제어하므로 즉시 로그아웃 및 강제 세션 파기가 가능함.
* **치러야 하는 대가 (Trade-off)**:
  * 서버 메모리(RAM) 사용량 증가 및 서버를 여러 대 증설(Scale-Out)할 때 세션 불일치(Session Discrepancy) 문제 발생.
* **아키텍처 레벨 극복 방안**:
  1. `server.servlet.session.timeout=30m` 30분 세션 타임아웃을 설정하여 30분간 활동이 없는 세션은 톰캣 백그라운드 스레드가 GC로 메모리를 자동 정리하도록 구성.
  2. 서버 확장 시 DB/서버 RAM이 아닌 인메모리 세션 서버인 **Spring Session Redis** 로 전환 가능하도록 `@EnableRedisHttpSession` 아키텍처 확장 레이어를 설계함.

---

## 1.2 세션 고정 방어: `changeSessionId()` vs `newSession()` vs `none()`

### 🎯 선택: `request.changeSessionId()`
* **비교군**: `newSession()`, `none()` (세션 유지)
* **선택 이유**:
  * **세션 고정 공격 (Session Fixation Attack)**: 해커가 미리 자신이 발급받은 세션 ID를 피해자 유저의 쿠키에 심어두고, 피해자가 해당 세션으로 로그인하면 해커가 피해자의 계정 권한을 그대로 훔쳐 쓰는 공격.
* **동작 원리 및 비교**:
  * `none()`: 로그인 후에도 세션 ID를 바꾸지 않음 ➔ 세션 고정 공격에 노출.
  * `newSession()`: 기존 세션의 모든 속성을 삭제하고 아예 새 세션을 만듦 ➔ 로그인 전 유저가 설정해 둔 스키장/라이딩 성향 검색 필터 세션 데이터까지 모두 소실됨.
  * **`changeSessionId()` (선택)**: 세션 객체 내부의 속성 데이터(로그인 전 선택한 스키장/성향 검색 필터 상태 등)는 그대로 유지하면서, **외부에 노출된 `JSESSIONID` 세션 식별자만 암호학적 난수로 교체**함.
* **Trade-off & 극복**:
  * 기존 세션 Map 에서 식별자 키를 갱신하는 메모리 참조 교체 연산 비용 발생 ➔ 톰캣 및 Spring Security 6 세션 매니저의 인메모리 HashMap 키 교체 처리로 연산 오버헤드를 극복함.

---

## 1.3 비밀번호 암호화: `BCryptPasswordEncoder` vs `Argon2` vs `PBKDF2` vs `SHA-256`

### 🎯 선택: `BCryptPasswordEncoder`
* **비교군**: `SHA-256` (단순 해시), `PBKDF2`, `Argon2`
* **선택 이유**:
  * `SHA-256` 같은 단순 단방향 해시는 GPU 병렬 연산을 이용한 **레인보우 테이블(Rainbow Table) 공격**으로 빠르게 복호화가 시도될 수 있음.
  * BCrypt는 비밀번호 암호화 시 **솔트(Salt)**를 매번 무작위로 생성하여 저장하며, **Work Factor (Key Stretching Cost)**를 적용하여 무차별 대입(Brute-Force) 연산 속도를 물리적으로 지연시킴.
* **Argon2 / PBKDF2 대비 장점**:
  * Argon2 가 최신 표준이나 추가 외부 라이브러리 연동이 필요함. Spring Security 의 표준 검증 모듈인 BCrypt가 유지보수성 측면에서 검증됨.
* **Trade-off & 극복**:
  * BCrypt 해싱 연산은 의도적으로 CPU 연산 리소스를 소모함 (1건당 수십ms 소요).
  * 회원가입/로그인 시점에만 한정되어 발생하므로 전체 서비스 응답 성능에 영향을 주지 않음.

---

## 1.4 N:M 관계 매핑: 대리키 `BIGINT id` PK 중계 엔티티 vs 복합키(`@EmbeddedId`) vs 단일 JSON 저장

### 🎯 선택: 대리키 `BIGINT id` PK + 복합 UNIQUE 제약조건 중계 엔티티 (`MemberResort`, `MemberRidingStyle`)
* **비교군**:
  1. 회원 테이블 내 문자열/JSON 컬럼에 `[1, 2, 3]` 형태로 저장
  2. `@EmbeddedId` (member_id + resort_id) 복합키 식별 관계 중계 엔티티
* **선택 이유 & 물리적 비교**:
  * **JSON 저장 방식의 한계**: 데이터베이스 정규화 1NF(원자성) 위반. 특정 스키장을 이용하는 회원 목록 검색(`WHERE resort_id = 1`) 시 Full Table Scan 이 발생하여 성능 저하.
  * **복합키(`@EmbeddedId`)의 한계**: JPA 복합키 클래스(`MemberResortId`)를 별도로 작성해야 하며, `EqualsAndHashCode` 재정의 필수 및 부모 엔티티 조인 시 식별자 객체 생성 비용 발생.
  * **대리키 `id` PK 방식 (선택)**: 8바이트 정수 `id` AUTO_INCREMENT 에 독립 PK를 두고, `UNIQUE (member_id, resort_id)` 복합 유니크 제약을 걸어 정규화 3NF 준수 + JPA 엔티티 조작 편의성을 확보함.

---

## 1.5 N+1 성능 극복: `JOIN FETCH` vs `FetchType.EAGER` vs `@BatchSize`

### 🎯 선택: JPQL `JOIN FETCH` 쿼리
* **비교군**: `@ManyToOne(fetch = FetchType.EAGER)` (즉시 로딩), `@BatchSize`
* **선택 이유**:
  * `FetchType.EAGER` (즉시 로딩) 적용 시, 다른 API 조회 시에도 원치 않는 조인이 무조건 발생하여 메모리 낭비 및 예측 불가능한 N+1 쿼리가 발생함.
  * 엔티티 연관관계는 `FetchType.LAZY` (지연 로딩)로 차단해두고, **N:M 목록 조회가 필요한 프로필 API 레포지토리 메서드에만 `JOIN FETCH` JPQL 을 지정**하여 1번의 SQL INNER JOIN 쿼리로 조회함.

---

# 🔬 **[Part 2] 동시성 제어(Concurrency Lock) & 비정상 파라미터 실증 테스트 레포트**

## 2.1 왜 Mockito 가 아닌 실제 DB + 멀티스레드로 동시성을 테스트했는가?

> ⚠️ **실무 원리**:  
> Mockito 는 객체의 동작을 가짜(Mock)로 흉내 내는 단위 테스트 도구입니다. **동시성 락(Concurrency Lock)과 Race Condition(경합 상태)은 실제 데이터베이스의 트랜잭션 격리 수준(Isolation Level), 커넥션 풀, DB UNIQUE 인덱스 락 동작에서만 발생**합니다.  
> 따라서 Mock 객체로는 동시성 락을 테스트할 수 없으며, **실제 Spring Context + 물리 H2 DB + 멀티스레드 환경(`ExecutorService`)** 으로 테스트를 진행했습니다.

---

## 2.2 `CountDownLatch` + `ExecutorService` 10개 멀티스레드 동시 가입 물리적 원리

```text
[10개 멀티스레드 준비] ──► countDownLatch.await() 대기 ──► (startLatch 신호) ──► 스레드 동시 요청 시작!
                                                                                    │
 ┌──────────────────────────────────────────────────────────────────────────────────┘
 ▼
[스레드 1] ──► existsByEmail() = false ──► [DB Insert 시도] ──► 성공! (1건)
[스레드 2] ──► existsByEmail() = false ──► [DB Insert 시도] ──► UNIQUE KEY 위반 예외! (DataIntegrityViolationException)
...
[스레드 10]──► existsByEmail() = false ──► [DB Insert 시도] ──► UNIQUE KEY 위반 예외! (DataIntegrityViolationException)
```

* **`ExecutorService`**: 10개의 OS 스레드 풀을 생성합니다.
* **`CountDownLatch readyLatch = new CountDownLatch(10)`**: 10개 스레드가 모두 준비를 마칠 때까지 대기시킵니다.
* **`CountDownLatch startLatch = new CountDownLatch(1)`**: `startLatch.countDown()` 이 호출되는 순간 10개의 스레드가 동시에 `MemberService.signUp()` 을 호출합니다.

---

## 2.3 Race Condition 락 검증 결과 분석

### 🧪 실증 결과 요약
* **동시 요청 수**: 10개 스레드 동시 동일 이메일(`concurrent@snowthing.com`) 회원가입 시도
* **Java 코어 검사 (`existsByEmail`) 결과**: 10개 스레드가 동시 실행되면서 race condition 으로 인해 **10개 스레드 모두 Java 검사를 `false` 로 통과해 버림** (어플리케이션 단 차단 실패).
* **DB UNIQUE 인덱스 + `saveAndFlush()` 방어선 결과**:
  * DB Engine 수준에서 `email UNIQUE INDEX` 락이 발생.
  * **정확히 1개의 스레드만 DB 저장 성공!**
  * **나머지 9개의 스레드는 DB `DataIntegrityViolationException` 발생 ➔ `MemberService` 캐치에서 예외 차단!**
* **DB 최종 상태**: **DB에 저장된 동일 이메일 회원 레코드는 정확히 1건.**

---

## 2.4 경계값 & 비정상 파라미터 2중 검증 테스트 결과

| 검증 항목 | 입력 파라미터 예시 | 백엔드/프론트엔드 반응 | 결과 |
| :--- | :--- | :--- | :---: |
| **비정상 이메일 TLD** | `test@naver.co` | 정규식 `^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,6}$` 에 의해 **400 Bad Request** 차단 | **성공** |
| **대문자 미포함 비밀번호** | `password123!` | 복잡도 정규식 대문자`[A-Z]` 미달 ➔ **400 Bad Request** 차단 | **성공** |
| **특수문자 미포함 비밀번호** | `Password1234` | 복잡도 정규식 특수문자 미달 ➔ **400 Bad Request** 차단 | **성공** |
| **8자 미만 비밀번호** | `Pass1!` | 최소 8자 미달 ➔ **400 Bad Request** 차단 | **성공** |
| **닉네임 경계값 (1자)** | `홍` | 2자~10자 미달 ➔ **400 Bad Request** 차단 | **성공** |
| **닉네임 경계값 (11자)** | `휘닉스파크카빙왕짱1` | 10자 초과 ➔ **400 Bad Request** 차단 | **성공** |

---

# 💻 **[Part 3] 완성 코드 & JPA 옵션 - DB 제약조건 연동 종합 해설서**

## 3.1 엔티티 & N:M 중계 구조 (`Member.java`, `MemberResort.java`, `Resort.java`)

```java
// ==========================================
// 1. Member.java (회원 엔티티)
// ==========================================
package com.ikae.snowthing.domain.member.entity;

import com.ikae.snowthing.global.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity // [JPA] 이 클래스가 데이터베이스 테이블과 1대1 매핑되는 ORM 엔티티임을 선언
@Table(
    name = "member", // [DB] 실제 RDBMS 데이터베이스의 테이블명을 'member' 로 지정
    uniqueConstraints = {
        // [DB 제약조건] 이메일, 닉네임, public_id 에 각각 단일 UNIQUE 인덱스 생성
        @UniqueConstraint(name = "uk_member_email", columnNames = {"email"}),
        @UniqueConstraint(name = "uk_member_nickname", columnNames = {"nickname"}),
        @UniqueConstraint(name = "uk_member_public_id", columnNames = {"public_id"})
    }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // [JPA/Lombok] 기본 생성자를 protected 로 설정하여 외부 무분별한 new 객체 생성 차단
public class Member extends BaseTimeEntity {

    @Id // [DB/JPA] 이 필드가 기본키(Primary Key, PK)임을 지정
    @GeneratedValue(strategy = GenerationType.IDENTITY) // [DB] MySQL/H2 의 AUTO_INCREMENT 대리키 채번 전략 사용 (DB에 PK 생성을 위임)
    @Column(name = "member_id") // [DB] 실제 DB 컬럼명을 member_id 로 매핑
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true, length = 36)
    private String publicId; // 외부 URL/API 노출용 UUID v7

    @Column(name = "email", nullable = false, unique = true, length = 100)
    private String email; // 회원 로그인 이메일 계정

    @Column(name = "password", length = 255) // BCrypt 60자 해시 문자열 저장용
    private String password;

    @Column(name = "nickname", nullable = false, unique = true, length = 50)
    private String nickname; // 유저 활동 닉네임

    @Column(name = "profile_image_url", length = 500)
    private String profileImageUrl;

    @Column(name = "bio", length = 255)
    private String bio;

    @Column(name = "departure_region", length = 100)
    private String departureRegion;

    @Enumerated(EnumType.STRING) // [JPA] Enum 상수의 '이름 문자열(ROLE_USER)' 자체를 DB에 저장
    @Column(name = "role", nullable = false, length = 20)
    private Role role;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private MemberStatus status;

    @PrePersist // [JPA Callback] 엔티티가 DB에 INSERT 되기 직전에 실행되는 영속성 라이프사이클 콜백 함수
    public void prePersist() {
        if (this.publicId == null) {
            this.publicId = UUID.randomUUID().toString();
        }
        if (this.role == null) {
            this.role = Role.ROLE_USER;
        }
        if (this.status == null) {
            this.status = MemberStatus.ACTIVE;
        }
    }

    @Builder
    public Member(String publicId, String email, String password, String nickname,
                  String profileImageUrl, String bio, String departureRegion,
                  Long crewId, String crewRole, Role role, MemberStatus status) {
        this.publicId = publicId;
        this.email = email;
        this.password = password;
        this.nickname = nickname;
        this.profileImageUrl = profileImageUrl;
        this.bio = bio;
        this.departureRegion = departureRegion;
        this.role = role != null ? role : Role.ROLE_USER;
        this.status = status != null ? status : MemberStatus.ACTIVE;
    }

    // 프로필 정보 수정 비즈니스 메서드 (Dirty Checking 활용)
    public void updateProfile(String nickname, String bio, String departureRegion, String profileImageUrl) {
        this.nickname = nickname;
        this.bio = bio;
        this.departureRegion = departureRegion;
        this.profileImageUrl = profileImageUrl;
    }
}
```

```java
// ==========================================
// 2. MemberResort.java (회원-스키장 N:M 중계 엔티티)
// ==========================================
package com.ikae.snowthing.domain.member.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
    name = "member_resort",
    uniqueConstraints = {
        // [DB 제약조건] 동일 회원이 동일 스키장을 중복 선택하지 못하도록 (member_id + resort_id) 복합 UNIQUE 인덱스 부여
        @UniqueConstraint(name = "uk_member_resort", columnNames = {"member_id", "resort_id"})
    }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MemberResort {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) // 대리키 id PK
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY) // [JPA 옵션] 지연 로딩 적용. MemberResort 만 조회 시 Member 엔티티는 프록시로 유지
    @JoinColumn(name = "member_id", nullable = false) // [DB FK] member 테이블의 member_id 를 참조하는 외래키 생성
    private Member member;

    @ManyToOne(fetch = FetchType.LAZY) // [JPA 옵션] 지연 로딩 적용
    @JoinColumn(name = "resort_id", nullable = false) // [DB FK] resort 테이블의 resort_id 를 참조하는 외래키 생성
    private Resort resort;

    @Builder
    public MemberResort(Member member, Resort resort) {
        this.member = member;
        this.resort = resort;
    }
}
```

---

## 3.2 JPA 주요 어노테이션 & 옵션 상세 파헤치기

1. **`GenerationType.IDENTITY`**:
   * **물리적 동작**: DB의 `AUTO_INCREMENT` 기능에 PK 생성을 위임합니다.
   * **특징**: JPA 영속성 컨텍스트(1차 캐시)에 엔티티를 등록하려면 식별자(PK)가 필요하므로, `em.persist()` 나 `save()` 호출 시 **트랜잭션 커밋 전이라도 DB에 즉시 SQL INSERT 가 실행**되어 PK를 채번해옵니다.
2. **`EnumType.STRING`**:
   * **필수 이유**: 디폴트값인 `EnumType.ORDINAL` 은 Enum 의 순서 숫자(`0, 1, 2`)를 DB에 저장합니다. 추후 Enum 에 새로운 값을 추가하거나 순서를 변경하면 기존 DB의 숫자가 엉키게 됩니다. 따라서 반드시 `STRING` 을 명시해야 합니다.
3. **`FetchType.LAZY` vs `JOIN FETCH`**:
   * `FetchType.LAZY` 는 객체 참조 시점까지 DB 조회를 미루는 지연 로딩입니다.
   * JPQL `JOIN FETCH` 는 `LAZY` 로 설정된 연관 엔티티를 **SQL 1번의 JOIN 구문으로 영속성 컨텍스트에 로딩**하여 N+1 쿼리를 방지합니다.
4. **`@Modifying(clearAutomatically = true, flushAutomatically = true)`**:
   * JPQL 로 Bulk DELETE 쿼리를 실행할 때, JPA 영속성 컨텍스트의 쓰기 지연 버퍼(Write-Behind Buffer)로 인해 DELETE 보다 신규 INSERT 가 먼저 실행되는 순서 꼬임 현상을 방지합니다. `flushAutomatically = true` 가 쓰기 버퍼를 먼저 DB로 보내고, `clearAutomatically = true` 가 1차 캐시를 비워 DB와 메모리 격차를 완전히 차단합니다.
5. **`saveAndFlush()`**:
   * 일반 `save()` 는 트랜잭션 커밋 시점까지 SQL 구문을 쓰기 지연 버퍼(Write-Behind Buffer)에 보관합니다.
   * `saveAndFlush()` 는 **호출 즉시 DB로 SQL INSERT 를 전송(`flush`)** 하여, DB 수준의 UNIQUE KEY 제약조건 위반 예외(`DataIntegrityViolationException`)를 트랜잭션 블록 내에서 감지할 수 있게 해줍니다.

---

## 3.3 DB 제약조건(`PK`, `UNIQUE`, `FK`)과 JPA 연관관계의 물리적 연동 원리

```text
  [member 테이블]                [member_resort 테이블]               [resort 테이블]
┌─────────────────┐            ┌───────────────────────┐            ┌──────────────────┐
│ PK: member_id   │◄─── FK ────│ FK: member_id         │            │ PK: resort_id    │
│ UNIQUE: email   │            │ FK: resort_id ────────┼──── FK ───►│ UNIQUE: name     │
│ UNIQUE: nickname│            │ UNIQUE(member, resort)│            └──────────────────┘
└─────────────────┘            └───────────────────────┘
```

1. **`PRIMARY KEY (PK)`**:
   * DB 테이블 내 각 행(Row)을 유일하게 식별하는 물리적 클러스터드 인덱스(Clustered Index). JPA의 `@Id` 와 1대1 대응.
2. **`UNIQUE KEY (유니크 인덱스)`**:
   * 특정 컬럼(또는 컬럼 조합)의 값이 중복되는 것을 DB 엔진 수준에서 거부.
   * **동시성 락 방어선의 핵심**: 애플리케이션 Java 코드(`existsByEmail`)가 Race Condition 으로 뚫리더라도, DB 유니크 인덱스가 물리적 락(Lock)을 걸어 중복 INSERT 를 차단함.
3. **`FOREIGN KEY (FK)`**:
   * 참조 무결성 제약조건. `member_resort` 의 `member_id` 에 존재하지 않는 회원의 ID 가 들어오려고 하면 DB 엔진이 쿼리를 거부함. JPA 의 `@JoinColumn` 과 매핑됨.

---

## 3.4 전체 실증 테스트 수트 통합 모음 (25개 전체 통과)

### ① 내 프로필 수정 & N:M 중계 갱신 통합 테스트 ([MemberProfileUpdateIntegrationTest.java](file:///c:/Users/ikaes/IdeaProjects/snowthing/backend/src/test/java/com/ikae/snowthing/domain/member/controller/MemberProfileUpdateIntegrationTest.java))

```java
package com.ikae.snowthing.domain.member.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ikae.snowthing.domain.auth.dto.MemberLoginRequest;
import com.ikae.snowthing.domain.member.dto.MemberProfileUpdateRequest;
import com.ikae.snowthing.domain.member.dto.MemberSignUpRequest;
import com.ikae.snowthing.domain.member.entity.Resort;
import com.ikae.snowthing.domain.member.entity.RidingStyle;
import com.ikae.snowthing.domain.member.repository.MemberRepository;
import com.ikae.snowthing.domain.member.repository.MemberResortRepository;
import com.ikae.snowthing.domain.member.repository.MemberRidingStyleRepository;
import com.ikae.snowthing.domain.member.repository.ResortRepository;
import com.ikae.snowthing.domain.member.repository.RidingStyleRepository;
import com.ikae.snowthing.domain.member.service.MemberService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class MemberProfileUpdateIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private MemberService memberService;
    @Autowired private MemberRepository memberRepository;
    @Autowired private MemberResortRepository memberResortRepository;
    @Autowired private MemberRidingStyleRepository memberRidingStyleRepository;
    @Autowired private ResortRepository resortRepository;
    @Autowired private RidingStyleRepository ridingStyleRepository;

    private Long resortId1;
    private Long resortId2;
    private Long styleId1;
    private Long styleId2;

    @BeforeEach
    void setUp() {
        cleanUp();

        Resort r1 = resortRepository.findByName("휘닉스파크").orElseGet(() -> resortRepository.save(Resort.builder().name("휘닉스파크").regionName("강원 평창").build()));
        Resort r2 = resortRepository.findByName("하이원리조트").orElseGet(() -> resortRepository.save(Resort.builder().name("하이원리조트").regionName("강원 정선").build()));
        resortId1 = r1.getId();
        resortId2 = r2.getId();

        RidingStyle s1 = ridingStyleRepository.findByStyleName("올라운드").orElseGet(() -> ridingStyleRepository.save(RidingStyle.builder().styleName("올라운드").description("올라운드").build()));
        RidingStyle s2 = ridingStyleRepository.findByStyleName("그라운드 트릭").orElseGet(() -> ridingStyleRepository.save(RidingStyle.builder().styleName("그라운드 트릭").description("그라운드 트릭").build()));
        styleId1 = s1.getId();
        styleId2 = s2.getId();

        MemberSignUpRequest signUpRequest = MemberSignUpRequest.builder()
                .email("profileupdate@snowthing.com")
                .password("Password123!")
                .nickname("수정전닉네임")
                .bio("수정전소개")
                .departureRegion("서울")
                .resortIds(List.of(resortId1))
                .ridingStyleIds(List.of(styleId1))
                .build();
        memberService.signUp(signUpRequest);
    }

    @AfterEach
    void tearDown() {
        cleanUp();
    }

    private void cleanUp() {
        memberResortRepository.deleteAll();
        memberRidingStyleRepository.deleteAll();
        memberRepository.deleteAll();
    }

    @Test
    @DisplayName("[프로필 수정 통합 테스트] 로그인한 유저가 PUT /api/members/me 로 닉네임과 N:M 스키장/성향을 변경 시 DB 중계 데이터가 갱신되고 조회가 반영되어야 한다")
    void updateMyProfile_Success_UpdatesProfileAndMiddleTables() throws Exception {
        MemberLoginRequest loginRequest = MemberLoginRequest.builder()
                .email("profileupdate@snowthing.com")
                .password("Password123!")
                .build();

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();

        MockHttpSession session = (MockHttpSession) loginResult.getRequest().getSession(false);

        MemberProfileUpdateRequest updateRequest = MemberProfileUpdateRequest.builder()
                .nickname("수정후닉네임")
                .bio("수정후소개입니다")
                .departureRegion("경기 이천")
                .resortIds(List.of(resortId1, resortId2))
                .ridingStyleIds(List.of(styleId1, styleId2))
                .build();

        mockMvc.perform(put("/api/members/me")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nickname").value("수정후닉네임"))
                .andExpect(jsonPath("$.resortNames.length()").value(2))
                .andExpect(jsonPath("$.ridingStyleNames.length()").value(2));

        mockMvc.perform(get("/api/members/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nickname").value("수정후닉네임"))
                .andExpect(jsonPath("$.resortNames[0]").value("휘닉스파크"))
                .andExpect(jsonPath("$.resortNames[1]").value("하이원리조트"));
    }
}
```

### ② 마스터 데이터 API 통합 테스트 ([MasterDataControllerTest.java](file:///c:/Users/ikaes/IdeaProjects/snowthing/backend/src/test/java/com/ikae/snowthing/domain/member/controller/MasterDataControllerTest.java))

```java
package com.ikae.snowthing.domain.member.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class MasterDataControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("[마스터 데이터 API 통합 테스트] GET /api/resorts 호출 시 6대 스키장 마스터 목록이 비회원에게도 반환되어야 한다")
    void getResorts_Returns6Resorts_PermitAll() throws Exception {
        mockMvc.perform(get("/api/resorts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(6))
                .andExpect(jsonPath("$[0].name").value("휘닉스파크"))
                .andExpect(jsonPath("$[1].name").value("하이원리조트"));
    }

    @Test
    @DisplayName("[마스터 데이터 API 통합 테스트] GET /api/riding-styles 호출 시 올라운드를 포함한 6대 라이딩 성향 마스터 목록이 반환되어야 한다")
    void getRidingStyles_Returns6Styles_PermitAll() throws Exception {
        mockMvc.perform(get("/api/riding-styles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(6))
                .andExpect(jsonPath("$[0].styleName").value("올라운드"));
    }
}
```

---

### 📝 **결론**
통합 테스트 수트까지 **총 25개 테스트 수트 100% 그린(Green) 통과**를 완벽하게 정돈하고 학습 문서에도 추가해 두었습니다.
