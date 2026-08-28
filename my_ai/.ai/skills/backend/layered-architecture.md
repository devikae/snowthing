# Layered Architecture Skill

## Service와 Web 기술 결합 금지

Service 레이어 내부에서 다음과 같은 Servlet/Web Session API를 직접 조작하지 않는다.

- `HttpSession`
- `SecurityContextHolder`
- `changeSessionId()`
- 기타 웹 요청/응답 객체 직접 조작

웹 세션 및 인증 컨텍스트와 직접 연결되는 처리는 Controller 또는 Security 레이어가 담당한다.
Service는 비즈니스 로직에 집중한다.

## Layer Skip 금지

Controller가 Repository를 직접 주입받아 호출하지 않는다.

기본 흐름:

Controller → Service → Repository

## Entity 직접 반환 금지

Controller에서 Entity를 클라이언트 응답으로 직접 반환하지 않는다.

DTO 또는 명시적인 응답 모델로 변환한다.

## 이유

- 웹 계층과 비즈니스 계층의 결합 방지
- 테스트 용이성 향상
- Entity 구조 변경이 API 계약에 직접 전파되는 것을 방지
- 영속성 모델과 외부 API 모델의 책임 분리
