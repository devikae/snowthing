# 카풀 API 명세

## 장소 검색

`GET /api/v1/carpools/places?query={주소 또는 장소명}`

- 카카오 로컬 API를 서버에서 호출한다.
- 최대 5개의 장소명·주소·경도·위도를 반환한다.
- 카카오 REST API 키는 브라우저에 전달하지 않는다.
- 검색어는 공백 제거 후 2~100자로 제한하고, 동일 IP는 분당 30회까지만 호출할 수 있다.

## 자동 비용 미리보기

`POST /api/v1/carpools/auto-preview`

```json
{
  "originLongitude": 127.123,
  "originLatitude": 37.539,
  "destinationResortId": 1,
  "tripType": "ROUND_TRIP",
  "fuelType": "DIESEL",
  "fuelEfficiency": 14.5,
  "passengerCapacity": 3,
  "routeSource": "KAKAO",
  "manualDistanceKm": null,
  "manualTollFee": null,
  "manualFuelPrice": null
}
```

백엔드는 사용자가 선택한 출발 좌표와 `resort` 마스터의 검증된 스키장 진입 좌표로 카카오 모빌리티에서 거리·통행료를 조회하고, 오피넷에서 해당 유종의 전국 평균가격을 조회한다. 리조트 이름을 매번 장소 검색해 첫 결과를 쓰지 않는다. 왕복은 출발지→리조트와 리조트→출발지를 각각 조회하고 두 결과를 합산한다. 일방통행·추천 경로·통행료 차이 때문에 편도 결과를 단순히 두 배로 만들지 않는다. 동일 IP는 미리보기·등록·수정을 합쳐 분당 12회까지만 외부 계산을 호출할 수 있다.

```json
{
  "distanceKm": 380.0,
  "tollFee": 24000,
  "durationSeconds": 21600,
  "fuelPrice": 1843.2,
  "fuelPriceTradeDate": "2026-10-03",
  "fuelPriceObservedAt": "2026-10-03T12:00:00",
  "fuelPriceSource": "OPINET",
  "estimatedFuelCost": 48307,
  "estimatedTotalCost": 72307,
  "estimatedCostPerPerson": 18077,
  "totalPassengerCount": 4,
  "routeSource": "KAKAO",
  "routeCalculatedAt": "2026-10-03T12:00:00"
}
```

오피넷과 유효 캐시를 모두 사용할 수 없으면 `CARPOOL_009`를 반환한다. 사용자는 `manualFuelPrice`에 1,000~3,000원/L 값을 넣어 다시 계산할 수 있다. 카카오 지도·길찾기 장애 시에는 `routeSource=MANUAL`, 출발 좌표는 `null`, `manualDistanceKm=1~2000`, `manualTollFee=0~20000`으로 요청한다. 왕복의 수동 거리는 왕복 총거리다. 서버는 입력된 총액을 받지 않고 거리·연비·유가·통행료로 다시 계산한다.

## 모집글 생성

`POST /api/v1/carpools`

인증된 회원만 호출할 수 있다. 브라우저가 유가·거리·통행료·계산 결과를 보내지 않는다. 백엔드는 저장 직전에 자동 계산을 다시 수행하고 그 결과를 `CarpoolDetail`에 저장한다.

```json
{
  "title": "서울에서 휘닉스파크 왕복",
  "content": "장비 두 개까지 적재 가능합니다.",
  "departureRegion": "서울 강동구 천호동",
  "meetingPlace": "천호역",
  "departureLatitude": 37.538,
  "departureLongitude": 127.123,
  "destinationResortId": 1,
  "tripType": "ROUND_TRIP",
  "departureAt": "2026-12-28T05:00:00",
  "returnAt": "2026-12-28T18:00:00",
  "passengerCapacity": 3,
  "fuelType": "DIESEL",
  "fuelEfficiency": 14.5,
  "costMode": "AUTO",
  "manualCostPerPerson": null,
  "equipmentLoadAvailable": true,
  "contactInfo": "https://open.kakao.com/o/example",
  "contactPublicToGuest": true,
  "routeSource": "KAKAO",
  "manualDistanceKm": null,
  "manualTollFee": null,
  "manualFuelPrice": null
}
```

`costMode`가 `AUTO`이면 외부 경로·유가를 기준으로 1인 비용을 계산한다. `MANUAL`이면 모집자가 `manualCostPerPerson`에 입력한 1인 금액을 게시글의 기준 금액으로 저장한다.

## 모집글 수정·삭제

- `PUT /api/v1/carpools/{publicId}`
- `DELETE /api/v1/carpools/{publicId}`

수정 요청은 생성 요청과 같은 입력 계약을 사용한다. 작성자·관리자 권한을 외부 API 호출 전에 먼저 확인하고, 외부 경로·유가를 서버에서 다시 계산한 뒤 기존 `Post`와 `CarpoolDetail`을 한 트랜잭션에서 변경한다. 삭제는 `Post`를 소프트 삭제하며 상세 데이터는 감사·복구 가능성을 위해 물리 삭제하지 않는다.

## 목록·상세

- `GET /api/v1/carpools?page=0&size=20`
- `GET /api/v1/carpools/{publicId}`

목록과 상세는 비회원도 조회할 수 있다. 연락처는 `contactPublicToGuest=true`이거나 작성자·관리자일 때만 반환한다. 출발 좌표는 작성자·관리자의 수정 화면에만 반환하고 일반 회원·비회원 응답에는 `null`로 내려 집결지 정밀 좌표를 불필요하게 공개하지 않는다.

목록은 0부터 시작하는 `page`와 `size`를 받고, 프런트에서는 사용자에게 1부터 시작하는 페이지 번호로 변환한다. `page`는 `0~99`, `size`는 `1~100`만 허용해 깊은 OFFSET 조회를 제한한다. 목록에는 출발 시각이 현재보다 같거나 늦은 글만 포함하며 정렬은 `createdAt DESC, id DESC` 최신순을 유지한다. 응답은 제목·경로·출발 시각·모집 인원·1인 비용·장비 적재 여부만 담는 목록 전용 DTO이므로 본문과 연락처를 일괄 노출하지 않는다. `Post`, 작성자, 리조트는 EntityGraph로 함께 조회해 목록 N+1 쿼리를 방지한다. 모집 진행 상태는 관리하지 않으므로 상태 필드와 `모집중` 표시는 제공하지 않는다.

## 댓글

카풀도 `Post`를 기준으로 저장되므로 기존 댓글 API를 그대로 사용한다.

- `GET/POST /api/v1/posts/{publicId}/comments`
- `GET /api/v1/comments/{commentId}/replies`
- `PUT/DELETE /api/v1/comments/{commentId}`

댓글과 대댓글 작성은 로그인 회원만 가능하며, 루트 댓글은 커서 페이징하고 각 댓글에는 대댓글 5건을 미리 표시한다.

## 오류

- `400 CARPOOL_001`: 편도·왕복 일정 오류
- `400 CARPOOL_002`: 인원·연비·좌표 등 입력 오류
- `400 CARPOOL_004`: 장소 검색 또는 길찾기 실패. 수동 거리·통행료 입력으로 우회하지 않고 재시도한다.
- `503 CARPOOL_005`: 오피넷 응답 실패
- `503 CARPOOL_006`: 카카오 API 키 미설정
- `503 CARPOOL_007`: 오피넷 API 키 미설정
- `429 CARPOOL_008`: 장소 검색 또는 비용 미리보기 호출 한도 초과
- `503 CARPOOL_009`: 오피넷과 유효 캐시를 사용할 수 없어 사용자 유가 입력 필요

구형 `POST /cost-preview`는 브라우저가 거리와 유가를 결정하는 구조이므로 제거한다.
