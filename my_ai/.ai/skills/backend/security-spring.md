# Spring Security Skill

## 기본 원칙

- 인증(Authentication)과 인가(Authorization)를 구분한다.
- Controller 내부의 단순 if문만으로 권한 정책 전체를 구현하지 않는다.
- 클라이언트가 전달한 userId, role, ownerId를 신뢰하지 않는다.
- 인증된 사용자 식별자는 서버의 Security Context 또는 검증된 토큰/세션에서 가져온다.
- UI에서 버튼을 숨기는 것은 보안 통제가 아니다. 서버가 최종 권한 검사를 수행한다.

## 세션 인증

- 로그인 성공 시 Session Fixation 대응을 확인한다.
- 세션 Cookie의 `HttpOnly`, `Secure`, `SameSite` 설정을 검토한다.
- 세션 만료, 로그아웃, 동시 세션 정책을 정의한다.
- 서버에서 세션 무효화를 명확히 수행한다.

## JWT/토큰 인증

- Access Token을 장기간 유효하게 두지 않는다.
- Refresh Token 탈취 대응 전략을 검토한다.
- 토큰 서명 검증뿐 아니라 issuer, audience, expiration 등 필요한 claim을 검증한다.
- 민감정보를 JWT Payload에 넣지 않는다. Payload는 암호화가 아니라 인코딩일 수 있다.
- 브라우저 저장 위치(LocalStorage/Cookie)의 XSS/CSRF Trade-off를 설명하고 결정한다.

## CSRF

- Cookie 기반 인증에서는 CSRF 위험을 반드시 검토한다.
- 단순히 “REST API니까 CSRF disable”이라고 결정하지 않는다.
- SameSite Cookie, CSRF Token, Origin/Referer 검증 등 적용 방식을 인증 구조와 함께 판단한다.

## CORS

- `*` 허용을 운영 환경 기본값으로 사용하지 않는다.
- credential 사용 시 허용 origin을 명확히 제한한다.
- 개발용 origin과 운영용 origin을 설정으로 분리한다.

## 비밀번호

- 평문 저장 금지.
- 단방향 비밀번호 해시 사용.
- Salt가 포함된 검증된 알고리즘(예: BCrypt, Argon2)을 사용한다.
- 암호화와 해시를 혼동하지 않는다.
- 비밀번호 정책은 길이만이 아니라 brute-force 대응, rate limit, MFA 가능성 등을 함께 검토한다.

## 권한

- 역할(Role)과 소유권(Ownership)을 구분한다.
- 관리자 Role이 있다고 모든 도메인 권한을 자동으로 허용하지 말고 정책을 명시한다.
- Method Security를 사용할 경우 Controller/Service 중 책임 위치를 프로젝트 정책에 맞게 일관되게 적용한다.
