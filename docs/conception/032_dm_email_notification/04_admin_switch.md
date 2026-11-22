# 쪽지 메일 관리자 스위치 (IKA-109)

`01_design.md`의 쪽지 도착 메일을 재시작 없이 켜고 끄기 위한 변경이다. 발송 구조(outbox, 선점, 결과 불명 정책)는 바꾸지 않는다.

## 배경

- 기록(`enqueue-enabled`)과 발송(`send-enabled`)은 환경변수에서 기동할 때 한 번만 읽었다. 켜거나 끄려면 `prod.env`를 고치고 백엔드를 다시 띄워야 했다.
- 발송이 꺼져 있으면 스케줄러 빈 자체가 없었다(`@ConditionalOnProperty`). 그래서 보관 기간이 지난 행을 지우는 정리 작업도 돌지 않았다.
- 메일이 잘못 나가는 일이 생겼을 때 관리자가 화면에서 바로 멈출 방법이 없었다.

## 스위치의 뜻

| 스위치 | 설정 키(`system_config`) | 켜면 | 끄면 |
|---|---|---|---|
| 기록 | `DM_MAIL_ENQUEUE_ENABLED` | 쪽지가 저장될 때 같은 트랜잭션에서 `dm_mail_outbox`에 발송 요청을 쓴다(받는 사람이 메일 알림을 켠 경우만) | 다음 쪽지부터 발송 요청을 쓰지 않는다. 이미 쌓인 행은 그대로 둔다 |
| 발송 | `DM_MAIL_SEND_ENABLED` | 워커가 쌓인 요청을 SES로 보낸다 | 다음 폴링 주기부터 보내지 않는다. 쌓인 행은 PENDING으로 남는다 |

- 두 스위치는 서로 독립이다. 기록만 켜면 요청이 쌓이기만 하고, 발송만 켜면 이미 쌓인 요청만 보낸다.
- 초기값은 둘 다 `false`다(마이그레이션 `061_migration_dm_mail_admin_switch.sql`). 설정 행이 없어도 `false`로 본다.

## 환경변수와의 관계

```
기록이 실제로 동작 = SNOWTHING_DM_MAIL_ENQUEUE_ENABLED(환경변수) AND DM_MAIL_ENQUEUE_ENABLED(화면)
발송이 실제로 동작 = SNOWTHING_DM_MAIL_SEND_ENABLED(환경변수) AND DM_MAIL_SEND_ENABLED(화면) AND 메일 공급자가 ses
```

- 환경변수는 서버 쪽 비상 차단이다. 꺼져 있으면 화면 스위치를 켜도 동작하지 않고, 관리자 화면에 "서버 설정에서 막혀 있습니다"가 보인다. 환경변수는 재시작해야 바뀐다.
- 지금 운영은 환경변수를 넣지 않아 둘 다 `false`다. 이 변경을 배포해도 메일은 나가지 않는다. 화면 스위치 초기값도 꺼짐이라 이중으로 막혀 있다.
- 판단은 `DmMailSwitches` 한 곳에서 한다. 쪽지 저장(`DmMailOutboxWriter`)과 워커(`DmMailScheduler`)가 같은 판단을 쓴다.

### 선택지와 결정

| 선택지 | 장점 | 단점 | 결정 |
|---|---|---|---|
| 환경변수를 없애고 화면 스위치만 둔다 | 단순하다 | 화면 권한만으로 실제 회원에게 메일이 나간다. 배포 직후 실수로 켜지는 것을 서버에서 막을 방법이 없다 | 안 함 |
| 환경변수 OR 화면 | 둘 중 하나만 켜도 된다 | 끄려면 둘 다 꺼야 한다. 비상 차단이 되지 않는다 | 안 함 |
| 환경변수 AND 화면 | 어느 한쪽에서든 끌 수 있다. 기존 운영 값(false)에서 동작이 바뀌지 않는다 | 처음 켤 때 두 군데를 켜야 한다 | 채택 |

## 반영 시점과 조회 비용

- 화면 스위치는 IKA-108 AI 회원 스위치와 같은 방식으로 읽는다. `SystemConfigService`의 메모리 캐시(Caffeine, 쓰고 5분 뒤 만료)에서 읽고, 캐시에 없을 때만 `system_config`를 PK로 한 번 조회한다.
- 쪽지 저장마다, 워커 폴링(1초)마다 스위치를 보지만 DB 조회는 키마다 5분에 한 번이다.
- 관리자 API로 바꾸면 같은 요청에서 캐시 값도 바꾼다. 그래서 다음 쪽지, 다음 폴링 주기(최대 1초)부터 반영된다. 워커가 이미 보내고 있던 받는 사람 한 명의 메일까지는 나간다(배치 사이에서 스위치를 다시 본다).
- 관리자 API를 거치지 않고 DB 값을 직접 고치면 캐시가 만료될 때까지 최대 5분 늦는다. 서버를 여러 대로 늘리면 요청을 받지 않은 서버도 최대 5분 늦는다. 지금 운영은 한 대다.

## 스케줄러

- `DmMailScheduler` 빈은 항상 뜬다. 폴링 주기마다 유효한 발송 스위치를 본다.
- 꺼져 있는 주기에는 캐시만 읽고 DB를 건드리지 않는다. 예외는 아래의 기한 지난 행 정리로, 10분(`snowthing.dm-mail.stale-sweep-interval`)에 한 번 UPDATE 한 번이다.
- SES 클라이언트를 만드는 조건(`snowthing.mail.provider=ses`)은 그대로다. 공급자가 ses가 아니면 `DisabledDmMailSender`가 쓰이고 발송은 꺼진 것으로 본다.
- 보관 기간(30일)이 지난 ACCEPTED, SKIPPED 행을 지우는 정리(매일 04:40)는 이제 발송 스위치와 관계없이 돈다. 한 번에 1000행씩 최대 20번 지운다(전에는 하루 1000행이 한도였다).

## 오래된 대기 건

기존 정책은 "쪽지가 생긴 뒤 24시간(`snowthing.dm-mail.deadline`)이 지나면 보내지 않는다"이다. 이 기준을 그대로 쓴다.

| 상황 | 처리 | `skip_reason` |
|---|---|---|
| 발송이 켜져 있고, 워커가 잡은 행이 24시간을 넘겼다 (기존) | 발송 직전 확인에서 SKIPPED | `EXPIRED` |
| 발송이 꺼져 있는 동안 24시간을 넘긴 PENDING, RETRY_WAIT 행 (신규) | 스케줄러가 10분마다 한꺼번에 SKIPPED | `SEND_OFF_EXPIRED` |
| 발송을 다시 켠 첫 주기, 재시작 직후 첫 주기 (신규) | 메일을 보내기 전에 같은 정리를 먼저 돌린다 | `SEND_OFF_EXPIRED` |

- 발송을 다시 켜면 24시간 안에 온 쪽지의 요청만 나간다. 받는 사람이 이미 읽은 쪽지는 기존 확인(`ALREADY_READ`)에서 빠지고, 같은 받는 사람의 여러 건은 한 통으로 묶인다. 그래서 켜는 순간 나가는 메일은 받는 사람마다 많아야 한 통이다.
- 기록만 켜고 발송을 오래 꺼 두면: 대기 행은 최근 24시간치까지만 남고, 넘긴 행은 SKIPPED가 된 뒤 30일 뒤에 지워진다. outbox는 끝없이 커지지 않는다.
- 정리는 `created_at` 기준 UPDATE 한 번에 1000행, `event_id` 순서로 잠근다. 워커의 선점과 같은 순서라 서로 교착하지 않는다. 워커가 이미 잡은 행(PROCESSING)은 건드리지 않는다.
- 정리 UPDATE는 `idx_dm_mail_outbox_due (status, due_at, event_id)`의 status 범위로 대기 행만 훑는다. 대기 행이 최근 24시간치로 묶여 있어 양이 작다. 운영 데이터로 `EXPLAIN`을 확인한 것은 아니다.

## 관리자 API와 화면

```
GET /api/v1/admin/dm-mail/switches
→ 200 {
  "enqueue": { "configKey": "DM_MAIL_ENQUEUE_ENABLED", "serverAllowed": false, "switchOn": false, "effective": false },
  "send":    { "configKey": "DM_MAIL_SEND_ENABLED",    "serverAllowed": false, "switchOn": false, "effective": false },
  "mailProviderReady": true,
  "deadlineHours": 24
}

PATCH /api/v1/admin/configs/{configKey}   (기존 API)
{ "value": "true" }
```

- 바꾸는 API는 새로 만들지 않고 IKA-108과 같은 관리자 설정 API를 쓴다. ROLE_ADMIN만 부를 수 있고 CSRF 토큰이 필요하다.
- 바꿀 때마다 `admin_action_log`에 `CONFIG_UPDATE`, 대상 `CONFIG`, 설정 키, 바뀐 값이 조치한 관리자와 함께 남는다. 관리자 화면 "조치 기록"에서 볼 수 있다.
- 화면은 관리자 메뉴 "AI, 시스템 > 쪽지 메일"(`/admin/dm-mail`)이다. 스위치 두 개, 서버 설정 차단 안내, 발송 상태 요약(대기, 성공, 재시도 대기, 실패, 확인 필요, 건너뜀)을 보여 준다. 요약은 기존 `GET /api/v1/admin/dm-mail/outbox/summary`를 쓴다.
- 발송을 켤 때는 실제 회원에게 메일이 나간다는 확인 문구를 띄운다.

## 운영에서 켜는 절차

1. SES 권한 확인: 운영 서버의 IAM 역할에 `ses:SendEmail` 권한이 있고 `SNOWTHING_MAIL_PROVIDER=ses`인지 본다. 가입 인증 메일이 나가고 있으면 같은 권한이다.
2. `prod.env`에 두 값을 넣는다. `compose.prod.yml`에는 이미 두 변수가 올라가 있다.
   ```
   SNOWTHING_DM_MAIL_ENQUEUE_ENABLED=true
   SNOWTHING_DM_MAIL_SEND_ENABLED=true
   ```
   백엔드를 다시 띄운다. 이때까지는 화면 스위치가 꺼져 있어 아무 일도 일어나지 않는다.
3. 관리자 화면 "쪽지 메일"에서 "서버 설정에서 막혀 있습니다" 안내가 사라졌는지 본다.
4. 기록 스위치를 켠다.
5. 쪽지를 하나 보내고(받는 사람이 쪽지함에서 메일 알림을 켠 계정이어야 한다) 발송 상태의 "대기"가 1 늘었는지 본다.
6. 발송 스위치를 켠다. 확인 문구에 대기 건수가 나온다. 몇 초 뒤 새로고침해서 "성공"이 늘고 "대기"가 줄었는지, 실제 메일이 도착했는지 본다.
7. 문제가 생기면 발송 스위치를 끈다. 다음 주기(1초)부터 나가지 않는다. 화면에 접근할 수 없으면 `prod.env`의 값을 `false`로 바꾸고 다시 띄운다.

## 검증

- 자동 테스트: `DmMailAdminSwitchIntegrationTest`(스위치 조합 4가지, 실행 중 전환, 오래된 건, 조치 기록, 권한), `DmMailServerBlockIntegrationTest`(환경변수 차단, 꺼진 동안 DB 무접근), `DmMailSwitchesTest`(AND 판단표). 기존 `DmMailOutboxIntegrationTest`는 고치지 않았다.
- 로컬 전체 스택(MySQL, 가짜 SES)에서 관리자 로그인으로 화면을 확인했다. 실제 SES 발송과 운영 환경에서는 확인하지 않았다.
