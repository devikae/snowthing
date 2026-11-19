# IKA-88 검증 증거

- 수집일: 2026-11-17 (KST)
- 환경: 로컬 Docker MySQL 8.0, 가짜 SES HTTP 서버(별도 프로세스), 같은 jar로 띄운 앱 프로세스. 운영 DB와 실제 SES는 쓰지 않았다.
- DB: 마이그레이션은 빈 DB `snowthing_ika88_mig`(member 테이블만 복사), 실험은 격리 DB `snowthing_ika88_exp`.
- 캡처 방식: 각 단계에서 실제 DB에 SQL을 실행하거나 가짜 SES 통계, 앱 로그를 읽어 실행 시각과 함께 한 화면으로 그렸다. 같은 이름의 `.txt`가 원본 출력이다.
- 실험 데이터는 쪽지 트랜잭션이 커밋된 직후와 같은 상태(쪽지와 발송 요청)를 SQL 한 트랜잭션으로 넣어 만들었다.
- 해석과 표는 `../02_verification.md`, `../03_benchmark.md`에 있다.

## 1. 마이그레이션 053

촬영 당시 파일 번호는 052였다. 2026-11-18에 내용은 그대로 두고 이름만 053으로 바꿨다. 원본 출력과 그림에는 052가 남아 있다.

| 단계 | 확인 |
|---|---|
| ![](migration/01-before-tables.png) | 적용 전에는 member 테이블만 있다 |
| ![](migration/02-apply-twice.png) | 같은 파일을 두 번 적용해도 mysql 종료 코드 0 |
| ![](migration/03-after-tables.png) | 테이블 3개, 인덱스, `uk_dm_mail_outbox_intent` 유니크 제약 |
| ![](migration/04-validate-boot.png) | 053(당시 052)으로 만든 테이블에서 `ddl-auto=validate`로 앱 기동 성공 |

## 2. 실험 1: 같은 DB를 보는 워커 두 개

받는 사람 40명, 받는 사람마다 쪽지 3개(발송 요청 120행)를 한 트랜잭션으로 커밋했다. 가짜 SES 수락 지연은 300ms다.

| 단계 | 확인 |
|---|---|
| ![](experiment1/01-two-workers-running.png) | 앱 프로세스 두 개 기동 |
| ![](experiment1/02-fake-ses-before.png) | 시작 전 가짜 SES 요청 0 |
| ![](experiment1/03-seeded.png) | 커밋 직후 조회. 두 워커가 이미 받는 사람 2명을 동시에 잡고 있다(PROCESSING 6행) |
| ![](experiment1/04-final-status.png) | 120행 모두 ACCEPTED, 메일 40통 |
| ![](experiment1/05-bundles.png) | 메일 한 통에 묶인 행 수가 모두 3 (묶음이 쪼개지지 않음) |
| ![](experiment1/06-mails-per-recipient.png) | 받는 사람 40명 모두 메일 1통 |
| ![](experiment1/07-fake-ses-after.png) | 가짜 SES 요청 40, 수락 40, 두 통 이상 받은 사람 0 |
| ![](experiment1/08-worker-share.png) | 워커별 처리: 21명, 19명 |

## 3. 실험 2: 앱이 모두 꺼진 동안 커밋, 재기동 후 처리

| 단계 | 확인 |
|---|---|
| ![](experiment2/01-no-app-running.png) | 앱 프로세스 없음 |
| ![](experiment2/02-pending-while-down.png) | 꺼진 동안 커밋된 15행이 PENDING으로 남아 있다 |
| ![](experiment2/03-after-restart.png) | 재기동 후 ACCEPTED 15, 메일 5통 |
| ![](experiment2/04-fake-ses.png) | 가짜 SES 요청 5, 수락 5 |

## 4. 실험 3: SES 수락 직후, 결과 기록 전 강제 종료

lease 15초. 가짜 SES는 요청을 수락한 뒤 2.5초 동안 응답하지 않는다(앱 시도 제한 3초). 수락이 기록되는 즉시 앱을 강제 종료했다.

| 단계 | 확인 |
|---|---|
| ![](experiment3/01-killed.png) | 가짜 SES 요청 1, 수락 1 상태에서 앱 종료 |
| ![](experiment3/02-processing-after-kill.png) | 결과가 기록되지 않아 PROCESSING, MessageId 없음 |
| ![](experiment3/03-fake-ses-switched.png) | 재기동 전 가짜 SES를 정상 수락 모드로 전환. 재발송이 일어나면 요청 수가 늘어 보인다 |
| ![](experiment3/04-unknown-after-restart.png) | 재기동 후 lease 만료로 UNKNOWN(LEASE_EXPIRED) |
| ![](experiment3/05-fake-ses-no-resend.png) | 가짜 SES 요청 1, 수락 1 그대로. 자동 재발송 0 |
| ![](experiment3/06-log.png) | 재기동한 앱의 lease 만료 경고 로그, 발송 로그 0 |

메일은 실제로 한 통 나갔지만 DB만으로는 그 사실을 알 수 없다. 기본 정책대로 다시 보내지 않고 운영자가 확인할 UNKNOWN으로 격리됐다.

## 5. 자동 테스트

| 단계 | 확인 |
|---|---|
| ![](tests/01-full-suite.png) | `./gradlew spotlessCheck test` 1305건, 실패 0, 오류 0, 건너뜀 1(기존 테스트) |
| ![](tests/02-related-tests.png) | 이번 작업 관련 테스트 64건 이름과 결과 |

원본: `tests/summary.txt` (테스트 결과 XML에서 뽑았다)

## 6. 성능 비교

| 단계 | 확인 |
|---|---|
| ![](benchmark/01-sync-vs-outbox.png) | 같은 조건에서 동기 기준선과 Outbox의 쪽지 응답 지연, Outbox 알림 지연 |
| ![](benchmark/02-excluded-run1.png) | 제외한 첫 실행. 제한 시간이 SES 지연보다 짧아 Outbox 60건이 UNKNOWN, 공급자는 59건 수락 |

원본: `benchmark/run2-*.json`(채택), `benchmark/run1-excluded-*.json`(제외)

## 7. 화면

| 계정 | 데스크톱 | 모바일 |
|---|---|---|
| 신규 회원(기본 켜짐) | ![](ui/new-desktop.png) | ![](ui/new-mobile.png) |
| 신규 회원, 끈 뒤 새로고침 | ![](ui/new-desktop-after-toggle.png) | |
| 기존 회원(기본 꺼짐) | ![](ui/old-desktop.png) | ![](ui/old-mobile.png) |
| 기존 회원, 켠 뒤 새로고침 | ![](ui/old-desktop-after-toggle.png) | |
| 소셜 내부 주소(켤 수 없음) | ![](ui/social-desktop.png) | ![](ui/social-mobile.png) |

## 8. 로그 원본

`logs/`에 실험별 앱 로그 중 기동, 쪽지 메일 관련 줄만 남겼다.

## 9. 재현 도구

- `tools/FakeSesMain.java`: 수동 실험용 가짜 SES. `java FakeSesMain.java 18599`로 띄우고 `GET /_admin/mode?m=ACCEPT|ACCEPT_THEN_HANG&delayMs=N`, `/_admin/stats`, `/_admin/reset`으로 조작한다.
- `tools/seed-template.sql`: 실험 시드 틀.
- 앱은 `--snowthing.mail.provider=ses --snowthing.dm-mail.enqueue-enabled=true --snowthing.dm-mail.send-enabled=true --snowthing.dm-mail.ses.endpoint=http://127.0.0.1:18599`로 띄우고, 가짜 자격 증명(`AWS_ACCESS_KEY_ID=test`)을 쓴다.
