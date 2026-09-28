# 리조트캠 설계 문서

공식 리조트 웹캠을 SnowThing에서 리조트별로 모아 보는 공개 기능의 설계 문서입니다. SnowThing은 영상 원본을 저장하거나 중계하지 않고, 브라우저가 공개된 외부 영상 소스에 직접 연결합니다.

## 문서 목록

1. [요구사항](01_requirements.md)
2. [도메인 모델](02_domain-model.md)
3. [ERD](03_erd.md)
4. [API 명세](04_api-spec.md)
5. [외부 영상 및 접근 정책](05_external-stream-policy.md)
6. [테스트 계획](06_test-plan.md)
7. [프런트엔드 명세](07_frontend-spec.md)
8. [구현 계획](08_implementation-plan.md)
9. [외부 영상 연결 아키텍처 ADR](ADR-301-external-stream.md)
10. [결정 요구](결정요구.md)

## 확정 범위

- 비로그인 사용자를 포함한 공개 조회
- 12개 국내 리조트와 소속 카메라의 DB 마스터 데이터 관리
- 리조트 전체 카메라 그리드와 개별 카메라 화면
- PC 가시 영역 음소거 자동재생, 모바일 사용자 선택 재생
- HLS, YouTube, iframe, 외부 링크 재생 유형
- 기존 `/resort`를 `/resort-cam`으로 연결

## 제외 범위

- 관리자 화면과 관리 API
- 즐겨찾기와 사용자 지정 4분할
- 날씨, 영상 캡처, PIP
- 서버 프록시·재송출·녹화
- 자동 링크 상태 점검
- 슬로프 마스터 데이터

