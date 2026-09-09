# Sprint 04 댓글 조회 아키텍처 벤치마크 가이드

Sprint 03에서 결정한 댓글 조회 아키텍처(Adjacency List + 루트 Cursor 페이징 + 대댓글 Top-5 프리뷰 및 분리 API)가 1K, 10K, 100K, 1M 데이터와 Hotspot 쏠림 환경에서도 문제없이 버티는지 검증하기 위한 재현 가이드입니다.

---

## 1. Benchmark Seed 코드 위치

벤치마크 데이터 생성과 계측 스크립트는 다음 경로에 있습니다.

- **SQL 벌크 시드 (MySQL Native)**
  - 공통 시드 프로시저 템플릿: `database/benchmark/seed-template.sql`
  - 규모별 실행 파일: `database/benchmark/seed-1k.sql`, `seed-10k.sql`, `seed-100k.sql`, `seed-1m.sql`
- **Spring/Java 시드 하네스 & 검증 테스트**
  - 시드 주입 하네스: `backend/src/test/java/com/ikae/snowthing/domain/comment/spike/CommentBenchmarkSeedHarness.java`
  - 정합성·불변식 검증 테스트 러너: `backend/src/test/java/com/ikae/snowthing/domain/comment/spike/CommentBenchmarkSeedRunnerTest.java`
  - 벤치마크 전용 프로필 설정: `backend/src/test/resources/application-benchmark.yml`
- **계측 및 실행계획 자동화 스크립트 (PowerShell)**
  - 레이턴시(평균, p95) 반복 측정: `database/benchmark/measure-timing.ps1`
  - 전통형 EXPLAIN 및 EXPLAIN ANALYZE 수집: `database/benchmark/collect-explain-plan.ps1`

---

## 2. 사전 조건 및 안전장치

### 사전 조건
- Docker Desktop 실행 중
- `snowthing-mysql` 컨테이너 구동 (포트 3306)
- MySQL 계정: `snowuser`
- 대상 스키마: `snowthing_test` 또는 `snowthing_benchmark_{1k,10k,100k}`
- 작업 위치: 프로젝트 루트 디렉터리

### 안전장치 (운영 DB 차단)
- SQL 프로시저 시작 시 현재 데이터베이스명이 `test` 또는 `benchmark`를 포함하지 않으면 `SIGNAL SQLSTATE '45000'`을 던져 즉시 쿼리를 중단합니다.
- Java 시더(`CommentBenchmarkSeedHarness`) 역시 JDBC URL 검증을 거쳐 `test`/`benchmark` 외의 스키마나 원격 호스트 접근을 차단합니다.
- 기존 데이터를 정리할 때도 `benchmark-sprint04-*` prefix가 붙은 데이터만 삭제하므로 다른 테스트 데이터와 섞이지 않습니다.

---

## 3. 실행 및 초기화 명령

비밀번호가 셸 히스토리에 남지 않도록 환경변수로 설정하고 실행합니다.

```powershell
$env:MYSQL_PWD = 'snowthing_pass_2026!'
```

### (1) SQL 파이프라인으로 시드 데이터 주입
기존 benchmark prefix 데이터를 먼저 정리하고, 고정된 seed로 데이터를 생성합니다.

```powershell
# 1천건 (Small)
Get-Content database/benchmark/seed-1k.sql -Raw | docker exec -i -e MYSQL_PWD=$env:MYSQL_PWD snowthing-mysql mysql -u snowuser snowthing_test

# 1만건 (Medium)
Get-Content database/benchmark/seed-10k.sql -Raw | docker exec -i -e MYSQL_PWD=$env:MYSQL_PWD snowthing-mysql mysql -u snowuser snowthing_test

# 10만건 (Large)
Get-Content database/benchmark/seed-100k.sql -Raw | docker exec -i -e MYSQL_PWD=$env:MYSQL_PWD snowthing-mysql mysql -u snowuser snowthing_test

# 100만건 (Challenge - 수 분 소요)
Get-Content database/benchmark/seed-1m.sql -Raw | docker exec -i -e MYSQL_PWD=$env:MYSQL_PWD snowthing-mysql mysql -u snowuser snowthing_test
```

### (2) Gradle 테스트 러너로 시드 및 불변식 검증
Spring Context 기반으로 데이터를 주입하고 8대 도메인 불변식을 함께 검증할 때 사용합니다.

```bash
# 기본 1천건 검증
./gradlew test --tests CommentBenchmarkSeedRunnerTest -PincludeBenchmark

# 환경변수로 규모를 지정해 실행할 때
BENCHMARK_COMMENTS=10000 ./gradlew test --tests CommentBenchmarkSeedRunnerTest -PincludeBenchmark
```

### (3) 레이턴시 측정 및 실행계획 수집
각 쿼리별로 warm-up 5회 후 20회를 반복 측정해 평균과 p95 레이턴시를 계산합니다.

```powershell
# 특정 스키마 성능 측정 (결과는 metrics/실행시간.csv에 누적)
./database/benchmark/measure-timing.ps1 -Scale 1k -Schema snowthing_benchmark_1k

# 실행계획 수집 (explain-plans 폴더로 txt 파일 추출)
./database/benchmark/collect-explain-plan.ps1 -Scale 1k -Schema snowthing_benchmark_1k
```

---

## 4. 데이터 분포 설명

현실적인 커뮤니티 트래픽과 데이터 쏠림을 재현하기 위해 다음과 같은 규칙으로 데이터를 분배했습니다.

- **게시글 100개 구성 (`seq` = 시더 내부 0~99 순번, `public_id` 접미사)**:
  DB의 PK(`post_id`)는 AUTO_INCREMENT 특성상 환경마다 값이 달라지므로, 벤치마크 스크립트가 일관되게 특정 글을 찾을 수 있도록 `public_id`를 `benchmark-sprint04-post-{seq}` 형태로 고정 부여한 논리적 순번 번호입니다.
  전체 댓글은 **45% : 45% : 10%** 규칙으로 세 그룹에 분배됩니다.
  - **일반 게시글 80개 (`seq 0 ~ 79`)**: 전체 댓글의 45%를 80개에 고르게 분산 (1M 기준 글당 약 5,600건)
  - **중간 규모 게시글 19개 (`seq 80 ~ 98`)**: 전체 댓글의 45%를 19개에 집중 분산 (1M 기준 글당 약 23,600건)
  - **Hot Post 1개 (`seq 99`)**: 전체 댓글의 **10%를 단 1개 글에 몰아넣은 초인기글**
    - 1K 규모: **약 100건** 집중
    - 10K 규모: **약 1,000건** 집중
    - 100K 규모: **약 10,000건 (1만 건)** 집중
    - 1M 규모: **약 100,000건 (10만 건)** 집중
- **댓글 계층 구조**:
  - 루트 댓글: 전체의 약 20% (`GREATEST(100, target_comments / 5)`)
  - 대댓글: 전체의 약 80%
- **Hotspot 쏠림 (루트 1개 대댓글 몰림)**:
  - 위 Hot Post(`seq 99`)에 달린 수많은 루트 댓글 중, **0번 루트 댓글(`seq 0`) 1개에 대댓글을 도메인 정책 최대치인 100개(활성 상한)**까지 몰아넣었습니다.
  - 이를 통해 "댓글이 10만 개 달린 인기글 안에서, 특정 댓글에만 답글 100개가 폭주했을 때"의 인덱스 탐색 및 페이징 성능을 실측합니다.
- **Soft Delete 비율**:
  - 전체 댓글의 약 20%를 삭제 상태(`is_deleted = true`)로 구성했습니다.
  - 단, 위 Hotspot 루트의 대댓글은 100개 모두 활성 상태를 유지하여 상한선 부하를 엄밀하게 측정하도록 했습니다.
- **결정론적 재현성**:
  - 고정 시드(`@seed = 20260907`)를 사용해 언제 다시 돌려도 동일한 ID와 타임스탬프 분포가 생성됩니다.
  - 동일한 `created_at`을 가진 댓글 묶음에서도 PK(`comment_id ASC`) 타이브레이커가 깨지지 않는지 함께 확인합니다.

---

## 5. 검증 결과 요약

### (1) 9대 시나리오별 성능 측정 결과 (단위: ms, warm-up 5회 후 20회 반복)

| 시나리오 | 1K 평균/p95 | 10K 평균/p95 | 100K 평균/p95 | 1M 평균/p95 | 사용 인덱스 | 인덱스 제거 시(Invisible) 영향 |
|---|---:|---:|---:|---:|---|---|
| 루트 첫 페이지 (20건) | 0.332 / 0.468 | 0.549 / 0.754 | 2.606 / 2.979 | 36.578 / 39.388 | `idx_comment_post_parent_id` | 1M 기준 226ms로 급증 (풀스캔 발생) |
| 루트 중간 커서 페이징 | 0.294 / 0.370 | 0.416 / 0.622 | 1.498 / 2.094 | 19.840 / 22.533 | `idx_comment_post_parent_id` | Cursor 조건으로 스캔 범위를 줄여 안정적 |
| 루트 마지막 페이지 | 0.315 / 0.468 | 0.347 / 0.508 | 0.376 / 0.584 | 0.449 / 0.692 | `idx_comment_post_parent_id` | 데이터 증가에도 거의 영향 없음 |
| 대댓글 Top-5 일괄 조회 | 0.445 / 0.653 | 0.435 / 0.612 | 0.468 / 0.682 | 0.479 / 0.626 | `idx_comment_parent_deleted_id` | 1M에서도 0.6ms대 유지 |
| Hotspot 대댓글 첫 페이지 | 0.435 / 0.662 | 0.377 / 0.552 | 0.424 / 0.608 | 0.449 / 0.600 | `idx_comment_parent_deleted_id` | parent_id 조건으로 좁혀져 쏠림에도 안정적 |
| Hotspot 대댓글 중간 커서 | 0.427 / 0.655 | 0.402 / 0.579 | 0.464 / 0.699 | 0.498 / 0.724 | `idx_comment_parent_deleted_id` | 커서 seek 덕분에 0.7ms 이내 유지 |
| 활성 대댓글 수 집계 | 0.222 / 0.342 | 0.227 / 0.326 | 0.191 / 0.336 | 0.258 / 0.388 | `idx_comment_parent_deleted_id` | 커버링 인덱스로만 카운트해 가장 빠름 |
| 삭제된 루트 조회 | 0.366 / 0.553 | 0.529 / 0.602 | 1.729 / 2.124 | 29.876 / 32.770 | `idx_comment_post_parent_id` | 삭제 상태 포함 시 후보 행 크기에 비례 |
| 삭제된 대댓글 조회 | 0.412 / 0.610 | 0.395 / 0.580 | 0.430 / 0.640 | 0.460 / 0.650 | `idx_comment_parent_deleted_id` | placeholder 노출 정책 기준 안정적 |

### (2) 핵심 인덱스 분석
- **`idx_comment_post_parent_id` (`post_id`, `parent_id`, `comment_id`)**:
  - 루트 댓글 조회 시 필수 인덱스로 선택됩니다.
  - 인덱스를 끄면 1M 환경에서 탐색 대상 행이 2,000건에서 347,250건으로 늘어나며 응답 속도가 6배 이상 느려집니다.
- **`idx_comment_parent_deleted_id` (`parent_id`, `is_deleted`, `comment_id`)**:
  - 특정 부모 밑의 대댓글 페이징 및 활성 대댓글 카운트에 선택됩니다.
  - 1K부터 1M까지 데이터가 1,000배 늘어나도 응답 속도가 0.4~0.7ms 수준으로 균일하게 유지됩니다.

### (3) 데이터 정합성 불변식 검증 결과
주입 후 `CommentBenchmarkSeedRunnerTest`와 `guides/정합성-검증.sql`을 실행해 다음 항목을 전수 확인했습니다.
1. 전체 댓글 수 일치 여부 (`roots + replies == total`)
2. 게시글별 활성 `comment_count` 값과 실제 활성 댓글 수 일치 여부
3. 부모 댓글과 자식 대댓글의 `post_id` 일치 여부 (고아 노드 및 엉뚱한 게시글 매핑 0건)
4. 루트 댓글당 활성 대댓글 100개 상한 준수 여부
5. Cursor 페이징 연속 조회 시 데이터 누락이나 중복 0건
6. 동일 시각 등록 댓글 간 `comment_id ASC` 정렬 일관성 유지

---

## 6. 관련 문서 링크

- **아키텍처 결정서**: [ADR-002-댓글아키텍처.md](ADR-002-댓글아키텍처.md)
- **실행계획 원문 모음**: [explain-plans/](benchmark/explain-plans/)
- **쿼리 원문 모음**: [queries/](benchmark/queries/)
- **상세 측정 수치**: [results/댓글-벤치마크-결과.md](benchmark/results/댓글-벤치마크-결과.md)
- **메트릭 CSV 데이터**: [metrics/](benchmark/metrics/)
