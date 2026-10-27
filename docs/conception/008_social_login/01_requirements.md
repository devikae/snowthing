# 소셜 로그인 (OAuth 2.0) 요구사항 명세서

## 1. 목적 및 배경

- 회원가입 시 이메일 인증번호 입력과 복잡한 비밀번호 설정 과정을 거치지 않고도, 사용자가 자주 이용하는 소셜 계정(네이버, 구글)으로 빠르게 가입 및 로그인할 수 있도록 지원한다.
- 소셜 제공자(IdP)의 고유 사용자 ID를 로그인 식별자로 사용한다. 제공자 이메일은 신규 회원 생성과 안내에 사용하되, 이메일 문자열만으로 기존 SnowThing 계정과 자동 통합하지 않는다.

## 2. 지원 범위 (Providers)

1. **네이버 (`NAVER`)**: OAuth 2.0 Authorization Code Flow (`nid.naver.com`, `openapi.naver.com`)
2. **구글 (`GOOGLE`)**: OAuth 2.0 Authorization Code Flow (`accounts.google.com`, `oauth2.googleapis.com`, `openid email profile`)

## 3. 핵심 비즈니스 정책

### 3.1. 제공자 이메일 수신과 신뢰 경계
- 신규 소셜 회원을 생성하려면 이메일 주소가 필요하다. 이메일 제공에 동의하지 않은 미연결 계정은 `OAUTH_EMAIL_REQUIRED`로 거절한다.
- 이미 연결된 계정은 `(provider, providerUserId)`로 식별하므로 이후 프로필 응답에서 이메일이 빠져도 로그인할 수 있다.
- 네이버 프로필의 이메일 존재 여부는 현재 이메일 소유권 검증으로 취급하지 않는다.
- 구글은 `email_verified = true`이면서 Gmail 주소이거나 Workspace `hd` 클레임이 있을 때만 SnowThing의 `emailVerifiedAt`을 기록한다. 외부 도메인 이메일은 `email_verified = true`여도 로컬 인증 완료로 승격하지 않는다.
- 이메일 신뢰 여부가 낮더라도 기존 계정과 충돌하지 않는 신규 소셜 계정은 추가 이메일 인증 없이 생성한다.
- 비권위 이메일은 일반 가입·권위 있는 소셜 가입이 사용하는 `Member.email`을 점유하지 않는다. 신규 회원의 `Member.email`에는 제공자와 제공자 사용자 ID로 만든 `@users.invalid` 내부 식별 이메일을 저장하고 실제 이메일은 `MemberSocialAccount.providerEmail`에만 보관한다.

### 3.2. 계정 조회 및 연결 충돌 정책 (Account Linking)
- 1명의 회원(`Member`)은 구글, 네이버 소셜 계정을 각각 최대 1개씩(총 2개 복수 연동) 연결할 수 있다.
- 소셜 인증 완료 시 다음 순서로 회원을 식별한다:
  1. `(provider, providerUserId)`로 이미 연결된 `MemberSocialAccount`가 존재하면 해당 `Member`로 즉시 로그인한다.
  2. 연결된 소셜 계정은 없으나 권위 있는 정규화 이메일(`emailNormalizer.normalize(email)`)과 일치하는 기존 `Member`가 존재하면 자동 연결하지 않고 `OAUTH_ACCOUNT_LINK_REQUIRED`로 거절한다. 이메일 일치는 기존 SnowThing 계정의 현재 소유권 증명이 아니다. 비권위 이메일은 이 조회에 사용하지 않는다.
  3. 권위 있는 이메일과 일치하는 회원도 없으면 신규 `Member`와 `MemberSocialAccount`를 즉시 자동 생성하고 로그인한다. 비권위 프로필은 실제 이메일을 중복 조회하지 않고 내부 식별 이메일로 회원을 생성한다.

### 3.3. 최초 가입 시 별도 입력 화면 없는 즉시 가입 및 닉네임 자동 부여
- 최초 소셜 로그인 사용자는 추가 정보 입력 화면을 거치지 않고 즉시 자동 가입 및 세션 로그인 처리한다.
- 자동 생성되는 닉네임은 SnowThing 닉네임 정책(`^[a-zA-Z0-9가-힣]{2,10}$`, 2~10자 한글·영문·숫자, UNIQUE)을 100% 만족하도록 소셜 닉네임의 허용 문자 접두사(최대 6자, 없으면 `"보더"`)와 4자리 난수 영숫자를 조합해 생성하며, 마이페이지에서 언제든 수정할 수 있다.
- 소셜 전용 가입 회원의 `password` 컬럼은 `NULL`로 저장한다. 제공자 이메일을 SnowThing에서 별도로 확인하기 전에는 `emailVerifiedAt`이 `NULL`일 수 있으며, 비밀번호 재설정을 완료하면 이메일 소유권 확인과 함께 비밀번호를 설정할 수 있다.

### 3.4. 정지·탈퇴 회원 차단
- 조회되거나 연결 대상인 `Member`의 상태가 `SUSPENDED`(정지) 또는 `WITHDRAWN`(탈퇴)인 경우 세션 발급을 차단하고 에러 코드(`ACCOUNT_SUSPENDED`, `ACCOUNT_WITHDRAWN`)와 함께 로그인 화면으로 리다이렉트한다.
