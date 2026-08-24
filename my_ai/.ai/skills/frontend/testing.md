# Frontend Testing Skill

## 테스트 우선순위

- 사용자가 실제로 하는 행동과 결과를 중심으로 검증한다.
- 구현 내부 state나 private 함수 자체보다 화면에 보이는 결과와 접근 가능한 요소를 기준으로 한다.

## 구분

- Unit: 순수 유틸/복잡한 계산
- Component: 렌더링과 interaction
- Integration: API mocking을 포함한 화면 흐름
- E2E: 실제 브라우저 기준 핵심 사용자 시나리오

## 필수 케이스

- loading
- error
- empty
- success
- form validation
- 권한별 UI
- 중복 클릭/submit
- navigation 및 URL state
