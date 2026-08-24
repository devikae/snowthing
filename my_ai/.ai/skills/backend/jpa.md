# JPA Skill

## 구현 전 점검

- 연관관계 구조를 확인한다.
- N+1 가능성을 확인한다.
- Fetch 전략을 확인한다.
- 페이징과 Fetch Join이 충돌하지 않는지 확인한다.
- 조회 전용 DTO Projection이 더 적합한지 검토한다.
- Cascade와 orphanRemoval의 실제 영향 범위를 확인한다.
- 트랜잭션 범위를 확인한다.

## 원칙

- N+1을 피하기 위해 무조건 EAGER로 변경하지 않는다.
- 조회 성능 문제를 해결할 때 Fetch Join, EntityGraph, Batch Fetch, DTO Projection 등의 대안을 비교한다.
- 쿼리 수뿐 아니라 조회 데이터량과 중복 Row, 메모리 사용량도 함께 고려한다.
- JPA 동작 원리를 설명할 때 Persistence Context, Dirty Checking, Proxy, Flush 등 관련 메커니즘을 빠뜨리지 않는다.
