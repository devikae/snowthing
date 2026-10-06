# 이메일 인증 ERD와 마이그레이션

## 1. 관계

```mermaid
erDiagram
    MEMBER {
        BIGINT member_id PK
        VARCHAR email UK
        DATETIME email_verified_at NULL
        VARCHAR password
    }

    EMAIL_VERIFICATION {
        BIGINT verification_id PK
        VARCHAR public_id UK
        VARCHAR email
        VARCHAR purpose
        CHAR code_digest
        CHAR token_digest NULL
        VARCHAR status
        SMALLINT failed_attempt_count
        SMALLINT send_count
        DATETIME send_window_started_at
        VARCHAR request_ip
        DATETIME sent_at NULL
        DATETIME expires_at
        DATETIME verified_at NULL
        DATETIME token_expires_at NULL
        DATETIME consumed_at NULL
        VARCHAR ses_message_id NULL
        DATETIME created_at
        DATETIME updated_at
    }
```

`email_verification`은 가입 전 이메일도 저장하므로 `member` FK를 갖지 않습니다. 비밀번호 재설정 시에도 이메일로 회원을 조회하며, 응답에서는 회원 존재 여부를 숨깁니다.

## 2. 제약조건과 인덱스

- `UNIQUE(public_id)`
- `UNIQUE(email, purpose)`
- `INDEX(status, expires_at)`
- `CHECK purpose IN ('SIGN_UP', 'PASSWORD_RESET')`
- `CHECK status IN ('PENDING', 'SENT', 'VERIFIED', 'CONSUMED', 'SEND_FAILED', 'FAILED', 'EXPIRED')`
- `CHECK failed_attempt_count BETWEEN 0 AND 5`
- `CHECK send_count >= 0`

## 3. 마이그레이션

카풀 기능이 `007~012`를 사용하므로 새 파일 `013_migration_email_verification.sql`에 다음을 추가합니다.

1. `member.email_verified_at` nullable 컬럼
2. `email_verification` 테이블과 제약조건
3. 조회·정리용 인덱스

기존 회원을 실제 검증 없이 인증된 것으로 표시하지 않으므로 `email_verified_at`은 백필하지 않습니다. 기존 운영 마이그레이션은 체크섬이 기록되므로 수정하지 않습니다.

## 4. 정리 정책

매일 새벽 인증번호 만료 시각이 24시간 이상 지난 행을 상태와 관계없이 삭제합니다. 이 시점에는 15분짜리 후속 토큰도 이미 만료되므로 진행 중인 인증을 지우지 않습니다. 정상 가입과 비밀번호 변경 이력은 `member.email_verified_at`과 보안 감사 로그로 확인하며 인증번호 관련 개인정보는 오래 보관하지 않습니다.
