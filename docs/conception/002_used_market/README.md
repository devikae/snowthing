# 중고장터 설계 문서

- 문서 상태: Reviewed
- 작성일: 2026-09-28
- 대상 브랜치: `feature/used-market`
- 대상 도메인: `com.ikae.snowthing.domain.market`

## 문서 구성

중고장터는 설계 합의가 끝난 항목부터 문서로 확정합니다. 결정하지 않은 내용을 구현 편의에 맞춰 임의로 채우지 않습니다.

1. [`01_requirements.md`](./01_requirements.md): 제품 범위와 업무 규칙
2. `02_domain-model.md`: 도메인 경계, 엔티티, 값 객체, 상태 전이
3. `03_erd.md`: 논리·물리 ERD와 인덱스
4. `04_api-spec.md`: API 계약과 오류 응답
5. `05_access-and-image-policy.md`: 회원 전용 접근과 이미지 전달 정책
6. `06_test-plan.md`: 단위·통합·보안·동시성 테스트 계획
7. `07_frontend-spec.md`: 목록형·앨범형·작성·상세 화면 설계
8. `08_implementation-plan.md`: 구현 순서와 검증 경계
9. `ADR-201 post-extension.md`: 기존 게시글과 중고거래 확장 테이블을 1:1로 연결하는 결정
10. `결정요구.md`: 1차 구현 확정 사항과 후속 결정 시점

## 현재 진행 상태

- 제품 요구사항과 전체 설계 초안: 작성
- 1차 구현 결정: `결정요구.md` 1장 확정
- 후속 결정: 이미지 식별자 계약과 운영 정책은 `결정요구.md`에 보류
- 애플리케이션 코드 및 DB 마이그레이션: 미작성
