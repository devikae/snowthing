# 카풀 구현 계획

## 1단계: 문서·계약

- `CARPOOL` 카테고리와 Post 확장 규칙 확인
- `CarpoolDetail` ERD와 production migration 작성
- ErrorCode·요청/응답 DTO 계약 확정
- 오피넷·카카오모빌리티 환경변수와 timeout 정책 확정

## 2단계: 백엔드 도메인

- CarpoolDetail Entity와 Repository
- 카풀 생성·조회·수정·삭제 Service/Controller
- Post와 CarpoolDetail 동일 트랜잭션 처리
- 입력 검증과 연락처 노출 정책
- 비용 계산 순수 도메인 서비스

## 3단계: 외부 연동

- 오피넷 Client와 캐시
- 카카오모빌리티 Route Client
- 외부 장애 fallback 및 로그
- API 키·timeout·응답 매핑 테스트

## 4단계: 프런트엔드

- 카풀 목록·상세·작성·수정 화면
- 자동 계산·수동 보정 UI
- 댓글 연결
- 연락처 공개 동의 UI

## 5단계: 검증·배포

- 단위·통합·브라우저 테스트
- 빈 RDS migration 검증
- 운영 설정에 외부 API 키 주입
- 외부 HTTPS에서 게시글·댓글·계산·연락처 노출 검증

구현 중 API 제공자의 실제 요청 형식이나 비용 정책이 설계와 다르면 코드를 먼저 바꾸지 않고 문서와 계약을 갱신한 뒤 진행합니다.
