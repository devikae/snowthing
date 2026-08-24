# Frontend Configuration Skill

## 환경 구분

- API Base URL, feature flag, analytics id 등 환경별 값을 명시적으로 분리한다.
- build-time env와 runtime env의 차이를 이해하고 사용한다.
- 개발 proxy 설정과 운영 API endpoint를 혼동하지 않는다.

## Secret 금지

브라우저로 전달되는 값은 사용자가 볼 수 있다고 가정한다.
OAuth Client Secret, 서버 API Secret, DB Credential 등을 프론트 코드 또는 공개 환경변수에 넣지 않는다.

## Build

- production build에서 source map 공개 여부를 검토한다.
- debug flag와 mock API가 운영 build에 포함되지 않도록 한다.
- CSP 등 보안 헤더는 배포 환경 설정과 함께 검토한다.
