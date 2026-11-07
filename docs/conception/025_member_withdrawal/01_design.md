# 회원 탈퇴 설계 (IKA-39)

## 결정

| 항목 | 결정 |
|---|---|
| 진입 | 내 정보 수정 화면(`/profile`) 맨 아래 |
| 본인 확인 | 비밀번호 회원은 비밀번호, 소셜 회원은 `탈퇴` 직접 입력 |
| 글과 댓글 | 남긴다. 작성자는 `탈퇴한 회원`으로 표시 |
| 개인정보 | 즉시 삭제 또는 익명화 (아래 표) |
| 재가입 | 같은 이메일 30일 차단. HMAC 해시만 보관, 30일 뒤 스케줄러가 삭제. 소셜도 차단 |
| 정지 회원 | 탈퇴 불가. 재가입으로 정지를 피하지 못하게 한다 |
| 세션 | 커밋 뒤 `MemberSessionRegistry.invalidateAll`. API 토큰 폐기 |
| AI 멤버 | 탈퇴 대상 아님 |
| 범위 밖 | 토큰 자동 로그인 변경, 관리자 강제 탈퇴 |

## API

`POST /api/v1/members/me/withdrawal` (세션 인증, CSRF, 회원별 15분 10회)

```json
{ "password": "비밀번호 회원", "confirmation": "소셜 회원은 탈퇴" }
```

| 결과 | 상태 | error |
|---|---|---|
| 성공 | 204 | |
| 비밀번호 틀림 | 400 | `CURRENT_PASSWORD_MISMATCH` |
| 확인 문구 틀림 | 400 | `WITHDRAWAL_CONFIRMATION_MISMATCH` |
| 정지 회원 | 403 | `ACCOUNT_SUSPENDED` |
| 이미 탈퇴 (동시 요청) | 403 | `ACCOUNT_WITHDRAWN` |
| AI 멤버, 비밀번호도 소셜 연결도 없는 계정 | 403 | `ACCESS_DENIED` |
| 요청 한도 초과 | 429 | `PROFILE_ACTION_LIMIT` |

재가입 차단 응답은 409 `WITHDRAWN_EMAIL_RESTRICTED`다. 가입 인증 메일 요청, 가입 저장, 소셜 가입에서 확인한다. 이메일 중복 확인 API는 `available: false`만 돌려주고 차단 이유를 구분하지 않는다.

## 개인정보 처리 표

| 테이블 | 컬럼 | 처리 |
|---|---|---|
| `member` | `email` | `withdrawn-{id}@withdrawn.invalid`로 교체 |
| `member` | `nickname` | `withdrawn_{id}`로 교체. 표시는 `탈퇴한 회원` |
| `member` | `password`, `bio`, `departure_region`, `email_verified_at`, `crew_id`, `crew_role` | NULL |
| `member` | `profile_image_url` | NULL. 파일은 `profile_image_cleanup`으로 넘겨 삭제 |
| `member` | `public_id`, `role`, `created_at` | 유지. 식별 정보가 아니고 FK와 차단 해제에 쓴다 |
| `member_social_account` | `provider_user_id`, `provider_email` | 행 삭제 |
| `member_resort`, `member_riding_style` | 취향 | 행 삭제 |
| `member_registration_consent` | 동의 기록 | 행 삭제 |
| `member_marketing_consent_history` | 광고 수신 변경 이력 | 행 삭제 |
| `member_block` | 내가 건 차단 | 행 삭제. 나를 차단한 행은 상대가 풀 수 있게 유지 |
| `email_verification` | `email`, `request_ip` | 행 삭제 |
| `api_token` | 토큰 | `revoked_at` 기록 |
| `notification` | 내가 받은 알림 | 행 삭제 |
| `notification` | 남이 받은 알림의 `actor_name` | `탈퇴한 회원`으로 교체 |
| `post`, `comment` | `writer_ip` | IPv4는 앞 두 자리만 남기고 나머지는 `0.0.0.0` |
| `post_reaction` | `writer_ip` | NULL |
| `market_listing` | `contact` | `탈퇴한 회원`으로 교체(NOT NULL, CHECK 때문), 응답에서는 null. `trade_status`는 `SOLD` |
| `carpool_detail` | `contact_info` | NULL, 비회원 공개 해제 |
| `post_image_upload` | `owner_email` | 익명 이메일로 교체 |
| 메모리 `ChatRecentHistoryBuffer` | 닉네임, 대화 | 해당 회원 메시지 제거 |

### 남는 것

| 위치 | 내용 | 이유 |
|---|---|---|
| `post`, `comment`, `resort_report` | 본문 | 설계 결정. 본문 안에 직접 쓴 개인정보는 자동으로 지우지 못한다 |
| `direct_message` | 쪽지 본문 | `022_direct_message` 설계대로 상대 화면에 남긴다 |
| `notification.preview` | 남이 받은 알림의 댓글 미리보기 | 알림 보관 기간이 지나면 지워진다 |
| `answer_draft`, `answer_draft_audit` | 회원 ID | 익명화된 회원을 가리킨다 |
| `idempotency_record` | `member:{id}` | 보관 기간이 지나면 지워진다 |
| `request_budget_window` | 이메일 SHA-256이 든 키 | 한도 창이 끝나면 지워진다 |
| `withdrawn_email` | 이메일 HMAC | 30일 보관 |
| 애플리케이션 로그, 백업 | 접속 기록 등 | 이 작업 범위 밖. 보관 기간은 처리방침에서 정한다 |

`character_group`, `character_participant`는 회원과 연결되어 있지 않다.

## 왜 이렇게 했나

- **FK 유지**: 글과 댓글의 `member_id`를 NULL로 바꾸면 비회원 글과 구분이 안 되고 표시 코드도 갈린다(`알 수 없음`, `익명보더`). 회원 행은 익명화해 남기고 표시 단계에서 `Member.isWithdrawn()`으로 가린다.
- **HMAC**: 이메일은 사전 대입이 쉬워 단순 SHA-256은 되돌릴 수 있다. `snowthing.email.verification.secret`으로 HMAC을 만든다. 비밀값을 바꾸면 그때까지의 차단이 풀리는 대가가 있다.
- **소셜 차단**: 네이버 회원의 `member.email`은 제공자 ID로 만든 내부 값이라 이메일이 바뀌어도 같은 계정의 재가입을 막는다. 제공자 이메일도 함께 저장해 같은 이메일의 일반 가입을 막는다.
- **만료 판단**: `expires_at`과 현재 시각을 비교한다. 스케줄러가 늦어도 30일이 지나면 풀린다.
- **판매글 닫기**: 연락처가 없는 판매글이 판매 중으로 남으면 구매자가 연락할 길이 없다.

## 처리방침

`shared/registration-policies/draft-2026-11-01-v1.json`의 "보관과 삭제" 문단은 회원정보 보관 기간을 "확인 중"이라고 적고 있어 이 동작과 충돌하지는 않지만 탈퇴 처리를 설명하지 않는다. 문구는 사용자 결정 대기 항목이라 이 작업에서 바꾸지 않았다.

## 동시성과 한계

- 회원 행을 `FOR UPDATE`로 잠그고 시작한다. 동시 탈퇴는 하나만 성공한다. 프로필 수정과 사진 교체는 옛 이메일로 찾지 못해 실패한다.
- 차단 해시와 익명화가 같은 트랜잭션이라 재가입이 그 사이에 끼어들 수 없다.
- 세션 끊기는 커밋 뒤다. 로그인이 탈퇴 커밋 직전에 인증을 끝내고 `invalidateAll` 뒤에 세션을 등록하면 그 세션은 남는다. 이메일로 회원을 찾는 API는 실패하지만 ID로 쓰는 API는 세션이 끝날 때까지 통과할 수 있다. 비밀번호 변경에도 있는 틈이고 범위 밖이라 그대로 둔다.
- `notification.actor_name`, `post_image_upload.owner_email`은 인덱스가 없어 UPDATE가 표를 훑으며 행을 잠근다. 탈퇴가 드물고 표가 작아 받아들인다.
- `actor_name`은 탈퇴 시점 닉네임으로 찾으므로 그 전에 쓰던 닉네임이 찍힌 알림은 보관 기간이 지날 때까지 남는다.

## 마이그레이션

`database/production/039_migration_withdrawn_email.sql`. 새 테이블 하나다. 앱 교체 전에 실행해도 옛 앱은 이 테이블을 쓰지 않는다. 다른 테이블과 같이 `utf8mb4_unicode_ci`다.
