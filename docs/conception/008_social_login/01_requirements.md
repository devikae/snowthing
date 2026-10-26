# 소셜 로그인 (OAuth 2.0) 요구사항 명세서

## 1. 목적 및 배경

- 회원가입 시 이메일 인증번호 입력과 복잡한 비밀번호 설정 과정을 거치지 않고도, 사용자가 자주 이용하는 소셜 계정(네이버, 구글)으로 빠르게 가입 및 로그인할 수 있도록 지원한다.
- 소셜 제공자(IdP)로부터 검증된 이메일(`email_verified = true`)을 필수로 수신하여 기존 이메일 가입 계정 및 타 소셜 계정과 1:N으로 안전하게 통합한다.

## 2. 지원 범위 (Providers)

1. **네이버 (`NAVER`)**: OAuth 2.0 Authorization Code Flow (`nid.naver.com`, `openapi.naver.com`)
2. **구글 (`GOOGLE`)**: OAuth 2.0 Authorization Code Flow (`accounts.google.com`, `oauth2.googleapis.com`, `openid email profile`)

## 3. 핵심 비즈니스 정책

### 3.1. 검증된 실제 이메일 필수 수신 정책
- 두 제공자 모두 사용자의 실제 이메일 주소 제공 및 이메일 검증 여부(`emailVerified = true`)가 확인되어야만 가입·로그인을 허용한다.
- 사용자가 소셜 동의 화면에서 이메일 제공 동의를 해제하거나, 이메일이 미인증 상태인 경우 로그인을 거부하고 로그인 화면으로 에러 코드(`OAUTH_EMAIL_REQUIRED`, `OAUTH_EMAIL_NOT_VERIFIED`)와 함께 리다이렉트한다.

### 3.2. 계정 조회 및 1:N 자동 연결 정책 (Account Linking)
- 1명의 회원(`Member`)은 구글, 네이버 소셜 계정을 각각 최대 1개씩(총 2개 복수 연동) 연결할 수 있다.
- 소셜 인증 완료 시 다음 순서로 회원을 식별한다:
  1. `(provider, providerUserId)`로 이미 연결된 `MemberSocialAccount`가 존재하면 해당 `Member`로 즉시 로그인한다.
  2. 연결된 소셜 계정은 없으나 정규화된 이메일(`emailNormalizer.normalize(email)`)과 일치하는 기존 `Member`가 존재하면, 해당 `Member`에 `MemberSocialAccount`를 신규 생성해 자동 연결한 뒤 로그인한다. 이때 기존 회원의 `emailVerifiedAt`이 `NULL`이면 현재 시각으로 갱신한다.
  3. 일치하는 이메일 회원도 없으면 신규 `Member`와 `MemberSocialAccount`를 즉시 자동 생성하고 로그인한다.

### 3.3. 최초 가입 시 별도 입력 화면 없는 즉시 가입 및 닉네임 자동 부여
- 최초 소셜 로그인 사용자는 추가 정보 입력 화면을 거치지 않고 즉시 자동 가입 및 세션 로그인 처리한다.
- 자동 생성되는 닉네임은 SnowThing 닉네임 정책(`^[a-zA-Z0-9가-힣]{2,10}$`, 2~10자 한글·영문·숫자, UNIQUE)을 100% 만족하도록 소셜 닉네임의 허용 문자 접두사(최대 6자, 없으면 `"보더"`)와 4자리 난수 영숫자를 조합해 생성하며, 마이페이지에서 언제든 수정할 수 있다.
- 소셜 전용 가입 회원의 `password` 컬럼은 `NULL`로 저장하며, 추후 일반 비밀번호 로그인도 원할 경우 기존 '비밀번호 재설정' 플로우를 통해 비밀번호를 설정할 수 있다.

### 3.4. 정지·탈퇴 회원 차단
- 조회되거나 연결 대상인 `Member`의 상태가 `SUSPENDED`(정지) 또는 `WITHDRAWN`(탈퇴)인 경우 세션 발급을 차단하고 에러 코드(`ACCOUNT_SUSPENDED`, `ACCOUNT_WITHDRAWN`)와 함께 로그인 화면으로 리다이렉트한다.
