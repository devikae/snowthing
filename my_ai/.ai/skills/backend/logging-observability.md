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

## 배포·원격 실행 로그

- SSM, CI/CD, 원격 명령의 stdout/stderr가 누구에게 노출되는지 확인한다.
- 컨테이너 로그, Nginx 오류 로그, SQL 오류 원문을 `tee` 등으로 CI 로그에 그대로 복사하지 않는다.
- 장애 원문은 서버의 제한된 디렉터리에 저장하고 디렉터리·파일 권한, 암호화, 접근 주체, 보관 기간, 삭제 방법을 정한다.
- CI에는 서비스명, 배포 SHA/digest, 상태, 종료 코드, 재시작 횟수, 서버 진단 파일 경로처럼 비민감 요약만 출력한다.
- 로그 보관 기간 정리는 장애가 다시 발생할 때만 실행하지 말고 정상 배포나 정기 작업에서도 실행되게 한다.
