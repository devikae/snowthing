# 소셜 로그인 도메인 모델 명세서

## 1. 도메인 구성 요소

### 1.1. `SocialProvider` (Enum)
지원하는 OAuth 2.0 공급자를 나타낸다.
- `GOOGLE` (`google`)
- `NAVER` (`naver`)

대소문자 구분 없이 경로 변수(`{provider}`)를 파싱하며, 지원하지 않는 값일 경우 `ErrorCode.OAUTH_PROVIDER_UNSUPPORTED` 예외를 발생시킨다.

### 1.2. `MemberSocialAccount` (Entity)
`Member`와 1:N 관계를 맺는 소셜 계정 연결 엔티티이다.

| 필드명 | 타입 | 필수 여부 | 설명 |
| :--- | :--- | :--- | :--- |
| `id` (`social_account_id`) | `Long` | PK | 내부 식별자 (`AUTO_INCREMENT`) |
| `member` (`member_id`) | `Member` | FK, NOT NULL | 연결된 회원 (`LAZY`) |
| `provider` | `SocialProvider` | NOT NULL | 소셜 제공자 (`GOOGLE`, `NAVER`) |
| `providerUserId` | `String` | NOT NULL | 제공자 측 고유 회원 식별자 (`sub` 또는 `id`) |
| `providerEmail` | `String` | NOT NULL | 연결 시점 소셜 측으로부터 전달받은 이메일 |
| `connectedAt` | `LocalDateTime` | NOT NULL | 소셜 계정 연결 시각 |

### 1.3. `OAuthUserProfile` (Value Object / Record)
외부 OAuth2 UserInfo 응답을 도메인 표준 규격으로 변환한 불변 레코드이다.
- `SocialProvider provider`
- `String providerUserId`
- `String email`
- `boolean emailVerified`
- `String nickname`

### 1.4. `SocialNicknameGenerator` (Domain Component)
소셜 닉네임으로부터 [`NicknamePolicy`](file:///c:/Users/ikaes/IdeaProjects/snowthing/backend/src/main/java/com/ikae/snowthing/domain/member/validation/NicknamePolicy.java) (`^[a-zA-Z0-9가-힣]{2,10}$`)를 충족하는 고유 닉네임을 생성한다.
- 소셜 닉네임에서 한글(`가-힣`), 영문(`a-zA-Z`), 숫자(`0-9`)만 추출한다.
- 추출된 문자열이 비어 있으면 기본 접두사 `"보더"`를 사용하고, 있으면 앞 최대 6글자를 접두사로 사용한다.
- 첫 시도에 정제된 소셜 닉네임 자체가 2~10자이고 중복되지 않으면 그대로 사용하며, 중복되거나 규칙에 맞지 않으면 `접두사(최대 6자) + 4자리 난수 영소문자/숫자`(총 6~10자)를 생성한다.

## 2. 계층 책임 분리 (`AGENTS.md` 24번 수칙 준수)

- **Controller (`OAuth2AuthController`)**:
  - `HttpSession`을 통한 OAuth2 `state` 및 `returnUrl` 저장·검증·소멸 처리
  - 인증 성공 후 `httpRequest.changeSessionId()`, `SecurityContextRepository` 저장, `MemberSessionRegistry` 등록 및 302 리다이렉트 응답 생성
- **External Client (`OAuth2ExternalClient`)**:
  - DB 트랜잭션 외부에서 인가 코드(`code`)로 Access Token 교환 및 UserInfo 조회 수행 (`RestClient` + 명시적 타임아웃)
- **Service (`SocialAuthService`)**:
  - 서블릿/세션 API(`HttpSession`, `SecurityContextHolder`)를 일절 참조하지 않음
  - `OAuthUserProfile` 검증 및 회원 조회·자동 연결·신규 생성 트랜잭션 수행
