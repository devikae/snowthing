# 카풀 도메인 모델

## 1. 경계

카풀은 기존 게시판 도메인의 확장 컨텍스트입니다. `Post`가 제목·본문·작성자·상태·댓글의 기준을 갖고, `CarpoolDetail`이 카풀에만 필요한 값과 계산 스냅샷을 갖습니다.

## 2. 핵심 객체

### Post

- 카테고리: `CARPOOL`
- 기존 작성자·상태·조회수·댓글 카운트 정책 재사용
- 일반 게시글과 동일한 수정·삭제 권한 적용

### CarpoolDetail

| 필드 | 의미 |
|---|---|
| `postId` | Post와 1:1 관계의 PK/FK |
| `departureRegion` | 출발 지역 표시명 |
| `meetingPlace` | 공개 가능한 집결지 표시명 |
| `departureLatitude/Longitude` | 경로 계산용 집결지 좌표 |
| `destinationResortId` | 리조트 마스터 FK |
| `tripType` | `ONE_WAY`, `ROUND_TRIP` |
| `departureAt` | 출발 예정 시각 |
| `returnAt` | 왕복 복귀 예정 시각, 편도는 NULL |
| `passengerCapacity` | 운전자 제외 모집 인원 |
| `fuelType` | `GASOLINE`, `DIESEL`, `LPG` 등 지원 연료 |
| `fuelEfficiency` | km/L 기준 차량 연비 |
| `fuelPrice` | 계산에 사용한 유가 스냅샷 |
| `fuelPriceObservedAt` | 유가 조회 시각 |
| `routeDistanceKm` | 최종 적용 거리 |
| `routeTollFee` | 최종 적용 통행료 |
| `estimatedFuelCost` | 예상 연료비 |
| `estimatedTotalCost` | 연료비+통행료 |
| `estimatedCostPerPerson` | 운전자 포함 예상 1인 부담금 |
| `routeSource` | `KAKAO`, `MANUAL` |
| `routeCalculatedAt` | 경로 계산 시각 |
| `contactInfo` | 작성자가 입력한 외부 연락 수단 |
| `contactPublicToGuest` | 비회원 공개 동의 |

## 3. 불변식

- `CARPOOL` Post는 정확히 하나의 `CarpoolDetail`을 가져야 합니다.
- 왕복이면 `returnAt > departureAt`이어야 합니다.
- 편도이면 `returnAt`은 NULL이어야 합니다.
- `passengerCapacity`, `fuelEfficiency`, `routeDistanceKm`는 양수입니다.
- `routeTollFee`는 0 이상입니다.
- 저장된 계산 결과는 저장된 스냅샷 값으로 재현할 수 있어야 합니다.
- 연락처는 `contactPublicToGuest = true`일 때만 비회원 응답에 포함합니다.

## 4. 관계

```text
Member 1 ── N Post 1 ── 1 CarpoolDetail
                  ├── N Comment
                  ├── N PostImage
                  └── N PostReaction
CarpoolDetail N ── 1 Resort
```
