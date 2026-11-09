# 홈 화면 설치와 웹 푸시 알림

티켓: IKA-65

## 목표

- 사이트를 휴대폰 홈 화면에 앱처럼 설치할 수 있다.
- 허용한 회원은 기존 알림(내 글 댓글, 내 댓글 답글, 1:1 문의 답변, 운영 조치 안내)을 휴대폰 푸시로도 받는다.
- 서버에 VAPID 키가 없으면 발송과 구독 등록이 꺼지고, 알림 저장이나 댓글 작성에는 영향이 없다.

범위 밖: 오프라인 캐시, 마케팅 푸시, 종류별 알림 끄기, 쪽지 알림(쪽지는 아직 `notification`을 만들지 않는다).

## 검토한 문서

- `022_notification/01_design.md`: 알림은 댓글 커밋 뒤 `REQUIRES_NEW` 트랜잭션에서 만든다. 설계 후속 항목에 "실시간 푸시"가 있었다.
- `025_content_report`: 운영 조치 결과도 `MODERATION_RESULT` 알림으로 저장한다.
- `025_member_withdrawal/01_design.md`: 탈퇴 때 회원 밖 개인정보를 `MemberWithdrawalRepository`에서 SQL로 지운다.
- 새 N:M 관계는 없다. 회원 1 : 구독 N이다.

## 흐름

```
댓글 작성 트랜잭션 커밋
  └ NotificationEventListener(AFTER_COMMIT) → NotificationWriter(REQUIRES_NEW)
        알림 저장 → NotificationsCreatedEvent 발행
              ↓ 알림 트랜잭션 커밋 후
        NotificationPushListener (@TransactionalEventListener AFTER_COMMIT + @Async web-push 풀)
              받는 사람들의 구독 조회 → 기기마다 암호화해 POST
              201: last_used_at 갱신 / 404, 410: 구독 삭제 / 그 밖: 로그만
운영 조치(ContentModerationService)도 알림 저장 뒤 같은 이벤트를 발행한다.
```

- 알림이 롤백되면 푸시도 없다(AFTER_COMMIT).
- 발송은 별도 스레드 풀(core 2, max 4, 대기열 500)에서 돈다. 요청 스레드는 푸시 서비스 응답을 기다리지 않는다. 대기열이 차면 그 발송은 버리고 경고 로그만 남긴다. 알림은 이미 DB에 있어 사이트에서는 보인다.
- 발송 중 어떤 예외도 알림 저장에 영향이 없다.

## 데이터

`database/production/045_migration_push_subscription.sql`

| 칼럼 | 설명 |
|---|---|
| push_subscription_id | PK |
| member_id | 회원 FK |
| endpoint | 푸시 서비스 주소, VARCHAR(1024) |
| endpoint_hash | endpoint SHA-256 16진수, 유니크 |
| p256dh, auth | 브라우저 공개키와 인증 비밀값(base64url) |
| created_at, last_used_at | KST |

- endpoint 길이가 일정하지 않아 utf8mb4 유니크 인덱스 한도(3072바이트)를 넘을 수 있다. 그래서 해시 칼럼에 유니크를 건다.
- 등록은 `INSERT ... ON DUPLICATE KEY UPDATE` 한 문장이다. 같은 기기를 다른 계정이 다시 켜면 그 행이 지금 계정으로 옮겨진다. 공유 기기에서 이전 계정의 알림이 계속 오지 않게 하려는 것이다.
- 같은 endpoint를 동시에 넣으면 InnoDB가 교착으로 한쪽을 되돌릴 수 있다(테스트에서 재현). 문장 하나라 다시 실행해도 결과가 같으므로 최대 3번 다시 시도한다.
- 회원당 최근 10개 기기만 남긴다. 넘으면 `last_used_at`이 오래된 것부터 지운다. 가짜 endpoint를 많이 넣어 발송을 부풀리는 것도 막는다.
- `idx_push_subscription_member (member_id, last_used_at)`: 발송 때 받는 사람별 조회와 10개 정리에 쓴다.
- 404, 410 응답이면 구독을 지운다. 회원 탈퇴 때도 지운다.

## API

모두 로그인 필수. 위임 토큰과 API 토큰은 401(알림 API와 같은 규칙).

| API | 설명 |
|---|---|
| `GET /api/v1/push/config` | `{ enabled, publicKey }`. 키가 없으면 `enabled=false, publicKey=null` |
| `PUT /api/v1/push/subscriptions` | 브라우저 `PushSubscription.toJSON()` 그대로. 204. 키가 없으면 503 `PUSH_001`, 형식이 틀리면 400 `PUSH_002` |
| `DELETE /api/v1/push/subscriptions` | 본문 `{ endpoint }`. 내 구독만 지운다. 없어도 204. 키가 없어도 끌 수는 있다 |

- endpoint는 요청 본문으로 들어오고 서버가 그 주소로 POST를 보낸다. SSRF 통로가 되지 않게 `https`이고 호스트가 알려진 푸시 서비스(`fcm.googleapis.com`, `push.apple.com`, `push.services.mozilla.com`, `notify.windows.com`와 그 하위 도메인)인 주소만 받는다. 포트는 443만, 사용자 정보가 든 주소는 거절, 리다이렉트는 따라가지 않는다.
- p256dh는 65바이트 P-256 곡선 위의 점인지, auth는 16바이트인지 확인한다.

## 푸시 본문

잠금 화면에 그대로 보이므로 작성자 이름, 댓글 미리보기, 조치 사유는 넣지 않는다.

| 종류 | 본문 | 이동 |
|---|---|---|
| POST_COMMENT | 내 글에 새 댓글이 달렸어요 | 글 상세 `#comments` |
| COMMENT_REPLY | 내 댓글에 새 답글이 달렸어요 | 글 상세 `#comments` |
| INQUIRY_ANSWER | 문의에 답변이 달렸어요 | 글 상세 `#comments` |
| MODERATION_RESULT | 운영팀 안내가 도착했어요 | `/notifications` |

- 제목은 "눈팅". 글 상세 경로는 화면의 `notificationHref`와 같다(MARKET은 `/market`, CARPOOL은 `/carpool`, 나머지 `/posts`).
- 운영 조치는 글이 가려졌거나 지워졌을 수 있어 알림 목록으로 보낸다.
- 푸시를 눌러 들어가도 알림은 읽음 처리되지 않는다. 알림 목록에서 읽음으로 바뀐다.
- TTL 1일, Urgency normal.

## 화면

- `app/manifest.ts`: `display: standalone`, 192/512 PNG와 maskable 아이콘 추가. 아이콘은 기존 `icon.svg`에서 만들었다.
- `public/sw.js`: `push`, `notificationclick`만 처리한다. fetch를 듣지 않아 페이지 요청과 캐시에 관여하지 않는다. 클릭하면 같은 주소의 창이 있으면 그 창으로, 없으면 새 창으로 연다. 같은 출처 경로만 연다.
- 서비스 워커는 사용자가 알림을 켤 때만 등록한다. 켜지 않은 사람에게는 아무것도 설치되지 않는다.
- `/notifications` 위쪽에 "휴대폰 알림 받기" 스위치.
  - 서버 키가 없으면 스위치를 숨긴다.
  - iOS Safari 탭에서는 "홈 화면에 추가한 뒤 그 앱에서 켤 수 있다"는 안내만 보인다(iOS 16.4부터 홈 화면 앱에서만 웹 푸시가 된다).
  - 권한이 차단돼 있으면 설정에서 허용하라는 안내.
  - 페이지를 열 때 이미 켜져 있으면 구독을 서버에 다시 보내 `last_used_at`을 고치고, 다른 계정이었다면 지금 계정으로 옮긴다.
- 로그아웃하면 이 기기 구독을 먼저 지운다(최대 1.5초 기다림).
- CSP: 지금 화면 응답의 CSP는 `frame-ancestors 'self'`뿐이라 `worker-src`나 `script-src` 제약이 없다. `/sw.js`는 같은 출처 정적 파일이고 scope `/`라 충돌하지 않는다.

## 결정과 대안

### 라이브러리: JDK 표준 암호 API로 직접 구현

| 선택지 | 장점 | 단점 |
|---|---|---|
| `nl.martijndwars:web-push` + BouncyCastle | 많이 쓰였고 API가 간단 | 2021년 이후 릴리스 없음. BouncyCastle(수 MB)과 Apache HttpAsyncClient를 끌고 온다. 비동기 클라이언트가 우리 스레드 풀 밖에서 돈다 |
| `com.interaso:webpush` 등 Kotlin 계열 | 유지보수 중 | Kotlin 표준 라이브러리 의존 |
| JDK 직접 구현 (선택) | 새 의존성 없음. ECDH, HKDF(HMAC-SHA256), AES-GCM, ES256(P1363 서명)이 모두 JDK 21에 있다. 기존 `HttpClient` 패턴(Discord)과 같다 | 암호 코드를 우리가 관리한다 |

- 암호 코드 위험은 RFC 8291 5절의 공개 예제(키, salt, 기대 암호문)와 바이트 단위로 같은지 테스트로 막았다. 테스트용 복호화기를 따로 써서 왕복도 확인한다.
- VAPID 키가 있는데 형식이 틀리거나 공개키와 비밀키가 짝이 아니면 기동을 멈춘다. 잘못된 키로 뜨면 모든 발송이 푸시 서비스에서 403으로 조용히 실패하기 때문이다.

### 발송 시점: 알림 저장 이벤트 + AFTER_COMMIT + @Async

- 대안 1: `NotificationWriter` 안에서 바로 발송. 요청 스레드(댓글 커밋 뒤)가 푸시 서비스 응답을 기다린다. 기각.
- 대안 2: 아웃박스 테이블과 스케줄러. 서버가 죽어도 유실이 없지만 테이블과 지연이 늘어난다. 022 알림 설계와 같은 이유로 유실을 감수한다.

### endpoint 유니크: 해시 칼럼

- 대안: `endpoint VARCHAR(700) CHARACTER SET ascii`에 직접 유니크. 길이 제한을 미리 정해야 하고 Hibernate 테스트 스키마와 문자셋이 달라진다. 해시가 단순하다.

## 운영 설정

| 환경 변수 | 값 |
|---|---|
| `WEB_PUSH_VAPID_PUBLIC_KEY` | 공개키 base64url(65바이트 비압축 점) |
| `WEB_PUSH_VAPID_PRIVATE_KEY` | 비밀키 base64url(32바이트). 비밀값 |
| `WEB_PUSH_VAPID_SUBJECT` | `mailto:운영메일` 또는 `https://snowthing.org` |

- `compose.prod.yml`에 세 변수의 전달 줄만 추가했다(기본값 빈 문자열 = 꺼짐). 값은 운영 `prod.env`에 사용자가 넣는다.
- 키 생성 예: `npx web-push generate-vapid-keys` (출력의 Public Key, Private Key가 그대로 base64url이다). 키를 바꾸면 기존 구독은 모두 다시 켜야 한다.
- 프론트는 공개키를 `/api/v1/push/config`로 받으므로 프론트 빌드 변수는 필요 없다.

## 검증

- RFC 8291 예제 암호문 일치, 왕복 복호화, 잘못된 공개키 거절.
- VAPID: 꺼짐 조건, JWT aud/exp/sub와 서명 검증, 키 짝 불일치와 subject 형식 오류에서 기동 실패.
- endpoint 허용 목록과 SSRF 주소 거절.
- 통합(MySQL): 설정 API, 비로그인 401, 계정 이동, 400 검증, 10개 제한, 내 구독만 해지, 동시 등록 한 행, 댓글 알림 후 암호화 푸시 발송과 본문에 댓글 내용 없음, 410 삭제, 일시 실패 시 구독 유지, 탈퇴 시 삭제, 키 없음에서 503과 발송 없음.

## 후속

- 쪽지 알림(쪽지가 `notification`을 만들게 되면 자동으로 푸시된다).
- 종류별 푸시 끄기 설정.
- 푸시 클릭 시 해당 알림 읽음 처리(푸시에 알림 ID를 실어 서비스 워커에서 읽음 API 호출).
