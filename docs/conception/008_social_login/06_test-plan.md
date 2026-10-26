# 소셜 로그인 테스트 계획

## 1. 외부 OAuth2 클라이언트 계약 테스트 (`OAuth2ExternalClientTest`)

- **Google / Naver 토큰 교환 및 프로필 조회**:
  - `MockRestServiceServer`를 사용하여 인가 코드 전송 시 `grant_type=authorization_code`, `client_id`, `client_secret`, `redirect_uri`, `code` 파라미터가 정확히 전송되는지 검증한다.
  - Access Token으로 각 공급자의 UserInfo 응답 JSON을 파싱하여 `OAuthUserProfile`(`provider`, `providerUserId`, `email`, `emailVerified`, `nickname`)이 정확히 생성되는지 검증한다.
- **예외 케이스**:
  - 클라이언트 설정(`clientId` 등) 누락 시 `OAUTH_PROVIDER_NOT_CONFIGURED` 발생 검증
  - 외부 API 4xx/5xx 응답 시 `OAUTH_AUTHENTICATION_FAILED` 변환 검증

## 2. 비즈니스 서비스 단위·통합 테스트 (`SocialAuthServiceTest`)

- **신규 회원 자동 가입**:
  - 처음 로그인하는 소셜 프로필인 경우 `Member`(`password = null`, `emailVerifiedAt != null`)와 `MemberSocialAccount`가 함께 저장되는지 검증한다.
- **기존 이메일 회원 자동 계정 연결**:
  - 이미 일반 회원가입(또는 타 소셜 가입)으로 존재하는 이메일인 경우, 새 `Member`를 만들지 않고 기존 `Member`에 `MemberSocialAccount`를 추가 연결하는지 검증한다.
- **이미 연결된 소셜 계정 재로그인**:
  - `(provider, providerUserId)`가 이미 존재하면 추가 insert 없이 기존 `Member`를 반환하는지 검증한다.
- **이메일 필수 및 인증 여부 검증**:
  - `email`이 `null`/빈 문자열이면 `OAUTH_EMAIL_REQUIRED`, `emailVerified = false`이면 `OAUTH_EMAIL_NOT_VERIFIED` 예외가 발생하는지 검증한다.
- **정지·탈퇴 회원 차단**:
  - `SUSPENDED` 회원은 `ACCOUNT_SUSPENDED`, `WITHDRAWN` 회원은 `ACCOUNT_WITHDRAWN` 예외 발생 검증.
- **닉네임 자동 생성 정책 (`SocialNicknameGeneratorTest`)**:
  - 특수문자/이모지/공백이 섞인 소셜 닉네임이나 1자/장문 닉네임에서도 항상 `^[a-zA-Z0-9가-힣]{2,10}$`를 만족하는지 검증한다.
  - 닉네임 중복 시 난수 접미사를 붙여 재시도하는지 검증한다.

## 3. 컨트롤러 및 보안 흐름 테스트 (`OAuth2AuthControllerTest`)

- 인가 요청 시 세션에 `state`와 검증된 `returnUrl`이 저장되고 소셜 인가 URL로 302 리다이렉트되는지 검증한다.
- 콜백 요청 시:
  - 정상 `state` 및 `code`일 때 세션(`SecurityContext`)이 생성되고 `MemberSessionRegistry`에 등록된 후 `{frontendBaseUrl}{returnUrl}`로 302 리다이렉트되는지 검증한다.
  - `state` 불일치 또는 재사용(Replay) 시 `/login?error=OAUTH_STATE_INVALID`로 리다이렉트되는지 검증한다.
  - 악성 `returnUrl`(`https://evil.com`, `//evil.com`) 입력 시 `/`로 안전하게 치환되는지 검증한다.
