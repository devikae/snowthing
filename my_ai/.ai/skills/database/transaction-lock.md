# Database Transaction & Lock Skill

## 트랜잭션

- DB 격리 수준이 실제 DB 제품과 버전에서 어떻게 동작하는지 확인한다.
- MySQL/InnoDB의 MVCC, Undo, Read View, next-key lock 등 제품 특성을 필요 시 설명한다.
- 애플리케이션 트랜잭션과 DB 트랜잭션을 동일 개념으로 뭉뚱그리지 않는다.

## Lock

- 낙관적 락과 비관적 락의 목적과 비용을 비교한다.
- 비관적 락은 lock wait, deadlock, throughput 저하를 고려한다.
- deadlock은 “발생하면 안 되는 버그”로만 보지 말고 재시도 전략까지 설계한다.
- lock ordering을 일정하게 유지할 수 있는지 검토한다.
