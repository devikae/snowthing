# Transaction Skill

## 기본 원칙

- 트랜잭션 경계는 일반적으로 Service 계층의 하나의 유즈케이스 단위를 기준으로 잡는다.
- 단순히 모든 Service 메서드에 `@Transactional`을 붙이지 않는다.
- 읽기 전용 작업은 적절한 경우 `readOnly = true`를 사용한다.
- 트랜잭션 범위를 필요 이상으로 길게 유지하지 않는다.
- 외부 API 호출, 파일 업로드, 메시지 전송 등을 DB 트랜잭션 안에 오래 묶지 않는다.

## 반드시 검토할 항목

- 원자성: 함께 성공/실패해야 하는 작업인가
- 격리 수준: 동시 트랜잭션 간 어떤 현상이 허용되는가
- 락: 낙관적/비관적 락이 필요한가
- 재시도: Deadlock 또는 Optimistic Lock 실패 시 어떻게 처리할 것인가
- 이벤트/외부 시스템: DB 커밋과 외부 전송의 정합성을 어떻게 맞출 것인가

## Spring 주의점

- 같은 클래스 내부 self-invocation으로 `@Transactional` 프록시가 우회되지 않는지 확인한다.
- private 메서드에 붙인 `@Transactional`이 기대대로 적용된다고 가정하지 않는다.
- Checked Exception rollback 정책을 필요에 따라 명시한다.
- Lazy Loading을 유지하려고 트랜잭션을 Controller까지 무분별하게 확장하지 않는다.

## 분산 정합성

DB와 메시지 브로커/외부 서비스 간 원자성이 필요하면 단순 try-catch가 아니라 Outbox, Saga, 보상 트랜잭션, Idempotency 등을 검토한다.
