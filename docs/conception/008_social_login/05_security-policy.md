# 소셜 로그인 보안 및 운영 정책

## 1. OAuth 2.0 CSRF 및 Replay 방어 (`state` 검증)

- 인가 시작 시 `SecureRandom`으로 생성한 32바이트(Base64Url 인코딩) 일회성 `state` 토큰을 `HttpSession`에 저장한다.
- 콜백 진입 시 세션에 보관된 `state`를 **즉시 꺼내어 제거(One-time Consume)**한 뒤, 쿼리 파라미터 `state`, 요청 `provider`, 만료 시간(10분 이내)을 상수 시간 비교(`MessageDigest.isEqual`)로 검증한다.
- 불일치하거나 만료된 경우 즉시 `OAUTH_STATE_INVALID`로 차단하여 OAuth CSRF(로그인 세션 강제 바인딩 공격) 및 재전송 공격을 방어한다.

## 2. Open Redirect 방어 (`returnUrl` 화이트리스트 검증)

- `returnUrl` 파라미터는 반드시 단일 슬래시(`/`)로 시작하고 `//`나 `http:`, `https:`, 역슬래시(`\`), 제어문자를 포함하지 않으며 URI 문법상 유효한 상대 경로만 허용한다.
- 최종 리다이렉트 URL은 서버 설정에 고정된 `snowthing.oauth2.frontend-base-url` (`http://localhost:3000` 또는 `https://snowthing.org`) 뒤에 검증된 상대 경로만 결합하여 외부 피싱 사이트로의 리다이렉트를 원천 차단한다.

## 3. 이메일 미인증 계정 연결 공격(Pre-hijacking) 방어

- 소셜 계정과 기존 이메일 계정을 자동 통합할 때, 소셜 제공자가 보낸 이메일 검증 플래그를 반드시 확인한다:
  - **Google**: `email_verified == true`
  - **Naver**: `resultcode == "00"` 및 `response.email` 존재 여부 확인
- 이메일이 누락되었거나 미인증인 경우 어떠한 경우에도 기존 `Member`와 연결하지 않으며 신규 가입도 허용하지 않는다.

## 4. 세션 고정 방어 및 실시간 채팅 세션 연동

- 소셜 인증 완료 후 세션을 수립할 때 기존 일반 로그인([`AuthController`](file:///c:/Users/ikaes/IdeaProjects/snowthing/backend/src/main/java/com/ikae/snowthing/domain/auth/controller/AuthController.java))과 동일하게 `httpRequest.changeSessionId()`를 호출하여 인가 요청 단계의 임시 세션 ID를 교체한다.
- [`MemberSessionRegistry`](file:///c:/Users/ikaes/IdeaProjects/snowthing/backend/src/main/java/com/ikae/snowthing/global/security/MemberSessionRegistry.java)에 세션을 등록하여 로그아웃 및 비밀번호 재설정 시 활성 세션 및 라이브톡 WebSocket 연결이 일관되게 종료되도록 보장한다.

## 5. 외부 HTTP 호출과 DB 트랜잭션 분리

- 소셜 공급자의 Token Endpoint 및 UserInfo Endpoint 호출은 `@Transactional` 외부에서 수행하며, 연결 타임아웃(기본 2초)과 읽기 타임아웃(기본 4초)을 강제하여 외부 장애가 DB 커넥션 풀(HikariCP)로 전파되는 것을 차단한다.

## 6. OAuth 인가 요청 세션 고갈 방어

- 신뢰할 수 있는 프록시 체인을 검증하는 `ClientIpResolver`로 클라이언트 IP를 식별하고, IP당 1분 20회의 인가 시작 요청만 허용한다.
- 요청 제한 검사는 `HttpServletRequest#getSession(true)`보다 먼저 수행하여 제한을 초과한 요청이 서버 메모리에 신규 세션을 만들지 못하게 한다.
- 현재 Caffeine 기반 제한은 단일 애플리케이션 인스턴스 범위다. 다중 인스턴스로 확장할 때는 API Gateway 또는 Redis 기반 분산 제한으로 교체한다.

## 7. 소셜 식별자 및 동시성 무결성

- `provider_user_id`는 `utf8mb4_bin` collation으로 저장하여 대소문자가 다른 불투명 식별자를 서로 다른 계정으로 취급한다.
- 회원·소셜 계정 저장 시 유니크 제약 이름을 확인해 알려진 이메일, 닉네임, 소셜 연결 충돌만 도메인 오류로 변환한다. 알 수 없는 무결성 오류는 숨기거나 다른 충돌로 오분류하지 않는다.
- `saveAndFlush` 실패 후 rollback-only 트랜잭션 안에서 재시도하지 않는다. 동시 닉네임 충돌은 명시적 오류로 종료하고 사용자가 새 인가 흐름을 시작하도록 한다.
