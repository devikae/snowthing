# 🧪 댓글 조회 아키텍처 Spike 실험 마스터 가이드

본 문서는 Snowthing 댓글/대댓글 도메인의 최적 조회 아키텍처를 결정하기 위해 진행하는 **3대 후보 동시 Spike 실험(Spike Benchmark)**의 설계, 가설, 테스트 데이터 셋업, 측정 방법론 및 실행 절차를 정리한 마스터 가이드입니다.

---

## 1. 실험 목적 및 배경

### 1) 왜 이 실험을 하는가?
- 현재의 "단일 쿼리 전체 메모리 조립" 방식은 댓글 수가 적을 때는 문제가 없으나, 인기 게시글이나 핫스팟 스레드에서 **응답 페이로드 폭증과 서버 OOM(Out of Memory) 위험**을 안고 있습니다.
- "루트 커서 페이징 + 대댓글 Batch" 및 "하이브리드 프리뷰(루트 Batch + 대댓글 분리 API)"와의 **실제 쿼리 수, 읽은 행 수, 직렬화 바이트 크기, 실행 시간, 실행 계획(EXPLAIN)**을 수치로 직접 측정하여 가장 균형 잡힌 아키텍처를 선택하기 위함입니다.

### 2) Spike 6대 실험 규칙 (Engineering Rules)
1. **실험군 선택 완료**: [1. 메모리 트리 조립], [2. 루트 커서 + 대댓글 Batch], [3. 루트 Batch + 대댓글 API (하이브리드)] 3가지 후보를 선정함.
2. **반나절 타임박스(Timebox)**: 후보 1개당 최대 3~4시간(반나절) 이내로 최소 실행 가능한 PoC 코드만 작성하여 빠르게 측정함.
3. **미완성 PoC 구현**: 예외 처리, 프론트 연동 등 불필요한 코드를 배제하고 오직 쿼리와 DTO 매핑 기능만 검증함.
4. **동일 데이터 및 동일 측정 기준**: 실제 MySQL DB에 주입된 동일한 1,000건 데이터셋과 동일한 측정 하네스(`CommentSpikeBenchmarkHarness`)를 사용함.
5. **5대 필수 지표 기록**: 실행 SQL, 쿼리 수, 읽은 Row 수, 응답 직렬화 바이트 크기, MySQL `EXPLAIN` 실행 계획을 반드시 남김.
6. **실험 코드 격리 및 정리**: 실험 코드는 3개 독립 워크트리 브랜치에 유지하며, 최종 선택안만 메인 PR에 병합하고 나머지는 폐기함.

---

## 2. 테스트 데이터셋 구성 및 실제 DB 주입 방법

실제 MySQL 8.0 DB(`snowthing`)에 1,000건의 데이터를 적재하여 실제 쿼리 실행 계획과 DB I/O를 측정합니다.

### 1) DB 주입 방법 (2가지 중 택 1)
- **방법 1 (SQL 직접 실행)**: `database/spike_seed_comments.sql`을 DBeaver, DataGrip 또는 MySQL CLI에서 직접 실행.
- **방법 2 (테스트 러너 실행)**: `./gradlew.bat test --tests *CommentSpikeDataSeederTest*` 실행.

### 2) 2대 데이터 시나리오 상세

### 📦 시나리오 A: [분산 데이터셋] 일반 커뮤니티 분포
- **게시글**: 1개 (`post_id = 998`, `public_id = post-spike-distributed-998`)
- **루트 댓글**: 100개 (`parent_id IS NULL`)
- **대댓글**: 900개 (100개 루트 댓글에 각각 9개씩 균등 분산)
- **총 댓글 수**: 1,000개
- **검증 목적**: 일상적인 게시글 환경에서 루트 페이징과 대댓글 Batch 조회의 페이로드 및 쿼리 효율 검증.

### 📦 시나리오 B: [집중 핫스팟 데이터셋] 논쟁글/인기 댓글 쏠림
- **게시글**: 1개 (`post_id = 999`, `public_id = post-spike-hotspot-999`)
- **루트 댓글**: 500개 (`parent_id IS NULL`)
- **대댓글**: 500개 (**오직 `1번 루트 댓글` 1개에 대댓글 500개가 전부 몰려있는 극단적 핫스팟**)
- **총 댓글 수**: 1,000개
- **검증 목적**: 특정 댓글에 대댓글이 수백 개 몰렸을 때, 각 아키텍처가 응답 크기와 메모리를 안전하게 통제할 수 있는지 한계 검증.

---

## 3. 실험 대상 3대 후보군

```
1. [후보 1 : spike/candidate-1-in-memory]
   - findByPostIdWithMember 단일 쿼리로 1,000건 전체 로딩 -> Java Map 2-Depth 조립 -> DTO 반환

2. [후보 2 : spike/candidate-2-root-cursor-batch]
   - 루트 댓글 20개 커서 페이징 (1st Query)
   - WHERE parent_id IN (20개 IDs) 대댓글 전체 일괄 조회 (2nd Query) -> DTO 반환

3. [후보 3 : spike/candidate-3-hybrid-preview]
   - 루트 댓글 20개 조회 + 각 루트당 대댓글 상위 5개만 Batch 조회 (총 2회 쿼리, 응답 크기 최대 120개 고정)
   - 5개 초과 대댓글 "더보기" 클릭 시 GET /api/v1/comments/{commentId}/replies?cursor=... 분리 페이징 (3rd Query)
```

---

## 4. 측정 방법론 및 5대 핵심 지표

`CommentSpikeBenchmarkHarness`를 호출하여 아래 지표를 콘솔 로그로 추출하고 보고서에 기록합니다:

1. **실행 SQL 쿼리 수 (Query Count)**: 1회 API 호출 시 DB로 전송된 실제 쿼리 횟수.
2. **읽은 Row 수 (Fetched Rows)**: DB가 메모리로 반환한 실제 엔티티/행 개수.
3. **응답 직렬화 페이로드 크기 (JSON Payload Bytes)**: `ObjectMapper.writeValueAsBytes`로 측정한 실제 네트워크 전송 크기.
4. **실행 시간 (Elapsed Time)**: Service 메서드 진입부터 DTO 반환까지의 소요 시간 ($ms$).
5. **MySQL EXPLAIN 실행 계획**: 인덱스 Scan 유형 (`ref`, `range`, `ALL` 등) 확인.

---

## 5. 결과 작성 및 취합 절차

1. 각 워크트리 터미널에서 구현 및 테스트 실행
2. 측정된 수치를 `docs/study/sprint03/comment/test/spike_result_candidate_{1|2|3}.md`로 작성 및 커밋
3. 3개 브랜치 실험이 완료되면 메인 브랜치에서 최종 ADR-001 5장(Spike 결과)에 통합 반영
