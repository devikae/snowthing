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
| `trip_type` | VARCHAR(20) NOT NULL | 편도/왕복 |
| `departure_at` | DATETIME NOT NULL | 출발 시각 |
| `return_at` | DATETIME NULL | 왕복 복귀 시각 |
| `passenger_capacity` | INT NOT NULL, CHECK > 0 | 운전자 제외 인원 |
| `fuel_type` | VARCHAR(20) NOT NULL | 연료 종류 |
| `fuel_efficiency` | DECIMAL(6,2) NOT NULL, CHECK > 0 | km/L |
| `fuel_price` | DECIMAL(10,2) NOT NULL, CHECK >= 0 | 유가 스냅샷 |
| `fuel_price_observed_at` | DATETIME NOT NULL | 유가 조회 시각 |
| `route_distance_km` | DECIMAL(8,2) NOT NULL, CHECK > 0 | 최종 적용 거리 |
| `route_toll_fee` | INT NOT NULL, CHECK >= 0 | 최종 적용 통행료 |
| `estimated_fuel_cost` | INT NOT NULL, CHECK >= 0 | 예상 연료비 |
| `estimated_total_cost` | INT NOT NULL, CHECK >= 0 | 총 예상 비용 |
| `estimated_cost_per_person` | INT NOT NULL, CHECK >= 0 | 운전자 포함 1인 비용 |
| `route_source` | VARCHAR(20) NOT NULL | 자동/수동 |
| `route_calculated_at` | DATETIME NULL | 경로 계산 시각 |
| `contact_info` | VARCHAR(500) NULL | 외부 연락 수단 |
| `contact_public_to_guest` | BOOLEAN NOT NULL DEFAULT FALSE | 비회원 공개 동의 |
| `created_at`, `updated_at` | DATETIME NOT NULL | 감사 시각 |

운영 DB는 `ddl-auto: validate`이므로 신규 테이블은 별도 production migration으로 추가합니다. 기존 `post` 테이블에는 `CARPOOL` 카테고리 허용만 반영하고, 카풀 테이블의 CHECK와 FK는 이름을 지정합니다.
