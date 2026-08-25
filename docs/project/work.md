## 2026-07-30

### 완료

- `AGENTS.md`와 `docs/project/project.md` 지침 확인.
- 프론트엔드/백엔드 분리 개발 방향 확인.
  - `backend/`: Spring 코드 예정
  - `frontend/`: Next.js 코드 예정
  - 추후 Docker Compose로 통합 예정
- `frontend/`에 Next.js 목업 프로젝트 생성.
- Cohere 참고 디자인 방향을 반영한 Snowthing 대시보드 목업 구현.
  - AI 게시글 요약
  - 리조트별 리프트 혼잡도
  - 카풀 비용 계산
  - 장비 VS
  - 같이타요/강습/보더명함 매칭 허브
- `npm run lint` 통과.
- `npm run build` 통과.

### 이슈 / 확인 필요

- 현재 루트의 Spring 프로젝트는 아직 `backend/`로 이동하지 않음.
- `npm audit --omit=dev` 기준 Next 최신 의존성 체인에서 high 취약점 3건이 보고됨.
  - `postcss`
  - `sharp`
  - 현재 `npm audit fix --force`는 Next를 오래된 버전으로 낮추는 breaking change를 제안하므로 적용하지 않음.
- 추후 실제 개발 전에 저장소 구조를 `backend/`, `frontend/`로 정리할 필요 있음.

### 추가 작업

- 기존 목업 디자인이 산만해서 `frontend/app/page.tsx`, `frontend/app/globals.css`를 전면 재작성.
- 운영 대시보드 느낌을 줄이고 사용자 커뮤니티 홈에 가깝게 변경.
  - 상단 현재 상태 요약
  - AI 게시판 흐름 요약
  - 게시글 피드
  - 리조트 상태
  - 같이타요 매칭
  - 카풀 계산 카드
- 그라디언트와 장식 요소를 줄이고, 밝은 앱 UI 기반으로 정리.
- 재검증 결과:
  - `npm run lint` 통과
  - `npm run build` 통과

### 추가 수정

- 프로젝트 해석을 정정.
  - Snowthing은 스노보드 커뮤니티가 중심.
  - 같이타요는 게시판이 아니라 조건 기반 매칭 시스템.
  - 카풀은 모집글과 비용 계산기가 연결되는 커뮤니티 기능.
  - 리프트 줄 제보는 리조트별 보조 정보 기능.
- `frontend/app/page.tsx`를 다시 수정.
  - 자유/익명/카풀/장비 VS/맛집 게시판 탭 구성.
  - 같이타요를 매칭 조건, 추천 상대, 매칭 점수 중심으로 분리.
  - AI 게시글 요약을 커뮤니티 흐름 요약으로 배치.
- `frontend/app/globals.css` 색상 재정리.
  - 기존 베이지/보라 계열 제거.
  - 화이트/차콜/아이스 블루 계열로 변경.
- 재검증 결과:
  - `npm run lint` 통과
  - `npm run build` 통과

### 서버 기동 확인

- 프론트엔드 Next.js 개발 서버 실행 확인.
  - URL: `http://localhost:3000`
  - HTTP 확인 결과: `200`
- 백엔드 Spring Boot 서버 실행 확인.
  - URL: `http://localhost:8080`
  - 루트 경로(`/`)는 아직 컨트롤러가 없어 `404` 응답.
  - 애플리케이션 자체는 Tomcat 8080 포트에서 정상 기동 확인.

## 2026-08-06

### 완료

- `obra/superpowers` 기반의 브레인스토밍 스킬 도입 및 규칙 체계화.
- `.agents/skills/brainstorming/SKILL.md` 스킬 파일 생성 완료.
  - 코드 수정 전 1:1 대화식 질의응답, 2~3가지 접근법 제안, 설계 승인 절차(HARD-GATE) 명시.
- `docs/conception/problem-definition.md` 작성 완료.
  - 서비스 한 문장 정의, 문제 정의(Why), 핵심 사용자 정의(Target User) 문서화.
- `docs/conception/mvp-scope.md` 작성 완료.
  - 1차 MVP 포함 기능(In-Scope), 차후 로드맵(Out-of-Scope), 1차 MVP 사용자 시나리오 문서화.
- `docs/studySessionAuth260806.md` 작성 완료.
  - 노션(Notion)에 복사하여 공부할 수 있는 세션 인증 정책, 쿠키 보안 속성, 기술적 트레이드오프, 기술 부채 TIL 학습 파일 생성.
- `docs/conception/authentication-session.md` 작성 완료.
  - 세션 기반 인증 흐름, 쿠키 보안 정책, 권한 검증(소유자 검증), 세션 만료 및 로그아웃 설계 문서화.
- `docs/conception/erd.md` 작성 완료.
  - 총 10개 엔티티(회원, 리조트, 라이딩스타일, 카테고리, 게시글, 이미지, 추천/비추천, 댓글) Mermaid ERD 및 상세 스키마, 역정규화/Soft Delete 설계 문서화.
- `docs/conception/sprint1-spec.md` 작성 완료.
  - 이번 주 애자일 개발 목표인 [회원가입/세션 로그인] + [게시글 CRUD] + [댓글 CRUD] 상세 실행 명세서 및 QA 체크리스트 문서화.
- `docs/studyArchConcepts260806.md` 작성 완료.
  - "No Silver Bullet" 철학 기반 FK 무결성, N:M 중계 테이블 vs Bitmask/JSONB/Redis Set 대안 장단점, 역정규화, JWT 서명/검증 원리, Redis, 쿠키 보안 깊이 있는 TIL 스토리텔링 학습 가이드 생성.
- `docs/studyPkStrategy260807.md` 작성 완료.
  - AUTO_INCREMENT 보안 문제(ID 추측 공격), 복합 PK JPA 개발 지옥, 4가지 PK 전략(AUTO_INCREMENT+Public ID, UUID, TSID, 중계테이블 단일대리키) 비교 분석 TIL 학습 가이드 생성.
- `docs/conception/domain-model.md` 작성 완료.
  - Snowthing 4대 핵심 도메인 영역(회원/프로필, 게시판/커뮤니티, 리조트/현장정보, 소셜/매칭) 및 Bounded Context 명세 문서화.
- `docs/conception/erd.md` 최종 업데이트 완료.
  - PK 전략(내부 BIGINT id + 외부 보안용 public_id UUID), N:M RDBMS 중계 테이블 정규화(`member_resort`, `member_riding_style` 단일 대리키 id 적용), `crew` 마스터 테이블 및 `member.crew_id`/`crew_role` 반영.
- `docs/studyDomainErd260807.md` 작성 완료.
  - 노션(Notion)에 복사하여 적을 핵심 도메인 목록, ERD 초안, 주요 제약조건, 게시글과 회원 관계 검토 정리 문서 생성.
- `docs/conception/api-spec.md` 작성 완료.
  - RESTful 명사형 URL 체계, 세션 기반 인증(`/api/auth`), 비회원 익명 작성 지원, 비동기 추천/비추천 처리, 5가지 HTTP Status Code별 표준 에러 응답 규격 명세 완료.
- `docs/studyApiDesign260808.md` 작성 완료.
  - 노션(Notion) 복사용 REST API URL 명사 중심 설계 원칙, `/api` 접두사 3가지 이유, 비동기 추천 기법(낙관적 UI / `@Async`), 글로벌 에러 응답 객체 TIL 문서 생성.
- `docs/studyArchPrinciples260810.md` 작성 완료.
  - 3단계 딥다이브 (1단계: 물리 작동 원리, 2단계: 2차/3차 치명적 한계, 3단계: 최종 실무 아키텍처) 기반 동시성 락, UUID v7 인덱스, N+1 쿼리, N:M 중계 테이블 종합 가이드 생성.
- `database/ddl.sql` 및 `docker-compose.yml` 작성 완료.
  - 최상위 루트 디렉토리 동등 계층으로 `database` 폴더 배치. 11개 엔티티 DDL 작성 및 MySQL 8.0 도커 컨테이너 최초 실행 시 `/docker-entrypoint-initdb.d/` 자동 SQL 마운트 실행 환경 구축.
- `docs/studySessionFlow260810.md` 및 `docs/conception/authentication-session.md` 작성 완료.
  - 마크다운 및 노션 환경에서 시각적으로 다이어그램으로 렌더링되는 세션 인증 플로우차트(Mermaid Flowchart TD) 작성 완료.
- `docs/conception/system-architecture.md` 및 `docs/studySystemArch260810.md` 작성 완료.
  - Next.js 프론트엔드, Nginx 리버스 프록시, Spring Boot 백엔드, Redis 비동기 캐시, MySQL 8.0 전체 시스템 구성도(Mermaid System Architecture) 작성 완료.
- `docs/conception/sprint01/07.system-architecture.md`, `08.week1-schedule.md`, `README.md` 수정 완료.
  - 백엔드 주 버전을 최신 표준인 **Spring Boot 4.x (Java 21 LTS)**로 전면 상향 및 일치 완료.


- `README.md` 프로젝트 메인 가이드 작성 완료.
  - 개요, 기술스택, 디렉토리 구조, 아키텍처 특징, 및 Redis 도입 Rationale(Write-Behind, 멱등성, Scale-out 분산 세션 필연성) 명세 완료.
- `.gitignore` 수정 완료.
  - `docs/study*.md` 노션 학습 문서 커밋 제외 설정, 민감 정보(`*.env`, `application-secret*.yml`, `*.pem`, `*.key`), 빌드 및 IDE 설정 제외 완비.
- `AGENTS.md` 공통 행동 강령 18, 19번 반영 완료.
  - 18번: AI 독단적 결정 금지 및 사전 의논 원칙 명시.
  - 19번: 현업 표준 방식 제시, 겉핥기 축약 금지, 물리적 원리/장점/Trade-off/유즈케이스 딥다이브 상세 설명 원칙 명시.
- `docs/conception/sprint01/` 문서 전면 정제 완료.
  - 문서 내 조잡하고 과장된 '0.0001초', '0.001초', '초고속', '지옥 방지', '극상', '테러' 등의 비엔지니어링 수식어 및 통속적 표현을 모조리 전면 삭제하고, 정돈된 엔지니어링 표준 명세로 교정 완료.
- `Sprint 1 Day 1` 백엔드 회원 엔티티 구현 완료 (`com.ikae.snowthing`).
  - `BaseTimeEntity.java` 공통 JPA Auditing 엔티티 생성.
  - `Role.java` (`GUEST`, `ROLE_USER`, `ROLE_ADMIN`) 권한 Enum 구현.
  - `MemberStatus.java` (`ACTIVE`, `SUSPENDED`, `WITHDRAWN`) 계정 상태 Enum 구현.
  - `Member.java` 회원 메인 JPA 엔티티 구현 (PK Dual `id` + `publicId` UUID, `@Enumerated(EnumType.STRING)` 매핑 완비).
- `docs/conception/sprint01/04.erd.md` 데이터 타입 전면 교정 완료.
  - Mermaid ERD 다이어그램 및 엔티티 명세 표 내의 비엔지니어링/자바식 타입 표기(`string`, `bigint` 등)를 실제 **MySQL 8.0 DDL 물리 데이터 타입(`BIGINT`, `VARCHAR(36)`, `VARCHAR(100)`, `DATETIME`, `BOOLEAN`, `TEXT`, `LONGTEXT`, `INT` 등)**으로 100% 정밀 교정 완비.
- `Sprint 1 Day 1` 회원가입 요청 유효성 검증(`POST /api/members`) 및 비즈니스 서비스 완료.
  - `MemberSignUpRequest.java` DTO 구현 (`@NotBlank`, `@Email`, `@Size` 4대 유효성 검사 및 BCrypt `toEntity` 변환 완비).
  - `MemberSignUpResponse.java` 성공 응답 DTO 구현.
  - `MemberRepository.java` 이메일/닉네임 존재 여부 쿼리 메소드(`existsByEmail`, `existsByNickname`) 추가.
  - `MemberService.java` 회원가입 비즈니스 서비스 구현 (중복 검증 ➔ BCrypt 암호화 ➔ DB 저장).
  - `MemberController.java` REST API 컨트롤러 구현 (`POST /api/members`, `@Valid` 검증).
  - `SecurityConfig.java` BCryptPasswordEncoder 빈 등록 및 기본 스프링 시큐리티 설정 완비.
  - `MemberServiceTest.java` 및 `MemberControllerTest.java` 단위/통합 테스트 작성 ➔ **`BUILD SUCCESSFUL` (100% 통과 실증 완료)**.
- `docs/study/studyMemberAuthValidation260815.md` 노션 복사용 독학 스터디 문서 전면 보완 완료 (규칙 8, 9, 10번 완벽 준수).
  - **PART 0 신설**: 11개 전 데이터베이스 테이블 관계도(Mermaid ERD) 및 테이블별 제약조건(PK, FK, UNIQUE, NOT NULL, CHECK) 명세 추가.
  - N:M 중계 테이블 단일 대리키 `id` + 복합 유니크 제약조건 설계 이유, 댓글 `parent_id` 의 `ON DELETE SET NULL` 삭제 정책 이유, `writer_ip` 및 BCrypt 비회원 비번, 역정규화 카운트 필드 등 **설계 비하인드(Why & How) 상세 해설 완비**.
  - 마크다운 문서 내 인용 코드 블록(Code Snippets)마다 각 라인/어노테이션/메소드를 왜 이렇게 작성했고 왜 이렇게 사용하는지 1:1 정밀 해설 주석(`// 💡 [왜 ... 사용하는가?]`) 배치 완료.
- `Sprint 1 Day 2` 이메일 동시성 예외 처리 보완 & 3대 검증 테스트 완수 (`BUILD SUCCESSFUL` 실증 완료).
  - `MemberService.java` `saveAndFlush()` 및 `DataIntegrityViolationException` 캐치 ➔ 동시 가입 시 0.0001초 DB UNIQUE 인덱스 방어선 구축.
  - `MemberServiceSignUpVerificationTest.java`: 저장된 비번이 `$2a$` BCrypt 해시 포맷인지 검증, 응답 DTO 리플렉션 검사를 통해 internal `id` 비노출 검증 완비.
  - `MemberConcurrencyTest.java`: `CountDownLatch` + `ExecutorService` 10개 멀티스레드 벼락 동시 가입 시 **정확히 1건만 성공, 9건 예외 차단, DB 1건 저장** 실증 완료.
- `Sprint 1 Day 2` 내 프로필 수정 API/UI 및 인증/권한 실패 전용 실증 테스트 수트 완비.
  - `MemberProfileUpdateRequest.java` DTO 및 `MemberController.java` (`PUT /api/members/me`) 구현.
  - `MemberService.java` 내 프로필 닉네임 중복검사, bio/지역 수정 및 기존 N:M 중계 데이터 삭제 ➔ 새 체크박스 ID 리스트 `saveAll` Batch 갱신 연동.
  - `SecurityConfig.java` 에 `AccessDeniedHandler` (권한 부족 403 Forbidden JSON) 및 `/api/admin/**` `hasRole(ADMIN)` 인가 연동.
  - `SecurityAuthFailureTest.java` 전용 테스트 수트 작성 ➔ 미인증 유저 `401 Unauthorized` 차단 3종 및 권한 부족 `403 Forbidden` 차단 1종 100% 실증.
  - 프론트엔드 `page.tsx` 메인 프로필 카드에 **"✏️ 프로필 수정" 폼/모달 연동** 및 `PUT /api/members/me` 즉시 갱신 탑재.
  - 백엔드 `.\gradlew.bat test` 21개 전체 테스트 **100% 통과 실증 (`BUILD SUCCESSFUL in 21s`)** 및 백엔드 서버 재구동.






























- `docs/conception/mvp-scope.md` 및 `authentication-session.md` 아키텍처 확장 설계 업데이트 완료.
  - 세션 / JWT 스위칭 설계 (`application.yml` + `@ConditionalOnProperty`) 및 OAuth2 소셜 연동 대비(`password NULLABLE`, `member_social` 테이블 확장) 요구사항 미리 명시 완료.



## 2026-08-07

### 완료

- GitHub 원격 저장소 `devikae/snowthing` 확인.
  - 원격 `main` 브랜치에 `.github/ISSUE_TEMPLATE/bug_report.md`, `.github/ISSUE_TEMPLATE/feature_request.md`, `README.md`만 존재하는 상태 확인.
- 초기 커밋 재구성 방향 확정.
  - 백엔드 Spring Boot 기본 틀, 프론트 Next.js 목업 틀, GitHub 이슈 템플릿을 하나의 `Initial commit`으로 정리 예정.
  - 기존 원격 커밋 내역은 force push로 새 초기 커밋 하나만 보이도록 정리 예정.
- `.gitignore` 정리.
  - `frontend/node_modules/`, `frontend/.next/`, `*.log`, `.agents/`, `docs/conception/`, `docs/study*.md` 제외 규칙 추가.
- 원격 저장소의 이슈 템플릿과 README 내용을 로컬에 반영.

### 이슈 / 확인 필요

- 원격 히스토리를 덮어쓰는 작업이므로 push 전에 커밋 대상 파일 목록을 최종 확인해야 함.
- `docs/project/work.md`는 작업 기록용 로컬 문서이므로 초기 공개 커밋 대상에서 제외할지 최종 확인 필요.

### 추가 완료

- 저장소 구조를 모노레포 형태로 재정리.
  - 루트의 Spring Boot 관련 파일을 `backend/` 하위로 이동.
  - `frontend/`는 기존 Next.js 목업 구조 유지.
- `.gitignore`를 새 폴더 구조 기준으로 수정.
  - `backend/.gradle/`, `backend/build/` 제외.
  - `backend/gradle/wrapper/gradle-wrapper.jar`는 유지.
  - `frontend/.next/`, `frontend/node_modules/`, 로그 파일, 로컬 학습 문서 제외 유지.
- 새 구조 기준으로 `Initial commit`을 다시 구성하고 원격 `main` 히스토리를 덮어쓸 예정.
- `frontend/package.json`의 `latest` 의존성을 실제 설치 버전으로 고정.
  - Next.js `16.2.12`, React `19.2.8`, TypeScript `6.0.3`, ESLint `9.39.5`.
  - `npm install --package-lock-only`, `npm run lint`, `npm run build` 통과.

### 추가 이슈

- `npm install --package-lock-only` 기준 high 취약점 4건이 보고됨.
  - 현재 목적은 초기 틀 정리와 버전 고정이므로 자동 수정은 적용하지 않음.
- IntelliJ 실행 구성의 `SnowthingApplication`에 X 표시가 나타남.
  - 백엔드가 루트에서 `backend/`로 이동되어 기존 실행 구성이 메인 클래스/모듈을 못 찾는 상태로 추정.
  - `IntelliJ` 모듈 인지 확인.

## 2026-08-18

### 완료

- 프로젝트 메인 `README.md` 전면 업데이트:
  - **세션 인증 흐름 다이어그램**: Mermaid SequenceDiagram 기반 세션 인증/인가, 세션 고정 방어, 로그아웃 파이프라인 시각화 명세 추가.
  - **핵심 아키텍처 고민 및 의사결정 3선 추가**:
    1. **JPA Auditing (`BaseTimeEntity`)**: 애플리케이션 수동 주입 및 DB `sysdate` 대비 1차 캐시 시간 불일치 해결 원리 포함.
    2. **다중 디바이스 지원 & 30일 Remember-Me 슬라이딩 세션**: 이탈률 감소 및 메모리 OutOfMemory(OOM) 방지 세션 타임아웃 구성 포함.
    3. **세션 고정 공격(Session Fixation) 방어**: `request.changeSessionId()` 를 활용한 기존 데이터 보존 및 세션 키 재발급 메커니즘 포함.
- GitHub 이슈 분할 양식(Issue #1 ~ #4) 100% 정제 및 마크다운 제공 완료.
- 회원가입 스프린트 작업 브랜치 생성:
  - `main` 기준 `feature/sprint01-signup` 브랜치 생성 완료.
  - 기존 작업 중 변경사항은 유지한 상태로 브랜치만 전환함.





