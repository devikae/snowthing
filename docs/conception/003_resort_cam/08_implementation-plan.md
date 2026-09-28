# 리조트캠 구현 계획

본 문서는 구현 순서만 정의합니다. 각 단계 착수 전 사용자 확인을 받습니다.

## 1단계: 소스 호환성 표

- 12개 리조트 카메라 후보 수집
- SnowThing HTTPS Origin에서 HLS·CORS·iframe 호환성 확인
- `source_type`, 코드, 이름, 순서, fallback URL 확정

## 2단계: 운영 마이그레이션

- `005_migration_resort_cam.sql`
- 기존 리조트 코드 백필
- 신규 리조트와 `resort_camera` 생성
- 반복 실행 및 제약 검증 스크립트

## 3단계: 백엔드 조회

- `Resort` 확장과 `ResortCamera` 엔티티
- `CameraSourceType`
- Repository 정렬 조회
- Service DTO 변환과 방어적 복사
- 공개 Controller와 Security 허용
- 단위·통합 테스트

## 4단계: 프런트엔드 기반

- `hls.js` 도입
- 라우트와 공통 레이아웃
- 리조트 탐색·모바일 드로어
- 전체/개별 화면과 오류 UI

## 5단계: 재생 제어

- 플레이어 어댑터
- IntersectionObserver와 최대 6개 제한
- 모바일 선택 재생
- 라우트 변경 시 자원 정리

## 6단계: 기존 화면 연결

- `/resort` 리다이렉트
- 상단 메뉴와 홈 링크 변경
- 기존 가상 리조트 데이터 제거

## 7단계: 검증

- 마이그레이션 검증
- 백엔드 전체 테스트와 Spotless
- 프런트엔드 ESLint와 production build
- PC·모바일 실제 외부 영상 수동 검증
- 다크 모드와 오류 fallback 확인

