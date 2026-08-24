# Database Index & Query Skill

## 인덱스 원칙

- 조회가 느리다는 이유만으로 무조건 인덱스를 추가하지 않는다.
- WHERE, JOIN, ORDER BY, GROUP BY와 실제 데이터 분포를 확인한다.
- 선택도, cardinality, 복합 인덱스 컬럼 순서를 고려한다.
- 인덱스 추가가 INSERT/UPDATE/DELETE와 저장공간에 주는 비용을 설명한다.
- 중복되거나 거의 동일한 인덱스를 만들지 않는다.

## 복합 인덱스

- leftmost prefix 특성을 고려한다.
- equality 조건과 range 조건의 순서를 실제 쿼리 기준으로 검토한다.
- 정렬까지 인덱스로 해결 가능한지 확인한다.

## 쿼리 분석

- EXPLAIN/EXPLAIN ANALYZE 등 실행계획을 확인한다.
- Full Scan이 항상 나쁘다고 단정하지 않는다.
- 작은 테이블이나 낮은 선택도에서는 Full Scan이 더 적절할 수 있다.
- 인덱스를 타는지 여부만이 아니라 실제 rows, cost, filter, filesort/temp 사용 등을 확인한다.

## 금지

- `SELECT *`를 습관적으로 사용하지 않는다.
- 애플리케이션에서 수천 번 반복 조회하는 N+1성 접근을 방치하지 않는다.
- 깊은 OFFSET pagination의 비용을 무시하지 않는다.
