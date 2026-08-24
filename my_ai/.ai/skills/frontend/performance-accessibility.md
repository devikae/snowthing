# Frontend Performance & Accessibility Skill

## 성능

- `useMemo`, `useCallback`, memo를 무조건 적용하지 않는다. 실제 렌더링 비용과 참조 안정성이 필요한 경우 사용한다.
- 큰 리스트는 pagination/virtualization을 검토한다.
- 이미지 크기와 lazy loading을 고려한다.
- bundle size와 불필요한 dependency를 확인한다.
- SSR/CSR/SSG 선택은 데이터 특성과 SEO, 개인화, 캐시 가능성 기준으로 판단한다.

## 접근성

- 의미 있는 HTML 요소를 우선한다.
- 버튼을 div click으로 대체하지 않는다.
- 키보드 탐색 가능성을 확인한다.
- input과 label 연결을 보장한다.
- 색상만으로 상태를 전달하지 않는다.
- modal/dialog의 focus trap과 focus restore를 고려한다.
