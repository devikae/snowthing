# 쪽지

## 배경

- 회원끼리 1:1로 연락할 수단이 없다. 라이브톡은 전체 채팅이라 개인 연락에 맞지 않는다.
- 중고장터는 쪽지 도메인을 설계한 뒤 "쪽지 보내기" 버튼을 붙이기로 미뤄 두었다 ([07_frontend-spec.md](../002_used_market/07_frontend-spec.md), [ADR-201](../002_used_market/ADR-201%20post-extension.md)).
- 보더명함, 같이타요, 강습매칭도 매칭 뒤 쪽지로 연결하는 흐름을 전제로 한다 ([project.md](../../project/project.md)).

## 요구사항

- 로그인 회원이 다른 회원에게 1:1 쪽지를 보낸다.
- 받은 쪽지함과 보낸 쪽지함을 따로 본다. 쪽지를 열면 읽음 처리한다.
- 답장은 원문의 발신자에게 새 쪽지를 보내는 것이다.
- 쪽지는 게시글과 댓글의 작성자 닉네임 메뉴, 중고장터 판매글 상세에서 보낸다.
- 판매글에서 보낸 쪽지는 판매글을 참조해 어떤 물건 이야기인지 양쪽이 안다.
- 회원을 차단하면 서로 쪽지를 보낼 수 없다.
- 헤더에 안 읽은 쪽지 수를 표시한다.

## 범위

| 넣는 것 | 빼는 것 |
|---|---|
| 1:1 쪽지 보내기, 받은함, 보낸함, 상세, 답장 | 대화방형 스레드, 실시간 푸시 |
| 작성자 닉네임 메뉴와 중고장터 판매글에서 보내기 | 공개 프로필 화면 (보더명함에서 다룬다) |
| 판매글 참조 | 카풀, 게시글 같은 범용 출처 연결 |
| 회원 차단, 발송 제한 | 쪽지 신고 (신고 도메인과 함께 설계한다) |
| 헤더 안 읽은 수 배지 (폴링) | 알림 도메인 |

## 구현 방식

편지함형으로 만든다. 쪽지 한 통이 행 하나이고, 발신자와 수신자가 각자 자기 쪽에서만 지운다.

검토한 대안은 다음과 같다.

- 대화방형(DM 스레드): 상대별 대화방에 말풍선이 쌓인다. 대화방, 참여자, 마지막 읽은 위치 테이블이 더 필요하고 라이브톡과 역할이 겹친다. 실시간 반영 요구로 이어지기 쉬운데 Redis 같은 브로커 도입은 보류 상태다.
- 프로필에서만 보내기: 판매글 연결을 나중에 마이그레이션으로 추가해야 하고 중고장터의 미뤄 둔 요구가 남는다.
- 범용 출처(`source_type`, `source_id`): 확장성은 좋지만 FK를 걸 수 없어 정합성을 애플리케이션이 책임진다. 지금 출처는 판매글 하나뿐이다.

## 데이터 모델

마이그레이션 `database/production/034_migration_direct_message.sql` (032, 033은 함께 진행한 IKA-21, IKA-22가 써서 034로 바꿨다).

### direct_message

| 컬럼 | 타입 | 설명 |
|---|---|---|
| direct_message_id | BIGINT PK | |
| sender_id | BIGINT NOT NULL | FK member |
| receiver_id | BIGINT NOT NULL | FK member |
| market_listing_id | BIGINT NULL | FK market_listing, 판매글에서 보낸 경우 |
| content | VARCHAR(1000) NOT NULL | |
| read_at | DATETIME(6) NULL | NULL이면 안 읽음 |
| sender_deleted_at | DATETIME(6) NULL | 발신자 쪽 삭제 |
| receiver_deleted_at | DATETIME(6) NULL | 수신자 쪽 삭제 |
| created_at, updated_at | DATETIME(6) | BaseTimeEntity |

인덱스

- `(receiver_id, receiver_deleted_at, direct_message_id)`: 받은함 목록, 안 읽은 수
- `(sender_id, sender_deleted_at, direct_message_id)`: 보낸함 목록

### member_block

| 컬럼 | 타입 | 설명 |
|---|---|---|
| member_block_id | BIGINT PK | |
| blocker_id | BIGINT NOT NULL | FK member, 차단한 회원 |
| blocked_id | BIGINT NOT NULL | FK member, 차단당한 회원 |
| created_at | DATETIME(6) | |

- UNIQUE `(blocker_id, blocked_id)`
- 차단은 쪽지 전용이 아니다. 같이타요, 카풀에서도 재사용한다.

### 결정

- 삭제 컬럼을 양쪽으로 나눈다. 편지함형에서는 한쪽이 지워도 상대 편지함에 남아야 한다. 양쪽 모두 지운 행의 물리 삭제는 운영 단계에서 정한다.
- 답장용 `parent_id`를 두지 않는다. 답장은 새 쪽지이고 화면에서 원문을 인용한다.
- 판매글은 소프트 삭제라 FK가 깨지지 않는다. 연결된 판매글의 게시글이 삭제 상태면 "삭제된 판매글"로 표시한다.

## API

모두 로그인 필요. 회원은 `publicId`로 가리킨다.

| 메서드 | 경로 | 설명 |
|---|---|---|
| POST | `/api/v1/messages` | 보내기 `{receiverPublicId, content, marketListingPublicId?}` |
| GET | `/api/v1/messages/received?cursor=` | 받은함, `direct_message_id DESC` 커서 페이징 |
| GET | `/api/v1/messages/sent?cursor=` | 보낸함 |
| GET | `/api/v1/messages/{id}` | 상세. 수신자가 처음 열면 `read_at` 기록 |
| DELETE | `/api/v1/messages/{id}` | 내 쪽에서만 삭제 |
| GET | `/api/v1/messages/unread-count` | 헤더 배지 |
| POST | `/api/v1/members/blocks/{memberPublicId}` | 차단 |
| DELETE | `/api/v1/members/blocks/{memberPublicId}` | 차단 해제 |
| GET | `/api/v1/members/blocks` | 내 차단 목록 |

- `marketListingPublicId`는 판매글 게시글의 `publicId`다 (`findDetailByPostPublicId`). 판매글 작성자가 수신자가 아니면 거부한다.

## 규칙

| 상황 | 처리 |
|---|---|
| 자기 자신에게 보냄 | 400 |
| 수신자가 ACTIVE가 아님 (정지, 탈퇴) | 400 "보낼 수 없는 회원입니다" |
| 수신자가 AI 회원 (`AiMemberRoster.memberIds()`) | 400 "보낼 수 없는 회원입니다" |
| 수신자가 나를 차단함 | 400 "보낼 수 없는 회원입니다" (차단 사실을 드러내지 않는다) |
| 내가 수신자를 차단함 | 400 "차단한 회원에게는 보낼 수 없습니다" |
| 남의 쪽지 상세, 삭제 | 404 (존재 여부를 숨긴다) |
| 내 쪽에서 이미 삭제한 쪽지 | 404 |

- 차단 전에 받은 쪽지는 남기고 목록에서도 숨기지 않는다.
- 탈퇴 회원의 쪽지는 남기고 상대 화면에 "탈퇴한 회원"으로 표시한다.
- 발송 제한: `ContentWriteRateLimiter.Action.DIRECT_MESSAGE(Duration.ofSeconds(5), 30)`. 5초에 1건, 시간당 30건.
- 시간은 KST, 목록은 `direct_message_id DESC`로 정렬한다.

## 프론트

- 헤더 쪽지 아이콘과 안 읽은 수 배지. 페이지 이동 때와 탭이 보이는 동안 60초마다 조회한다.
- `/messages`: 받은함, 보낸함 탭, 상세, 답장.
- 게시글과 댓글의 작성자 닉네임 메뉴에 "쪽지 보내기", "차단"을 추가한다. 본인과 AI 회원에게는 숨긴다.
- 중고장터 판매글 상세에 "쪽지 보내기" 버튼. 로그인 상태이고 본인 글이 아닐 때만 보인다.
- 설정 화면에 차단 목록과 해제.

## 영향 범위

- 중고장터: 외부 연락처 필드는 유지한다 ([결정요구.md](../002_used_market/결정요구.md)).
- 성능: 안 읽은 수는 수신자 인덱스로 세는 가벼운 쿼리다. 접속자 수 대비 폴링 부하는 작다.
- 개인정보: 쪽지 본문은 관리자 화면에 노출하지 않는다. 신고 도메인이 생기면 신고된 쪽지만 열람한다.
- 알림 도메인이 생기면 배지 조회를 알림으로 옮긴다.

## 테스트

- 발송 거부 규칙 표의 경우마다 서비스 테스트
- 남의 쪽지 상세, 삭제가 404인지
- 한쪽 삭제 뒤 상대 목록에는 남는지
- 수신자가 열 때만 `read_at`이 기록되고 발신자가 열면 바뀌지 않는지
- 판매글 작성자가 아닌 수신자와 판매글 조합 거부
- 차단 중복 요청이 UNIQUE 위반 500이 아니라 멱등하게 처리되는지
