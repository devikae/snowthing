# 중고장터 구현 계획

- 문서 번호: `PLAN-USED-MARKET-001`
- 상태: Draft
- 작성일: 2026-09-28
- 전제: 설계 문서와 `결정요구.md` 검토 후 구현 시작

이 문서는 구현 순서와 검증 경계를 정한다. 아직 애플리케이션 코드나 운영 SQL을 작성하지 않는다.

## 1. 구현 원칙

- DB → 도메인 → Repository → Service → Controller → Frontend 순으로 진행한다.
- 각 단계에서 테스트를 함께 작성한다.
- `PostService`에 중고거래 조건을 계속 추가하지 않고 `market` 도메인에 전용 서비스를 둔다.
- 기존 댓글과 이미지 코드는 재사용하되 중고장터 조건부 정책을 명시적으로 추가한다.
- 일반 게시판 동작을 바꾸는 부분은 회귀 테스트를 먼저 보강한다.
- 운영 스키마는 애플리케이션 자동 DDL이 아니라 버전 SQL로 반영한다.

## 2. 단계별 계획

### 1단계. 기준 계약 확정

- `결정요구.md`의 구현 전 확인 항목 확정
- 요구사항·도메인·ERD·API 문서 동기화
- API 필드명과 ErrorCode 번호 확정
- 화면 와이어프레임 검토

완료 기준: 설계 문서에 `Draft` 대신 `Accepted` 표시 가능

### 2단계. DB 마이그레이션

- `004_migration_used_market.sql` 작성
- `market_category`, `market_listing` 생성
- `MARKET` 게시판 분류와 상품 분류 기준 데이터 추가
- FK·UNIQUE·CHECK·인덱스 추가
- checksum runner로 기존 운영 스키마부터 적용 테스트

완료 기준: 최초 적용, 재실행 skip, checksum 변경 실패, CHECK 위반 검증

### 3단계. 도메인 모델

예상 패키지:

```text
domain/market/
├─ entity/
│  ├─ MarketListing.java
│  ├─ MarketCategory.java
│  ├─ ProductCondition.java
│  ├─ TransactionMethod.java
│  └─ TradeStatus.java
├─ repository/
├─ service/
├─ controller/
└─ dto/
```

- Entity 불변식과 상태 변경 메서드 구현
- `MarketListing.version`에 `@Version`
- 생성자·수정 메서드에서 가격 상한 20,000,000원과 연락처 1~30자 검증
- `DAMAGED` 상태에도 별도 하자 설명 필드를 요구하지 않고 본문을 사용
- ErrorCode와 타입 기반 예외 추가
- 단위 테스트 작성

완료 기준: 도메인 불변식 단위 테스트 통과

### 4단계. Repository와 조회

- 판매글 상세 JOIN 조회
- 목록 OFFSET 조회와 COUNT 쿼리
- 상품 분류·상품 상태·거래 상태·제목 검색 필터
- 판매 완료 포함 규칙 구현
- 목록 projection에서 판매자와 첫 이미지 조회
- N+1과 정렬 안정성 테스트
- MySQL EXPLAIN 확인

완료 기준: 필터 행렬·페이징·COUNT 정합성 통합 테스트 통과

### 5단계. 명령 서비스

- 등록 트랜잭션
- 전체 수정과 이미지 교체
- `OPTIMISTIC_FORCE_INCREMENT` 또는 동등한 Post-only 수정 버전 증가
- 거래 상태 PATCH
- Soft Delete
- 작성자·관리자 권한 검증
- 충돌 시 전체 롤백

완료 기준: 경쟁 트랜잭션에서 하나만 성공하고 실패 요청의 Post 변경까지 롤백

### 6단계. 기존 도메인 우회 차단

- 일반 게시글 목록·전체글·베스트에서 `MARKET` 제외
- 일반 게시글 생성·수정·상세에서 `MARKET` 거부
- ReactionService에서 중고장터 추천 거부
- CommentService에서 중고장터 로그인·비익명 정책 적용
- 대댓글 분리 API까지 같은 접근 정책 적용

완료 기준: 일반 게시판 기존 기능 회귀 테스트와 우회 보안 테스트 통과

### 7단계. 이미지 연결 검증

- 판매글당 최대 5장
- CloudFront host와 원본 prefix 검증
- 중복 URL 거부
- 목록 썸네일과 상세 원본 URL 변환
- 사진 없는 글 null 반환
- 1차 구현은 기존 게시글과 같은 `imageUrls` 요청 계약 유지
- `imageKey` 전환 여부는 이미지 저장 구조를 재검토할 때 후속 결정

완료 기준: 이미지 0·5·6장 경계와 외부 URL 테스트 통과

### 8단계. REST API

- 비로그인 홈용 공개 썸네일 미리보기 API
- 상품 분류 API
- 목록·검색 API
- 등록·상세·수정·상태 변경·삭제 API
- 관리자 운영 상태 API
- 공통 오류 응답 연결
- CSRF와 인증 설정
- 상세 응답 `private, no-store`
- 공개 미리보기는 `publicId`와 `thumbnailImageUrl`만 반환하고 최대 4건으로 제한

완료 기준: MockMvc 권한 행렬과 계약 테스트 통과

### 9단계. 프런트엔드 목록

- `/market` 인증 처리
- 검색·필터·OFFSET 페이지
- 목록형과 앨범형
- localStorage 보기 설정
- 모바일 목록형 강제
- 로딩·빈 결과·오류 상태

완료 기준: 보기 전환 시 추가 API 요청이 없고 모바일에서 앨범형이 노출되지 않음

### 10단계. 작성·수정·상세

- 판매글 폼과 교차 필드 검증
- 이미지 0~5장 업로드·순서
- 상세 갤러리·판매자·연락처
- 거래 상태 변경
- 댓글 컴포넌트 연결
- 409 충돌 화면

완료 기준: 정상 거래 흐름과 충돌 E2E 통과

### 11단계. 전역 메뉴와 홈

- 헤더 중고장터 진입점
- 로그인 여부와 관계없이 홈에 최신 판매 중·예약 중 상품 썸네일만 최대 4건 표시
- 비로그인 사용자가 썸네일을 누르면 return URL을 보존한 로그인 화면으로 이동
- 로그인 회원이 썸네일을 누르면 판매글 상세로 이동
- 기존 정적 중고장터 예시 제거

완료 기준: 공개 미리보기 응답에 제목·가격·상태·판매자·연락처가 포함되지 않고, 로그인 여부에 따라 클릭 경로가 올바르게 갈림

### 12단계. 전체 검증과 배포 준비

- 백엔드 Spotless·전체 테스트
- 프런트 ESLint·production build
- MySQL 마이그레이션 검증
- API와 UI E2E
- 다크모드·모바일 확인
- `docs/project/work.md` 결과 기록

운영 반영은 migration Environment 승인을 받은 뒤 스키마 마이그레이션을 먼저 실행하고 애플리케이션을 배포한다.

## 3. 권장 커밋 단위

1. `docs: 중고장터 설계 확정`
2. `feat: 중고장터 스키마와 도메인 모델 추가`
3. `feat: 중고장터 조회와 명령 API 구현`
4. `fix: 게시판 댓글 추천 경로의 중고장터 정책 적용`
5. `feat: 중고장터 목록과 보기 전환 UI 구현`
6. `feat: 중고장터 작성 상세 수정 UI 구현`
7. `test: 중고장터 통합 및 E2E 검증 보강`

DB·백엔드·프런트 전체를 한 커밋에 넣지 않는다. 각 커밋에서 테스트 가능한 경계를 유지한다.

## 4. 주요 위험과 대응

| 위험 | 대응 |
|---|---|
| 일반 전체글에 판매글 노출 | MARKET 제외 Repository 테스트 |
| 일반 추천 API 우회 | ReactionService 도메인 검사 |
| 공개 댓글 API 우회 | CommentService 조건부 인증 |
| Post만 수정되고 거래 정보 실패 | 단일 트랜잭션 |
| 제목만 수정해 version 미증가 | 명시적 force increment와 테스트 |
| 연락처 캐시·로그 노출 | 상세 no-store, Request body 로깅 금지 |
| 외부 이미지 URL 삽입 | 1차 구현은 host·prefix 검증, imageKey 전환은 후속 결정 |
| 필터 COUNT 불일치 | content·count 동일 predicate 테스트 |
| 깊은 OFFSET 지연 | 100페이지 제한, 실행계획 측정 |
| 운영 migration 실패 | 배포 선행 실행과 checksum, 앱 배포 차단 |

## 5. 롤백 경계

- 스키마는 기존 코드가 참조하지 않는 테이블을 추가하는 expand 변경이다.
- 새 애플리케이션 배포 실패 시 이전 이미지로 롤백할 수 있다.
- 이미 작성된 판매글 데이터가 있으면 테이블을 자동 DROP하지 않는다.
- 수정 마이그레이션은 forward fix를 우선한다.
- 공개 메뉴와 화면은 API 안정성을 확인한 뒤 활성화할 수 있다.

## 6. 완료 정의

- 설계 문서와 실제 계약 일치
- 결정요구 구현 전 항목 확정
- DB 제약과 애플리케이션 검증 일치
- 백엔드·프런트 전체 검사 통과
- 일반 게시판·댓글·추천 회귀 없음
- 회원 전용 접근과 우회 경로 차단 검증
- 낙관적 락 실제 경쟁 테스트 통과
- 운영 마이그레이션 재현 자료 확보
