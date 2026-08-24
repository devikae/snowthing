# Backend Performance Skill

## 원칙

- 성능 최적화는 측정 없이 추측으로 진행하지 않는다.
- CPU, Memory, DB, Network, Lock, GC 등 병목 위치를 먼저 확인한다.
- 단순히 캐시를 붙이는 것으로 성능 문제를 덮지 않는다.

## 확인 항목

- N+1
- 불필요한 전체 컬럼 조회
- 대량 결과 반환
- OFFSET 깊은 페이지
- 반복 외부 API 호출
- 동기 처리로 긴 응답 시간
- 불필요한 객체 생성
- connection pool 고갈
- thread pool 고갈
- 캐시 정합성

## 캐시

캐시 도입 시 반드시 정의한다.

- 캐시 대상
- key
- TTL
- 갱신 전략
- 무효화 전략
- stale data 허용 범위
- cache stampede 대응
- 장애 시 fallback
