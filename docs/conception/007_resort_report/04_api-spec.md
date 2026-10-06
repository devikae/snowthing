# 오늘의 설질(Resort Report) API 명세서

## 1. 개요
오늘의 설질 제보 등록 및 당일 제보 목록 조회 엔드포인트 명세이다.

---

## 2. API 상세

### 2.1 오늘의 설질 제보 등록
- **URI**: `POST /api/v1/resort-reports`
- **인증**: 필요 (인증된 회원 세션 쿠키 또는 인증 토큰)
- **Content-Type**: `application/json`

#### 요청 Body
```json
{
  "resortId": 1,
  "content": "하이원 아테나 슬로프 현재 설질 뽀송하고 대기줄 거의 없습니다!"
}
```

#### 필드 제약사항
- `resortId`: Long, 필수
- `content`: String, 필수, 1자 이상 100자 이하

#### 응답
- **성공 (201 Created)**
```json
{
  "reportId": 101,
  "resortId": 1,
  "resortName": "하이원",
  "resortCode": "HIGH1",
  "authorNickname": "눈꽃라이더",
  "content": "하이원 아테나 슬로프 현재 설질 뽀송하고 대기줄 거의 없습니다!",
  "createdAt": "2026-10-06T18:45:00"
}
```

- **실패 응답**:
  - `400 Bad Request`: `{"code": "INVALID_RESORT_REPORT_CONTENT", "message": "설질 제보 내용은 1자 이상 100자 이하여야 합니다."}`
  - `401 Unauthorized`: 로그인 필요
  - `404 Not Found`: `{"code": "RESORT_NOT_FOUND", "message": "리조트를 찾을 수 없습니다."}`

---

### 2.2 오늘의 설질 제보 목록 조회
- **URI**: `GET /api/v1/resort-reports/today`
- **인증**: 불필요 (Public)
- **Query Parameters**:
  - `resortId` (Long, Optional): 특정 리조트 ID로 필터링 (미지정 시 전체 리조트)
  - `limit` (Integer, Optional, 기본값 20, 최대 100): 조회 건수 제한

#### 응답
- **성공 (200 OK)**
```json
[
  {
    "reportId": 101,
    "resortId": 1,
    "resortName": "하이원",
    "resortCode": "HIGH1",
    "authorNickname": "눈꽃라이더",
    "content": "하이원 아테나 슬로프 현재 설질 뽀송하고 대기줄 거의 없습니다!",
    "createdAt": "2026-10-06T18:45:00"
  },
  {
    "reportId": 100,
    "resortId": 3,
    "resortName": "휘닉스파크",
    "resortCode": "PHOENIX",
    "authorNickname": "스노우맨",
    "content": "휘팍 챔피언 약간 아이스 있어요 조심하세요",
    "createdAt": "2026-10-06T17:30:12"
  }
]
```
