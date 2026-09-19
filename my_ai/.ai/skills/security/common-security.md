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

## 파일·Object Storage

- 파일명, 객체 키, prefix를 클라이언트가 전달한 그대로 storage API에 사용하지 않는다.
- `..` 문자열 차단 하나로 path/object 경계를 보장하지 않는다. canonicalization, 허용 prefix, 서버 생성 UUID, 소유권 검사를 함께 적용한다.
- 애플리케이션 IAM Role은 bucket 전체가 아니라 필요한 bucket·prefix·action으로 제한한다.
- upload 역할과 CDN read 역할을 분리하고, public access 차단 여부를 확인한다.
- 공개 객체와 회원 전용 객체를 같은 prefix와 같은 CDN cache behavior로 운영하지 않는다.

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
