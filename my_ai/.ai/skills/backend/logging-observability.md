# Logging & Observability Skill

## 로깅 원칙

- 운영에서 원인을 추적할 수 있는 로그를 남긴다.
- 비밀번호, Access Token, Refresh Token, Session ID, 주민번호, 카드번호 등 민감정보를 로그에 기록하지 않는다.
- 단순 `printStackTrace`, `System.out.println`을 운영 로깅으로 사용하지 않는다.
- 로그 레벨(DEBUG/INFO/WARN/ERROR)을 의미에 맞게 사용한다.
- 동일 예외를 여러 계층에서 중복 로깅하여 로그를 폭증시키지 않는다.

## 구조화

가능하면 다음 식별자를 함께 남긴다.

- request/trace id
- 사용자 식별자의 안전한 내부 ID
- 주요 도메인 ID
- 처리 결과
- 오류 코드

## 관측성

필요에 따라 다음을 검토한다.

- Metrics
- Tracing
- Health Check
- Slow Query
- Error Rate
- Latency percentile

개발 환경에서만 필요한 상세 로그와 운영 로그를 분리한다.
