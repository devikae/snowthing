# Snowthing 실시간 라이브톡 STOMP 메시지 규격서 (Live Chat Specification)

- **문서 번호**: `SPEC-WS-LIVECHAT`
- **상태**: `Accepted`
- **최종 수정일**: 2026-09-20
- **대상 패키지**: `com.ikae.snowthing.domain.chat`, `com.ikae.snowthing.global.config.websocket`
- **기반 문서**: `docs/conception/live_chat/ADR-101 라이브챗 기술결정.md`, `docs/conception/live_chat/chat_policy.md`, `media_1789831353588.png`

---

## 1. 통신 프로토콜 및 연결 규약

- **프로토콜**: WebSocket + STOMP (over TLS/WSS)
- **엔드포인트 URL**: `https://snowthing.org/ws-chat` (SockJS fallback: `/ws-chat`)
- **Heartbeat**: 클라이언트 `[10000, 10000]`, 서버 `[10000, 10000]` (10초 주기 PING/PONG)
- **세션 인증**: HTTP 핸드셰이크 시 브라우저의 `JSESSIONID` 세션 쿠키를 자동 연동한다.
  - 로그인 회원: Spring Security `Principal` 바인딩 완료 (`CustomUserDetails` 또는 `member_id`).
  - 비로그인 방문자: Anonymous 상태로 연결 허용 (수신 전용).

---

## 2. 채널 및 목적지(Destination) 요약

| 구분 | 목적지 (Destination) | 전송 방향 | 권한 | 설명 |
| :--- | :--- | :---: | :---: | :--- |
| **라이브톡 대화 구독** | `/sub/chat/main` | 서버 ➔ 클라이언트 | 전체 (비로그인 포함) | 메인 화면 실시간 라이브톡 수신 |
| **라이브톡 대화 발신** | `/pub/chat/messages` | 클라이언트 ➔ 서버 | **로그인 회원 필수** | 라이브톡 메시지 전송 |
| **개인 에러 수신** | `/user/queue/errors` | 서버 ➔ 클라이언트 | 로그인 회원 | 도배, 금칙어, 외부링크 차단 에러 수신 |

---

## 3. 프레임 상세 명세

### 3.1. 웹소켓 연결 (`CONNECT`)

#### Client Frame
```http
CONNECT
accept-version:1.1,1.2
heart-beat:10000,10000

^@
```

#### Server Response (`CONNECTED`)
```http
CONNECTED
version:1.2
heart-beat:10000,10000

^@
```

---

### 3.2. 라이브톡 구독 (`SUBSCRIBE`)

사용자는 메인 화면 진입 시 `/sub/chat/main` 채널을 구독한다.

#### Client Frame
```http
SUBSCRIBE
id:sub-0
destination:/sub/chat/main

^@
```

---

### 3.3. 메시지 발신 (`SEND`)

로그인된 회원이 라이브톡으로 메시지를 발송한다. 비로그인 유저가 발송할 경우 서버에서 연결 인터셉터 차단 및 `CHAT_UNAUTHORIZED` 에러를 반환한다.

#### Client Frame (리조트 제보 전송 예시)
```http
SEND
destination:/pub/chat/messages
content-type:application/json

{
  "resortTag": "PHOENIX",
  "content": "챔피언 상단 시야 확 트였고 설질 짱짱하게 먹습니다! 리프트 대기 3분 컷 개꿀"
}
^@
```

#### Client Frame (일반 잡담 전송 예시)
```http
SEND
destination:/pub/chat/messages
content-type:application/json

{
  "resortTag": null,
  "content": "오늘 다들 출격하시나요? 날씨가 많이 춥네요."
}
^@
```

#### Request Payload Specification
| 필드명 | 타입 | 필수 여부 | 제약 조건 | 설명 |
| :--- | :---: | :---: | :--- | :--- |
| `resortTag` | String | 선택 (Nullable) | `PHOENIX`, `VIVALDI`, `HIGH1`, `YONGPYONG`, `WELLI_HILLI`, `ETC` 중 1개 또는 `null`/빈값 | 슬로프 제보 대상 리조트 코드. 미선택 시 일반 잡담 |
| `content` | String | **필수** | 1자 이상 100자 이하, 공백만으로 구성 불가 | 대화 본문 |

---

### 3.4. 라이브톡 브로드캐스팅 수신 (`MESSAGE`)

서버가 유효성 검사와 컴플라이언스 필터링을 거친 후 `/sub/chat/main`을 구독 중인 모든 사용자에게 전송하는 페이로드이다. 본문은 원문 문자열이며 React JSX 텍스트 렌더링 경계에서 이스케이프한다.

#### Server Broadcast Frame
```http
MESSAGE
subscription:sub-0
message-id:msg-0191b2c3-4d5e-7f8a-9b0c-1d2e3f4a5b6c
destination:/sub/chat/main
content-type:application/json

{
  "messageId": "0191b2c3-4d5e-7f8a-9b0c-1d2e3f4a5b6c",
  "sender": {
    "publicId": "0191a1b2-c3d4-e5f6-a7b8-c9d0e1f2a3b4",
    "nickname": "몽블랑카버"
  },
  "resortTag": "PHOENIX",
  "content": "챔피언 상단 시야 확 트였고 설질 짱짱하게 먹습니다! 리프트 대기 3분 컷 개꿀",
  "sentAt": "2026-09-20T00:20:00.120"
}
^@
```

#### Broadcast Payload Specification
| 필드명 | 타입 | 설명 |
| :--- | :---: | :--- |
| `messageId` | String | 메시지 고유 식별자 (UUID v7 기반, 프론트엔드 React `key` 및 DOM 제어용) |
| `sender.publicId` | String | 보낸 회원 외부 식별자 (클라이언트의 본인/타인 메시지 좌우 배치 판별에 사용) |
| `sender.nickname` | String | 보낸 회원 닉네임 |
| `resortTag` | String (Nullable) | 리조트 코드 (`PHOENIX` 등). 잡담인 경우 `null` |
| `content` | String | 앞뒤 공백을 제거한 원문. 프론트엔드는 JSX 텍스트로만 렌더링하며 HTML로 재해석하지 않음 |
| `sentAt` | String | 발송 일시 (ISO-8601, `YYYY-MM-DDTHH:mm:ss.SSS`) |

---

### 3.5. 프론트엔드 메신저형 대화 버블(Chat Bubble) 렌더링 규격

수신된 메시지의 `sender.publicId`와 현재 로그인한 유저의 `currentMember.publicId`를 대조하여 렌더링을 분기한다.

```typescript
const isMine = Boolean(currentMember && message.sender.publicId === currentMember.publicId);
```

1. **`isMine === true` (내 메시지)**:
   - 배치: 우측 정렬 (`justify-end`, `ml-auto`)
   - 스타일: 연한 하늘색 말풍선 (`bg-sky-50 text-sky-950 border border-sky-100 rounded-2xl rounded-tr-sm`)
   - 프로필/닉네임: 생략하여 본인 발언임을 직관적으로 표현
   - 시간: 말풍선 좌측 하단에 상대 시간(`방금`, `N분 전`) 표기
2. **`isMine === false` (타인 메시지)**:
   - 배치: 좌측 정렬 (`justify-start`, `mr-auto`)
   - 스타일: 깔끔한 흰색 말풍선 (`bg-white text-slate-800 border border-slate-200 rounded-2xl rounded-tl-sm`)
   - 아바타: `resortTag`가 존재할 경우 해당 리조트 2글자 약칭(`휘팍`, `비발`, `용평`, `하이`, `웰팍`, `기타`) 원형 아바타 노출. 잡담(`resortTag == null`)일 경우 기본 사용자 아이콘 노출
   - 상단 헤더: `닉네임` + 리조트 컬러 뱃지 (`휘닉스`, `비발디` 등, 잡담 시 뱃지 생략)
   - 시간: 말풍선 우측 하단에 상대 시간 표기

---

### 3.6. 개인 에러 알림 수신 (`/user/queue/errors`)

발신자가 도배 정책을 위반하거나 외부 링크를 전송하여 발송이 거부되었을 때, 전체 채널이 아닌 **해당 발신자에게만 단독으로 전송되는 STOMP 에러 메시지**이다.

#### Server Frame
```http
MESSAGE
destination:/user/queue/errors
content-type:application/json

{
  "code": "CHAT_001",
  "message": "외부 링크 및 메신저 연락처는 전송할 수 없습니다.",
  "timestamp": "2026-09-20T00:20:01.005"
}
^@
```

---

## 4. 에러 코드 매트릭스

`com.ikae.snowthing.global.error.ErrorCode`에 신설할 라이브톡 전용 비즈니스 에러 코드이다.

| 에러 코드 | Enum 상수명 | HTTP 대응 | 설명 및 안내 메시지 |
| :--- | :--- | :---: | :--- |
| **`CHAT_001`** | `CHAT_EXTERNAL_LINK_FORBIDDEN` | 400 | 외부 링크 및 메신저 연락처는 전송할 수 없습니다. |
| **`CHAT_002`** | `CHAT_DUPLICATE_MESSAGE` | 429 | 동일한 메시지를 연속으로 보낼 수 없습니다 (5초간 대기). |
| **`CHAT_003`** | `CHAT_BURST_RATE_LIMIT` | 429 | 메시지 전송 속도가 너무 빠릅니다. 잠시 후 다시 시도해 주세요. |
| **`CHAT_004`** | `CHAT_MESSAGE_EMPTY` | 400 | 공백 메시지는 전송할 수 없습니다. |
| **`CHAT_005`** | `CHAT_MESSAGE_TOO_LONG` | 400 | 메시지는 최대 100자까지 작성할 수 있습니다. |
| **`CHAT_006`** | `CHAT_UNAUTHORIZED` | 401 | 로그인 후 라이브톡에 참여할 수 있습니다. |
| **`CHAT_007`** | `CHAT_KILL_SWITCH_ACTIVE` | 503 | 현재 라이브톡 점검 중입니다. |

---

## 5. 통신사실확인자료 감사 로그 규격

- **로그 목적**: 통신비밀보호법 제15조의2(3개월 보관 의무) 준수 및 수사기관 영장 집행 대응.
- **저장 위치**: `/var/log/snowthing/chat_audit.log` (배포 환경)
- **보관 정책**: Logback Daily Rolling 정책 적용, 90일 경과 파일 자동 삭제 (`maxHistory = 90`).
- **포맷**: 탭 구분(TSV) 포맷으로 대화 본문을 제외한 메타데이터만 단방향 비동기 적재.

```text
# 포맷 규격
[timestamp]\t[member_id]\t[client_ip]\t[channel]

# 기록 예시
2026-09-20T00:20:00.123+09:00\t102\t121.135.24.56\tMAIN_CHAT
2026-09-20T00:20:01.890+09:00\t88\t211.202.11.90\tMAIN_CHAT
```
