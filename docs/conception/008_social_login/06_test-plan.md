# 소셜 로그인 테스트 계획

## 1. 외부 OAuth2 클라이언트 계약 테스트 (`OAuth2ExternalClientTest`)

- **Google / Naver 토큰 교환 및 프로필 조회**:
  - `MockRestServiceServer`를 사용하여 인가 코드 전송 시 `grant_type=authorization_code`, `client_id`, `client_secret`, `redirect_uri`, `code` 파라미터가 정확히 전송되는지 검증한다.
  - Access Token으로 각 공급자의 UserInfo 응답 JSON을 파싱하여 `OAuthUserProfile`(`provider`, `providerUserId`, `email`, `emailAuthoritative`, `nickname`)이 정확히 생성되는지 검증한다.
  - 네이버 이메일과 `hd` 없는 구글 외부 도메인은 `emailAuthoritative = false`, Gmail과 Workspace는 필요한 조건을 만족할 때 `true`인지 검증한다.
- **예외 케이스**:
  - 클라이언트 설정(`clientId` 등) 누락 시 `OAUTH_PROVIDER_NOT_CONFIGURED` 발생 검증
  - 외부 API 4xx/5xx 응답 시 `OAUTH_AUTHENTICATION_FAILED` 변환 검증

## 2. 비즈니스 서비스 단위·통합 테스트 (`SocialAuthServiceTest`)

- **신규 회원 자동 가입**:
  - 처음 로그인하는 소셜 프로필인 경우 `Member`(`password = null`)와 `MemberSocialAccount`가 함께 저장되는지 검증한다.
  - 네이버와 구글 외부 도메인은 `emailVerifiedAt = null`, 권위 있는 구글 이메일은 인증 시각이 기록되는지 검증한다.
- **기존 이메일 회원 자동 연결 차단**:
  - 이미 존재하는 이메일이면 새 `Member`와 `MemberSocialAccount`를 만들지 않고 `OAUTH_ACCOUNT_LINK_REQUIRED`를 반환하는지 검증한다.
- **이미 연결된 소셜 계정 재로그인**:
  - `(provider, providerUserId)`가 이미 존재하면 추가 insert 없이 기존 `Member`를 반환하는지 검증한다.
- **이메일 필수 범위 검증**:
  - 미연결 프로필의 `email`이 `null`/빈 문자열이면 `OAUTH_EMAIL_REQUIRED`가 발생하는지 검증한다.
  - 이미 연결된 프로필은 이메일이 누락되어도 공급자 고유 ID로 로그인되는지 검증한다.
- **정지·탈퇴 회원 차단**:
  - `SUSPENDED` 회원은 `ACCOUNT_SUSPENDED`, `WITHDRAWN` 회원은 `ACCOUNT_WITHDRAWN` 예외 발생 검증.
- **닉네임 자동 생성 정책 (`SocialNicknameGeneratorTest`)**:
  - 특수문자/이모지/공백이 섞인 소셜 닉네임이나 1자/장문 닉네임에서도 항상 `^[a-zA-Z0-9가-힣]{2,10}$`를 만족하는지 검증한다.
  - 닉네임 중복 시 난수 접미사를 붙여 재시도하는지 검증한다.
- **동시성 및 무결성 오류**:
  - 회원 닉네임 유니크 제약 충돌을 `DUPLICATE_NICKNAME`으로 변환하고 같은 트랜잭션에서 재시도하지 않는지 검증한다.
  - 회원 이메일 유니크 제약 충돌을 `OAUTH_ACCOUNT_LINK_REQUIRED`로 변환하고, 소셜 계정 유니크 제약은 OAuth 오류로, 알 수 없는 무결성 오류는 원본 그대로 전파하는지 검증한다.

## 3. 컨트롤러 및 보안 흐름 테스트 (`OAuth2AuthControllerTest`)

- 인가 요청 시 세션에 `state`와 검증된 `returnUrl`이 저장되고 소셜 인가 URL로 302 리다이렉트되는지 검증한다.
- 두 탭의 요청을 동시에 저장하고 역순 콜백도 각각 성공하는지, 변조된 콜백이 정상 요청을 삭제하지 않는지, 최대 5개 제한이 적용되는지 검증한다.
- 콜백 요청 시:
  - 정상 `state` 및 `code`일 때 세션(`SecurityContext`)이 생성되고 `MemberSessionRegistry`에 등록된 후 `{frontendBaseUrl}{returnUrl}`로 302 리다이렉트되는지 검증한다.
  - `state` 불일치 또는 재사용(Replay) 시 `/login?error=OAUTH_STATE_INVALID`로 리다이렉트되는지 검증한다.
  - 악성 `returnUrl`(`https://evil.com`, `//evil.com`) 입력 시 `/`로 안전하게 치환되는지 검증한다.
  - URI 문법상 유효하지 않은 `returnUrl` 입력 시 `/`로 치환되는지 검증한다.
  - IP당 분당 20회 제한을 초과한 인가 요청이 신규 세션 생성 전에 거부되는지 검증한다.

## 4. 운영 마이그레이션 검증

- `member_social_account.provider_user_id`의 collation이 `utf8mb4_bin`인지 검증한다.
- 대소문자만 다른 제공자 사용자 ID가 서로 다른 값으로 비교되는지 확인한다.
