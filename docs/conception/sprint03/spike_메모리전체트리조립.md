# [Spike 결과 보고서] 후보 1: 메모리 전체 트리 조립

- **브랜치명**: `devikae/sprint03-spikeTest-01-메모리-조립`
- **측정 일시**: 2026-08-29
- **작성자**: devikae (자동 생성)

---

## 1. 구현 요약 (PoC Implementation)
- `findByPostIdWithMember` 한 번으로 게시글별 댓글 1,000건과 작성자를 조회
- `LinkedHashMap<Long, MutableCommentNode>`에서 루트/대댓글을 연결한 뒤 불변 2-Depth DTO로 변환
- Hibernate Statistics로 각 시나리오의 JPQL 실행 횟수가 1회인지 검증

---

## 2. 측정 결과 데이터 매트릭스

| 시나리오 | 쿼리 수 (Count) | 읽은 Row 수 (Rows) | 응답 크기 (Bytes / KB) | 실행 시간 (Elapsed ms) |
| :--- | :---: | :---: | :---: | :---: |
| **[시나리오 A] 분산 1,000건** | 1회 | 1000행 | 215490 B (210.44 KB) | 83.468 ms |
| **[시나리오 B] 집중 핫스팟 1,000건** | 1회 | 1000행 | 210784 B (205.84 KB) | 35.401 ms |

---

## 3. 실행된 실제 SQL 및 MySQL EXPLAIN

### 1) [시나리오 A] 분산 1,000건

#### [Query 1]
```sql
SELECT c.*, m.*
FROM comment c
LEFT JOIN member m ON m.member_id = c.member_id
WHERE c.post_id = 998
ORDER BY c.created_at ASC, c.comment_id ASC
```

**EXPLAIN 분석**:

| table | type | key | rows | Extra |
| :--- | :--- | :--- | :--- | :--- |
| c | ref | fk_comment_post | 1000 | Using temporary; Using filesort |
| m | ALL | null | 3 | Using where; Using join buffer (hash join) |

### 2) [시나리오 B] 집중 핫스팟 1,000건

#### [Query 1]
```sql
SELECT c.*, m.*
FROM comment c
LEFT JOIN member m ON m.member_id = c.member_id
WHERE c.post_id = 999
ORDER BY c.created_at ASC, c.comment_id ASC
```

**EXPLAIN 분석**:

| table | type | key | rows | Extra |
| :--- | :--- | :--- | :--- | :--- |
| c | ref | fk_comment_post | 1000 | Using temporary; Using filesort |
| m | ALL | null | 3 | Using where; Using join buffer (hash join) |

---

## 4. 발견된 결함 및 한계점 (Issues & Bottlenecks)
- 댓글 총량에 비례해 엔티티와 DTO가 동시에 메모리에 존재합니다.
- 핫스팟 시나리오는 한 루트 DTO가 대댓글 500개를 한 응답에 포함합니다.
- 전체 응답 방식이라 루트 페이징이나 대댓글 더보기로 페이로드 상한을 통제할 수 없습니다.

---

## 5. 최종 평가 및 소견
단일 JPQL로 N+1 없이 2-Depth 트리를 조립할 수 있다는 가설은 확인했습니다. 다만 댓글 증가량이 DB 조회 행, JVM 메모리, 직렬화 크기에 그대로 반영되므로 운영 기본안으로 채택하기 전 후보 2·3과 응답 상한 및 핫스팟 안정성을 비교해야 합니다.
