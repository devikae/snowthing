# 카풀 API 명세

## 1. 생성

`POST /api/v1/carpools`  
인증: 로그인 회원

요청은 게시글 기본 정보와 카풀 상세 정보를 함께 받습니다. 서비스는 `Post`와 `CarpoolDetail`을 하나의 트랜잭션에서 저장합니다.

```json
{
  "title": "12월 28일 서울 출발 휘닉스파크",
  "content": "장비 2개까지 가능합니다.",
  "departureRegion": "서울 강동구",
  "meetingPlace": "천호역 인근",
  "departureLatitude": 37.539,
  "departureLongitude": 127.123,
  "destinationResortId": 1,
  "tripType": "ROUND_TRIP",
  "departureAt": "2026-12-28T05:00:00",
  "returnAt": "2026-12-28T18:00:00",
  "passengerCapacity": 3,
  "fuelType": "DIESEL",
  "fuelEfficiency": 14.5,
  "routeDistanceKm": 190.0,
  "routeTollFee": 12000,
  "contactInfo": "https://open.kakao.com/o/example",
  "contactPublicToGuest": true
}
```

거리·통행료를 생략하면 서버가 카카오모빌리티 경로 API를 시도합니다. 외부 API 실패 시 클라이언트가 제공한 수동 값으로 등록할 수 있습니다.

## 2. 목록·상세

- `GET /api/v1/carpools`: 기존 게시글 목록 규칙과 동일한 페이징·검색
- `GET /api/v1/carpools/{publicId}`: 게시글과 카풀 상세를 함께 반환

비회원 상세 응답에는 `contactPublicToGuest=true`일 때만 `contactInfo`를 포함합니다. 로그인 회원에게는 공개 동의가 false여도 연락처를 보여주지 않습니다.

## 3. 수정·삭제

- `PUT /api/v1/carpools/{publicId}`: 작성자 본인만
- `DELETE /api/v1/carpools/{publicId}`: 기존 게시글 삭제 정책 재사용

수정 시 유가·경로를 다시 계산할 수 있으며, 재계산한 스냅샷과 시각을 함께 교체합니다. 외부 API 실패 시 기존 저장값을 유지하고 사용자에게 재시도 또는 수동 입력을 안내합니다.

## 4. 계산 미리보기

`POST /api/v1/carpools/cost-preview`는 저장하지 않고 경로·유가·비용을 미리 계산합니다. 로그인 여부와 무관하게 호출할 수 있지만 API 호출량 제한을 적용합니다.

```json
{
  "fuelType": "GASOLINE",
  "fuelEfficiency": 12.0,
  "distanceKm": 200.0,
  "tollFee": 10000,
  "passengerCapacity": 3
}
```

## 5. 공통 오류

- `401 AUTH_REQUIRED`: 로그인 필요
- `400 INVALID_CARPOOL_SCHEDULE`: 왕복 시각 오류
- `400 INVALID_CARPOOL_VALUE`: 인원·연비·거리·통행료 오류
- `400 CARPOOL_DETAIL_REQUIRED`: 카풀 상세 누락
- `400 ROUTE_CALCULATION_FAILED`: 자동 경로 계산 실패
- `503 FUEL_PRICE_PROVIDER_UNAVAILABLE`: 유가 제공자 장애로 자동 조회 불가
- `403 ACCESS_DENIED`: 작성자 외 수정·삭제
- `404 POST_NOT_FOUND`: 게시글 없음
