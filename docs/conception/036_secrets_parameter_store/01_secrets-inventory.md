# 운영 비밀값 목록

- 상태: 작성 (2026-11-22)
- 기준일: 2026-11-22 (KST), `origin/main` 62aa424b 기준
- Linear: IKA-110
- 관련 문서: `02_parameter-store.md`(Parameter Store 선택 경로와 이전 절차)

이 문서에는 값을 적지 않는다. 저장소가 공개라서 내부 호스트 이름, 계정 ID, 실제 키도 적지 않는다.

## 1. 값이 있는 곳

| 위치 | 무엇이 있나 | 누가 읽나 |
|---|---|---|
| 서버 `/etc/snowthing/prod.env` | 운영 환경변수(비밀과 설정이 섞여 있음) | 배포 스크립트가 `docker compose --env-file`로 |
| 서버 `/etc/snowthing/images.env` | 서비스별 실행 이미지 digest 주소(IKA-105) | 배포 스크립트가 쓰고 읽음 |
| 서버 `/etc/snowthing/migration.env` | 마이그레이션 DB 접속 정보 | 백엔드 배포 명령이 root 셸로 `source` |
| 서버 `/etc/snowthing/parameter-store.env` | Parameter Store 경로(선택, IKA-110). 비밀 아님 | 배포 스크립트, `scripts/compose-prod.sh` |
| 서버 `/opt/snowthing-ai/` | AI 회원 페르소나, 말투 예시 | 백엔드와 mcp-bot 컨테이너(읽기 전용 마운트) |
| GitHub Actions 변수(`vars`) | 배포 역할 ARN, 리전, 인스턴스 ID, ECR 저장소, 프론트 빌드 값 | 배포 워크플로 |
| 실행 중인 컨테이너 | 만들어질 때 받은 환경변수 전체 | docker가 컨테이너 설정으로 보관(root 전용) |

워크플로는 GitHub `secrets`를 쓰지 않는다(자동 발급되는 `GITHUB_TOKEN`만 라벨 워크플로가 쓴다). AWS 권한은 OIDC 임시 자격 증명이다.

## 2. 구분 기준

- 비밀: 값을 아는 것만으로 무언가를 할 수 있다. 유출되면 교체한다.
- 설정값: 동작을 정하는 값. 유출 자체는 피해가 없거나 작다. 다만 공개 저장소에 적으면 안 되는 것(내부 주소, 계정 ID, 이메일)은 따로 표시한다.
- 공개값: 브라우저나 로그인 주소에 원래 드러나는 값.

## 3. 백엔드 환경변수 (`compose.prod.yml`의 backend)

### 3.1 비밀

| 이름 | 용도 | 유출되면 | 교체 방법과 사용자 영향 | 권한을 줄이는 방법 |
|---|---|---|---|---|
| `SNOWTHING_EMAIL_VERIFICATION_SECRET` (필수, 32자 이상) | 이메일 인증 코드와 토큰, 비밀번호 재설정 토큰, 탈퇴 이메일의 HMAC. 멱등 키 HMAC과 로그인 유지 키의 기본값으로도 쓰인다 | DB 내용까지 함께 유출되면 저장된 digest에서 6자리 인증 코드와 탈퇴 이메일을 오프라인으로 맞혀 볼 수 있다. 값만으로는 로그인할 수 없다 | 새 임의 문자열(32자 이상)로 바꾸고 백엔드를 다시 만든다. 영향: 진행 중인 가입 인증과 비밀번호 재설정 링크가 무효(다시 요청), 보관 중(최대 24시간)인 멱등 기록과 해시가 달라져 재전송이 새 요청으로 처리, 탈퇴 이메일 해시가 기존 기록과 맞지 않아 재가입 제한이 풀린다. 마지막 항목은 되돌릴 수 없으므로 유출이 확실할 때만 바꾼다 | 용도별 비밀을 따로 두면 한 번에 깨지는 범위가 준다. `SNOWTHING_REMEMBER_ME_KEY`는 이미 분리할 수 있다 |
| `SNOWTHING_REMEMBER_ME_KEY` (선택) | 로그인 유지로 만든 인증 객체가 이 서버에서 나왔는지 확인 | 단독으로는 쓸 곳이 거의 없다. 쿠키는 DB의 토큰으로 검증한다 | 새 임의 문자열로 바꾸고 백엔드를 다시 만든다. 코드 주석 기준으로 쿠키 서명에는 쓰지 않아 로그인 유지 쿠키는 그대로다(실제 교체로 확인하지는 않았다) | 비워 두면 이메일 인증 비밀값에서 만든다. 따로 두면 그 비밀을 바꿀 때 영향이 준다 |
| `SNOWTHING_GOOGLE_CLIENT_SECRET` | 구글 로그인 인가 코드 교환 | 등록된 리다이렉트 주소로만 코드가 가므로 단독 악용은 어렵다. 인가 코드 탈취와 겹치면 계정 가로채기 | 구글 클라우드 콘솔에서 새 보안 비밀 발급, 값 교체, 백엔드 재생성, 옛 비밀 삭제. 새 비밀을 먼저 추가할 수 있어 끊김 없이 바꿀 수 있다 | 리다이렉트 주소를 운영 주소 하나로 유지 |
| `SNOWTHING_NAVER_CLIENT_SECRET` | 네이버 로그인 | 위와 같다 | 네이버 개발자 센터에서 재발급, 값 교체, 백엔드 재생성. 재발급부터 재생성까지 네이버 로그인이 실패한다 | 콜백 주소 제한 |
| `SNOWTHING_KAKAO_LOGIN_CLIENT_SECRET` | 카카오 로그인 | 위와 같다 | 카카오 개발자 콘솔에서 재발급, 값 교체, 백엔드 재생성. 그 사이 카카오 로그인 실패 | 리다이렉트 주소 제한 |
| `SNOWTHING_KAKAO_REST_API_KEY` (필수) | 카풀 길찾기(카카오 모빌리티) 호출. 카카오 로그인의 클라이언트 ID 기본값이기도 하다 | 우리 앱 이름으로 API를 호출해 할당량을 쓴다. 로그인 주소에 client_id로 드러나는 값이라 완전한 비밀은 아니다 | 카카오 콘솔에서 재발급, 값 교체, 백엔드 재생성. 그 사이 카풀 경로 계산과 카카오 로그인 실패 | 콘솔의 허용 IP에 서버 주소만 등록, 클라이언트 시크릿 사용 켜기 |
| `SNOWTHING_OPINET_API_KEY` (필수) | 유가 조회 | 할당량 도용 | 오피넷에서 재발급, 값 교체, 백엔드 재생성 | 발급처가 주는 범위 안에서만 |
| `SNOWTHING_KMA_SERVICE_KEY` | 기상청 날씨 | 할당량 도용 | 공공데이터포털에서 재발급, 값 교체, 백엔드 재생성. 비어 있으면 날씨 기능이 꺼진다 | 활용 신청한 API만 |
| `SNOWTHING_BOT_INTERNAL_SECRET` (봇을 켜면 32바이트 이상) | 백엔드와 mcp-bot이 서로를 확인하는 공유 비밀 | 컨테이너 네트워크 안에서 봇 내부 API를 부를 수 있다. 포트를 밖으로 열지 않아 서버 안에 들어와야 쓸 수 있다 | 새 임의 문자열로 바꾸고 backend와 mcp-bot을 함께 다시 만든다(`scripts/compose-prod.sh up -d backend mcp-bot`). 한쪽만 바뀐 동안 봇 호출 실패 | 포트를 밖으로 열지 않는 현재 구성 유지 |
| `SNOWTHING_BOT_DELEGATION_SECRET` (봇을 켜면 32바이트 이상) | 봇이 회원 대신 행동할 때 쓰는 위임 토큰 서명(수명 60초) | 회원 대신 행동하는 토큰을 만들 수 있다 | 새 임의 문자열로 바꾸고 백엔드를 다시 만든다. 발급된 토큰(60초)만 무효 | 토큰 수명을 짧게 유지 |
| `SNOWTHING_OPENAI_API_KEY` (IKA-80, 선택) | AI 자동 검열 2단계(OpenAI Moderation API) 호출. `SNOWTHING_MODERATION_API_ENABLED`가 true일 때만 쓴다 | Moderation API 자체는 무료지만 키 권한이 넓으면 우리 계정으로 다른 모델을 불러 요금이 나간다 | OpenAI 대시보드에서 새 키 발급, 값 교체, `scripts/compose-prod.sh up -d backend`, 옛 키 폐기. 그 사이 2단계 판정만 실패하고 1단계 규칙은 계속 돈다 | 백엔드 전용 키를 mcp-bot의 `OPENAI_API_KEY`와 따로 두고, 허용 엔드포인트를 moderation으로 제한, 월 지출 한도 |
| `DISCORD_WEBHOOK_ALERT`, `DISCORD_WEBHOOK_EVENT`, `DISCORD_WEBHOOK_COLUMN`(IKA-112) | 운영 알림 채널 웹훅 주소. 주소 자체가 권한이다 | 채널에 아무 메시지나 올릴 수 있다(가짜 경보, 피싱 링크) | Discord에서 웹훅을 지우고 새로 만든 뒤 값 교체, 백엔드 재생성. 사용자 영향 없음 | 알림 전용 채널에만 연결 |
| `WEB_PUSH_VAPID_PRIVATE_KEY` | 웹 푸시 서명 | 구독 주소(DB)까지 알면 우리 이름으로 푸시를 보낼 수 있다 | 키 쌍을 새로 만들고 공개키, 비밀키를 함께 교체. 기존 구독이 모두 무효가 되어 사용자가 알림을 다시 켜야 한다. 영향이 커서 유출이 확실할 때만 | 없음(키 쌍 단위) |

### 3.2 설정값 중 공개 저장소에 적으면 안 되는 것

| 이름 | 용도 | 주의 |
|---|---|---|
| `SNOWTHING_PROD_DB_URL` (필수) | 운영 DB 주소. `jdbc:aws-wrapper:mysql://`로 시작해야 한다 | 내부 호스트 이름이 들어 있다. DB 인증은 IAM 토큰이라 주소만으로는 접속할 수 없다 |
| `SNOWTHING_ADMIN_BOOTSTRAP_EMAILS` | 기동할 때 관리자로 올릴 이메일 | 이 값을 바꿀 수 있으면 관리자를 만들 수 있다. prod.env 쓰기 권한이 곧 관리자 권한이다. 지정이 끝나면 비워 둔다 |
| `SNOWTHING_CLOUDFRONT_DISTRIBUTION_ID`, `SNOWTHING_SES_CONFIGURATION_SET` | AWS 리소스 식별자 | 비밀은 아니지만 문서에 적지 않는다 |

### 3.3 설정값

`SNOWTHING_DB_USERNAME`, `SNOWTHING_S3_BUCKET`, `SNOWTHING_IMAGE_BASE_URL`, `SNOWTHING_FRONTEND_BASE_URL`, `SNOWTHING_GOOGLE_REDIRECT_URI`, `SNOWTHING_NAVER_REDIRECT_URI`, `SNOWTHING_KAKAO_LOGIN_REDIRECT_URI`, `SNOWTHING_MAIL_PROVIDER`, `SNOWTHING_SES_REGION`, `SNOWTHING_MAIL_FROM`, `SNOWTHING_MAIL_FROM_NAME`, `SNOWTHING_DM_MAIL_ENQUEUE_ENABLED`, `SNOWTHING_DM_MAIL_SEND_ENABLED`, `SNOWTHING_CHAT_MAX_CONNECTIONS`, `SNOWTHING_BOT_ENABLED`, `SNOWTHING_BOT_BASE_URL`, `SNOWTHING_BOT_DELEGATION_TTL`, `SNOWTHING_BOT_REQUEST_TIMEOUT`, `SNOWTHING_AI_MEMBERS_ENABLED`, `SNOWTHING_AI_MEMBERS_DAILY_CALL_LIMIT`, `SNOWTHING_AI_MEMBERS_TOPIC_KEYWORDS`, `SNOWTHING_MODERATION_ENABLED`, `SNOWTHING_MODERATION_API_ENABLED`(IKA-80, 기본 false), `WEB_PUSH_VAPID_SUBJECT`, `PINPOINT_AGENT_ID`, `PINPOINT_APPLICATION_NAME`.

`SNOWTHING_DB_PASSWORD`는 운영에서 비워 둔다. 운영 프로필은 IAM 인증 플러그인을 쓴다. S3, SES, CloudFront, RDS 접근은 모두 인스턴스 역할로 하고 액세스 키는 어디에도 두지 않는다.

### 3.4 공개값

`SNOWTHING_GOOGLE_CLIENT_ID`, `SNOWTHING_NAVER_CLIENT_ID`, `SNOWTHING_KAKAO_LOGIN_CLIENT_ID`(로그인 주소에 드러난다), `WEB_PUSH_VAPID_PUBLIC_KEY`(브라우저에 내려준다).

## 4. mcp-bot 환경변수

| 이름 | 구분 | 용도 | 유출되면 | 교체 방법과 사용자 영향 | 권한을 줄이는 방법 |
|---|---|---|---|---|---|
| `OPENAI_API_KEY` | 비밀 | AI 회원 글, 댓글, 라이브톡 답변 생성 | 우리 계정으로 호출해 요금이 나간다. 백엔드의 `SNOWTHING_OPENAI_API_KEY`(3.1)와 함께 돈이 바로 나가는 키다 | OpenAI 대시보드에서 새 키 발급, 값 교체, `scripts/compose-prod.sh up -d mcp-bot`, 옛 키 폐기. 그 사이 AI 회원 활동만 멈춘다(관리자 화면 스위치로 먼저 꺼도 된다) | 프로젝트 전용 키, 월 지출 한도, 쓰는 모델과 엔드포인트만 허용 |
| `SNOWTHING_BOT_INTERNAL_SECRET` | 비밀 | 3.1과 같은 값 | 3.1 참고 | 3.1 참고 | 3.1 참고 |
| `OPENAI_MODEL`, `OPENAI_REASONING_EFFORT`, `OPENAI_COMPOSE_REASONING_EFFORT`, `SNOWTHING_BOT_TIMEOUT_MS` | 설정값 | 모델과 시간 제한 | 없음 | 값 교체 뒤 mcp-bot 재생성 | 해당 없음 |

## 5. 서버의 다른 파일

| 대상 | 구분 | 내용 | 주의 |
|---|---|---|---|
| `images.env`의 `BACKEND_IMAGE_URI`, `FRONTEND_IMAGE_URI`, `MCP_BOT_IMAGE_URI` | 설정값 | 실행 이미지 digest 주소 | 주소에 계정 ID가 들어 있어 문서에 적지 않는다. Parameter Store에 같은 이름을 넣어도 배포 스크립트가 무시한다 |
| `migration.env`의 `SNOWTHING_MIGRATION_DB_HOST`, `_PORT`, `_NAME`, `_USERNAME`, `_AUTH_MODE`, `_SSL_MODE`, `_SSL_CA` | 설정값 | 마이그레이션 실행기 접속 정보. 인증은 기본이 IAM | 배포 명령이 root 셸에서 `source`한다. 이 파일을 쓸 수 있으면 root로 명령을 실행할 수 있으므로 root 소유, 600이어야 한다 |
| `migration.env`의 `SNOWTHING_MIGRATION_DB_PASSWORD` | 비밀 | 암호 인증 모드일 때만 쓴다 | IAM 모드에서는 두지 않는다. 있다면 지운다 |
| `/opt/snowthing-ai/personas.yml`, `/opt/snowthing-ai/style/` | 비밀에 준함 | AI 회원 페르소나와 말투 예시 | 유출되면 어떤 계정이 AI 회원인지 드러난다. 저장소에 넣지 않는다 |
| Pinpoint 에이전트 설정(`/opt/pinpoint-agent-3.1.0`) | 설정값 | 수집 서버 주소 | 내부 주소라 문서에 적지 않는다 |

## 6. GitHub Actions 변수

| 이름 | 구분 | 용도 | 주의 |
|---|---|---|---|
| `AWS_DEPLOY_ROLE_ARN` | 설정값 | OIDC로 맡는 배포 역할 | 계정 ID가 들어 있다. 이 값을 안다고 역할을 맡을 수는 없다. 지키는 것은 역할 신뢰 정책의 저장소, 브랜치 조건이다 |
| `AWS_REGION`, `EC2_INSTANCE_ID`, `ECR_BACKEND_REPOSITORY`, `ECR_FRONTEND_REPOSITORY` | 설정값 | 배포 대상 | 문서에 적지 않는다 |
| `NEXT_PUBLIC_API_BASE_URL`, `NEXT_PUBLIC_KAKAO_MAP_APP_KEY`, `GOOGLE_SITE_VERIFICATION`, `NAVER_SITE_VERIFICATION` | 공개값 | 프론트 빌드에 들어가 브라우저로 나간다 | 카카오 지도 키는 카카오 콘솔의 사이트 도메인 제한이 지킨다 |

확인할 것: 공개 저장소의 Actions 로그는 누구나 볼 수 있다. 워크플로가 ECR 레지스트리 주소(계정 ID 포함)로 `docker push`를 하므로 로그에 계정 ID가 보일 수 있다. 계정 ID는 AWS 기준으로 비밀이 아니지만, 가리고 싶다면 `::add-mask::`로 가릴 수 있다. 이번 작업에서는 바꾸지 않았다.

## 7. 코드 기본값과 빠졌을 때의 동작

| 변수 | 빠지면 | 판단 |
|---|---|---|
| `SNOWTHING_PROD_DB_URL`, `SNOWTHING_KAKAO_REST_API_KEY`, `SNOWTHING_OPINET_API_KEY`, `SNOWTHING_EMAIL_VERIFICATION_SECRET` | compose가 `:?`로 막아 컨테이너를 만들기 전에 실패한다 | 안전 |
| `SNOWTHING_EMAIL_VERIFICATION_SECRET` | `application.yml` 공통 구역에 개발용 기본값(`local-development-...`)이 있다. 운영 프로필은 기본값 없이 다시 정의하고 compose도 막는다 | 지금은 안전. 운영 프로필의 재정의를 지우면 공개된 기본값으로 뜨게 되므로 지우지 않는다 |
| `SNOWTHING_GOOGLE_CLIENT_SECRET`, `SNOWTHING_NAVER_CLIENT_SECRET`, `SNOWTHING_KAKAO_LOGIN_CLIENT_SECRET`, `SNOWTHING_KMA_SERVICE_KEY`, `DISCORD_WEBHOOK_*`, `WEB_PUSH_VAPID_*`, `OPENAI_API_KEY`, `SNOWTHING_OPENAI_API_KEY` | compose 기본값이 빈 문자열이라 오류 없이 뜨고 해당 기능만 실패하거나 꺼진다 | 주의. 비밀을 prod.env에서 지운 뒤 Parameter Store 없이 손으로 `docker compose up`을 하면 이 값들이 빈 채로 컨테이너가 다시 만들어진다. 손으로 띄울 때는 `scripts/compose-prod.sh`만 쓴다 |
| `SNOWTHING_BOT_INTERNAL_SECRET`, `SNOWTHING_BOT_DELEGATION_SECRET` | 봇이 켜져 있으면 백엔드가 32바이트 미만을 거부하고 시작하지 않는다 | 백엔드는 안전. mcp-bot 쪽의 빈 값 처리는 다른 저장소라 여기서 확인하지 못했다 |
| `SNOWTHING_REMEMBER_ME_KEY` | 이메일 인증 비밀값에서 만든다 | 동작한다. 두 값이 묶여 있다는 점만 기억한다 |
| `SNOWTHING_KAKAO_LOGIN_CLIENT_ID` | `SNOWTHING_KAKAO_REST_API_KEY`를 쓴다 | 의도된 기본값 |
| `SNOWTHING_S3_BUCKET`, `SNOWTHING_IMAGE_BASE_URL`, `SNOWTHING_FRONTEND_BASE_URL`, 리다이렉트 주소 | 저장소에 적힌 운영 값을 쓴다 | 동작한다. 버킷 이름과 도메인이 저장소에 공개돼 있다 |
| 로컬 프로필의 DB 암호 기본값, CI 워크플로의 테스트 DB 암호 | 저장소에 공개된 값 | 운영 어디에도 같은 값을 쓰지 않는다 |

`application.yml`에는 있지만 `compose.prod.yml`이 넘기지 않는 변수는 prod.env나 Parameter Store에 넣어도 컨테이너에 전달되지 않는다. 지금 해당하는 것: `SNOWTHING_SLOPE_STATUS_ENABLED_RESORTS`, `SNOWTHING_SLOPE_STATUS_CRON`, `SNOWTHING_SEARCH_*`(임베딩 API 키 `SNOWTHING_SEARCH_EMBEDDING_API_KEY` 포함, 비밀, 지금은 쓰지 않음), `SNOWTHING_ANSWER_DRAFT_PUBLISH_ENABLED`, `SNOWTHING_AI_MEMBERS_FIRST_OPENING_DATE`, `SNOWTHING_DM_MAIL_SES_ENDPOINT`, 각종 타임아웃(`SNOWTHING_CARPOOL_*`, `SNOWTHING_WEATHER_*`, `SNOWTHING_OAUTH2_*`). 쓰려면 compose에 먼저 추가한다.

## 8. 유출이 의심될 때

교체가 이력 삭제보다 먼저다. 한 번 밖에 나간 값은 이미 복사됐다고 본다. 커밋이나 로그를 지워도 그 값은 계속 쓸 수 있다.

1. 어떤 값인지 정하고 발급처에서 바로 폐기하거나 재발급한다. 순서: 돈이 나가는 것(`OPENAI_API_KEY`, `SNOWTHING_OPENAI_API_KEY`), 계정에 닿는 것(소셜 로그인 시크릿, 봇 비밀), 나머지.
2. 새 값을 넣고(Parameter Store 또는 prod.env) 해당 서비스를 다시 만든다: `sudo AWS_REGION=<리전> scripts/compose-prod.sh up -d <서비스>`. 공유 값은 두 서비스를 함께.
3. 악용 흔적을 본다: 발급처 사용량과 요금, CloudTrail의 `GetParametersByPath`와 `Decrypt` 호출 주체와 시각, Discord 채널, 관리자 계정 목록.
4. 새 나간 길을 막는다(잘못 올린 파일, 열린 권한, 로그 출력).
5. 그 다음에 이력을 지운다(커밋, Actions 로그, 채팅). 지워도 1번을 건너뛸 수 없다.
6. 서버 안에서 명령이 실행된 흔적이 있으면 값 하나가 아니라 전부가 읽혔다고 본다. 목록의 비밀을 모두 바꾸고, 인스턴스 역할의 임시 자격 증명도 살아 있으므로 IAM에서 역할의 활성 세션을 취소하고 인스턴스를 새로 만든다.
7. 무엇을 언제 바꿨는지 `docs/project/work.md`에 남긴다. 값은 적지 않는다.

`SNOWTHING_EMAIL_VERIFICATION_SECRET`과 `WEB_PUSH_VAPID_PRIVATE_KEY`는 교체의 사용자 영향이 크다(3.1 참고). 의심 단계에서는 다른 값부터 바꾸고, 이 둘은 유출이 확인되면 바꾼다.
