# 🏔️ Snowthing (눈팅)

> **전국 스키장 정보와 라이딩 경험을 나누고, 함께 탈 유저를 찾는 윈터스포츠 전용 커뮤니티 플랫폼**

---

##  1. 프로젝트 개요 (Overview)

* **서비스명**: Snowthing (눈팅)
* **목적**: 기존 노후화된 윈터스포츠 커뮤니티의 불편함(http 접속, 보안 취약, 이미지 첨부 오류 등)을 극복하고, 스노보더/스키어를 위한 커뮤니티 플랫폼을 제공합니다.
* **1차 MVP 핵심 타겟**:
  1. **숙련 이용자 (시즌 보더)**: 실시간 설질/현장 정보 공유, 프로필 N:M 중계(베이스 스키장/라이딩 성향) 등록
  2. **초보 이용자 (입문 보더)**: 부담 없는 비회원 익명 작성 및 정제된 Q&A 지식 탐색

---

##  2. 기술 스택 (Tech Stack)

### Frontend
* **Core**: Next.js 16 (App Router), TypeScript, React 19
* **State & Fetching**: React State, Fetch API (`credentials: 'include'`)
* **Styling**: Vanilla CSS, UI Component System

### Backend
* **Core**: Java 21 (LTS), Spring Boot 3.3.x, Spring Data JPA
* **Security & Auth**: Spring Security 6, Spring Session (Servlet Memory / Redis Ready), BCrypt
* **Build & Test**: Gradle, JUnit5, Mockito, MockMvc

### Database & Cache
* **RDBMS**: H2 (In-Memory Dev), MySQL 8.0 (Production)
* **In-Memory Cache**: Spring Session Redis (Scale-out Ready)

---

## 3. 세션 기반 인증 & 주요 아키텍처 설계 고민과 해결 (Architecture & Auth Flow)

Snowthing은 사용자 경험(UX) 최우선과 데이터 무결성 및 보안성을 수호하기 위해 다음과 같은 핵심 아키텍처 설계를 선택하였습니다.

```mermaid
sequenceDiagram
    autonumber
    actor User as 👤 사용자 (Browser)
    participant Client as 💻 Next.js (Port 3000)
    participant Server as ⚙️ Spring Boot (Port 8080)
    participant Session as 🧠 Tomcat Session Manager

    Note over User, Server: 1. 비회원 접속 & 회원가입/로그인 시도
    User->>Client: 폼 입력 (이메일, 비밀번호, N:M 체크박스)
    Client->>Server: POST /api/auth/login (with credentials: 'include')
    
    Note over Server: 2. 비밀번호 정밀 정규식 & BCrypt 해시 검증
    Server->>Server: passwordEncoder.matches(raw, encoded)
    
    Note over Server, Session: 3. 세션 고정 방어 (Session Fixation Defense)
    Server->>Session: request.changeSessionId() 호출! (세션 식별자 교체)
    Session-->>Server: 신규 32자리 JSESSIONID 발급 (기존 스키장/성향 검색 필터 세션 데이터 유지)
    
    Server-->>Client: Set-Cookie: JSESSIONID=A1B2...; Path=/; HttpOnly; SameSite=Lax
    Client-->>User: 로그인 성공 (메인 프로필 대시보드 전환)

    Note over User, Server: 4. 인증된 API 요청 (프로필 조회/수정)
    User->>Client: 프로필 수정 클릭 (PUT /api/members/me)
    Client->>Server: PUT /api/members/me (Cookie: JSESSIONID=A1B2...)
    Server->>Session: JSESSIONID 세션 검증 & SecurityContext 복원
    Server-->>Client: 200 OK (수정된 프로필 & N:M 태그 칩 반환)

    Note over User, Server: 5. 세션 로그아웃 (3단계 소멸 프로세스)
    User->>Client: 로그아웃 버튼 클릭
    Client->>Server: POST /api/auth/logout
    Server->>Server: 1) ThreadLocal.clearContext() 청소
    Server->>Session: 2) session.invalidate() 톰캣 세션 파기
    Server-->>Client: 3) Set-Cookie: JSESSIONID=; Max-Age=0 (쿠키 즉시 만료)
```

---

### 핵심 아키텍처 고민 및 기술적 의사결정 

#### 1. 공통 엔티티와 JPA Auditing (`@EnableJpaAuditing`) 도입

`createdAt`, `updatedAt` 컬럼은 회원가입, 게시글, 댓글 등 시스템 전체 도메인에서 공통으로 사용됩니다. 기존에는 애플리케이션 레벨에서 직접 `set`을 호출하거나 DB 레벨의 `sysdate`를 이용하려 했으나 여러 트레이드오프가 존재했습니다.

1. **애플리케이션 수동 주입 방식**: 엔티티마다 시간 생성 코드가 중복되고, 개발자의 실수로 `null`이 들어갈 위험이 있습니다.
2. **DB 레벨 `sysdate` 주입 방식**: JPA 1차 캐시 메모리에 올라간 엔티티 객체는 두 필드를 `null`로 가지고 있어, 회원가입 직후 응답 DTO를 반환할 때 시간이 `null`로 출력되는 **1차 캐시 메모리 ↔ DB 간 데이터 불일치**가 발생합니다.

- **해결 방안**: `BaseTimeEntity` 공통 추상 엔티티를 작성하고 JPA 영속성 라이프사이클 이벤트를 감시하는 `AuditingEntityListener`를 적용했습니다.  
  SQL 패킷이 DB로 넘어가기 바로 전 **자바 메모리 객체의 두 필드에 현재 시각을 먼저 채워 넣도록 처리**하여 코드 중복과 1차 캐시 데이터 불일치를 동시에 해결했습니다.

---

#### 2. 단일 세션 제한 ➔ 다중 디바이스 지원 및 30일 Remember-Me 슬라이딩 세션

초기에는 온라인 게임이나 은행 앱처럼 계정 도용 방지를 위해 하나의 세션만 허용하는 단일 세션을 구상했으나, 본 커뮤니티의 특성에 맞춰 **유저의 이탈률을 낮추고 장시간 체류하도록 만드는 것**이 서비스의 핵심 목표였습니다.

- **정책 비교 및 선택**:
  1. **단일 세션 + 짧은 만료시간 (초기안)**: 모바일/웹 동시 접속이 불가능하여 유저 경험(UX)이 저하되고 재로그인 스트레스가 증가함.
  2. **무제한 세션 (대안)**: 로그인 유저가 늘어날수록 서버 메모리(RAM)에 세션 객체가 누적되어 **OutOfMemory(OOM)로 인한 서버 다운** 위험 발생.
  3. **[선택] 다중 세션 지원 + 슬라이딩 세션 + Remember-Me (30일)**:
     - **기본 세션 1시간**: 활동 중인 유저는 세션 만료시간이 지속적으로 연장(Sliding Session)되어 끊김 없는 연속적인 경험 제공.
     - **Remember-Me (30일)**: 로그인 시 체크박스를 선택한 유저에게 30일 장기 세션을 부여하여 이용 편리성 확보.
     - **메모리 보호**: 30일 동안 활동이 전혀 없는 만료된 세션은 서블릿 컨테이너가 메모리에서 즉시 파기(Garbage Collection)하도록 구성하여 메모리 고갈 방지.

---

#### 3. 세션 고정 공격(Session Fixation Attack) 방어 메커니즘

로그인 성공 시 단순 기본 세션 발급만 수행하면, 로그인 전 해커가 유저 브라우저에 미리 심어둔 비회원 세션 ID가 로그인 시 유저의 권한 세션으로 그대로 승격되어 **공격자가 유저의 로그인 권한을 훔쳐내는 세션 탈취(Session Hijacking)** 위협에 무방비로 노출됩니다.

- **해결 방안**: 로그인 성공 직후 Spring Security의 `request.changeSessionId()`를 명시적으로 호출합니다.  
  기존 세션에 보관되어 있던 속성 데이터(장바구니 등)는 메모리 상에서 100% 보존하면서, 클라이언트 브라우저로 내려주는 **세션 식별자 키(`JSESSIONID`)만을 32자리 새 난수로 즉시 재발급**하여 공격자의 이전 세션 ID를 물리적으로 완전히 무효화시켰습니다.

---

## 4. 프로젝트 물리 디렉토리 구조 (Project Structure)

```
snowthing/ (프로젝트 최상위 루트)
├── backend/                  # Spring Boot 백엔드 애플리케이션
│   ├── src/main/java/com/ikae/snowthing/
│   │   ├── domain/auth/      # 세션 로그인/로그아웃 컨트롤러 & 서비스
│   │   ├── domain/member/    # 회원, N:M 스키장/성향 엔티티 & 레포지토리
│   │   └── global/config/    # SecurityConfig, CorsConfig, DataInitializer
│   └── src/test/java/        # 25개 백엔드 단위/통합/보안 테스트 수트
├── frontend/                 # Next.js 프론트엔드 애플리케이션
│   ├── app/signup/page.tsx   # 이메일/비밀번호 정규식 & N:M 체크박스 회원가입 UI
│   ├── app/login/page.tsx    # credentials: 'include' 세션 로그인 UI
│   └── app/page.tsx          # 메인 프로필 대시보드 & 프로필 수정 UI (PUT)
├── docs/                     # 기획, 아키텍처, ERD, API 명세 문서
│   ├── conception/           # 기획 및 아키텍처 명세서 (sprint01/)
│   └── project/              # work.md 작업 이력 및 sql_logs 리포트
└── README.md
```

---

##  5. 실행 및 테스트 (Build & Run)

### Backend (Spring Boot)
```bash
cd backend
.\gradlew.bat test       # 전체 25개 통합/단원 테스트 실행
.\gradlew.bat bootRun    # 8080 포트 백엔드 서버 기동
```

### Frontend (Next.js)
```bash
cd frontend
npm run lint             # 코드 스타일 검증
npm run build            # 프론트엔드 프로덕션 빌드
npm run dev              # 3000 포트 개발 서버 기동
```
