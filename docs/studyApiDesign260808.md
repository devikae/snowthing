# [Notion] REST API 설계 원칙 & 에러 응답 표준화 정리 (TIL)

> 📌 **작성 일자**: 2026년 8월 8일  
> 🏷️ **문서 목적**: 노션(Notion)에 붙여넣어 RESTful API 디자인 철학, `/api` 접두사의 의미, 비동기 API 설계, 표준 에러 응답 규격을 공부하기 위한 TIL 정리 문서

---

## 1. 💡 REST API URL 설계 철학: 왜 `POST /api/join`이 아니라 `POST /api/members`인가?

### 1.1. REST API의 기본 원칙: "URL은 명사, 행동은 HTTP 메서드"
* **HTTP 메서드 (`GET`, `POST`, `PUT`, `DELETE`)**: **행동(동사)**을 담당합니다.
  * `GET`: 가져와라 (조회)
  * `POST`: 새로 생성해라 (생성/등록)
  * `PUT`: 수정해라 (수정)
  * `DELETE`: 삭제해라 (삭제)
* **URL 주소 (`/api/members`)**: **대상(명사/자원)**을 담당합니다.

### 1.2. 회원가입의 RESTful 해석
* '회원가입'은 데이터베이스 관점에서 **"새로운 회원(`members`) 데이터 1명을 새로 생성(`POST`)하는 행위"**입니다.
* **`POST` (새로 생성해라!)** + **`/api/members` (회원들 집합에)** ➔ **회원가입!**

| 행위 | ❌ 옛날 동사 중심 방식 | ⭕ 요즘 RESTful 방식 (표준) |
| :--- | :--- | :--- |
| **회원가입** | `POST /api/join` 또는 `/api/signup` | **`POST /api/members`** |
| **회원 목록 조회** | `GET /api/getMembers` | **`GET /api/members`** |
| **회원 정보 수정** | `POST /api/updateMember` | **`PUT /api/members/{publicId}`** |
| **회원 탈퇴** | `POST /api/deleteMember` | **`DELETE /api/members/{publicId}`** |

---

## 2. 🌐 URL 주소 앞에 `/api` 접두사를 붙이는 3가지 실무적 이유

1. **"화면(HTML)" 요청과 "순수 데이터(JSON)" 요청의 명확한 구분**
   * `/members` ➔ 웹 브라우저 화면(HTML) 요청.
   * `/api/members` ➔ 백엔드 데이터(JSON) 요청임을 한눈에 파악 가능.
2. **프론트엔드(Next.js 3000포트)와 백엔드(Spring Boot 8080포트) Nginx 라우팅의 편의성**
   * Nginx 설정에서 `"주소에 /api/ 가 들어간 요청만 백엔드 포트로 전달해라"` 라고 한 줄로 라우팅 규칙 지정 가능.
3. **세션 쿠키(`JSESSIONID`) 보안 범위의 제한**
   * `Path=/api`로 지정하여 프론트엔드 화면 이동 시 쿠키 전송을 막고, 백엔드 API 요청 시에만 안전하게 쿠키를 전송하도록 범위 제한.

---

## 3. ⚡ 게시글 추천/비추천 비동기(Async) 처리 기법

### 3.1. 프론트엔드: '낙관적 업데이트 (Optimistic UI Update)'
* 유저가 추천 클릭 시, 백엔드 응답을 기다리지 않고 **0.001초 만에 화면의 숫자와 하트 색깔을 먼저 변경**.
* 백엔드 API가 에러(409 등)를 반환하면 그때 원래 숫자로 원복(`-1`).

### 3.2. 백엔드: 비동기 이벤트 처리 & Redis 버퍼링
* 백엔드는 추천 클릭 시 DB를 직접 치지 않고, **`200 OK` 응답을 즉시 끊어준 뒤 백그라운드 쓰레드(`@Async`)로 DB 업데이트 실행**.
* 대규모 트래픽 시 Redis 메모리 카운터만 즉시 올려주고 10초 주기 비동기 배치(Batch)로 DB 반영.

---

## 4. 🚨 글로벌 에러 응답 표준화 규격 (Global Error Response)

### 4.1. 에러 응답 JSON 포맷
```json
{
  "timestamp": "2026-08-08T10:20:00",
  "status": 400,
  "code": "INVALID_INPUT_VALUE",
  "message": "입력값이 유효하지 않습니다.",
  "errors": [
    {
      "field": "email",
      "value": "invalid-email-format",
      "reason": "올바른 이메일 형식이 아닙니다."
    }
  ]
}
```

### 4.2. 주요 HTTP Status Code 정리
* `400 Bad Request`: 유효성 검사 실패 (`INVALID_INPUT_VALUE`), 중복 가입 (`DUPLICATE_EMAIL`)
* `401 Unauthorized`: 비로그인 작성 시도 (`UNAUTHORIZED`), 비밀번호 불일치 (`INVALID_CREDENTIALS`)
* `403 Forbidden`: 타인의 글/댓글 수정·삭제 시도 (`ACCESS_DENIED`), 비회원 암호 오류 (`INVALID_ANON_PASSWORD`)
* `404 Not Found`: 존재하지 않는 게시글/댓글/회원 조회 (`RESOURCE_NOT_FOUND`)
* `409 Conflict`: 이미 추천/비추천 투표를 한 경우 (`ALREADY_REACTED`)
* `500 Internal Error`: 서버 내부 비즈니스 로직 예외 발생 (`INTERNAL_SERVER_ERROR`)
