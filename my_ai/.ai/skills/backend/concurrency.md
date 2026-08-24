# Concurrency Skill

## 기본 원칙

동시에 여러 요청이 들어올 수 있는 기능은 단일 요청 기준으로만 설계하지 않는다.

## 반드시 검토할 사례

- 재고 감소
- 좋아요/추천 카운트
- 쿠폰 발급
- 포인트 차감
- 중복 가입
- 중복 결제/주문
- 게시글 UP 횟수 제한
- 좌석/예약

## 해결 수단

상황에 따라 다음을 비교한다.

- DB Unique Constraint
- Atomic Update
- Optimistic Lock
- Pessimistic Lock
- Redis Atomic Operation / Distributed Lock
- Message Queue 직렬화
- Idempotency Key

## 규칙

- `synchronized`를 서버가 여러 대일 수 있는 환경의 만능 해결책으로 사용하지 않는다.
- Redis Lock을 도입하기 전에 DB 제약이나 atomic query로 해결 가능한지 확인한다.
- Lock을 사용하면 lock 범위, timeout, deadlock, retry 정책을 함께 설계한다.
- Counter 증가/감소는 Read-Modify-Write 경쟁 조건을 확인한다.
