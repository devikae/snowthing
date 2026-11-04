# Discord 알림 설계

- 상태: 설계 승인 (2026-11-04)
- 기준일: 2026-11-04 (KST)
- Linear: IKA-18
- 관련 설계: `docs/conception/012_profile_settings/04_image_cost_control.md`(SNS `snowthing-monitoring-alerts`, CloudWatch 경보), `docs/conception/018_ai_members/01_design.md`(AI 멤버 계정)

## 1. 목적과 범위

운영 장애와 주요 서비스 이벤트를 Discord로 받는다. 지금은 CloudWatch 경보가 이메일로만 오고, 서버 500 에러는 로그에만 남아 바로 알 수 없다.

| 포함 | 제외 |
|---|---|
| 서버 500 에러(요청 처리 중 미처리 예외) | 스케줄러, 비동기 작업 안에서 난 에러 |
| CloudWatch 경보(기존 SNS 토픽) | 배포 결과, 헬스체크 |
| 신규 가입(이메일, 소셜) | 글, 카풀, 장터 건별 알림 |
| 일일 요약(가입, 글, 댓글 수) | 사이트 사용자 알림 기능 |

후속: 문의게시판이 생기면 등록된 문의글도 #이벤트 채널로 알린다. 게시판이 아직 없어 이번 범위에서 뺐다. 가입 알림과 같이 등록 트랜잭션 AFTER_COMMIT 이벤트로 `DiscordNotifier.event()`를 호출하면 된다. 문의 본문과 작성자 연락처는 넣지 않고 제목과 링크만 보낸다.

## 2. 채널

| 채널 | 웹훅 환경변수 | 내용 |
|---|---|---|
| #장애 | `DISCORD_WEBHOOK_ALERT` | 500 에러, CloudWatch 경보 |
| #이벤트 | `DISCORD_WEBHOOK_EVENT` | 신규 가입, 일일 요약 |

- 채널을 나눠 가입 알림 사이에 장애가 묻히지 않게 한다.
- 웹훅 URL은 비밀값이다. 운영 `prod.env`와 Lambda 환경변수에만 둔다. 저장소, 로그, 응답에 남기지 않는다.

## 3. 접근 방식 결정

| 안 | 내용 | 결정 |
|---|---|---|
| A | 앱 이벤트는 백엔드가 직접 보내고, CloudWatch 경보는 SNS 구독 Lambda가 전달 | 채택 |
| B | 모든 신호를 로그 메트릭 필터로 CloudWatch에 모은 뒤 Lambda가 전달 | 500 상세 정보 손실, 가입과 요약을 메트릭으로 만들기 억지라 제외 |
| C | 500을 Logback Discord Appender(ERROR 레벨)로 수집 | 스케줄러 에러까지 잡지만 프레임워크 ERROR 로그로 시끄럽고, Spring 밖 객체라 설정과 테스트가 복잡해 제외 |

- CloudWatch 경보에 Lambda가 필요한 이유: SNS HTTPS 구독은 구독 확인 요청과 SNS 봉투 JSON을 보내지만 Discord 웹훅은 `content`, `embeds` 형식만 받는다. AWS 공식 채팅 연동(Amazon Q Developer in chat applications)은 Slack, Teams, Chime만 지원한다.
- SNS를 백엔드 엔드포인트로 받아 전달하는 방법은 쓰지 않는다. 백엔드가 죽었을 때 경보가 같이 사라진다.

## 4. 백엔드 공통: DiscordNotifier

- `RestClient`로 웹훅에 POST한다. 연결, 읽기 타임아웃은 각각 3초.
- 전용 스레드풀(코어 1, 최대 2, 큐 100)에서 비동기로 보낸다. 큐가 차면 버린다. 사용자 요청은 Discord 응답을 기다리지 않는다.
- 웹훅 URL이 비어 있으면 보내지 않는다. 로컬, 테스트 환경은 기본 비활성이다.
- 실패 시 `WARN` 로그(웹훅 URL 제외)만 남기고 재시도하지 않는다. 알림 실패가 본 기능에 영향을 주지 않는 것이 우선이다.
- Discord 메시지 길이 제한(content 2000자)을 넘지 않게 자른다. `allowed_mentions`를 비워 `@everyone` 같은 멘션이 동작하지 않게 한다.
- 시각은 모두 KST로 표시한다.

## 5. #장애: 서버 500 에러

- `GlobalExceptionHandler`의 `@ExceptionHandler(Exception.class)`에서 기존 로그를 남긴 뒤 알림을 요청한다.
- 중복 억제: 키는 `예외 클래스 + 첫 번째 프로젝트 스택 프레임(com.ikae)`. 같은 키는 5분에 한 번만 보낸다. 억제된 횟수는 다음 알림에 "지난 알림 이후 N회"로 붙인다. 키 저장은 메모리(`ConcurrentHashMap`)이고, 오래된 키는 정리한다.
- 메시지 내용

```
🚨 500 에러
시각: 2026-11-04 14:03:12 KST
요청: POST /api/v1/posts
예외: java.lang.IllegalStateException: ...(200자 제한)
위치: PostService.create(PostService.java:88)
(지난 알림 이후 12회)
```

- 넣지 않는 값: 요청 본문, 쿼리 문자열, 헤더, 사용자 이메일과 ID.
- 단일 인스턴스 기준이다. 인스턴스가 늘면 인스턴스마다 5분에 한 번씩 보낼 수 있다.

## 6. #장애: CloudWatch 경보

- 기존 SNS 토픽 `snowthing-monitoring-alerts`에 Lambda 구독을 추가한다. 기존 이메일 구독은 유지한다.
- Lambda(Python, 표준 라이브러리만 사용)는 SNS 메시지의 `AlarmName`, `NewStateValue`, `NewStateReason`, `StateChangeTime`을 읽어 보낸다. ALARM은 🚨, OK는 ✅. 경보 형식이 아닌 메시지는 원문 앞부분만 보낸다.
- 코드는 저장소에 둔다. 설정 스크립트는 기존 `scripts/configure-*.py`처럼 기본이 읽기 전용 계획 출력이고 `--apply`로만 적용한다. 운영 적용 전 사용자 확인을 받는다.
- 비용: Lambda, SNS 전송 모두 프리티어 범위다.

## 7. #이벤트: 신규 가입

- 이메일 가입(`MemberService.signUp`)과 소셜 가입(`SocialAuthService.createSocialMember`)에서 `MemberSignedUpEvent`를 발행한다.
- `@TransactionalEventListener(phase = AFTER_COMMIT)`에서 알림을 요청한다. 커밋된 가입만 알린다.
- AI 멤버 생성(`AiMemberBootstrap`), 관리자 초기화(`AdminBootstrap`, `DataInitializer`)는 이벤트를 발행하지 않는다.
- 메시지: `🎉 신규 가입: {닉네임} ({EMAIL|KAKAO|...}) 14:03`. 이메일은 넣지 않는다.

## 8. #이벤트: 일일 요약

- 매일 09:00 KST, `@Scheduled(cron = "0 0 9 * * *", zone = "Asia/Seoul")`.
- 집계 구간: 전날 00:00 이상, 당일 00:00 미만(KST).
- 항목: 신규 가입 수, 글 수, 댓글 수. 글과 댓글은 사람과 AI를 나눈다.
- AI 구분: 작성자 이메일이 `@snowthing.invalid`인 계정. AI 멤버 기능을 꺼도 기존 AI 계정의 글을 계속 구분할 수 있다. 신규 가입 수에서도 AI 계정은 뺀다.
- 삭제된 글, 댓글의 포함 여부는 구현 계획에서 엔티티 구조를 확인해 정한다(기본: 작성 시점 기준으로 포함).
- 메시지

```
📊 11/03 요약
신규 가입 3
글 12 (사람 5 / AI 7)
댓글 30 (사람 18 / AI 12)
```

- 서버가 09:00에 내려가 있었으면 그날 요약은 건너뛴다. 재전송하지 않는다.

## 9. 영향 범위

- DB 스키마, API 계약 변경 없음.
- 새 운영 설정: `DISCORD_WEBHOOK_ALERT`, `DISCORD_WEBHOOK_EVENT`(`compose.prod.yml` 전달), AWS Lambda 함수와 SNS 구독.
- 성능: 500 알림과 가입 알림은 비동기다. 일일 요약은 하루 한 번 count 쿼리 몇 개이고, `created_at` 인덱스 여부는 구현 계획에서 확인한다.
- 보안: 웹훅 URL 유출 시 누구나 채널에 글을 쓸 수 있다. 유출되면 Discord에서 웹훅을 재발급한다. 메시지에 개인정보와 요청 데이터를 넣지 않는다.
- 테스트: 중복 억제, 메시지 포맷, 길이 제한, AFTER_COMMIT 동작, 요약 집계 구간을 단위, 통합 테스트로 확인한다. 실제 Discord 전송은 운영 웹훅으로 수동 확인한다.

## 10. 사용자 준비 항목

- Discord 서버에 #장애, #이벤트 채널을 만들고 각 채널의 웹훅 URL을 발급한다.
- 운영 `prod.env`에 두 값을 넣는 작업과 Lambda 적용은 사용자 확인 후 진행한다.
