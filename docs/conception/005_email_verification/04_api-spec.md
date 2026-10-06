# 이메일 인증 API 명세

모든 공개 변경 API는 기존 CSRF 쿠키 정책을 그대로 적용합니다. 인증번호와 토큰은 응답 로그에 기록하지 않습니다.

## 1. 이메일 사용 가능 여부

```http
POST /api/v1/members/email-availability
```

```json
{ "email": "user@example.com" }
```

```json
{ "available": true }
```

이 결과는 안내용입니다. 최종 가입 시 다시 검사하고 DB UNIQUE 제약조건으로 경쟁 요청을 차단합니다.
동일 IP의 요청은 최근 60초 기준 10회까지 허용하며, 초과하면 `429 EMAIL_010`을 반환합니다.

## 2. 가입 인증번호 발송

```http
POST /api/v1/auth/email-verifications/sign-up
```

```json
{ "email": "user@example.com" }
```

```json
{
  "requestId": "uuid",
  "expiresInSeconds": 300,
  "resendAvailableInSeconds": 60
}
```

중복 이메일은 `409`, 발송 제한은 `429`, SES 장애는 `503`을 반환합니다.

## 3. 가입 인증번호 확인

```http
POST /api/v1/auth/email-verifications/sign-up/confirm
```

```json
{ "requestId": "uuid", "code": "123456" }
```

```json
{ "verificationToken": "opaque-token", "expiresInSeconds": 900 }
```

## 4. 회원가입

기존 `POST /api/v1/members`에 `emailVerificationToken`을 추가합니다.

```json
{
  "email": "user@example.com",
  "emailVerificationToken": "opaque-token",
  "password": "Password123!",
  "nickname": "라이더",
  "bio": null,
  "departureRegion": null,
  "resortIds": [],
  "ridingStyleIds": []
}
```

회원 저장과 가입 토큰 소비를 한 트랜잭션으로 처리합니다.

## 5. 비밀번호 재설정 요청

```http
POST /api/v1/auth/password-reset/requests
```

```json
{ "email": "user@example.com" }
```

계정 존재 여부와 관계없이 다음 응답을 반환합니다.

```http
202 Accepted
```

```json
{ "message": "입력한 이메일로 안내를 전송했습니다." }
```

클라이언트가 다음 화면을 진행할 수 있도록 응답에는 임의의 `requestId`를 포함하되, 존재하지 않는 계정에는 유효한 인증번호가 생성되지 않습니다.

## 6. 재설정 인증번호 확인

```http
POST /api/v1/auth/password-reset/confirm
```

```json
{ "requestId": "uuid", "code": "123456" }
```

```json
{ "resetToken": "opaque-token", "expiresInSeconds": 900 }
```

## 7. 새 비밀번호 설정

```http
PUT /api/v1/auth/password-reset
```

```json
{ "resetToken": "opaque-token", "newPassword": "NewPassword123!" }
```

성공 시 `204 No Content`를 반환합니다. 비밀번호 변경 후 모든 기존 세션을 만료시키며 사용자는 다시 로그인해야 합니다.

## 8. 오류 코드

| 코드 | HTTP | 의미 |
|---|---:|---|
| `EMAIL_001` | 409 | 이미 가입된 이메일 |
| `EMAIL_002` | 400 | 인증번호 형식 오류 |
| `EMAIL_003` | 400 | 인증번호 불일치 |
| `EMAIL_004` | 410 | 인증번호 만료 |
| `EMAIL_005` | 429 | 인증 실패 횟수 초과 |
| `EMAIL_006` | 429 | 재발송 대기 또는 발송 한도 초과 |
| `EMAIL_007` | 503 | 메일 발송 서비스 장애 |
| `EMAIL_008` | 400 | 인증 토큰이 유효하지 않거나 용도가 다름 |
| `EMAIL_009` | 410 | 인증 토큰 만료 또는 이미 사용됨 |
| `EMAIL_010` | 429 | 이메일 중복 확인 IP 호출 한도 초과 |

비밀번호 재설정 요청 단계에서는 `EMAIL_001`이나 회원 없음 오류를 반환하지 않습니다.
