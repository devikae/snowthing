# 동호회 및 시즌방 홍보 게시판 ERD 스펙

## 1. ERD 관계도

기존 `post_category`와 `post` 테이블을 그대로 사용하며, 신규 물리 테이블 추가는 없습니다.

```mermaid
erDiagram
    POST_CATEGORY ||--o{ POST : "1:N"
    MEMBER ||--o{ POST : "1:N"

    POST_CATEGORY {
        bigint category_id PK
        varchar(50) name
        varchar(50) code UK
    }

    POST {
        bigint post_id PK
        varchar(36) public_id UK
        bigint category_id FK
        bigint member_id FK
        varchar(200) title
        longtext content
        varchar(45) writer_ip
        boolean is_anonymous
        varchar(255) anonymous_password
        int view_count
        int comment_count
        int like_count
        int dislike_count
        boolean has_image
        varchar(20) status
        boolean is_deleted
        datetime created_at
        datetime updated_at
    }
```

## 2. 신규 기준데이터 (마스터 레코드)

| category_id (자동증가) | name | code | 비고 |
|---|---|---|---|
| Auto | 동호회 모집 | `CREW` | 익명 작성 불가 |
| Auto | 시즌방 모집 | `SEASON_ROOM` | 익명 작성 불가 |
