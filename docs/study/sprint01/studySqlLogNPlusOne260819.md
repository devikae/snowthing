# 📚 [TIL] JPA N+1 문제의 본질, MultipleBagFetchException 및 실제 SQL 로깅 실증 분석 (2026-08-19)

> **노션(Notion) 복사용 학습 정리 문서**  
> 본 문서는 Snowthing 백엔드 프로젝트 개발 중 발생할 수 있는 JPA 쿼리 N+1 문제, 다중 1:N 조인 시 발생하는 `MultipleBagFetchException`의 원리와 실증 SQL 로깅 결과를 7대 기술 필수 요소 체계에 맞춰 정리한 공부 기록입니다.

---

## 📌 PART 1. JPA N+1 문제 및 Fetch Join 7대 필수 서술 요소 체계

### ① 개념 (무엇인가 - 명확한 정의)
* **N+1 문제(N+1 Select Problem)**: 1건의 주 엔티티(예: Member)를 조회하는 쿼리(1회)를 실행했을 때, 연관된 지연 로딩(LAZY) 엔티티(예: MemberResort)를 참조하는 과정에서 **연관 엔티티 N개에 대해 N번의 추가 SELECT 쿼리가 쏟아져 나오는 성능 저하 현상**을 의미합니다.
* **JPQL Fetch Join**: JPQL 구문 내에서 `JOIN FETCH` 키워드를 사용하여, 연관된 엔티티나 컬렉션을 단 1회의 SQL 조인(JOIN)으로 영속성 컨텍스트에 한 번에 끌어오는 JPA 전용 최적화 기법입니다.

### ② 왜 사용하는지 (Why - 도입 목적 및 배경)
* **DB I/O 네트워크 오버헤드 폭발 방지**: N+1 문제가 발생하면 회원 1,000명 조회 시 1,001번의 DB 네트워크 둥지를 틀게 되어 DB Connection Pool 고갈 및 응답 지연(Latency)이 폭발합니다.
* **단 1회의 SQL 전송으로 성능 최적화**: `JOIN FETCH`를 통해 1,001번의 쿼리를 단 1번의 쿼리로 줄여 DB 커넥션 비용을 99.9% 절감합니다.

### ③ 어떨 때 사용하는지 (When - 적합한 유즈케이스 및 사용 상황)
* `@ManyToOne`, `@OneToOne` 단일 연관 엔티티를 즉시 한 번에 조회해야 할 때.
* `@OneToMany`, `@ManyToMany` 다대다/일대다 컬렉션 연관관계를 **단 1개만** 한 번에 조인하여 조회해야 할 때.

### ④ 어떻게 사용하는지 (How - 구체적 구현 방식 및 코드 예시)

#### 1) MemberResortRepository JPQL Fetch Join 구문
```java
public interface MemberResortRepository extends JpaRepository<MemberResort, Long> {

    // MemberResort 조회 시 연관된 Resort 엔티티를 단 1회의 JOIN SQL로 함께 조회
    @Query("SELECT mr FROM MemberResort mr JOIN FETCH mr.resort WHERE mr.member.id = :memberId")
    List<MemberResort> findAllByMemberIdWithResort(@Param("memberId") Long memberId);
}
```

#### 2) MemberRidingStyleRepository JPQL Fetch Join 구문
```java
public interface MemberRidingStyleRepository extends JpaRepository<MemberRidingStyle, Long> {

    // MemberRidingStyle 조회 시 연관된 RidingStyle 엔티티를 단 1회의 JOIN SQL로 함께 조회
    @Query("SELECT mrs FROM MemberRidingStyle mrs JOIN FETCH mrs.ridingStyle WHERE mrs.member.id = :memberId")
    List<MemberRidingStyle> findAllByMemberIdWithRidingStyle(@Param("memberId") Long memberId);
}
```

### ⑤ 장점은 무엇인지 (Pros / Advantages)
1. **N+1 쿼리 원천 차단**: 연관 엔티티 수에 비례하여 쿼리가 늘어나는 비효율이 100% 제거됩니다.
2. **객체 그래프 탐색의 안정성**: 지연 로딩(LAZY) 상태에서 발생하던 `LazyInitializationException` 에러가 원천 예방됩니다.

### ⑥ 다른 기술/대안은 무엇이 있는지 (Alternatives - 타 기술과의 비교)

| 비교 항목 | JPQL `JOIN FETCH` | `@EntityGraph` | `@BatchSize` (hibernate.default_batch_fetch_size) | Querydsl DTO Projection |
| :--- | :--- | :--- | :--- | :--- |
| **방식** | JPQL 문법에 `JOIN FETCH` 직접 명시 | 어노테이션 기반 `attributePaths` 지정 | `IN (?, ?, ?)` 쿼리로 N개를 모아서 묶음 조회 | SQL DTO 직렬화 조인 쿼리 작성 |
| **장점** | 명확한 SQL 제어, 컴파일 시 검증 | JPQL을 작성하지 않고 재사용 가능 | 다중 1:N 컬렉션 페치 가능, 페이징 유지 | 객체 변환 오버헤드 0, 최고의 조회 성능 |
| **단점** | 다중 컬렉션 페치 불가 (`MultipleBagFetchException`) | 쿼리가 복잡해지면 가독성 저하 | N개 묶음 쿼리가 여전히 2~3회 발생 | 엔티티 영속성 컨텍스트 1차 캐시 관리 불가 |

### ⑦ 트레이드오프는 무엇인지 (Trade-off & 극복 방안)
* **트레이드오프 (MultipleBagFetchException 위협)**:
  - JPA 자바 명세상 2개 이상의 일대다(`1:N`) List 컬렉션(`member_resort`, `member_riding_style`)을 단 1개의 JPQL 쿼리에서 동시에 `JOIN FETCH` 하면, 카테시안 곱(Cartesian Product) 데이터 뻥튀기로 인해 **`MultipleBagFetchException` 에러가 발생하며 서버 기동이 중단**됩니다.
* **아키텍처/서비스 레벨 극복 방안**:
  - 회원 1명당 총 3회의 쿼리로 분리하여 조회:
    1. `Member` 회원 기본 정보 조회 (1회)
    2. `MemberResort` + `Resort` Join Fetch (1회)
    3. `MemberRidingStyle` + `RidingStyle` Join Fetch (1회)
  - 이 방식은 회원 수가 N명으로 늘어나더라도 **쿼리 수가 N에 따라 증가하는 N+1이 아니라, 항상 고정된 3회 쿼리만 실행**되므로 `MultipleBagFetchException`을 피하고 N+1을 완벽히 극복하는 최적의 실무 아키텍처 구조입니다.

---

## 🧪 PART 2. 실제 백엔드 실행 SQL 로그 실증 검증 (Empirical Verification)

실제 백엔드 통합 테스트 실행 시 Hibernate가 데이터베이스(H2/MySQL)로 전송한 **실제 SQL 쿼리 로깅 결과**입니다:

### 1. `GET /api/members/me` 회원 프로필 조회 시 실행된 SQL 로그

```sql
-- 쿼리 1: 회원 기본 정보 조회 (1회)
Hibernate: 
    select
        m1_0.member_id,
        m1_0.created_at,
        m1_0.updated_at,
        m1_0.bio,
        m1_0.departure_region,
        m1_0.email,
        m1_0.nickname,
        m1_0.password,
        m1_0.profile_image_url,
        m1_0.public_id,
        m1_0.role,
        m1_0.status 
    from
        member m1_0 
    where
        m1_0.email=?

-- 쿼리 2: N:M 선호 스키장 JOIN FETCH (1회)
Hibernate: 
    select
        mr1_0.member_resort_id,
        mr1_0.created_at,
        mr1_0.updated_at,
        mr1_0.member_id,
        r1_0.resort_id,
        r1_0.name,
        r1_0.region_name 
    from
        member_resort mr1_0 
    join
        resort r1_0 
            on r1_0.resort_id=mr1_0.resort_id 
    where
        mr1_0.member_id=?

-- 쿼리 3: N:M 라이딩 성향 JOIN FETCH (1회)
Hibernate: 
    select
        mrs1_0.member_riding_style_id,
        mrs1_0.created_at,
        mrs1_0.updated_at,
        mrs1_0.member_id,
        rs1_0.riding_style_id,
        rs1_0.description,
        rs1_0.style_name 
    from
        member_riding_style mrs1_0 
    join
        riding_style rs1_0 
            on rs1_0.riding_style_id=mrs1_0.riding_style_id 
    where
        mrs1_0.member_id=?
```

---

## 💡 결론 및 실증 요약

* **N+1 발생 여부**: ❌ **발생하지 않음 (N+1 100% 원천 차단)**
* **실행 쿼리 총 수**: **고정 3회 (회원 100명이 조회하더라도 쿼리 수는 증가하지 않음)**
* **MultipleBagFetchException 예방**: 다중 `1:N` 컬렉션을 2개의 `JOIN FETCH` 분리 쿼리로 이관하여 카테시안 곱 뻥튀기와 런타임 예외를 완전히 차단함.
