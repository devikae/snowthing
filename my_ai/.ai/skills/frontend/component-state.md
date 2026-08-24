# Frontend Component & State Skill

## Component

- 컴포넌트는 하나의 명확한 UI 책임을 갖게 한다.
- 단순 재사용 가능성만으로 지나치게 잘게 쪼개지 않는다.
- 거대한 페이지 컴포넌트에 API 호출, validation, 모든 상태, 렌더링을 몰아넣지 않는다.
- Presentation과 비즈니스/데이터 로직 분리가 필요한지 검토한다.

## State

상태를 구분한다.

- Local UI State
- Server State
- URL State
- Form State
- Global Client State

모든 상태를 Redux/Zustand 같은 전역 Store에 넣지 않는다.
Server State는 React Query/TanStack Query 등 전용 도구가 더 적절한지 검토한다.
URL로 공유/복원이 필요한 필터·페이지·검색 조건은 URL State를 고려한다.

## React 주의

- 불필요한 Effect를 만들지 않는다.
- `useEffect`를 값 계산 용도로 습관적으로 사용하지 않는다.
- dependency 누락으로 stale closure가 발생하지 않도록 한다.
- index를 list key로 사용하는 것이 안전한지 확인한다.
