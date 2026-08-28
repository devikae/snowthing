# [Notion] 세션 인증 순서도 (Session Authentication Flowchart)

> 📌 **작성 일자**: 2026년 8월 10일 (최종 수정: 2026년 8월 12일)  
> 🏷️ **문서 목적**: 마크다운(Markdown) 및 노션(Notion) 환경에서 100% 정상 visual 다이어그램으로 렌더링되는 **Spring Session Redis** 기반 세션 인증 순서도

---

## 📊 1. 세션 인증 전체 플로우차트 (Flowchart TD)

Below is the Mermaid flowchart rendered directly in GitHub Markdown and Notion.

```mermaid
flowchart TD
    A["클라이언트 API 요청 /api/..."] --> B{"1. 비인증 허용 API인가? (PermitAll)"}
    
    %% 비인증 허용 경로
    B -- Yes --> C["비인증 로직 즉시 실행 (회원가입/목록조회/비회원글작성)"]
    C --> END1["200 OK / 201 Created 응답"]

    %% 인증 필요 경로
    B -- No --> D{"2. JSESSIONID 쿠키가 헤더에 존재하는가?"}
    
    %% 쿠키 없음
    D -- No --> E1["401 Unauthorized 에러 (로그인이 필요합니다)"]

    %% 쿠키 존재
    D -- Yes --> E2{"3. Spring Session Redis 저장소에 유효한 세션이 존재하는가?"}
    
    %% 세션 만료/파기됨
    E2 -- No --> F1["401 Unauthorized 에러 (세션이 만료되었습니다)"]
    F1 --> F2["Set-Cookie: JSESSIONID=; Max-Age=0 (쿠키 클리어)"]

    %% 세션 유효함
    E2 -- Yes --> G{"4. 요청 종류 구분"}

    %% 경로 1: 로그인 요청 처리 (Session Fixation 방어)
    G -- 로그인 요청 --> H1["Security: changeSessionId (기존 세션 파기 & 신규 세션ID 재발급)"]
    H1 --> H2["Spring Session Redis에 memberId & role 0.0001초 저장"]
    H2 --> H3["Set-Cookie: JSESSIONID=new_id (HttpOnly, Secure, SameSite=Lax)"]
    H3 --> END2["200 OK 로그인 성공"]

    %% 경로 2: 일반 인증 API 요청 (프로필/글작성 등)
    G -- 일반 인증 API --> I1["Spring Session Redis에서 memberId & role 0.0001초 추출"]
    I1 --> I2["SecurityContextHolder에 인증 객체 등록"]
    I2 --> I3["소유자 권한 검증 & 비즈니스 로직 실행"]
    I3 --> END3["200 OK 응답 데이터 반환"]

    %% 경로 3: 로그아웃 요청
    G -- 로그아웃 요청 --> J1["Redis 세션 완전 파기 (spring:session 키 삭제)"]
    J1 --> J2["Set-Cookie: JSESSIONID=; Max-Age=0 (브라우저 쿠키 즉시 만료)"]
    J2 --> END4["200 OK 로그아웃 성공"]

    %% 스타일링
    style A fill:#333333,stroke:#ffffff,color:#ffffff
    style B fill:#1f618d,stroke:#ffffff,color:#ffffff
    style D fill:#1f618d,stroke:#ffffff,color:#ffffff
    style E2 fill:#1f618d,stroke:#ffffff,color:#ffffff
    style G fill:#28b463,stroke:#ffffff,color:#ffffff
    style E1 fill:#922b21,stroke:#ffffff,color:#ffffff
    style F1 fill:#922b21,stroke:#ffffff,color:#ffffff
    style H1 fill:#d4ac0d,stroke:#ffffff,color:#000000
    style J1 fill:#d4ac0d,stroke:#ffffff,color:#000000
```

---

## 🔍 2. 플로우차트 단계별 조건 분기 설명

### 1단계: API 접근 권한 판별 (`PermitAll` vs `Authenticated`)
* `/api/members` (회원가입), `/api/auth/login` (로그인), `/api/posts` (목록/상세 조회) 같은 **비인증 공개 API는 쿠키 검증을 스킵하고 즉시 실행**됩니다.

### 2단계 & 3단계: 2중 세션 쿠키 검증 (`JSESSIONID` + Spring Session Redis)
* **1차 검증 (브라우저 쿠키)**: 요청 헤더에 `JSESSIONID` 쿠키가 아예 없으면 즉시 `401 Unauthorized`를 반환합니다.
* **2차 검증 (Spring Session Redis RAM)**: 쿠키가 있더라도 Redis 세션 저장소(`spring:session:sessions:...`)에서 만료되거나 삭제되었으면 `401 Unauthorized` 반환과 함께 브라우저 쿠키를 만료(`Max-Age=0`)시킵니다.

### 4단계: 요청 종류별 세션 락 & 처리 메커니즘
1. **로그인 시 (Session Fixation 방어)**:
   * 기존 임시 세션 ID를 파기하고 신규 세션 ID를 발급하는 `request.changeSessionId()`를 호출하여 세션 탈취 공격을 방어합니다.
   * `Set-Cookie: JSESSIONID=...; Path=/api; HttpOnly; Secure; SameSite=Lax` 쿠키를 내려줍니다.
2. **일반 인증 API 요청 시**:
   * Redis에서 0.0001초 만에 `memberId`를 추출하여 `SecurityContextHolder`에 등록 후 비즈니스 로직을 수행합니다.
3. **로그아웃 시**:
   * Redis 세션 키를 삭제하여 서버 세션을 완전 파기하고, 쿠키 만료 헤더를 내려줍니다.
