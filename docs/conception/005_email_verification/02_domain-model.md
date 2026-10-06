# 이메일 인증 도메인 모델

## 1. EmailVerification

이메일과 용도별 현재 인증 상태를 보관하는 Aggregate입니다. `(email, purpose)` 조합은 하나의 현재 상태만 가집니다.

| 속성 | 설명 |
|---|---|
| `publicId` | 브라우저에 전달하는 인증 요청 UUID |
| `email` | 공백 제거와 소문자 변환을 마친 이메일 |
| `purpose` | `SIGN_UP`, `PASSWORD_RESET` |
| `codeDigest` | 서버 비밀값으로 만든 인증번호 HMAC |
| `tokenDigest` | 인증 성공 후 발급한 일회용 토큰 HMAC |
| `status` | 인증 생명주기 상태 |
| `failedAttemptCount` | 인증번호 실패 횟수 |
| `sendCount` | 현재 시간 구간의 이메일별 발송 횟수 |
| `sendWindowStartedAt` | 발송 횟수 구간 시작 시각 |
| `requestIp` | 마지막 발송 요청 IP |
| `sentAt` | SES 접수 성공 시각 |
| `expiresAt` | 인증번호 만료 시각 |
| `verifiedAt` | 인증번호 확인 시각 |
| `tokenExpiresAt` | 가입·재설정 토큰 만료 시각 |
| `consumedAt` | 토큰 사용 시각 |
| `sesMessageId` | SES가 반환한 메시지 ID |

## 2. 상태 전이

```text
PENDING ──SES 성공──> SENT ──번호 확인──> VERIFIED ──가입/재설정──> CONSUMED
   │                    │
   └──SES 실패──> SEND_FAILED
                        ├──만료──> EXPIRED
                        └──5회 실패──> FAILED
```

- 재발송은 같은 행의 `publicId`, 번호 HMAC, 만료 시각을 모두 교체합니다.
- 이전 `publicId`와 인증번호는 즉시 무효가 됩니다.
- `VERIFIED` 상태에서만 일회용 토큰을 사용할 수 있습니다.
- `CONSUMED` 상태는 다시 사용할 수 없습니다.

## 3. Member 변경

`Member`에 `emailVerifiedAt`을 추가합니다.

- 신규 회원: 가입 트랜잭션에서 현재 시각 저장
- 기존 회원: `NULL` 유지, 로그인 허용
- 기존 회원이 비밀번호 재설정을 완료하면 현재 시각 저장
- 이메일 주소 변경 기능은 제공하지 않음

## 4. 계층 책임

### Controller

- HTTP 요청 검증
- 클라이언트 IP 전달
- 비밀번호 재설정 완료 후 보안 계층에 세션 만료 요청

### Service

- 이메일 정규화와 중복 확인
- 인증번호 생성·검증과 상태 전이
- 회원 생성·비밀번호 변경 트랜잭션
- 세션·서블릿 API를 직접 호출하지 않음

### Mail Adapter

- 도메인 서비스가 정의한 메일 발송 포트를 구현
- 운영에서는 SES v2, 테스트에서는 Fake 구현 사용
- SES SDK 모델을 서비스·컨트롤러 DTO로 노출하지 않음

### Security Component

- 로그인 세션 등록
- 회원별 기존 세션 만료
- 연결된 라이브톡 세션 종료
