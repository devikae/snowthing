# 리조트캠 ERD

```mermaid
erDiagram
    RESORT ||--o{ RESORT_CAMERA : contains
    RESORT ||--o{ MEMBER_RESORT : preferred_by

    RESORT {
        BIGINT resort_id PK
        VARCHAR code UK
        VARCHAR name UK
        VARCHAR region
        INT display_order
        BOOLEAN active
    }

    RESORT_CAMERA {
        BIGINT resort_camera_id PK
        BIGINT resort_id FK
        VARCHAR code
        VARCHAR name
        VARCHAR source_type
        VARCHAR source_url
        VARCHAR external_page_url NULL
        INT display_order
        BOOLEAN active
        DATETIME created_at
        DATETIME updated_at
    }
```

## 제약조건과 인덱스

```sql
UNIQUE KEY uk_resort_code (code)
UNIQUE KEY uk_resort_camera_code (resort_id, code)
KEY idx_resort_camera_visible (resort_id, active, display_order, resort_camera_id)
CHECK (display_order >= 0)
```

`member_resort`는 기존 관계를 유지합니다. 신규 리조트 추가는 기존 회원 데이터에 영향을 주지 않습니다.

## 마이그레이션 순서

1. `resort.code`, `display_order`, `active`를 호환 가능한 상태로 추가합니다.
2. 기존 6개 리조트의 코드를 이름으로 식별해 채웁니다.
3. 신규 6개 리조트를 코드와 함께 삽입합니다.
4. 누락과 중복을 검증한 뒤 `code NOT NULL`과 UNIQUE를 적용합니다.
5. `resort_camera`를 생성합니다.
6. `SELECT resort_id FROM resort WHERE code = ...` 방식으로 카메라 FK를 연결합니다.
7. `(resort_id, code)` 기준으로 재실행 안전성을 보장합니다.

