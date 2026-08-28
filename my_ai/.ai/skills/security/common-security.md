# Common Security Skill

## 기본 원칙

보안은 기능 구현 후 마지막에 붙이는 옵션이 아니라 설계 단계에서 함께 검토한다.

## 필수 점검

- Authentication
- Authorization
- Input Validation
- Output Encoding
- Secret Management
- Session/Token Security
- CSRF
- XSS
- SQL Injection
- SSRF
- File Upload
- Rate Limiting
- Brute Force
- Sensitive Data Exposure
- Dependency Vulnerability
- Security Headers

## 입력 신뢰 경계

다음 값은 모두 신뢰하지 않는다.

- 브라우저 입력
- URL path/query
- HTTP Header
- Cookie
- JWT payload의 검증 전 값
- 외부 API 응답
- 파일명과 MIME
- 메시지 큐 payload

## Secret

- API Key, DB Password, JWT Secret, OAuth Secret, private key를 소스코드에 넣지 않는다.
- Secret이 로그/에러 메시지/클라이언트 bundle에 포함되지 않는지 확인한다.
- Secret Rotation 가능성을 고려한다.

## 의존성

- 새 라이브러리 도입 시 유지보수 상태와 알려진 취약점을 검토한다.
- 사용하지 않는 의존성은 제거한다.
- 무작정 최신 버전 업그레이드도 금지하며 breaking change를 확인한다.

## 오류 응답

- Stack Trace, DB SQL, 내부 경로, class name 등 내부 구현 정보가 외부 응답으로 노출되지 않도록 한다.
