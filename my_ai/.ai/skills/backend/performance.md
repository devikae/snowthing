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

## 파일·객체 응답 메모리

- S3·파일·HTTP 응답을 `readAllBytes()`로 읽으면 객체 전체가 JVM heap에 올라간다는 점을 계산한다.
- DTO 방어적 복사와 accessor 복사가 겹치면 요청당 동일한 큰 배열이 여러 개 생길 수 있으므로 실제 복사 횟수를 확인한다.
- 최대 객체 크기 × 동시 요청 수 × 복사본 수를 기준으로 최악의 heap 사용량을 검토한다.
- 큰 정적 파일은 애플리케이션 중계보다 CDN·Object Storage 직접 전달을 우선 검토한다.
- 애플리케이션 중계가 필요하면 크기 상한을 먼저 확인하고 streaming response와 backpressure를 적용한다.
- `InputStream`, `ResponseInputStream` 등 외부 자원은 반드시 try-with-resources로 닫고, 읽기 도중 예외가 발생해도 close되는 테스트를 작성한다.

## 캐시와 접근 제어

- 인증·인가 결과에 따라 달라지는 응답은 공유 캐시가 재사용할 수 있는 `public`으로 설정하지 않는다.
- 비공개 응답은 요구사항에 따라 `private`, `no-store`, 짧은 TTL 중 하나를 선택하고 이유를 기록한다.
- 공개 CDN 캐시는 공개 가능한 객체에만 적용하고 회원 전용 객체와 prefix·cache behavior를 분리한다.
