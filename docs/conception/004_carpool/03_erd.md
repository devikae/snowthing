# 카풀 ERD

## 1. 관계

```text
member (1) ─── (N) post (1) ─── (1) carpool_detail (N) ─── (1) resort
                         │
                         ├── comment (N)
                         ├── post_image (N)
                         └── post_reaction (N)
```

## 2. `carpool_detail`

| 컬럼 | 타입/제약 | 설명 |
|---|---|---|
| `post_id` | BIGINT PK, FK `post.post_id`, ON DELETE CASCADE | 게시글과 1:1 |
| `departure_region` | VARCHAR(100) NOT NULL | 출발 지역 |
| `meeting_place` | VARCHAR(200) NOT NULL | 집결지 |
| `departure_latitude` | DECIMAL(10,7) NULL | 경로 계산 좌표 |
| `departure_longitude` | DECIMAL(10,7) NULL | 경로 계산 좌표 |
| `destination_resort_id` | BIGINT NOT NULL, FK | 도착 리조트 |
| `destination_latitude` | DECIMAL(10,7) NOT NULL | 계산 당시 목적지 위도 |
| `destination_longitude` | DECIMAL(10,7) NOT NULL | 계산 당시 목적지 경도 |
| `trip_type` | VARCHAR(20) NOT NULL | 편도/왕복 |
| `departure_at` | DATETIME NOT NULL | 출발 시각 |
| `return_at` | DATETIME NULL | 왕복 복귀 시각 |
| `passenger_capacity` | INT NOT NULL, CHECK > 0 | 운전자 제외 인원 |
| `fuel_type` | VARCHAR(30) NOT NULL | 연료 종류 |
| `fuel_efficiency` | DECIMAL(6,2) NOT NULL, CHECK > 0 | km/L |
| `cost_mode` | VARCHAR(20) NOT NULL DEFAULT `AUTO` | 자동 계산/1인 금액 직접 지정 |
| `fuel_price` | DECIMAL(10,2) NOT NULL, CHECK >= 0 | 유가 스냅샷 |
| `fuel_price_source` | VARCHAR(20) NOT NULL | `OPINET`, `CACHE`, `USER_INPUT` |
| `fuel_price_observed_at` | DATETIME NOT NULL | 유가 조회 시각 |
| `route_distance_km` | DECIMAL(8,2) NOT NULL, CHECK > 0 | 최종 적용 거리 |
| `route_toll_fee` | INT NOT NULL, CHECK >= 0 | 최종 적용 통행료 |
| `estimated_fuel_cost` | INT NOT NULL, CHECK >= 0 | 예상 연료비 |
| `estimated_total_cost` | INT NOT NULL, CHECK >= 0 | 총 예상 비용 |
| `estimated_cost_per_person` | INT NOT NULL, CHECK >= 0 | 운전자 포함 1인 비용 |
| `route_source` | VARCHAR(20) NOT NULL | 거리·통행료 계산 출처(`KAKAO`) |
| `route_calculated_at` | DATETIME NULL | 경로 계산 시각 |
| `contact_info` | VARCHAR(500) NULL | 외부 연락 수단 |
| `contact_public_to_guest` | BOOLEAN NOT NULL DEFAULT FALSE | 비회원 공개 동의 |
| `equipment_load_available` | BOOLEAN NOT NULL DEFAULT FALSE | 장비 적재 가능 여부 |
| `created_at`, `updated_at` | DATETIME NOT NULL | 감사 시각 |

목록 쿼리는 `departure_at >= now` 조건과 `created_at DESC, post_id DESC` 정렬을 사용합니다. `010`의 `(departure_at, created_at DESC, post_id DESC)` 인덱스는 미래 일정 검색 범위를 줄이지만 첫 컬럼이 범위 조건이므로 뒤 컬럼이 정렬을 항상 제거하지는 않습니다. 최신순을 바꾸지 않고 정렬 경로도 선택할 수 있도록 `012`에서 `(created_at DESC, post_id DESC)` 인덱스를 별도로 추가합니다. 정렬 인덱스를 선택하면 출발일 조건은 잔여 조건으로 평가되므로, 운영 데이터에서는 `EXPLAIN ANALYZE`로 두 인덱스의 스캔 행 수와 filesort 여부를 비교합니다.

## 3. `resort` 추가 컬럼

| 컬럼 | 타입/제약 | 설명 |
|---|---|---|
| `route_latitude` | DECIMAL(10,7) NULL | 검증된 스키장 진입 지점 위도 |
| `route_longitude` | DECIMAL(10,7) NULL | 검증된 스키장 진입 지점 경도 |

운영 DB는 `ddl-auto: validate`이므로 신규 테이블은 `007`, 비용 방식은 `008`, 장비 적재 여부는 `009`, 미래 일정 검색 인덱스와 13개 리조트 기준 좌표는 `010`, 유가 출처와 계산 당시 목적지 좌표 스냅샷은 `011`, 최신순 정렬 인덱스는 `012` production migration으로 순서대로 반영합니다. 기존 마이그레이션은 체크섬이 기록되므로 수정하지 않습니다.
