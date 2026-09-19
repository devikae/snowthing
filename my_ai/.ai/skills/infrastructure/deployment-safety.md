# Deployment Safety Skill

## Git commit과 배포 산출물

- Docker image는 workflow가 시작된 정확한 commit SHA에서 만든다.
- SSM·SSH 등 원격 배포에도 브랜치명이 아니라 같은 commit SHA를 전달한다.
- 서버는 해당 SHA를 명시적으로 fetch/reset하고 `rev-parse HEAD` 일치를 확인한 뒤 배포한다.
- image digest는 실행 image를 고정하고 commit SHA는 Compose·배포 script를 고정한다. 둘 중 하나만 기록하지 않는다.
- rollback 태그에서 원본 commit SHA를 복원해 과거 image와 과거 배포 설정을 함께 맞춘다.

## ECR 태그

- `candidate`, `release`, `stable`의 의미와 수명 주기를 구분한다.
- stable 승격 시 사용자가 선택한 정확한 release 태그만 제거한다.
- 같은 digest에 여러 commit의 태그가 존재할 수 있으므로 digest 기준 태그 일괄 삭제를 금지한다.
- 전체 개수·기간 정리는 명시적인 ECR lifecycle policy가 담당하게 한다.
- 태그는 이동·삭제될 수 있으므로 실제 실행 주소와 증거에는 digest를 기록한다.

## 원격 로그와 비밀정보

- SSM command 결과가 GitHub Actions 로그로 출력되는 경로를 점검한다.
- 컨테이너·프록시 로그 원문은 원격 서버의 제한 경로에만 보관한다.
- 진단 디렉터리는 운영자만 접근하게 하고 파일 권한과 보관 기간을 코드 또는 운영 정책으로 관리한다.
- Actions에는 민감정보가 없는 상태 요약과 진단 파일 위치만 출력한다.

## Reverse Proxy와 애플리케이션 설정

- Nginx·ALB·CDN 제한과 애플리케이션 제한을 함께 확인한다.
- proxy 설정 변경은 syntax test가 성공한 경우에만 reload한다.
- 설정 적용 전후 동일 요청으로 외부 HTTPS 경로를 검증한다.
- 애플리케이션 health check뿐 아니라 핵심 공개 API와 권한 거부도 확인한다.

## 검증

- workflow YAML parse와 shell syntax를 검사한다.
- 실제 배포에서 CI, image push, SSM 완료, local health, 외부 HTTPS 순서로 확인한다.
- 데이터나 태그를 변경하는 수동 workflow는 검증 목적만으로 무작정 실행하지 않는다.
- 실행하지 않은 stable 이동·rollback·migration은 정적 검증과 실제 검증을 구분해 보고한다.
