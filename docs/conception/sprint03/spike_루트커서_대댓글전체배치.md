# [Spike 결과 보고서] 후보 2: 루트 커서 페이징 + 대댓글 전체 Batch

- **브랜치명**: `sprint03-spikeTest-02-Cursor/Batch`
- **측정 일시**: 2026-08-29
- **작성자**: devikae (자동 생성)

---

## 1. 구현 요약 (PoC Implementation)
- 루트 댓글을 `(created_at, comment_id)` 복합 커서로 20개 조회합니다.
- 선택된 루트 ID를 `parent_id IN (...)`에 전달해 모든 대댓글을 한 번에 조회합니다.
- 두 쿼리 모두 작성자 정보를 LEFT JOIN하고, DTO 컬렉션은 방어적으로 복사합니다.

---

## 2. 측정 결과 데이터 매트릭스

| 시나리오 | 쿼리 수 (Count) | 읽은 Row 수 (Rows) | 응답 크기 (Bytes / KB) | 실행 시간 (Elapsed ms) |
| :--- | :---: | :---: | :---: | :---: |
| **[시나리오 A] 분산 1,000건** | 2회 | 200행 | 40830 B (39.87 KB) | 10.308 ms |
| **[시나리오 B] 집중 핫스팟 1,000건** | 2회 | 520행 | 106186 B (103.70 KB) | 14.988 ms |

---

## 3. 실행된 실제 SQL 및 MySQL EXPLAIN

### 1) [시나리오 A] 분산 1,000건

#### [Query 1]
```sql
SELECT c.comment_id, c.parent_id, c.content, c.is_deleted, c.created_at, m.nickname, c.is_anonymous, c.writer_ip FROM comment c LEFT JOIN member m ON m.member_id = c.member_id WHERE c.post_id = 998 AND c.parent_id IS NULL ORDER BY c.created_at ASC, c.comment_id ASC LIMIT 20
```

**EXPLAIN 분석**:

| table | type | key | rows | Extra |
| :--- | :--- | :--- | :--- | :--- |
| c | ref | fk_comment_parent | 603 | Using index condition; Using where; Using filesort |
| m | eq_ref | PRIMARY | 1 | null |

#### [Query 2]
```sql
SELECT c.comment_id, c.parent_id, c.content, c.is_deleted, c.created_at, m.nickname, c.is_anonymous, c.writer_ip FROM comment c LEFT JOIN member m ON m.member_id = c.member_id WHERE c.parent_id IN (4004, 4014, 4024, 4034, 4044, 4054, 4064, 4074, 4084, 4094, 4104, 4114, 4124, 4134, 4144, 4154, 4164, 4174, 4184, 4194) ORDER BY c.parent_id ASC, c.created_at ASC, c.comment_id ASC
```

**EXPLAIN 분석**:

| table | type | key | rows | Extra |
| :--- | :--- | :--- | :--- | :--- |
| c | range | fk_comment_parent | 180 | Using index condition; Using filesort |
| m | eq_ref | PRIMARY | 1 | null |

### 2) [시나리오 B] 집중 핫스팟 1,000건

#### [Query 1]
```sql
SELECT c.comment_id, c.parent_id, c.content, c.is_deleted, c.created_at, m.nickname, c.is_anonymous, c.writer_ip FROM comment c LEFT JOIN member m ON m.member_id = c.member_id WHERE c.post_id = 999 AND c.parent_id IS NULL ORDER BY c.created_at ASC, c.comment_id ASC LIMIT 20
```

**EXPLAIN 분석**:

| table | type | key | rows | Extra |
| :--- | :--- | :--- | :--- | :--- |
| c | ref | fk_comment_parent | 603 | Using index condition; Using where; Using filesort |
| m | eq_ref | PRIMARY | 1 | null |

#### [Query 2]
```sql
SELECT c.comment_id, c.parent_id, c.content, c.is_deleted, c.created_at, m.nickname, c.is_anonymous, c.writer_ip FROM comment c LEFT JOIN member m ON m.member_id = c.member_id WHERE c.parent_id IN (5004, 5005, 5006, 5007, 5008, 5009, 5010, 5011, 5012, 5013, 5014, 5015, 5016, 5017, 5018, 5019, 5020, 5021, 5022, 5023) ORDER BY c.parent_id ASC, c.created_at ASC, c.comment_id ASC
```

**EXPLAIN 분석**:

| table | type | key | rows | Extra |
| :--- | :--- | :--- | :--- | :--- |
| c | range | fk_comment_parent | 519 | Using index condition; Using filesort |
| m | eq_ref | PRIMARY | 1 | null |

---

## 4. 발견된 결함 및 한계점 (Issues & Bottlenecks)
- 응답 쿼리 수는 2회로 고정되지만 선택된 루트에 대댓글이 집중되면 응답 행과 페이로드는 제한되지 않습니다.
- 현재 스키마에는 `(post_id, parent_id, created_at, comment_id)` 복합 인덱스가 없어 EXPLAIN상 추가 정렬이나 넓은 스캔이 발생할 수 있습니다.
- 실행 시간은 로컬 단일 실행값이므로 반복 측정의 평균·백분위 지표가 아닙니다.

---

## 5. 최종 평가 및 소견
후보 2는 루트 수를 20개로 제한하면서 N+1 없이 2회 조회를 유지합니다. 다만 핫스팟 루트가 페이지에 포함되면 대댓글 전체가 반환되어 페이로드 상한을 보장하지 못하므로, 운영안에서는 대댓글 별도 커서 또는 프리뷰 제한을 함께 검토해야 합니다.
