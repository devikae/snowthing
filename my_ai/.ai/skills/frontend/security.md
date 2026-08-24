# Frontend Security Skill

## XSS

- 사용자 입력을 HTML로 직접 삽입하지 않는다.
- React의 `dangerouslySetInnerHTML` 사용은 필요성을 검토하고 sanitization 전략을 명확히 한다.
- Markdown/에디터 렌더링은 HTML injection 가능성을 검토한다.

## Token/Session

- Access Token 저장 위치를 결정할 때 XSS와 CSRF Trade-off를 설명한다.
- LocalStorage는 JavaScript로 접근 가능하므로 XSS 발생 시 탈취 가능성을 고려한다.
- HttpOnly Cookie는 JS 접근을 막지만 Cookie 자동 전송으로 CSRF 정책을 함께 설계해야 한다.
- 민감한 인증정보를 URL query parameter에 넣지 않는다.

## 환경변수

- 프론트 번들에 포함되는 환경변수는 Secret이 아니다.
- `NEXT_PUBLIC_*`, `VITE_*` 등 client-exposed 변수에 비밀키를 넣지 않는다.

## 기타

- 외부 링크의 `target="_blank"` 사용 시 필요한 `rel` 설정을 검토한다.
- 파일 다운로드/업로드 URL을 신뢰하지 않는다.
- 민감정보를 브라우저 console에 출력하지 않는다.
