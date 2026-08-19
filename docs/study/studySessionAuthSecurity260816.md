# 📚 [스터디 명세서] 세션 기반 인증, Spring Security 필터 체인, 세션 고정 방어 & 커뮤니티 다중 로그인 정책 딥다이브

> **본 문서는 노션(Notion)에 그대로 복사하여 독학 및 면접 대비용으로 활용할 수 있도록 작성된 깊이 있는 스터디 명세서입니다.**  
> 단순히 단편적인 비유에 그치지 않고, **직관적 비유(Analogy)**와 **객관적이고 명확한 기술 원리(Technical Specification)**를 1:1로 융합하여 서블릿 컨테이너(Tomcat)와 Spring Security의 7대 필수 설명 체계(개념, Why, When, How, Pros, Alternatives, Trade-off)를 매우 상세하게 파헤쳐 서술하고, **`SecurityContextHolder` 의 본질 및 세션-필터체인 삼각관계**와 **실제 프로젝트 소스코드 분석과 라인별 명세**를 100% 수록합니다.

---

# PART 0. Spring Security 핵심 요소 & 세션 인증 딥다이브

---

## 0.0 `SecurityContextHolder` 의 본질 & 왜 세션/필터체인과 같이 설명하는가?

```
+-----------------------------------------------------------------------------------+
|               0.0 SecurityContextHolder & 세션-필터체인 삼각관계                   |
+-----------------------------------------------------------------------------------+
| 1. 개념        │ 현재 실행 중인 자바 스레드의 인증 객체(SecurityContext)를 담는 정적 헬퍼|
| 2. 왜 사용     │ Controller/Service 어디서든 파라미터 없이 현재 로그인 유저를 조회하기 위해 |
| 3. 어떨 때 개입│ ①요청입구(세션복원), ②로그인시(인증주입), ③권한검사시, ④요청출구(스레드청소)|
| 4. 같이 설명유│ 세션(장기저장소) ↔ 필터체인(이송장치) ↔ SecurityContextHolder(스레드단기참조)|
| 5. 장점        │ 전역 접근성 제공, 계층 간 파라미터 전파 제거, Thread-Safe 보안 문맥 유지   |
| 6. 다른 대안   │ Controller부터 Repository까지 모든 메서드 매개변수로 User 객체 넘기기     |
| 7. 트레이드오프│ Tomcat Thread Pool 재사용 시 이전 유저 정보 유출 ➔ clearContext()로 극복  |
+-----------------------------------------------------------------------------------+
```

### 1) `SecurityContextHolder` 가 대체 무엇인가? (개념 - What)
* 🎈 **직관적 비유**:  
  식당 서빙 기사(자바 스레드)가 들고 다니는 **"공식 인증 서류 거치대 (명함 홀더)"**입니다. 서빙 기사의 옷 주머니 속 서류 거치대에는 "지금 이 서빙 기사가 서비스하고 있는 손님의 신분증(`Authentication`)"이 들어 있습니다.

* ⚙️ **객관적 기술 정의**:  
  `SecurityContextHolder`는 Spring Security에서 **현재 HTTP 요청을 처리 중인 자바 스레드의 인증 정보(`SecurityContext` 및 그 내부의 `Authentication` 객체)를 담고 있는 정적(Static) 래퍼/헬퍼 클래스**입니다.  
  내부적으로 `ThreadLocal`을 사용하여 멀티스레드 동시성 환경에서도 스레드 간 서로의 인증 정보가 섞이지 않도록 완전한 독점 메모리 격리를 제공합니다.

---

### 2) 왜 사용하는가? (Why - 도입 목적 및 배경)
* 🎈 **직관적 비유**:  
  손님의 이름과 회원 등급을 주방장, 계산원, 음료 담당원에게 일일이 계속 파라미터로 입으로 말해주는 대신, 서빙 기사가 자기 옷 주머니의 서류 거치대에서 바로 신분증을 뽑아서 확인하기 위함입니다.

* ⚙️ **객관적 기술 배경**:  
  1. **계층 간 파라미터 전파(Parameter Passing) 문제 해결**:  
     `SecurityContextHolder`가 없다면 Controller ➔ Service ➔ Repository ➔ Utility 클래스로 이어지는 모든 메서드의 매개변수에 `User` 객체나 `Long memberId`를 일일이 넘겨주어야 하여 코드 시그니처가 극도로 더러워집니다.
  2. **어디서나 전역 접근 가능 (Global Access Point)**:  
     스프링 애플리케이션의 어느 계층에서든 `SecurityContextHolder.getContext().getAuthentication()` 단 한 줄만 호출하면 현재 로그인한 사용자의 이메일, PK, Role 권한을 즉시 안전하게 조회할 수 있습니다.

---

### 3) 스프링의 실행 라이프사이클에서 언제/어느 시점에 개입하는가? (When & Spring Lifecycle Flow)
`SecurityContextHolder`는 사용자가 요청을 보내고 응답을 받을 때까지 **스프링 내부의 총 4가지 핵심 시점**에서 정교하게 개입합니다:

```
[클라이언트 요청] 
      │
      ▼
 1. [요청 진입 시점 (입구)] ──► SecurityContextHolderFilter 실행
                                 Tomcat 세션에서 인증 객체를 읽어와 SecurityContextHolder.setContext() 로 스레드에 주입!
      │
      ▼
 2. [로그인 성공 시점]    ──► AuthService.login() 실행
                                 비밀번호 검증 후 새로운 Authentication을 생성하여 SecurityContextHolder.setContext() 로 채움!
      │
      ▼
 3. [권한 검사 시점]      ──► AuthorizationFilter & 컨트롤러 실행
                                 SecurityContextHolder.getContext().getAuthentication() 을 꺼내 권한(ROLE_USER) 및 이메일 조회!
      │
      ▼
 4. [요청 종료 시점 (출구)] ──► SecurityContextHolderFilter 의 finally 절 실행
                                 SecurityContextHolder.clearContext() 를 실행하여 스레드 풀 반납 전 메모리 100% 청소!
```

---

### 4) 저건 왜 세션 / 필터체인과 같이 묶어서 설명했는가? (삼각관계 삼위일체 원리)
* 🎈 **직관적 비유**:  
  **금고(세션)**, **운반 수레(필터체인)**, **주머니 속 서류 거치대(SecurityContextHolder)**는 손님을 맞이할 때 하나처럼 움직이는 삼총사이기 때문입니다. 금고에만 짐을 넣어두고 운반 수레가 없으면 손님에게 가져다줄 수 없고, 서류 거치대가 없으면 일을 하는 동안 계속 손님 신분증을 손에 들고 다녀야 합니다.

* ⚙️ **객관적 기술 삼각관계 연동 원리**:

```
 ┌───────────────────────────┐          이송 (Read/Write)          ┌───────────────────────────┐
 │     Tomcat Session        │ ◄─────────────────────────────────► │ Spring Security Filter    │
 │ (SPRING_SECURITY_CONTEXT) │                                     │(SecurityContextHolderFilter)
 └───────────────────────────┘                                     └─────────────┬─────────────┘
               ▲                                                                 │
               │                                                                 │ 복원 & 청소
               │ (로그인 시 저장)                                                 │ (setContext / clearContext)
               │                                                                 ▼
 ┌─────────────┴─────────────┐         ThreadLocal 1:1 바인딩        ┌───────────────────────────┐
 │    AuthService.login()    │ ──────────────────────────────────► │   SecurityContextHolder   │
 │   (Business Logic Layer)  │                                     │  (ThreadLocal Memory)     │
 └─────────────────────────--┘                                     └───────────────────────────┘
```

1. **세션 (Tomcat Session)**: 톰캣 메모리의 `ConcurrentHashMap`에 저장되는 **장기 영구 저장소** (`SPRING_SECURITY_CONTEXT` 키).
2. **필터체인 (Spring Security Filter Chain)**: 세션과 `SecurityContextHolder` 사이에서 요청이 올 때 세션의 인증 객체를 꺼내어 스레드 메모리로 이송해 주고, 요청이 끝날 때 청소해 주는 **이송 및 제어 장치** (`SecurityContextHolderFilter`).
3. **`SecurityContextHolder` (ThreadLocal Memory)**: HTTP 요청이 처리되는 수 밀리초(ms) 동안 현재 자바 스레드가 빠르게 참조하고 사용하는 **단기 임시 메모리 저장소**.

> **💡 결론**: 세션(저장) ➔ 필터체인(이송/청소) ➔ `SecurityContextHolder`(임시 참조)는 세 개의 톱니바퀴가 하나로 물려 동작하므로, **이 셋 중 하나라도 빠지면 스프링 시큐리티의 세션 인증 동작 원리를 설명할 수 없기 때문에 함께 설명한 것**입니다!

---

### 5) 장점은 무엇인가? (Pros / Advantages)
- **전역 접근 편의성**: 계층 간 메서드 파라미터를 수정할 필요 없이 전역적으로 인증 사용자 정보 조회 가능.
- **Thread-Safe 메모리 격리**: 멀티스레드 환경에서도 각 스레드가 자신만의 인증 객체를 독점하여 쓰레드 간 상호 간섭 0%.

---

### 6) 다른 기술/대안은 무엇이 있는가? (Alternatives)
- **Controller부터 Repository까지 매개변수 전파**: 모든 메서드 매개변수로 `User` 객체를 넘김 (코드 난잡해짐).
- **Public Static 변수**: 전역 클래스 변수에 저장 (동시성 환경에서 타 유저 정보로 덮어씌워져 시스템 파멸).

---

### 7) 트레이드오프 및 `clearContext()` 대참사 극복 방안 (Trade-off & Mitigation)
- **대가 (Trade-off 및 대참사)**: Tomcat Thread Pool(`ThreadPoolExecutor`)은 스레드를 파기하지 않고 재사용합니다. 요청 종료 시 `clearContext()`를 호출하지 않으면, 다음 비회원 요청 시 이전 유저의 인증 객체가 그대로 남아 **타인의 마이페이지와 개인정보가 노출되는 대참사**가 발생합니다.
- **극복 방안**: `SecurityContextHolderFilter`가 `try-finally`의 `finally` 절에서 **`SecurityContextHolder.clearContext()` (`ThreadLocal.remove()`)**를 무조건 실행하여 스레드 반납 직전에 메모리를 100% 제거하고 청소합니다.

---

## 0.1 HTTP Stateless 특성 & `JSESSIONID` 세션 생성 내부 메커니즘

```
+-----------------------------------------------------------------------------------+
|                           0.1 세션 기반 인증 메커니즘 7대 서술                        |
+-----------------------------------------------------------------------------------+
| 1. 개념        │ 서버 메모리에 세션 바구니를 트고 난수 손목띠(JSESSIONID)를 주는 Stateful 인증 |
| 2. 왜 사용     │ HTTP의 무상태성(Stateless) 한계를 극복하고 실시간 통제권을 쥐기 위해        |
| 3. 어떨 때 사용│ 보안성과 즉각적인 세션 파기 제어가 필수적인 웹 서비스/커뮤니티              |
| 4. 어떻게 사용 │ Tomcat StandardManager(ConcurrentHashMap) + Set-Cookie 헤더 파이프라인 |
| 5. 장점        │ 민감정보 서버 은닉, 실시간 세션 강제 무효화 및 권한 통제 용이                |
| 6. 다른 대안   │ JWT (Stateless 토큰), Redis 중앙 세션 저장소 (Distributed Session)         |
| 7. 트레이드오프│ 메모리(RAM) 고갈 위험(OOM) ➔ 1시간/30일 타임아웃 & GC 세션 파기로 극복      |
+-----------------------------------------------------------------------------------+
```

### 1) 개념 (What)
* 🎈 **직관적 비유**:  
  놀이공원에 입장할 때 매표소 직원이 손님의 얼굴과 구매 정보를 기억하는 대신, **32자리 고유 번호가 적힌 무작위 자유이용권 손목띠(`JSESSIONID`)**를 손님의 손목(쿠키)에 매어주는 방식입니다. 손님은 놀이기구를 탈 때마다 자기 인적사항을 말할 필요 없이 손목띠만 보여주면 되고, 놀이공원 중앙 관리실(서버 메모리)은 그 손목띠 번호에 해당하는 손님의 바구니를 열어 이용 권한을 확인합니다.

* ⚙️ **객관적 기술 정의**:  
  세션 기반 인증(Session-based Authentication)이란, 클라이언트의 로그인 상태, 회원 PK, Role 권한 객체를 **서버 측 메모리(서블릿 컨테이너의 Session Store)**에 보관하고, 클라이언트 브라우저에게는 암호학적으로 안전한 무작위 난수 식별자 키(`JSESSIONID`)만을 HTTP 쿠키로 내려주어 매 요청마다 사용자를 식별하는 대표적인 **Stateful(상태 유지) 웹 인증 아키텍처**입니다.

---

### 2) 왜 사용하는지 (Why - 도입 목적 및 배경)
* 🎈 **직관적 비유**:  
  손님이 들어올 때마다 "이름이 뭐예요? 주민번호 대세요"라고 처음부터 다시 묻는다면 놀이공원이 마비되는 것처럼, HTTP 프로토콜은 한번 요청을 처리하면 이전 요청을 싹 잊어버리는 건망증이 있기 때문에 접속 상태를 기억해 둘 특수한 수단이 필요합니다.

* ⚙️ **객관적 기술 배경**:  
  1. **HTTP 프로토콜의 Stateless(무상태성) 특성 (RFC 6265) 극복**:  
     HTTP/1.1 및 HTTP/2 프로토콜은 클라이언트의 요청(Request)에 대해 서버가 응답(Response)을 마치면 TCP 연결을 끊거나 트랜잭션 문맥(Context)을 보존하지 않습니다. 따라서 로그인 상태를 유지하려면 서버나 클라이언트 중 한 곳에 상태를 저장해야 합니다.
  2. **서버 측의 강력한 세션 주권 확보**:  
     클라이언트에 모든 상태를 담는 토큰(JWT)과 달리, 세션 방식은 서버가 세션 저장소를 직접 소유하므로 **특정 사용자의 계정 탈취가 의심될 때 즉시 해당 세션을 강제 무효화(Kill)**할 수 있는 완전한 통제권을 제공합니다.

---

### 3) 어떨 때 사용하는지 (When - 적합한 유즈케이스)
* 🎈 **직관적 비유**:  
  관리자가 손님의 출입을 언제든 막거나 손목띠를 현장에서 싹 잘라버려야 하는 **보안이 엄격한 VIP 클럽이나 통합 관리 시스템**에 적합합니다.

* ⚙️ **객관적 기술 유즈케이스**:  
  - **단일 도메인 기반 커뮤니티 및 이커머스**: 쿠키 공유가 가능한 동일 도메인 범위 내에서 유저의 장시간 체류와 안전한 인증이 필요한 웹 서비스.
  - **보안과 실시간 통제가 최우선인 시스템**: 금융, 관리자(Admin) 콘솔, 보안 커뮤니티 등 계정 도용 시 즉각 세션을 파기해야 하는 서비스.

---

### 4) 어떻게 사용하는지 (How - 구체적 동작 메커니즘 & 코드 파이프라인)
* 🎈 **직관적 비유**:  
  1) 손님이 로그인 창구로 오면, 2) 금고 관리자가 32자리 무작위 난수가 찍힌 라벨을 출력하여 금고함에 붙이고, 3) 손님 손목에 그 라벨 쿠키를 차워준 뒤, 4) 다음부터 손님이 오면 라벨 번호와 금고함을 매칭하여 수건과 옷을 꺼내주는 원리입니다.

* ⚙️ **객관적 기술 메커니즘**:  
  1. **Tomcat 서블릿 컨테이너 내부 `StandardManager` 의 세션 맵**:  
     Apache Tomcat은 세션을 관리하기 위해 `org.apache.catalina.session.StandardManager` 클래스를 운용합니다. 내부에는 멀티스레드 세이프한 자바 동시성 자료구조가 선언되어 있습니다:
     ```java
     protected Map<String, Session> sessions = new ConcurrentHashMap<>();
     ```
  2. **세션 발급 물리 파이프라인**:
     - 클라이언트가 `POST /api/auth/login`으로 아이디/비밀번호 전송.
     - `httpRequest.getSession(true)` 호출 시 Tomcat의 `SessionIdGenerator` (SecureRandom 기반)가 **128비트 암호학적 무작위 난수**를 생성하여 32자리 16진수 문자열(`A1B2C3D4...`)을 반환.
     - `StandardSession` 객체를 생성하여 생성시각, 마지막 접근시각, `maxInactiveInterval`(3600초)을 세팅한 후 `sessions.put("A1B2C3D4...", standardSession)` 으로 메모리에 등록.
     - HTTP 응답 헤더 발송:
       ```http
       HTTP/1.1 200 OK
       Set-Cookie: JSESSIONID=A1B2C3D4...; Path=/; Secure; HttpOnly; SameSite=Lax
       ```
  3. **이후 요청 시 세션 복원 흐름**:
     - 브라우저가 매 요청마다 `Cookie: JSESSIONID=A1B2C3D4...`를 전송.
     - Tomcat의 `CoyoteAdapter`가 헤더에서 `JSESSIONID`를 추출하고, `StandardManager.findSession("A1B2C3D4...")`를 통해 메모리의 `StandardSession` 객체를 찾아 현재 요청 스레드에 바인딩.

---

### 5) 장점은 무엇인지 (Pros / Advantages)
* 🎈 **직관적 비유**:  
  손님 손목띠에는 그냥 무의미한 번호만 적혀 있어서 손목띠를 길에서 분실해도 손님의 실제 이름, 전화번호, 집 주소(개인정보)가 외부에 절대 노출되지 않는 안정성이 있습니다.

* ⚙️ **객관적 기술 장점**:  
  1. **데이터 은닉성 및 보안성**: 클라이언트 브라우저에는 32자리 무작위 세션 키만 저장될 뿐, 유저의 이메일, Role, PK 등 실제 데이터는 서버 메모리에 안전하게 숨겨집니다.
  2. **즉각적인 상태 제어**: 서버에서 `session.invalidate()`를 실행하는 순간 해당 세션은 물리적으로 즉시 소멸되어 완벽한 로그아웃 및 세션 무효화가 보장됩니다.

---

### 6) 다른 기술/대안은 무엇이 있는지 (Alternatives - 기술 비교)
* 🎈 **직관적 비유**:  
  서버 금고에 짐을 보관하는 **세션 방식**과 달리, 손님 본인의 가방에 암호화된 도장을 찍은 지갑(토큰)을 직접 들고 다니게 하는 **JWT 방식**이 있습니다.

* ⚙️ **객관적 기술 비교**:

| 비교 항목 | Stateful 세션 (`JSESSIONID`) | Stateless JWT (`Bearer Token`) |
| :--- | :--- | :--- |
| **상태 저장 위치** | **서버 메모리 (Tomcat ConcurrentHashMap)** | **클라이언트 (LocalStorage / Cookie)** |
| **서버 메모리 사용량** | 동시 접속자 수 비례 증가 (OOM 주의) | **0 (서버 메모리 사용 안 함)** |
| **즉각 세션 강제 만료** | **즉시 가능 (`session.invalidate()`)** | **불가능** (토큰 만료 시까지 직권 취소 어려움) |
| **수평 확장성 (Scale-Out)** | 세션 동기화(Sticky Session / Redis) 필요 | 별도 세션 서버 없이 **수평 확장 매우 용이** |

---

### 7) 트레이드오프 및 극복 방안 (Trade-off & Mitigation)
* 🎈 **직관적 비유**:  
  놀이공원에 손님이 100만 명 몰리면 손님의 물품 금고함(서버 RAM)이 터져나가는 대가(Trade-off)가 생깁니다. 이를 막기 위해 일정 시간 동안 안 찾아오는 금고는 직원이 비워버리거나(세션 타임아웃), 외부 대형 창고(Redis)를 빌려 해결합니다.

* ⚙️ **객관적 기술 트레이드오프 & 서비스/아키텍처 극복 방안**:  
  - **대가 (Trade-off)**: 접속자 수가 급증할수록 서버 RAM 메모리에 `StandardSession` 객체가 기하급수적으로 누적되어 Heap Memory 부족으로 인한 **OutOfMemoryError(OOM) 및 서버 셧다운**이 터질 수 있습니다.
  - **서비스 레벨 극복 방안**: `session.setMaxInactiveInterval(3600)` (1시간) 및 Remember-Me (30일) 타임아웃을 정교하게 설정하여, 지정된 시간 동안 활동이 없는 세션은 Tomcat 서블릿 컨테이너가 스케줄러를 통해 메모리에서 즉시 파기(Garbage Collection)하도록 제어합니다.
  - **아키텍처 레벨 극복 방안**: 추후 대규모 분산 환경으로 확장 시, 서버 자체 메모리에 세션을 저장하지 않고 **Redis 기반의 중앙 세션 저장소 (Spring Session Redis)**로 구조를 전환하여 서버 인스턴스가 늘어나도 세션이 안전하게 유실 없이 공유되도록 설계합니다.

---

## 0.2 세션 고정 공격 (Session Fixation Attack) & `changeSessionId()` 내부 물리 원리

```
+-----------------------------------------------------------------------------------+
|                        0.2 세션 고정 공격 & changeSessionId()                     |
+-----------------------------------------------------------------------------------+
| 1. 개념        │ 해커가 미리 획득한 세션ID를 피해자에게 심어 로그인 권한을 훔치는 공격   |
| 2. 왜 사용     │ 비회원->회원 보안 경계 전환 시 세션 ID를 교체하여 세션 탈취를 차단하기 위해|
| 3. 어떨 때 사용│ POST /api/auth/login 로그인, 권한 승격, 결제 등 보안 경계 전환 시        |
| 4. 어떻게 사용 │ Servlet 3.1 request.changeSessionId() ➔ 세션 데이터 보존 & 키만 재발급    |
| 5. 장점        │ 기존 장바구니/임시데이터 유실 없이 세션 고정 공격 100% 물리적 무력화      |
| 6. 다른 대안   │ session.invalidate() 후 새 세션 생성 (단점: 기존 임시 데이터 날아감)     |
| 7. 트레이드오프│ 프론트엔드 쿠키 갱신 동기화 오버헤드 ➔ CORS Set-Cookie 헤더 전달로 극복 |
+-----------------------------------------------------------------------------------+
```

### 1) 개념 (What)
* 🎈 **직관적 비유**:  
  해커가 미리 비회원으로 호텔 열쇠 `1111` 번을 발급받아 둡니다. 그리고 피해자에게 "이 열쇠로 들어가서 투숙해!" 라고 피싱을 칩니다. 피해자가 `1111` 번 방에 들어가 정상 투숙(로그인)했을 때, **호텔이 열쇠를 새 번호로 안 바꿔주면 해커가 복사해 둔 `1111` 번 열쇠로 피해자의 방을 마음대로 드나드는 공격**입니다.

* ⚙️ **객관적 기술 정의**:  
  세션 고정 공격(Session Fixation Attack)이란, 공격자가 웹 사이트에 접속하여 발급받은 비회원 세션 ID를 피해자의 브라우저 쿠키에 피싱/CSRF 기법으로 미리 강제 주입(Fixation)해 둔 뒤, 피해자가 정상 로그인을 완료했을 때 서버가 동일한 세션 ID를 계속 유지하는 허점을 악용하여 **피해자의 로그인된 세션 권한을 훔쳐내는 웹 보안 공격**입니다.

---

### 2) 왜 사용하는지 (Why - 방어의 필요성)
* 🎈 **직관적 비유**:  
  손님이 신원 확인(로그인)을 성공했으면 이전 열쇠를 뺏고 즉시 새 열쇠를 쥐어줘야 나쁜 마음을 품은 외부인이 이전 열쇠로 문을 열 수 없기 때문입니다.

* ⚙️ **객관적 기술 배경**:  
  서블릿 컨테이너는 비회원 사용자라도 장바구니나 필터 선택 정보를 저장하기 위해 로그인 전에 비회원 세션 ID(`SESSION_OLD_1111`)를 발급합니다. 로그인 후에도 이 세션 ID를 그대로 재사용하면, 해당 세션 메모리에 유저의 `SecurityContext`(인증 정보)가 채워지면서 **공격자가 쥐고 있던 비회원 세션 ID가 순식간에 강력한 로그인 권한 세션으로 승격**되는 치명적 보안 구멍이 뚫리기 때문입니다.

---

### 3) 어떨 때 사용하는지 (When - 적용 타이밍)
* 🎈 **직관적 비유**:  
  일반 손님이 신분증을 제시하고 **VIP 회원으로 등급을 전환하는 바로 그 순간** 열쇠를 교체해야 합니다.

* ⚙️ **객관적 기술 유즈케이스**:  
  - **로그인 처리 시점 (`POST /api/auth/login`)**: 비회원 상태에서 회원 인증 상태로 전환되는 물리적 시점.
  - **권한 상승 및 중요 트랜잭션 시점**: 일반 유저가 관리자(Admin) 권한을 획득하거나 결제/비밀번호 변경 등 핵심 보안 경계(Security Boundary)를 넘어설 때.

---

### 4) 어떻게 사용하는지 (How - `request.changeSessionId()` 동작 메커니즘)
* 🎈 **직관적 비유**:  
  손님이 쓰던 방 안의 짐(장바구니)은 하나도 안 건드리고 그대로 둔 채, **방 문고리의 자물쇠 열쇠 구멍(세션 ID 키값)만 찰칵하고 새 모양으로 바꿔치기**하는 방식입니다.

* ⚙️ **객관적 기술 메커니즘**:  
  Spring Security의 `ChangeSessionIdAuthenticationStrategy`가 동작하면서 Java Servlet 3.1+ 표준 규격 메서드인 `request.changeSessionId()`를 수행합니다.

  ```
  1. Tomcat ConcurrentHashMap 세션 맵에서 이전 세션 키 제거:
     sessions.remove("SESSION_OLD_1111")
  2. SecureRandom 난수 발생기를 통해 32자리 새 세션 ID 즉시 생성: "SESSION_NEW_9999"
  3. 기존 StandardSession 객체 내부의 속성 데이터(장바구니, 설정값)는 메모리 상에서 100% 보존!
  4. 새 세션 ID 키로 세션 맵에 재등록:
     sessions.put("SESSION_NEW_9999", existingStandardSession)
  5. HTTP 응답 헤더 발송:
     Set-Cookie: JSESSIONID=SESSION_NEW_9999; Path=/; Secure; HttpOnly; SameSite=Lax
  ```

---

### 5) 장점은 무엇인지 (Pros / Advantages)
* 🎈 **직관적 비유**:  
  손님이 비회원으로 장바구니에 담아둔 상품 목록이 로그인 후에도 그대로 유지되면서, 해커의 열쇠는 완벽히 쓰레기로 만들어버리는 1석 2조의 효과가 있습니다.

* ⚙️ **객관적 기술 장점**:  
  기존 세션을 완전히 파기하고 새로 만들 때 발생하는 **유저 데이터(장바구니, 임시 입력 폼 등) 손실 문제없이**, 세션 고정 공격만을 100% 물리적으로 깔끔하게 무력화합니다.

---

### 6) 다른 기술/대안은 무엇이 있는지 (Alternatives - 기술 비교)

| 세션 고정 방어 전략 | 동작 원리 | 유저 임시 데이터 유지 여부 | 보안성 |
| :--- | :--- | :--- | :--- |
| **`changeSessionId()` (선택)** | **세션 데이터는 보존하고 세션 ID 키값만 새로 재발급** | **100% 보존 (우수)** | **최상** |
| **`newSession`** | 기존 세션을 `session.invalidate()`로 완전 파기 후 새 세션 생성 | **전부 유실 (단점)** | 최상 |
| **`none` (방어 없음)** | 로그인 후에도 이전 비회원 세션 ID를 그대로 유지 | 보존됨 | **최악 (공격 노출)** |

---

### 7) 트레이드오프 및 극복 방안 (Trade-off & Mitigation)
* 🎈 **직관적 비유**:  
  열쇠 번호가 바뀌었기 때문에 손님 앱(프론트엔드)이 새 열쇠 번호를 모르면 문이 안 열리는 문제(401 에러)가 터질 수 있습니다.

* ⚙️ **객관적 기술 트레이드오프 & 극복 방안**:  
  - **대가 (Trade-off)**: 세션 ID 변경 시 서버가 응답 헤더로 전송하는 `Set-Cookie`를 프론트엔드 SPA(Next.js 등)나 모바일 앱 클라이언트가 제대로 수신하지 못하면, 후속 요청 시 이전 세션 ID를 전송하여 `401 Unauthorized` 에러가 발생합니다.
  - **극복 방안**: CorsConfig 설정 시 `config.setAllowCredentials(true)`를 명시하고, Axios/Fetch 요청 시 `withCredentials: true` 옵션을 연동하여 로그인 응답의 `Set-Cookie: JSESSIONID=SESSION_NEW_9999`를 프론트엔드 쿠키 저장소에 올바르게 갱신하도록 만듭니다.

---

## 0.3 Spring Security 필터 체인 (DelegatingFilterProxy ↔ FilterChainProxy)

```
+-----------------------------------------------------------------------------------+
|                        0.3 Spring Security 필터 체인 메커니즘                     |
+-----------------------------------------------------------------------------------+
| 1. 개념        │ Tomcat 서블릿 요청을 Spring Container 내부 보안 필터 모듈로 위임하는 체인 |
| 2. 왜 사용     │ 서블릿-스프링 메모리 격리를 극복하고 스프링 빈(Bean) 보안 로직을 적용하기 위해 |
| 3. 어떨 때 사용│ REST API 요청 진입점에서 CORS, 세션복원, 권한검사, 보안헤더 적용 시       |
| 4. 어떻게 사용 │ DelegatingFilterProxy ➔ FilterChainProxy ➔ DefaultSecurityFilterChain     |
| 5. 장점        │ 컨트롤러 진입 전 보안검사가 완벽히 완료되어 비즈니스 로직과 보안 로직 완전 분리 |
| 6. 다른 대안   │ Spring Interceptor (HandlerInterceptor) ➔ 컨트롤러 직전에만 동작함 (한계) |
| 7. 트레이드오프│ 15개 필터 릴레이로 인한 미세 레이턴시 ➔ 안 쓰는 필터 disable() 최적화로 극복|
+-----------------------------------------------------------------------------------+
```

### 1) 개념 (What)
* 🎈 **직관적 비유**:  
  Tomcat 서블릿 컨테이너가 **"건물 외곽 경비실"**이라면, Spring 컨테이너는 **"건물 내부 중앙 보안 통제실"**입니다. 외곽 경비실 경비원(`DelegatingFilterProxy`)은 들어오는 사람을 검문할 줄 몰라서, 통제실의 책임자(`FilterChainProxy`)에게 사람을 넘겨주고 통제실 안의 15개 전문 보안 수사팀(보안 필터 릴레이)이 순서대로 신분증과 서류를 검사하는 구조입니다.

* ⚙️ **객관적 기술 정의**:  
  Spring Security 필터 체인이란, 서블릿 컨테이너(Tomcat)로 들어오는 모든 HTTP 요청을 Spring ApplicationContext 내부에 정의된 보안 빈(Bean) 객체 체인으로 위임하여, **인증(Authentication), 인가(Authorization), CORS, CSRF, 세션 복원, 보안 헤더 주입**을 순차적으로 처리하는 프록시(Proxy) 기반의 보안 파이프라인 아키텍처입니다.

---

### 2) 왜 사용하는지 (Why - 도입 목적 및 배경)
* 🎈 **직관적 비유**:  
  외곽 경비실(Tomcat)과 통제실(Spring)은 소속이 완전 달라서, 외곽 경비원이 통제실 내부의 최첨단 장비(`@Bean`, DB Repository)를 직접 만질 수 없기 때문에 중간에 연결해 줄 전용 다리가 반드시 필요하기 때문입니다.

* ⚙️ **객관적 기술 배경**:  
  Java EE 서블릿 스펙에 따라 Tomcat 서블릿 컨테이너가 관리하는 `jakarta.servlet.Filter` 영역과, Spring IoC 컨테이너가 관리하는 ApplicationContext 메모리 영역은 **완전히 격리**되어 있습니다. Tomcat의 서블릿 필터는 스프링의 `@Bean`이나 의존성 주입(DI) 개념을 알지 못하므로, 서블릿 필터 규격을 지키면서도 스프링의 강력한 DI 및 데이터베이스 연동 보안 빈들을 자유롭게 사용하기 위해 위임 프록시 메커니즘을 사용합니다.

---

### 3) 어떨 때 사용하는지 (When - 적합한 유즈케이스)
* 🎈 **직관적 비유**:  
  외부인이 건물 로비(컨트롤러)로 들어오기 바로 직전, **입구 전체를 통제하고 신원 확인 및 무기 소지 검사를 해야 하는 모든 순간**에 사용합니다.

* ⚙️ **객관적 기술 유즈케이스**:  
  - **웹 애플리케이션의 모든 HTTP 요청 진입점**: 컨트롤러(`DispatcherServlet`)에 요청이 다다르기 전에 URL 권한 검사, CORS 헤더 검증, 세션 기반 유저 복원을 수행할 때.

---

### 4) 어떻게 사용하는지 (How - 서블릿-스프링 연동 파이프라인)
* 🎈 **직관적 비유**:  
  1) 외곽 경비원(`DelegatingFilterProxy`)이 손님을 잡고, 2) 통제실 책임자(`FilterChainProxy`)를 불러오면, 3) 책임자가 손님의 목적지 URL(`/api/members/me`)을 보고 적절한 검사장(`DefaultSecurityFilterChain`)으로 안내한 뒤, 4) 15개 수사팀이 순서대로 손님을 검사하여 통과시키는 방식입니다.

* ⚙️ **객관적 기술 메커니즘**:

  ```
  ┌─────────────────────────────────────────────────────────────────────────┐
  │ Servlet Container (Tomcat)                                              │
  │                                                                         │
  │  HTTP Request ──► [ApplicationFilterChain]                              │
  │                         │                                               │
  │                         ▼                                               │
  │             [DelegatingFilterProxy] (jakarta.servlet.Filter 구현체)      │
  └─────────────────────────┼───────────────────────────────────────────────┘
                            │ (WebApplicationContext.getBean("springSecurityFilterChain"))
  ┌─────────────────────────┼───────────────────────────────────────────────┐
  │ Spring ApplicationContext (Spring Container)                            │
  │                         │                                               │
  │                         ▼                                               │
  │             [FilterChainProxy] (Bean Name: "springSecurityFilterChain") │
  │                         │                                               │
  │                         ▼                                               │
  │             [List<SecurityFilterChain>]                                 │
  │                         │ (Matches AntPathRequestMatcher)               │
  │                         ▼                                               │
  │             [DefaultSecurityFilterChain]                                │
  │                         │                                               │
  │                         ├── 1. HeaderWriterFilter (보안 헤더 주입)       │
  │                         ├── 2. CorsFilter (CORS 검증)                   │
  │                         ├── 3. SecurityContextHolderFilter (세션 복원)   │
  │                         ├── 4. LogoutFilter (로그아웃 처리)             │
  │                         ├── 5. UsernamePasswordAuthenticationFilter     │
  │                         ├── 6. ExceptionTranslationFilter (401/403 변환) │
  │                         └── 7. AuthorizationFilter (최종 권한 검사)       │
  │                                 │                                       │
  │                                 ▼                                       │
  │                         [DispatcherServlet] (Spring MVC Entry Point)    │
  └─────────────────────────────────────────────────────────────────────────┘
  ```

  1. **`DelegatingFilterProxy` 프록시 위임**: Tomcat 서블릿 필터 체인에 등록된 `DelegatingFilterProxy`가 요청을 받으면, `WebApplicationContext`에서 `"springSecurityFilterChain"` 빈을 찾아 **`FilterChainProxy`** 객체로 요청을 위임.
  2. **`FilterChainProxy` 패턴 매칭**: `FilterChainProxy`는 가지고 있는 `List<SecurityFilterChain>` 중 현재 요청 URL과 매칭되는 단 하나의 **`DefaultSecurityFilterChain`**을 선정.
  3. **15개 보안 필터 릴레이 수행**: 선점된 필터 체인 내부의 보안 필터들이 `filterChain.doFilter()`를 통해 순차적으로 실행된 후, 이상이 없으면 Spring MVC 창구인 `DispatcherServlet`으로 제어권을 이전.

---

### 5) 장점은 무엇인지 (Pros / Advantages)
* 🎈 **직관적 비유**:  
  식당 주방장(컨트롤러)은 음식 만드는 비즈니스 로직에만 전념하고, 입구 경비팀(필터 체인)이 불량 손님 차단과 신원 확인을 전담하여 역할 분담이 완벽해집니다.

* ⚙️ **객관적 기술 장점**:  
  보안 검증 로직이 Spring MVC 컨트롤러 레이어와 완전히 격리되므로 비즈니스 코드의 가독성이 높아지고, 허가되지 않은 요청은 서블릿 레이어 근처에서 즉시 튕겨내므로 **컨트롤러 자원 낭비를 방지**합니다.

---

### 6) 다른 기술/대안은 무엇이 있는지 (Alternatives - 기술 비교)

* ⚙️ **Spring Security Filter Chain vs Spring Interceptor (HandlerInterceptor)**:

| 비교 항목 | Spring Security Filter Chain | Spring Interceptor (`HandlerInterceptor`) |
| :--- | :--- | :--- |
| **실행 위치** | **Tomcat 서블릿 체인 단계 (DispatcherServlet 이전)** | **Spring MVC 내부 단계 (DispatcherServlet 이후)** |
| **스프링 에러 처리** | `@ExceptionHandler` 도달 전이므로 별도 JSON 변환 필요 | `@ExceptionHandler`에서 예외 일통 처리 가능 |
| **보안 범위** | **웹 애플리케이션 전체 (CORS, Request Body, HTTP Header)** | Spring Controller 매핑 요청에만 제한 |
| **적합한 역할** | **인증/인가, CORS, CSRF, 세션 고정 방어 등 종합 보안** | 로깅, 뷰 데이터 전처리, 가벼운 파라미터 검증 |

---

### 7) 트레이드오프 및 극복 방안 (Trade-off & Mitigation)
* 🎈 **직관적 비유**:  
  건물 안으로 들어가는데 검문소를 15개나 지나가야 해서 아주 미세하게 들어가는 시간이 늘어나는 대가가 있습니다.

* ⚙️ **객관적 기술 트레이드오프 & 극복 방안**:  
  - **대가 (Trade-off)**: HTTP 요청이 올 때마다 10~15개의 보안 필터 인스턴스를 거치는 릴레이가 발생하여 미세한 CPU 오버헤드 및 레이턴시가 추가됩니다.
  - **극복 방안**: REST API 환경에서 불필요한 `formLogin(disable)`, `httpBasic(disable)`, `csrf(disable)` 등을 설정하여 사용하지 않는 필터들을 체인 파이프라인에서 완전히 제외(Bypass)시킴으로써 실행 오버헤드를 극소화합니다.

---

## 0.4 `SecurityContextHolder` 와 `ThreadLocal` 메모리 물리 원리 & `clearContext()` 대참사

```
+-----------------------------------------------------------------------------------+
|                   0.4 SecurityContextHolder & ThreadLocal 대참사                  |
+-----------------------------------------------------------------------------------+
| 1. 개념        │ 현재 실행 중인 자바 스레드의 ThreadLocal 메모리에 인증 객체를 보관하는 헬퍼|
| 2. 왜 사용     │ 컨트롤러/서비스에서 파라미터 전파 없이 언제든 로그인 유저를 즉시 조회하기 위해|
| 3. 어떨 때 사용│ 로그인 유저의 이메일/PK/권한 조회가 필요한 모든 서비스/리포지토리 레이어  |
| 4. 어떻게 사용 │ Java Thread 객체 내부 ThreadLocalMap 에 (ThreadLocal, SecurityContext) 저장|
| 5. 장점        │ 멀티스레드 환경에서 파라미터 전달 없이 완전한 Thread-Safe 독점 메모리 제공|
| 6. 다른 대안   │ 모든 메서드 매개변수로 User 객체 넘기기 (코드 복잡도 극심해짐)            |
| 7. 트레이드오프│ Tomcat Thread Pool 재사용 시 이전 유저 정보 유출 ➔ clearContext()로 극복|
+-----------------------------------------------------------------------------------+
```

### 1) 개념 (What)
* 🎈 **직관적 비유**:  
  식당 서빙 기사(자바 스레드)가 손님의 주문을 받아 일할 때, 손님이 누구인지 매번 소리쳐 묻지 않도록 **자신의 주머니 속 개인 메모장(`ThreadLocal`)**에 손님의 이름과 주문 내용을 적어두고 사용하는 방식입니다.

* ⚙️ **객관적 기술 정의**:  
  `SecurityContextHolder`란, 현재 HTTP 요청을 처리하고 있는 자바 스레드의 **`ThreadLocal` 독점 메모리 공간**에 인증 객체(`SecurityContext`)를 보관하여, 애플리케이션의 계층(Controller, Service, Repository)에 구애받지 않고 언제 어디서나 현재 로그인된 사용자 정보에 접근할 수 있도록 도와주는 **Spring Security의 인증 컨텍스트 관리 헬퍼 클래스**입니다.

---

### 2) 왜 사용하는지 (Why - 도입 목적 및 배경)
* 🎈 **직관적 비유**:  
  손님의 이름을 주문서 1번, 2번, 3번 주방 보조에게 일일이 계속 말로 전달하려면 너무 피곤하고 에러가 나기 쉬우므로, 서빙 기사 본인의 주머니 메모장을 열어서 확인하는 것이 훨씬 깔끔하기 때문입니다.

* ⚙️ **객관적 기술 배경**:  
  서비스 레이어나 리포지토리 레이어에서 현재 로그인한 회원 정보를 참조해야 할 때, 컨트롤러부터 최하위 메서드까지 모든 매개변수(Parameter)에 `User` 객체를 지저분하게 넘겨주어야 하는 **파라미터 전파(Parameter Passing) 문제**를 해결하기 위함입니다.

---

### 3) 어떨 때 사용하는지 (When - 적합한 유즈케이스)
* 🎈 **직관적 비유**:  
  "지금 이 주문 넣은 손님 회원 등급이 어떻게 되지?" 하고 **일하는 와중에 손님의 신원 정보가 필요할 때** 언제든 주머니 메모장을 꺼내 확인합니다.

* ⚙️ **객관적 기술 유즈케이스**:  
  - 서비스 레이어에서 현재 로그인한 유저의 PK, 이메일, Role 권한을 조회할 때 (`SecurityContextHolder.getContext().getAuthentication()`).
  - `@AuthenticationPrincipal` 어노테이션을 통해 컨트롤러 파라미터로 로그인 회원 정보를 바로 주입받을 때.

---

### 4) 어떻게 사용하는지 (How - Java `ThreadLocalMap` 메모리 물리 메커니즘)
* 🎈 **직관적 비유**:  
  서빙 기사 A와 서빙 기사 B는 서로 상대방의 주머니 속 메모장을 절대로 열어볼 수 없도록 자바 언어 차원에서 철저하게 호주머니(독점 메모리)를 격리해 주는 원리입니다.

* ⚙️ **객관적 기술 메커니즘**:
  1. Java의 `java.lang.Thread` 클래스 내부에는 `ThreadLocal.ThreadLocalMap threadLocals = null` 이라는 독점 필드가 정의되어 있습니다.
  2. `SecurityContextHolder.setContext(context)`를 호출하면, **현재 요청을 실행 중인 자바 `Thread` 객체 내부의 `ThreadLocalMap`에 `(ThreadLocal인스턴스, SecurityContext인스턴스)` 엔트리가 저장**됩니다.
  3. 다른 HTTP 요청을 처리하는 스레드 B는 스레드 A의 `ThreadLocalMap`에 절대 접근할 수 없으므로, 동시성 환경에서도 100% 안전한 **Thread-Safe 메모리 격리**가 성립됩니다.

---

### 5) 장점은 무엇인지 (Pros / Advantages)
* 🎈 **직관적 비유**:  
  다른 서빙 기사와 동선이나 주문 내용이 꼬일 염려가 전혀 없고, 코드 어디서든 주머니만 뒤지면 손님 정보를 즉시 꺼낼 수 있는 편리함이 있습니다.

* ⚙️ **객관적 기술 장점**:  
  메서드 매개변수 수정 없이 깔끔한 코드 구조를 유지할 수 있으며, 동시성 멀티스레드 환경에서 별도의 락(Lock)이나 Synchronized 없이도 완전히 안전하게 유저 컨텍스트를 유지할 수 있습니다.

---

### 6) 다른 기술/대안은 무엇이 있는지 (Alternatives - 기술 비교)

| 유저 인증 정보 전파 방식 | 물리적 구현 | 문제점 / 한계 |
| :--- | :--- | :--- |
| **`ThreadLocal` (선택)** | 자바 스레드 독점 메모리 (`ThreadLocalMap`) 활용 | 요청 종료 시 `clearContext()` 미호출 시 스레드 풀 오염 위험 |
| **매개변수 직접 전파** | Controller ➔ Service ➔ Repository 매개변수로 전달 | 모든 메서드 시그니처가 더러워지고 유지보수성 최악 |
| **전역 정적 변수 (Static)** | `public static User currentUser;` | **동시성 환경에서 타 유저 정보로 덮어씌워지는 치명적 버그 발생** |

---

### 7) 트레이드오프 및 `clearContext()` 대참사 극복 방안 (Trade-off & Mitigation)
* 🎈 **직관적 비유**:  
  서빙 기사가 퇴근하거나 다음 손님을 맞이할 때 **주머니 메모장을 안 지우고 그대로 놔두면, 다음 손님에게 이전 손님의 음식과 개인정보가 튀어나오는 대참사**가 터집니다.

* ⚙️ **객관적 기술 트레이드오프 & 대참사 극복 방안**:
  - **Tomcat Thread Pool(스레드 풀) 재사용 메커니즘**: Tomcat은 요청이 올 때마다 스레드를 새로 만들지 않고, CPU 오버헤드를 막기 위해 `ThreadPoolExecutor` (기본 200개 스레드)를 운용하여 요청 완료 후 **스레드를 파기하지 않고 스레드 풀로 반납받아 재사용**합니다.

  - **정보 유출 대참사 시나리오 (Security Incident)**:
    ```
    1. [유저 A (홍길동) 요청] ──► Tomcat 스레드-10 할당 ──► ThreadLocalMap 에 "유저A(홍길동) SecurityContext" 저장
    2. [요청 처리 완료] ──────► [대참사 원인]: SecurityContextHolder.clearContext() 호출 누락!
    3. [스레드-10 반납] ──────► ThreadLocalMap 에 "유저A(홍길동)" 정보가 그대로 남아있는 채 스레드 풀로 복귀!
    4. [유저 B (비회원) 요청] ──► Tomcat 스레드 풀에서 하필 이전의 "스레드-10" 재배정받음!
    5. [컨트롤러/서비스] ────► SecurityContextHolder.getContext().getAuthentication() 호출 시
                               💥 비회원 유저 B임에도 불구하고 유저 A(홍길동)의 인증 객체가 조회됨!
                               💥 유저 B의 화면에 유저 A(홍길동)의 마이페이지/개인정보가 노출되는 정보 유출 보안 사고 발생!
    ```

  - **아키텍처적 극복 방안**:
    Spring Security의 `SecurityContextHolderFilter`는 `try-finally` 블록의 `finally` 절에서 **`SecurityContextHolder.clearContext()`** (`ThreadLocal.remove()`)를 무조건 호출하도록 강제 설계되어 있습니다. 이로 인해 HTTP 응답이 나가는 직전 현재 스레드의 `ThreadLocalMap` 메모리를 100% 제거하고 청소한 뒤 스레드를 풀로 안전하게 반납시킵니다.

---

# PART 1. 세션 기반 인증 소스코드 분석 & 라인별 명세

---

## 1.1 `MemberLoginRequest.java` (로그인 DTO 및 입력 검증)

```java
package com.ikae.snowthing.domain.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class MemberLoginRequest {

    // 💡 [왜 @NotBlank 와 @Email 을 사용하는가?]
    // - @NotBlank: null, "", "  " (공백 입력)을 컨트롤러 진입 전 1차 차단합니다.
    // - @Email: 로그인 시 올바른 이메일 정규식 포맷인지를 Spring @Valid 가 자동 검증합니다.
    @NotBlank(message = "이메일은 필수 입력값입니다.")
    @Email(message = "올바른 이메일 형식이 아닙니다.")
    private String email;

    @NotBlank(message = "비밀번호는 필수 입력값입니다.")
    private String password;

    private boolean rememberMe; // Remember-Me 30일 옵션 선택 여부

    @Builder
    public MemberLoginRequest(String email, String password, boolean rememberMe) {
        this.email = email;
        this.password = password;
        this.rememberMe = rememberMe;
    }
}
```

---

## 1.2 `AuthService.java` (세션 로그인/로그아웃 비즈니스 서비스)

```java
package com.ikae.snowthing.domain.auth.service;

import com.ikae.snowthing.domain.auth.dto.MemberLoginRequest;
import com.ikae.snowthing.domain.auth.dto.MemberLoginResponse;
import com.ikae.snowthing.domain.member.entity.Member;
import com.ikae.snowthing.domain.member.repository.MemberRepository;
import com.ikae.snowthing.domain.member.repository.MemberResortRepository;
import com.ikae.snowthing.domain.member.repository.MemberRidingStyleRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {

    private final MemberRepository memberRepository;
    private final MemberResortRepository memberResortRepository;
    private final MemberRidingStyleRepository memberRidingStyleRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public MemberLoginResponse login(MemberLoginRequest request, HttpServletRequest httpRequest) {
        // 1. 회원 존재 여부 검증
        Member member = memberRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new IllegalArgumentException("INVALID_CREDENTIALS"));

        // 💡 [왜 passwordEncoder.matches() 를 쓰는가?]
        // - 이유: DB에 저장된 BCrypt 해시 비번은 솔트(Salt)가 섞여 있어 원본 평문 비번과 1:1 비교(equals)가 불가능합니다.
        // - 내부 동작: BCryptPasswordEncoder.matches()가 입력된 평문 비번에 DB의 솔트를 섞어 다시 해시한 후 60자리 해시값을 정밀 비교합니다.
        if (!passwordEncoder.matches(request.getPassword(), member.getPassword())) {
            throw new IllegalArgumentException("INVALID_CREDENTIALS");
        }

        // 💡 [왜 UsernamePasswordAuthenticationToken 을 생성하는가?]
        // - 이유: Spring Security가 인식할 수 있는 규격화된 인증 객체(Authentication)를 만들기 위함입니다.
        // - 역할: Principal(이메일), Credentials(null - 보안상 비번제거), Authorities(ROLE_USER)를 담아 인증 성공을 증명합니다.
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                member.getEmail(),
                null,
                Collections.singletonList(new SimpleGrantedAuthority(member.getRole().getKey()))
        );

        // 💡 [왜 ThreadLocal 에 SecurityContext 를 주입하는가?]
        // - 이유: 현재 이 요청을 처리 중인 자바 스레드의 ThreadLocal 메모리에 인증 정보를 저장하여, 
        //   이후 컨트롤러/서비스에서 로그인된 회원을 즉시 꺼낼 수 있도록 만듭니다.
        SecurityContext securityContext = SecurityContextHolder.createEmptyContext();
        securityContext.setAuthentication(authentication);
        SecurityContextHolder.setContext(securityContext);

        // 💡 [왜 httpRequest.changeSessionId() 를 무조건 호출해야 하는가?]
        // - 이유: 세션 고정 공격(Session Fixation Attack)을 방어하기 위해서입니다.
        // - 내부 동작: 서블릿 3.1 규격에 따라 톰캣 메모리의 기존 세션 ID(해커가 알 수도 있는 키)를 파기하고 32자리 새 난수 세션 ID로 즉시 교체합니다.
        HttpSession session = httpRequest.getSession(true);
        httpRequest.changeSessionId();

        // 💡 [세션 타임아웃 및 Remember-Me 처리]
        // - rememberMe가 true 일 경우 30일 (2592000초), false 일 경우 1시간 (3600초) 슬라이딩 세션 타임아웃 적용
        int sessionTimeoutSeconds = request.isRememberMe() ? 30 * 24 * 60 * 60 : 60 * 60;
        session.setMaxInactiveInterval(sessionTimeoutSeconds);

        // 💡 [왜 SPRING_SECURITY_CONTEXT_KEY 로 세션에 저장하는가?]
        // - 이유: 다음 HTTP 요청이 들어왔을 때, Spring Security의 SecurityContextHolderFilter가 세션 메모리에서 
        //   이 키를 조회하여 ThreadLocal에 SecurityContext를 자동으로 복원해주기 위해서입니다.
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, securityContext);
        session.setAttribute("SPRING_SECURITY_CONTEXT", securityContext);

        List<String> resortNames = memberResortRepository.findAllByMemberIdWithResort(member.getId()).stream()
                .map(mr -> mr.getResort().getName())
                .toList();

        List<String> ridingStyleNames = memberRidingStyleRepository.findAllByMemberIdWithRidingStyle(member.getId()).stream()
                .map(mrs -> mrs.getRidingStyle().getStyleName())
                .toList();

        return MemberLoginResponse.from(member, resortNames, ridingStyleNames);
    }

    public void logout(HttpServletRequest httpRequest) {
        // 1. 자바 스레드 메모리 청소
        SecurityContextHolder.clearContext();

        // 2. 톰캣 ConcurrentHashMap 에서 세션 파기
        HttpSession session = httpRequest.getSession(false);
        if (session != null) {
            session.invalidate();
        }
    }

    public MemberLoginResponse getMyProfile(String email) {
        Member member = memberRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("MEMBER_NOT_FOUND"));

        List<String> resortNames = memberResortRepository.findAllByMemberIdWithResort(member.getId()).stream()
                .map(mr -> mr.getResort().getName())
                .toList();

        List<String> ridingStyleNames = memberRidingStyleRepository.findAllByMemberIdWithRidingStyle(member.getId()).stream()
                .map(mrs -> mrs.getRidingStyle().getStyleName())
                .toList();

        return MemberLoginResponse.from(member, resortNames, ridingStyleNames);
    }
}
```

---

## 1.3 `SecurityConfig.java` (보안 필터체인 및 세션 설정)

```java
package com.ikae.snowthing.global.config;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of("http://localhost:3000"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    public SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                
                // 💡 [왜 HttpSessionSecurityContextRepository 를 등록하는가?]
                // - 이유: SecurityContextHolderFilter가 세션(HttpSession)에서 SPRING_SECURITY_CONTEXT를 읽어
                //   ThreadLocal로 안전하게 복원하도록 명시적 저장소를 등록합니다.
                .securityContext(securityContext -> securityContext
                        .securityContextRepository(securityContextRepository())
                )
                
                // 💡 [왜 sessionFixation().changeSessionId() 를 설정했는가?]
                // - 이유: 로그인 시 스프링 시큐리티 필터 차원에서도 세션 고정 방어를 2중으로 수호하기 위함입니다.
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                        .sessionFixation(sessionFixation -> sessionFixation.changeSessionId())
                )
                
                // 💡 [왜 logout 연동 설정을 구성했는가?]
                // - LogoutFilter가 POST /api/auth/logout 요청을 낚아채서 
                //   1) 세션 파기(invalidateHttpSession), 2) 스레드 청소(clearAuthentication), 3) JSESSIONID 쿠키 만료(deleteCookies)를 자동 수행합니다.
                .logout(logout -> logout
                        .logoutUrl("/api/auth/logout")
                        .invalidateHttpSession(true)
                        .clearAuthentication(true)
                        .deleteCookies("JSESSIONID")
                        .logoutSuccessHandler((request, response, authentication) -> {
                            response.setStatus(HttpServletResponse.SC_OK);
                            response.setContentType("application/json;charset=UTF-8");
                            response.getWriter().write("{\"message\":\"LOGOUT_SUCCESS\"}");
                        })
                )
                
                // 💡 [왜 authenticationEntryPoint 를 401 로 설정했는가?]
                // - 이유: 비회원이 /api/members/me 같은 인증 필요 API에 접근했을 때, 스프링 기본 302 리다이렉트나 403 대신 
                //   깔끔한 REST API 표준인 401 Unauthorized JSON 응답을 반환하기 위해서입니다.
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) -> {
                            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                            response.setContentType("application/json;charset=UTF-8");
                            response.getWriter().write("{\"error\":\"UNAUTHORIZED\"}");
                        })
                        .accessDeniedHandler((request, response, accessDeniedException) -> {
                            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                            response.setContentType("application/json;charset=UTF-8");
                            response.getWriter().write("{\"error\":\"FORBIDDEN\"}");
                        })
                )
                
                // 💡 [왜 URL 권한 규칙을 이렇게 설정했는가?]
                // - 회원가입(/api/members), 로그인(/api/auth/login), 리조트/성향 조사는 비회원 누구나 접근 허용(permitAll).
                // - 프로필 조회/수정(/api/members/me) 및 로그아웃은 로그인된 회원만 접근 허용(authenticated).
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/members", "/api/auth/login", "/api/resorts", "/api/riding-styles").permitAll()
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/members/me", "/api/auth/logout").authenticated()
                        .anyRequest().authenticated()
                );

        return http.build();
    }
}
```

---

## 1.4 `AuthControllerTest.java` (세션 로그인/고정방어 실증 테스트)

```java
package com.ikae.snowthing.domain.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ikae.snowthing.domain.auth.dto.MemberLoginRequest;
import com.ikae.snowthing.domain.member.dto.MemberSignUpRequest;
import com.ikae.snowthing.domain.member.repository.MemberRepository;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private MemberService memberService;
    @Autowired private MemberRepository memberRepository;

    @BeforeEach
    void setUp() {
        memberRepository.deleteAll();
        MemberSignUpRequest signUpRequest = MemberSignUpRequest.builder()
                .email("sessionuser@snowthing.com")
                .password("Password123!")
                .nickname("세션보더")
                .build();
        memberService.signUp(signUpRequest);
    }

    @AfterEach
    void tearDown() {
        memberRepository.deleteAll();
    }

    @Test
    @DisplayName("[검증 1] 올바른 로그인 요청 시 200 OK와 함께 회원 프로필이 반환되어야 한다")
    void login_Success() throws Exception {
        MemberLoginRequest loginRequest = MemberLoginRequest.builder()
                .email("sessionuser@snowthing.com")
                .password("Password123!")
                .build();

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.publicId").exists())
                .andExpect(jsonPath("$.email").value("sessionuser@snowthing.com"))
                .andExpect(jsonPath("$.nickname").value("세션보더"))
                .andExpect(jsonPath("$.role").value("ROLE_USER"));
    }

    @Test
    @DisplayName("[검증 2] 비밀번호가 일치하지 않는 경우 401 Unauthorized 가 반환되어야 한다")
    void login_InvalidPassword_Returns401() throws Exception {
        MemberLoginRequest loginRequest = MemberLoginRequest.builder()
                .email("sessionuser@snowthing.com")
                .password("WrongPassword999!")
                .build();

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("INVALID_CREDENTIALS"));
    }

    @Test
    @DisplayName("[검증 3] 로그인 시 changeSessionId() 가 호출되어 세션 ID가 새로 재발급(세션 고정 방어)되어야 한다")
    void login_ChangeSessionId_SessionFixationProtection() throws Exception {
        String oldSessionId = "BEFORE_LOGIN_SESSION_ID_9999";
        MockHttpSession beforeSession = new MockHttpSession(null, oldSessionId);

        MemberLoginRequest loginRequest = MemberLoginRequest.builder()
                .email("sessionuser@snowthing.com")
                .password("Password123!")
                .build();

        MvcResult mvcResult = mockMvc.perform(post("/api/auth/login")
                        .session(beforeSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();

        MockHttpSession afterSession = (MockHttpSession) mvcResult.getRequest().getSession();
        assertThat(afterSession).isNotNull();
        String afterSessionId = afterSession.getId();

        assertThat(afterSessionId).isNotEqualTo(oldSessionId);
    }

    @Test
    @DisplayName("[검증 4] 로그인 후 /api/members/me 접근 성공 및 로그아웃 후 세션 무효화로 접근 차단(401) 실증")
    void login_Me_And_Logout_SessionInvalidate_Success() throws Exception {
        MockHttpSession beforeSession = new MockHttpSession();

        MemberLoginRequest loginRequest = MemberLoginRequest.builder()
                .email("sessionuser@snowthing.com")
                .password("Password123!")
                .build();

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .session(beforeSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();

        MockHttpSession session = (MockHttpSession) loginResult.getRequest().getSession();

        mockMvc.perform(get("/api/members/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("sessionuser@snowthing.com"));

        mockMvc.perform(post("/api/auth/logout").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("LOGOUT_SUCCESS"));

        MockHttpSession emptySession = new MockHttpSession();
        mockMvc.perform(get("/api/members/me").session(emptySession))
                .andExpect(status().isUnauthorized());
    }
}
```

---

# PART 2. 스터디 요약 및 퀴즈 (면접 대비)

### ❓ Q1. `SecurityContextHolder`가 대체 무엇이며, 왜 Controller부터 Repository까지 파라미터 전달 없이 현재 유저 정보를 꺼낼 수 있는가?
- **A1**: `SecurityContextHolder`는 현재 HTTP 요청을 처리 중인 자바 스레드의 `ThreadLocal` 메모리 공간에 인증 객체(`SecurityContext`)를 보관하는 정적 헬퍼 클래스입니다. Java `Thread` 내부의 독점 메모리인 `ThreadLocalMap`에 저장되므로, 멀티스레드 동시성 환경에서도 스레드 간 인증 정보가 섞이지 않고 전역적으로 `SecurityContextHolder.getContext().getAuthentication()`을 통해 파라미터 전달 없이 안전하게 유저 객체를 꺼낼 수 있습니다.

### ❓ Q2. `SecurityContextHolder`는 왜 세션(Session) 및 스프링 시큐리티 필터 체인(Filter Chain)과 묶어서 같이 설명해야 하는가?
- **A2**: 세션은 톰캣 메모리에 영구 보관되는 **장기 저장소**이고, 필터 체인은 요청 진입 시 세션에서 인증 객체를 읽어 스레드 메모리로 복원하고 요청 완료 시 청소하는 **이송/제어 장치**이며, `SecurityContextHolder`는 요청이 수행되는 동안 스레드가 참조하는 **단기 임시 저장소**입니다. 세 가지가 하나처럼 움직이는 삼위일체 구조이기 때문에, 셋 중 하나라도 빠지면 스프링 시큐리티 세션 인증 메커니즘을 완성할 수 없어 함께 설명합니다.

### ❓ Q3. Tomcat의 Thread Pool 환경에서 `SecurityContextHolder.clearContext()`를 호출하지 않으면 어떤 심각한 보안 문제가 발생하는가?
- **A3**: Tomcat은 스레드 생성/파기 오버헤드를 방지하기 위해 `ThreadPoolExecutor` 기반으로 스레드를 재사용합니다. 요청 처리가 끝난 후 `clearContext()`를 호출하지 않고 스레드를 반납하면, 스레드 내부의 `ThreadLocalMap`에 이전 유저의 `SecurityContext`(인증 정보)가 남아있게 됩니다. 추후 다른 비회원이 동일한 스레드를 할당받아 요청을 전송할 때 이전 유저의 인증 객체가 그대로 조회되는 **심각한 개인정보 유출 보안 사고(Information Leakage)**가 발생합니다.

### ❓ Q4. `request.changeSessionId()`가 세션 고정 공격을 방어하는 물리적 메커니즘은 무엇인가요?
- **A4**: 로그인 성공 시 서블릿 3.1 규격의 `changeSessionId()`가 실행되면, Tomcat 세션 관리자(`StandardManager`)는 기존 세션 ID 키를 세션 맵에서 삭제하고, 암호학적 난수로 새로운 32자리 세션 ID 키를 발급하여 세션 맵에 재등록합니다. 이때 세션 내부의 사용자 속성 데이터(장바구니 등)는 100% 보존되며, 클라이언트 응답 헤더로 새 `JSESSIONID` 쿠키를 전달합니다. 결과적으로 공격자가 미리 유저에게 심어둔 이전 세션 ID 키는 완전히 파기되어 무효화되므로 세션 탈취 공격이 무력화됩니다.
