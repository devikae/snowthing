# 소셜 로그인 API 명세서

## 1. 엔드포인트 요약

| HTTP Method | URI | 설명 | 권한 |
| :--- | :--- | :--- | :--- |
| `GET` | `/api/v1/auth/oauth2/authorize/{provider}` | 소셜 로그인 인가 요청 시작 (302 Redirect) | `PermitAll` |
| `GET` | `/api/v1/auth/oauth2/callback/{provider}` | 소셜 로그인 인가 코드 콜백 처리 및 세션 발급 (302 Redirect) | `PermitAll` |
| `POST` | `/api/v1/auth/oauth2/link/{provider}` | 로그인한 회원의 소셜 계정 연결 시작 (IKA-73) | `Authenticated`, CSRF |
| `GET` | `/api/v1/members/me/social-accounts` | 내 로그인 방법 목록 (IKA-73) | `Authenticated` |
| `DELETE` | `/api/v1/members/me/social-accounts/{provider}` | 소셜 계정 연결 해제 (IKA-73) | `Authenticated`, CSRF |

> 운영 Nginx가 `/api/` 하위 요청을 백엔드(`8080`)로 프록시하므로, 모든 OAuth2 엔드포인트는 `/api/v1/auth/oauth2/...` 경로 아래에 둔다.

---

## 2. 상세 명세

### 2.1. 소셜 인가 요청 시작 (`GET /api/v1/auth/oauth2/authorize/{provider}`)
- **Path Parameter**:
  - `provider`: `naver` | `google` (대소문자 무관)
- **Query Parameter**:
  - `returnUrl` (선택): 로그인 성공 후 돌아갈 프론트엔드 상대 경로 (예: `/carpool`). `/`로 시작하고 `//`로 시작하지 않는 안전한 내부 경로만 허용하며, 그 외에는 `/`로 대체한다.
- **동작**:
  1. 해당 `provider`의 클라이언트 설정(`clientId`, `redirectUri`)이 유효한지 확인한다. 미설정 시 프론트엔드 `/login?error=OAUTH_PROVIDER_NOT_CONFIGURED`로 302 리다이렉트한다.
  2. 암호학적 난수 `state`와 `returnUrl`, 만료 시각(10분)을 `HttpSession`에 저장한다. 한 세션에서 최대 5개의 대기 요청을 보관하며 한도를 넘으면 가장 오래된 요청을 제거한다.
  3. 각 소셜 공급자의 Authorization Endpoint로 `302 Found` 리다이렉트한다.

### 2.2. 소셜 인가 콜백 (`GET /api/v1/auth/oauth2/callback/{provider}`)
- **Path Parameter**:
  - `provider`: `naver` | `google`
- **Query Parameter**:
  - `code` (선택): 소셜 공급자가 발급한 인가 코드
  - `state` (선택): CSRF 방지용 상태 토큰
  - `error` (선택): 사용자가 취소했거나 공급자 측 에러가 발생한 경우의 에러 문자열
- **성공 시 응답 (`302 Found`)**:
  - `Set-Cookie: JSESSIONID=...; HttpOnly; SameSite=Lax`
  - `Location: {frontendBaseUrl}{returnUrl}` (예: `https://snowthing.org/posts`)
- **실패 시 응답 (`302 Found`)**:
  - `Location: {frontendBaseUrl}/login?error={ErrorCode.name()}`

### 2.3. 소셜 계정 연결 (IKA-73)

- `POST /api/v1/auth/oauth2/link/{provider}`: 로그인한 회원만 쓴다. 이미 같은 제공자가 연결돼 있으면 `SOCIAL_PROVIDER_ALREADY_LINKED`로 바로 거부한다. 성공하면 `200 {"authorizationUrl": "..."}`를 돌려주고 프론트가 그 주소로 이동한다. 인가 요청과 같은 IP 제한을 받는다.
- 서버 세션의 state 목록에 연결할 회원 id(`linkMemberId`)를 함께 저장한다. 회원 id는 요청 값이 아니라 세션의 인증 정보에서 꺼낸다.
- 콜백은 2.2와 같은 주소를 쓴다. state에 `linkMemberId`가 있으면 로그인 대신 연결로 처리한다.
  1. 지금 세션의 회원이 `linkMemberId`와 다르면 토큰 교환 없이 `SOCIAL_LINK_SESSION_MISMATCH`로 끝낸다.
  2. 회원 행을 잠그고 활성 상태인지 다시 확인한다.
  3. 그 소셜 계정이 다른 회원에게 연결돼 있으면 `SOCIAL_ACCOUNT_IN_USE`로 거부한다. 계정을 합치지 않는다.
  4. 같은 제공자가 이미 연결돼 있으면 `SOCIAL_PROVIDER_ALREADY_LINKED`로 거부한다.
  5. 성공하면 `{frontendBaseUrl}/profile?socialLinked={PROVIDER}`, 실패하면 `{frontendBaseUrl}/profile?socialLinkError={ErrorCode.name()}`로 이동한다. 세션은 새로 만들지 않는다.
- `GET /api/v1/members/me/social-accounts`: `{"passwordLogin": true, "accounts": [{"provider": "KAKAO", "connectedAt": "..."}]}`. `passwordLogin`은 `member.password`가 있는지다(이메일 가입 회원만 있다).
- `DELETE /api/v1/members/me/social-accounts/{provider}`: `204`. 해제한 뒤 남는 로그인 방법(비밀번호 1개와 남은 소셜 연결 수의 합)이 0이면 `SOCIAL_LAST_LOGIN_METHOD`로 거부한다. 연결이 없으면 `SOCIAL_ACCOUNT_NOT_LINKED`.

---

## 3. 에러 코드 규격 (`ErrorCode`)

| ErrorCode Enum | 코드 | HTTP Status | 사용자 안내 메시지 |
| :--- | :--- | :--- | :--- |
| `OAUTH_PROVIDER_UNSUPPORTED` | `AUTH_003` | `400 Bad Request` | 지원하지 않는 소셜 로그인 제공자입니다. |
| `OAUTH_PROVIDER_NOT_CONFIGURED` | `AUTH_004` | `503 Service Unavailable` | 소셜 로그인 설정이 완료되지 않았습니다. |
| `OAUTH_STATE_INVALID` | `AUTH_005` | `400 Bad Request` | 소셜 로그인 인증 상태가 만료되었거나 유효하지 않습니다. 다시 시도해 주세요. |
| `OAUTH_AUTHENTICATION_FAILED` | `AUTH_006` | `401 Unauthorized` | 소셜 로그인 인증에 실패했습니다. 잠시 후 다시 시도해 주세요. |
| `OAUTH_EMAIL_REQUIRED` | `AUTH_007` | `400 Bad Request` | 소셜 계정의 이메일 제공 동의가 필요합니다. |
| `OAUTH_EMAIL_NOT_VERIFIED` | `AUTH_008` | `400 Bad Request` | 인증되지 않은 소셜 이메일 계정으로는 로그인할 수 없습니다. |
| `ACCOUNT_SUSPENDED` | `AUTH_009` | `403 Forbidden` | 이용이 정지된 계정입니다. |
| `ACCOUNT_WITHDRAWN` | `AUTH_010` | `403 Forbidden` | 탈퇴 처리된 계정입니다. |
| `OAUTH_RATE_LIMIT` | `AUTH_011` | `429 Too Many Requests` | 소셜 로그인 요청이 너무 많습니다. 잠시 후 다시 시도해 주세요. |
| `OAUTH_ACCOUNT_LINK_REQUIRED` | `AUTH_012` | `409 Conflict` | 이미 가입된 이메일입니다. 기존 로그인 방법을 이용해 주세요. |
| `SOCIAL_ACCOUNT_IN_USE` | `AUTH_015` | `409 Conflict` | 이미 다른 눈팅 계정에서 쓰고 있는 소셜 계정입니다. 이 계정에 연결하려면 그 계정을 먼저 탈퇴해 주세요. |
| `SOCIAL_PROVIDER_ALREADY_LINKED` | `AUTH_016` | `409 Conflict` | 이미 연결된 로그인 방법입니다. 다른 계정으로 바꾸려면 먼저 연결을 해제해 주세요. |
| `SOCIAL_ACCOUNT_NOT_LINKED` | `AUTH_017` | `404 Not Found` | 연결되지 않은 소셜 계정입니다. |
| `SOCIAL_LAST_LOGIN_METHOD` | `AUTH_018` | `409 Conflict` | 마지막 남은 로그인 방법은 해제할 수 없습니다. 다른 로그인 방법을 먼저 연결해 주세요. |
| `SOCIAL_LINK_SESSION_MISMATCH` | `AUTH_019` | `401 Unauthorized` | 연결을 시작한 계정과 지금 로그인한 계정이 다릅니다. 다시 로그인한 뒤 시도해 주세요. |
