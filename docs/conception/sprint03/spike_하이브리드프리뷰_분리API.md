# [Spike 결과 보고서] 후보 3: 루트 Batch + 대댓글 5개 프리뷰 & 분리 API

- **브랜치명**: `devikae/sprint03-spikeTest-03-Batch/API`
- **측정 일시**: 2026-08-29
- **작성자**: devikae 

---

## 1. 구현 요약 (PoC Implementation)
- 루트 댓글 20개 조회 후 MySQL 8 `ROW_NUMBER() OVER (PARTITION BY parent_id)`로 각 루트당 대댓글 5개만 일괄 조회합니다.
- 프리뷰는 총 2회 쿼리이며, 대댓글 더보기는 `comment_id` 커서와 `LIMIT 20`을 사용하는 분리 조회입니다.
- Spike 코드는 `src/test`에 격리했고 응답 컬렉션은 `List.copyOf()`로 방어적 복사했습니다.

---

## 2. 측정 결과 데이터 매트릭스

| 시나리오 | 쿼리 수 (Count) | 읽은 Row 수 (Rows) | 응답 크기 (Bytes / KB) | 실행 시간 (Elapsed ms) |
| :--- | :---: | :---: | :---: | :---: |
| **[분산] Post 998** | 2회 | 120행 | 22560 B (22.03 KB) | 14.594 ms |
| **[집중] Post 999** | 2회 | 25행 | 5685 B (5.55 KB) | 5.603 ms |
| **[더보기 호출 시] Post 999 핫스팟 루트** | 1회 | 20행 | 3582 B (3.50 KB) | 2.357 ms |

---

## 3. 실행된 실제 SQL 및 MySQL EXPLAIN

### 1) [분산] Post 998

#### [Query 1]
```sql
SELECT c.comment_id, c.parent_id, c.content, m.nickname, c.created_at, 0 AS reply_count
FROM comment c
LEFT JOIN member m ON m.member_id = c.member_id
WHERE c.post_id = 998
  AND c.parent_id IS NULL
  AND c.is_deleted = FALSE
  AND c.comment_id > 0
ORDER BY c.comment_id ASC
LIMIT 20
```

**EXPLAIN 분석**:

| table | type | key | rows | Extra |
| :--- | :--- | :--- | :--- | :--- |
| c | range | PRIMARY | 1001 | Using where |
| m | eq_ref | PRIMARY | 1 | null |

#### [Query 2]
```sql
WITH ranked_replies AS (
    SELECT c.comment_id,
           c.parent_id,
           c.content,
           m.nickname,
           c.created_at,
           ROW_NUMBER() OVER (
               PARTITION BY c.parent_id
               ORDER BY c.comment_id ASC
           ) AS reply_rank,
           COUNT(*) OVER (PARTITION BY c.parent_id) AS reply_count
    FROM comment c
    LEFT JOIN member m ON m.member_id = c.member_id
    WHERE c.parent_id IN (4004, 4014, 4024, 4034, 4044, 4054, 4064, 4074, 4084, 4094, 4104, 4114, 4124, 4134, 4144, 4154, 4164, 4174, 4184, 4194)
      AND c.is_deleted = FALSE
)
SELECT comment_id, parent_id, content, nickname, created_at, reply_count
FROM ranked_replies
WHERE reply_rank <= 5
ORDER BY parent_id ASC, comment_id ASC
```

**EXPLAIN 분석**:

| table | type | key | rows | Extra |
| :--- | :--- | :--- | :--- | :--- |
| <derived2> | ALL | null | 18 | Using where; Using filesort |
| c | range | fk_comment_parent | 180 | Using index condition; Using where; Using temporary; Using filesort |
| m | eq_ref | PRIMARY | 1 | null |

### 2) [집중] Post 999

#### [Query 1]
```sql
SELECT c.comment_id, c.parent_id, c.content, m.nickname, c.created_at, 0 AS reply_count
FROM comment c
LEFT JOIN member m ON m.member_id = c.member_id
WHERE c.post_id = 999
  AND c.parent_id IS NULL
  AND c.is_deleted = FALSE
  AND c.comment_id > 0
ORDER BY c.comment_id ASC
LIMIT 20
```

**EXPLAIN 분석**:

| table | type | key | rows | Extra |
| :--- | :--- | :--- | :--- | :--- |
| c | range | PRIMARY | 1001 | Using where |
| m | eq_ref | PRIMARY | 1 | null |

#### [Query 2]
```sql
WITH ranked_replies AS (
    SELECT c.comment_id,
           c.parent_id,
           c.content,
           m.nickname,
           c.created_at,
           ROW_NUMBER() OVER (
               PARTITION BY c.parent_id
               ORDER BY c.comment_id ASC
           ) AS reply_rank,
           COUNT(*) OVER (PARTITION BY c.parent_id) AS reply_count
    FROM comment c
    LEFT JOIN member m ON m.member_id = c.member_id
    WHERE c.parent_id IN (5004, 5005, 5006, 5007, 5008, 5009, 5010, 5011, 5012, 5013, 5014, 5015, 5016, 5017, 5018, 5019, 5020, 5021, 5022, 5023)
      AND c.is_deleted = FALSE
)
SELECT comment_id, parent_id, content, nickname, created_at, reply_count
FROM ranked_replies
WHERE reply_rank <= 5
ORDER BY parent_id ASC, comment_id ASC
```

**EXPLAIN 분석**:

| table | type | key | rows | Extra |
| :--- | :--- | :--- | :--- | :--- |
| <derived2> | ALL | null | 51 | Using where; Using filesort |
| c | range | fk_comment_parent | 519 | Using index condition; Using where; Using temporary; Using filesort |
| m | eq_ref | PRIMARY | 1 | null |

### 3) [더보기 호출 시] Post 999 핫스팟 루트

#### [Query 1]
```sql
SELECT c.comment_id, c.parent_id, c.content, m.nickname, c.created_at, 0 AS reply_count
FROM comment c
LEFT JOIN member m ON m.member_id = c.member_id
WHERE c.parent_id = 5004
  AND c.is_deleted = FALSE
  AND c.comment_id > 0
ORDER BY c.comment_id ASC
LIMIT 20
```

**EXPLAIN 분석**:

| table | type | key | rows | Extra |
| :--- | :--- | :--- | :--- | :--- |
| c | range | PRIMARY | 1001 | Using where |
| m | eq_ref | PRIMARY | 1 | null |

---

## 4. 발견된 결함 및 한계점 (Issues & Bottlenecks)
- 부모별 Top-N을 위해 윈도 함수 정렬과 임시 테이블 처리가 발생할 수 있습니다.
- 현재 인덱스는 `post_id`, `parent_id` 단일 인덱스뿐이므로 운영 반영 시 `(post_id, parent_id, comment_id)`와 `(parent_id, comment_id)` 복합 인덱스를 비교 검증해야 합니다.
- `LIMIT 20`만 사용하는 PoC이므로 정확한 `hasNext` 판정이 필요하면 21건 조회 또는 별도 존재 확인의 비용을 선택해야 합니다.

---

## 5. 최종 평가 및 소견
응답 크기는 루트 20개와 부모별 프리뷰 5개로 상한이 통제됩니다. 핫스팟의 나머지 대댓글은 분리 API로 넘겨 초기 응답과 메모리 사용량을 제한할 수 있습니다.
