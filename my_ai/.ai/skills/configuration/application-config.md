# Application Configuration Skill

## 환경 분리

- local/dev/test/staging/prod 환경의 설정 차이를 명확히 분리한다.
- 운영 환경 값을 코드에 하드코딩하지 않는다.
- 환경별 URL, DB 접속정보, CORS origin, 외부 API endpoint, feature flag 등을 설정으로 관리한다.
- 테스트 설정이 운영 설정에 섞이지 않도록 한다.

## 환경 변수

- Secret은 Git에 커밋하지 않는다.
- `.env`, `application-secret.yml`, private key 파일은 `.gitignore` 여부를 확인한다.
- sample 설정 파일에는 실제 비밀값 대신 placeholder를 둔다.
- 환경 변수 이름을 코드 여러 곳에서 문자열로 중복 사용하지 않는다.

## Spring 설정

- `@ConfigurationProperties` 등 타입 안전한 설정 바인딩을 우선 검토한다.
- 설정값 validation을 적용하여 누락된 필수 설정을 앱 시작 시 조기에 발견한다.
- 운영 환경에서 debug/trace 로그가 기본 활성화되지 않게 한다.
- Actuator endpoint 공개 범위를 검토한다.

## 파일 업로드 제한

- 서비스 코드의 파일 크기 상수만으로 업로드 제한이 적용된다고 판단하지 않는다.
- `spring.servlet.multipart.max-file-size`는 서비스의 단일 파일 정책과 맞춘다.
- `max-request-size`는 multipart boundary·part header 등 요청 오버헤드를 고려해 파일 제한보다 크게 설정한다.
- CDN/WAF/Nginx·Spring multipart parser·서비스 검증 중 가장 작은 제한이 실제 제한이므로 전 구간을 대조한다.
- proxy가 먼저 차단하면 애플리케이션 표준 오류가 나오지 않을 수 있으므로 413과 애플리케이션 오류 응답을 실제 환경에서 확인한다.
- parser 단계의 `MaxUploadSizeExceededException`도 프로젝트의 정적 `ErrorCode` 응답으로 변환한다.

## Docker/Container

- Secret을 Docker image layer에 bake하지 않는다.
- 포트 노출과 내부 네트워크 노출을 구분한다.
- root 사용자 실행 여부를 검토한다.
- healthcheck와 graceful shutdown을 고려한다.
