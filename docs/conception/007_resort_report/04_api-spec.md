# 오늘의 설질(Resort Report) API 명세서

## 등록

`POST /api/v1/resort-reports` — 로그인 필요

```json
{ "resortId": 1, "content": "아테나 상단 단단하고 엣지 잘 잡힙니다." }
```

성공: `201 Created`

```json
{
  "reportId": 101,
  "resortId": 1,
  "resortName": "하이원",
  "resortCode": "HIGH1",
  "authorNickname": "눈꽃라이더",
  "content": "아테나 상단 단단하고 엣지 잘 잡힙니다.",
  "createdAt": "2026-12-06T15:30:00+09:00",
  "canDelete": true
}
```

실패: `401 AUTH_001`, `404 RESORT_001`, `400 REPORT_001`, `429 REPORT_003`, `403 REPORT_004`.

## 오늘 목록

`GET /api/v1/resort-reports/today?resortId=1&page=1&size=20` — 공개

- `resortId`: 선택
- `page`: 기본 1, 범위 1~100
- `size`: 기본 20, 범위 1~100

```json
{
  "content": [
    {
      "reportId": 101,
      "resortId": 1,
      "resortName": "하이원",
      "resortCode": "HIGH1",
      "authorNickname": "눈꽃라이더",
      "content": "아테나 상단 단단하고 엣지 잘 잡힙니다.",
      "createdAt": "2026-12-06T15:30:00+09:00",
      "canDelete": false
    }
  ],
  "pageInfo": {
    "page": 1,
    "totalPages": 1,
    "totalElements": 1,
    "nextCursor": null,
    "hasNext": false,
    "pageSize": 20
  }
}
```

실패: `400 REPORT_005`, `400 REPORT_006`.

## 삭제

`DELETE /api/v1/resort-reports/{reportId}` — 작성자 또는 관리자, 성공 `204 No Content`.

실패: `401 AUTH_001`, `403 AUTH_002`, `404 REPORT_002`.

## 관리자 상태 변경

`PATCH /api/v1/admin/resort-reports/{reportId}/moderation-status` — 관리자 전용

```json
{ "moderationStatus": "HIDDEN" }
```

허용값은 `NORMAL`, `HIDDEN`, `BLOCKED`이며 성공 시 `204 No Content`다. 삭제는 일반 삭제 API를 사용한다.
