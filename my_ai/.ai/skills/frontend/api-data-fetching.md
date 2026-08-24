# Frontend API & Data Fetching Skill

## 원칙

- API 호출 로직을 UI 이벤트 안에 무질서하게 중복 작성하지 않는다.
- loading/error/empty/success 상태를 명확히 처리한다.
- 요청 취소와 오래된 응답 race condition 가능성을 고려한다.
- 서버 캐시와 브라우저 메모리 캐시의 의미를 구분한다.

## Mutation

- optimistic update를 사용할 경우 rollback 전략을 준비한다.
- 중복 submit을 막는다.
- 요청 실패 시 사용자가 재시도 가능한지 고려한다.

## API Contract

- 백엔드 DTO와 프론트 타입이 의미적으로 맞는지 확인한다.
- nullable/optional 차이를 무시하지 않는다.
- 날짜 문자열을 단순 문자열로 계속 다루다 timezone 버그가 생기지 않는지 검토한다.
