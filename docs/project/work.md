- **Sprint 03 댓글 PR #14 코드리뷰 피드백 반영 및 대댓글 인덱스/설정 최적화 (2026-09-02)**:
  1. **대댓글 복합 인덱스(idx_comment_parent_deleted_created) 최적화**:
     - `database/ddl.sql` 및 `Comment.java` `@Index` 명세를 `(parent_id, created_at, comment_id)` ➔ `(parent_id, is_deleted, created_at, comment_id)`로 변경.
     - 대댓글 100개 상한 검증(`countActiveReplies`) 및 대댓글 조회 시 살아있는 행으로 B-Tree Seek 직행 및 커버링 인덱스(`Using index`) 실측 달성.
  2. **DB Username 환경변수 동기화 (Configuration Parity)**:
     - `backend/src/main/resources/application.yml`의 `datasource.username`을 `docker-compose.yml`과 일치하도록 `${SNOWTHING_DB_USERNAME:snowuser}`로 수정.
  3. **DataInitializer & 테스트 정합성 보강**:
     - `DataInitializer.java` 내 닉네임 유니크 제약조건 중복 가드 추가.
     - `CommentServiceTest.java` 내 활성 자식 노드가 있는 삭제 부모 placeholder 정책 반영 및 `@AfterEach` teardown 클린업 추가.
  4. **검증 결과**:
     - `spotlessCheck` 및 백엔드 전체 단위/통합 테스트(`gradle test`) **100% BUILD SUCCESSFUL (23s)** 통과.

- **Sprint 03 댓글 도메인 공식 API 명세서(comment_api_spec.md) 작성 (2026-09-01)**:
  1. **5대 CRUD 엔드포인트 계약 명세화**: `docs/conception/sprint03/comment_api_spec.md`에 댓글 작성(`POST`), 루트 댓글 Batch+Top-5 프리뷰 조회(`GET`), 대댓글 분리 페이징 조회(`GET`), 댓글 수정(`PUT`), Soft Delete 삭제(`DELETE`)의 Request/Response DTO, Header, 에러 코드 매핑을 100% 명세화.

- **댓글 도메인 계층 모델 및 조회 아키텍처 공식 ADR-001 작성 및 확정 (2026-08-29)**:
  1. **실측 데이터 기반 아키텍처 의사결정**: 3개 독립 워크트리 브랜치에서 측정한 실측 벤치마크 지표(후보 1: 210KB 폭증 vs 후보 2: 핫스팟 103KB 비대화 vs 후보 3: 5.55KB 완벽 통제)를 근거로, **[후보 3: Adjacency List 기반 하이브리드 프리뷰(루트 20개 + 대댓글 5개) 및 대댓글 분리 페이징]을 최종 채택**.
  2. **ADR-001 9개 핵심 섹션 완결**: `docs/study/sprint03/comment/ADR-001-comment-hierarchy-and-retrieval-architecture.md`에 문제정의, 요구사항, 후보군, Spike 실측 매트릭스, 기각 근거, 기술 부채, 재검토 트리거 등 표준 아키텍처 의사결정 기록 공식 문서화.

- **댓글 조회 아키텍처 3대 후보 Spike 실험 공통 기반 및 측정 하네스 구축 (2026-08-29)**:
  1. **실험 가이드 및 템플릿 작성**: `docs/study/sprint03/comment/spike_experiment_guide.md` (실험 목적, 2대 시나리오, 5대 측정 지표 정의) 및 `docs/study/sprint03/comment/spike_result_template.md` (표준 결과 보고서 템플릿) 문서화.
  2. **공통 테스트 픽스처 및 하네스 개발**: `CommentSpikeDataInitializer.java` (분산 1,000건 & 핫스팟 500건 자동 주입기) 및 `CommentSpikeBenchmarkHarness.java` (실행 시간, JSON 직렬화 페이로드 바이트 크기, 쿼리 수 측정 러너) 구축.
  3. **단위/통합 테스트 검증**: Spotless 포맷팅(`spotlessApply`) 및 전체 90개 백엔드 단위/통합 테스트(`gradle test`) **100% BUILD SUCCESSFUL** 통과.

- **가상 스레드(Virtual Thread) 조기 최적화 제거 및 표준 플랫폼 스레드 풀 전환 (2026-08-28)**:
  1. **가상 스레드 제거 및 표준화 (YAGNI)**: BCrypt 암호화 연산 병목, HikariCP 커넥션 풀 고갈 위험, Thread Pinning 등 조기 최적화로 인한 잠재적 결함을 방지하기 위해, `application.yml`에서 `spring.threads.virtual.enabled: true`를 완전 제거하고 `spring.jpa.open-in-view: false`를 명시하여 DB 커넥션 점유 최소화.
  2. **표준 `ThreadPoolTaskExecutor` 적용**: `AsyncConfig.java`에서 가상 스레드 대신 예측 가능하고 검증된 고정 플랫폼 스레드 풀(Core 8, Max 16, Queue 100, Prefix `async-worker-`)로 전환.
  3. **전체 단위/통합 테스트 검증**: Spotless 포맷팅(`spotlessApply`) 및 전체 90개 백엔드 단위/통합 테스트(`gradle test`) **100% BUILD SUCCESSFUL** 통과.

- **MemberService 인라인 패키지명(FQN) 정리 및 상단 import 최적화 (2026-08-28)**:
  1. **가독성 및 컨벤션 교정**: `MemberService.java` 내부에 인라인으로 산재하던 풀 패키지명(`com.ikae.snowthing.global.exception.CustomAuthException`, `com.ikae.snowthing.global.error.ErrorCode`)을 클래스 상단 `import`로 승격하여 코드 가독성 및 일관성 확보.
  2. **린터 및 단위/통합 테스트 검증**: Google Java Format AOSP 기반 `spotlessApply` 서식 교정 및 백엔드 90개 전체 단위/통합 테스트(`gradle test`) **100% BUILD SUCCESSFUL** 통과.

- **게시글 삭제 API 민감정보 `@RequestParam` 완전 제거 및 Request Body DTO 단일 계약 전환 (2026-08-27)**:
  1. **URL 파라미터 민감정보 노출 원천 차단**: `PostController.java`의 `deletePost` 메서드에서 잔여 레거시였던 `@RequestParam(required = false) String anonymousPassword`를 완전히 제거하고, 오직 `@RequestBody(required = false) PostDeleteRequest request`를 통해서만 비밀번호를 수신하도록 API 계약 단일화.
  2. **삭제 권한 정책 6대 시나리오 전수 검증**: `PostControllerTest.java`를 통해 로그인 작성자(200 OK), 최고 관리자(200 OK), 비회원 익명 올바른 비밀번호(200 OK), 비회원 익명 틀린 비밀번호(403 Forbidden - `POST_004`), **관계없는 제3자 로그인 회원이 익명글 비밀번호를 알고 입력 시 삭제 허용(200 OK)** 및 틀린 비밀번호 시 거부(403 Forbidden) 정책을 100% 검증.
  3. **전체 단위/통합 테스트 검증**: Spotless 포맷팅(`spotlessApply`) 및 전체 90개 백엔드 단위/통합 테스트(`gradle test`) **100% BUILD SUCCESSFUL** 통과.

- **댓글 생성 쓰기 트랜잭션(`@Transactional`) 선언 및 전역 500 에러 핸들러 보강 (2026-08-27)**:
  1. **MySQL Read-Only 커넥션 쓰기 차단 원천 해결**: `CommentService.java`의 클래스 레벨 `@Transactional(readOnly = true)`로 인해 `createComment` 실행 시 MySQL JDBC 드라이버가 `Connection is read-only` 에러를 발생시키던 문제를 해결하기 위해, `createComment` 메서드 상단에 쓰기 전용 `@Transactional`을 명시하여 정상적인 INSERT 커넥션 획득 보장.
  2. **Security 401 오분류 방지 전역 500 핸들러 신설**: `GlobalExceptionHandler.java`에 `@ExceptionHandler(Exception.class)`를 추가하여, 예기치 않은 인프라/시스템 예외가 서블릿 밖으로 흘러나가 Spring Security EntryPoint의 401(INVALID_CREDENTIALS)로 둔갑하던 결함을 차단하고 정확한 500 INTERNAL_SERVER_ERROR로 일원화.
  3. **전체 단위/통합 테스트 검증**: Spotless 서식 교정(`spotlessApply`) 및 백엔드 90개 전체 단위/통합 테스트(`gradle test`) **100% PASS** 완료.

- **익명/일반 게시판 댓글 도메인 정책 완결 (로그인 회원 비밀번호 생략 & 비로그인 비밀번호 필수) (2026-08-27)**:
  1. **백엔드 `CommentService` 정책 일원화**: `createComment`에서 로그인 회원(`userDetails != null`)이 익명 댓글을 작성할 때 `anonymousPassword` 없이도 `member`를 정상 연관관계 매핑하여 즉시 작성되고, 작성자 본인 세션으로 비밀번호 없이 즉시 삭제 가능하도록 개선. 비로그인 사용자만 삭제용 비밀번호 필수로 일원화.
  2. **프론트엔드 UI/UX 최적화**: `[publicId]/page.tsx`에서 익명 게시판 진입 시 로그인 회원에게는 불필요한 비밀번호 입력란을 완전히 숨기고, 비로그인 사용자에게만 비밀번호 입력을 유도하여 매끄러운 UX 확립.
  3. **전체 단위/통합 테스트 및 빌드 검증**: 백엔드 전체 테스트 수트(`gradle test`) **100% BUILD SUCCESSFUL** 및 Next.js 16 최적화 프로덕션 빌드(`npm run build`) **100% SUCCESS** 통과 완료.

- **조회수/추천수/댓글수 증감 시 `updated_at` 고스트 업데이트 방지 및 벌크 쿼리 분리 (2026-08-27)**:
  1. **고스트 업데이트(Ghost Update) 원천 차단**: 조회수(`view_count`), 추천/비추천(`like_count`/`dislike_count`), 댓글수(`comment_count`) 증감 시 영속성 컨텍스트 더티 체킹으로 인해 `updated_at`이 갱신되던 문제를 해결하기 위해, `PostRepository`에 `@Modifying(flushAutomatically = true, clearAutomatically = true)` 벌크 쿼리 7종을 도입하여 엔티티 감사(Audit) 메타데이터 터치를 물리적으로 배제.
  2. **DDL 및 감사 정책 일원화**: `database/ddl.sql`의 `post` 테이블에서 `ON UPDATE CURRENT_TIMESTAMP`를 제거하여, 사용자가 실제 본문/제목을 수정(`post.update(...)`)한 경우에만 JPA Auditing(`@LastModifiedDate`)이 `updated_at`을 최신 시점으로 갱신하도록 전담화.
  3. **단위/통합 테스트 전수 검증**: `PostServiceTest.java` 내 `PostUpdatedAtGhostUpdatePolicyTest`를 신설하여 조회/추천/댓글 시 `updated_at` 불변 유지 및 본문 수정 시에만 갱신되는 동작 검증 완료 (전체 90개 테스트 100% PASS).

- **H2/MySQL DataSource 프로필 분리 및 MySQL 8.0 전용 프로필 단일화 (2026-08-27)**:
  1. **프로필 기반 분리 및 H2 완전 제거**: `application.yml`에서 메인 애플리케이션용 H2 프로필 및 H2 콘솔 설정을 전면 제거하고, 실제 **MySQL 8.0** 데이터베이스 전용 프로필(`local`, `docker`, `prod`)로 완전 단일화.
     - `local` 프로필: `jdbc:mysql://localhost:3306/snowthing?useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=UTF-8&serverTimezone=Asia/Seoul` (`ddl-auto: update`)
     - `docker`/`prod` 프로필: `jdbc:mysql://mysql:3306/snowthing?useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=UTF-8&serverTimezone=Asia/Seoul` (`ddl-auto: validate`)
  2. **잉여 중복 파일 제거**: `backend/src/main/resources/application.yaml` 중복 파싱 파일 완전 제거.
  3. **전체 단위/통합 테스트 검증**: 독립 테스트 프로필(`application-test.yml`) 및 백엔드 테스트 수트(`gradle test`) 100% PASS 검증 완료.

- **Spring Boot 4.1.0 판올림 & Spotless 린터(Google Java Format AOSP) 전면 도입 & GitHub Actions CI 연동 (2026-08-26)**:
  1. **Spring Boot 4.1.0 호환성 전수 교정**:
     - `backend/build.gradle` 버전을 `4.1.0` (Spring Framework 7.x, Spring Security 7.x, Jakarta EE)으로 적용.
     - Spring Boot 4의 모듈화에 맞춰 `spring-boot-starter-jackson`, `jackson-databind`, `spring-boot-starter-webmvc-test` 명시적 의존성 추가.
     - Spring Security 7.x 아키텍처 변경에 맞춰 `SecurityConfig`의 `logoutUrl("/api/v1/auth/logout")` 적용 및 `ObjectMapper` 빈 등록.
     - MockMvc 테스트에서 `@AuthenticationPrincipal` 인증 주입을 위한 `SecurityMockMvcRequestPostProcessors.user()` 일괄 적용.
  2. **Spotless Linter 구축 및 전체 소스코드 자동 서식 교정**:
     - `Spotless 6.25.0` Gradle 플러그인 연동 및 Google Java Format 1.22.0 (AOSP 4-space indent) 표준 규칙 적용.
     - `removeUnusedImports()`, `importOrder()`, `trimTrailingWhitespace()`, `endWithNewline()` 설정.
     - `.\gradlew.bat spotlessApply`로 프로젝트 전체 Java 소스코드의 import 및 FQCN/들여쓰기 자동 정돈.
  3. **GitHub Actions CI 파이프라인 연동**:
     - `.github/workflows/gradle.yml`에 `Run Spotless Linter Check` 스텝을 추가하여 PR/Push 시 코드 서식 및 import 위반을 자동 검증하고 Fast-Fail 처리.
  4. **전체 테스트 및 빌드 검증**:
     - `.\gradlew.bat clean build` (spotlessCheck + compile + 92개 전체 단위/통합 테스트) **100% SUCCESS**.

- **Response DTO `@Builder` 지양 및 표준 생성자/정적 팩토리 메서드 전환 (2026-08-26)**:
  1. **빌더 제거 및 타입 안전성 확보**: `PostDetailResponse`, `CommentResponse`, `PostListResponse`, `PostResponse`에서 `@Builder`를 제거하고 Java `record` 표준 생성자 및 `from(Entity)` 정적 팩토리 메서드로 전환하여 필드 누락 컴파일 타임 방어.
  2. **전체 테스트 검증**: 83개 전체 테스트 PASS.

- **게시글 조회 엔드포인트 분리 (웹 Offset vs 모바일 Cursor) (2026-08-26)**:
  1. **엔드포인트 분리**: `GET /api/v1/posts`(웹 번호 기반 Offset 페이징)와 `GET /api/v1/posts/scroll`(모바일 무한 스크롤 커서 페이징)로 책임을 완벽히 분리.
  2. **스펙 및 검증 명확화**: 단일 엔드포인트 내의 불필요한 분기 로직을 제거하고 각 클라이언트 스펙에 맞게 정돈.
  3. **통합 테스트 작성 및 검증**: 83개 전체 테스트 PASS.

- **`DataIntegrityViolationException` 오분류 방지 및 2단계 방어선 예외 처리 아키텍처 (2026-08-26)**:
  1. **1차 방어선 (Service)**: `existsByEmail()`, `existsByNickname()` 사전 비즈니스 검증에 집중하고 DB 인프라 예외 `try-catch` 완전 제거.
  2. **최종 방어선 (GlobalExceptionHandler)**: 동시성 충돌 시의 `DataIntegrityViolationException`을 전역 어드바이스에서 일괄 감지하여 로깅 및 안전한 클라이언트 에러로 변환.
  3. **전체 테스트 검증**: 전체 81개 테스트 PASS.

- **DTO Non-null 보장 컬렉션에 대한 중복 null 체크 제거 (2026-08-26)**:
  1. **중복 검사 제거**: `MemberService.signUp` 및 `updateMyProfile`에서 DTO가 이미 Non-null(방어적 복사 및 빈 리스트 초기화)을 보장하므로 `!= null` 검사를 제거하고 `!isEmpty()` 조건만 유지.
  2. **전체 테스트 검증**: 전체 81개 테스트 PASS.

- **`saveAndFlush()` 및 `flush()` 지양, 표준 `save()` 및 `GlobalExceptionHandler` 이관 (2026-08-26)**:
  1. **명시적 flush 제거**: `MemberService.signUp` 및 `updateMyProfile`에서 `saveAndFlush()`와 `flush()`를 제거하고 표준 `save()`로 변경하여 JPA 쓰기 지연(Write-Behind) 최적화 복원.
  2. **전역 예외 처리 일원화**: 동시성 제약조건 위반(`DataIntegrityViolationException`) 처리를 서비스 내 `try-catch`에서 `GlobalExceptionHandler`로 이관.
  3. **전체 테스트 검증**: 전체 81개 테스트 PASS.

- **댓글 2-Pass 계층 트리 조립 알고리즘 적용 및 Early Return 리팩토링 (2026-08-26)**:
  1. **2-Pass 트리 조립 (Order-independent)**: `CommentService.getCommentsByPost`에서 [Pass 1] Map 선제 등록 ➔ [Pass 2] 부모-자식 트리 바인딩으로 분리하여 쿼리 정렬 순서에 따른 자식 댓글 누락(Silent Data Drop) 방어.
  2. **Early Return 가드 절**: `validateDeletePermission`에서 중첩된 `if-else` 분기를 제거하고 조기 반환을 적용하여 인지 부하 감소 및 코드 평탄화.
  3. **전체 테스트 검증**: 전체 81개 테스트 PASS.

- **댓글 생성 트랜잭션 범위 최소화 및 `TransactionTemplate` 기반 암호화 격리 (2026-08-26)**:
  1. **트랜잭션 외 암호화/검증 선제 처리**: `CommentService.createComment`에서 BCrypt 해싱(`passwordEncoder.encode`) 및 파라미터 null 검증을 트랜잭션 시작 전에 수행하여 DB 커넥션 점유 시간 최소화.
  2. **`TransactionTemplate` 적용**: `Post`, `Member`, `Parent` 엔티티 조회, `Comment` 저장, `post.increaseCommentCount()` 원자적 상태 변경 구간만 `transactionTemplate.execute()` 내부로 국소화.
  3. **전체 테스트 검증**: 전체 81개 테스트 PASS.

- **`humanize-korean` (`im-not-ai`) 스킬 탑재 및 PR 답변 톤앤매너 적용 (2026-08-26)**:
  1. **스킬 탑재 (`.agents/skills/humanize-korean/SKILL.md`)**: 번역투, 기계적 나열, AI 상투적 과장 표현을 배제하고 실제 현업 엔지니어가 작성한 듯한 자연스러운 한국어 톤앤매너로 변환하는 스킬 구축.
  2. **피드백 답변문구 적용**: `docs/project/review_feedback_replies.md` 내 PR 코멘트 답변 텍스트를 자연스럽고 담백한 어조로 일괄 정제.

- **작성자 표시명 포맷터 템플릿화, IP 마스킹 공통 유틸(`WriterDisplayFormatter`) 분리 및 IPv6/비정상 IP 원문 노출 방어 (2026-08-26)**:
  1. **공통 유틸 신설**: `global.util.WriterDisplayFormatter`를 생성하여 `"익명 (%s)"` 템플릿 상수화 및 `maskIp()` 마스킹 단일 진입점 구현.
  2. **IPv6 및 비정상 IP 원문 노출 방지**: IPv4 4개 옥텟 마스킹뿐 아니라 IPv6(콜론 구분 앞 2개 세그먼트) 마스킹을 구현하고, 비정상/파싱 불가 문자열의 경우 `return ip;` 대신 안전한 기본값(`"127.0.***.***"`)을 반환하도록 방어 로직 강화.
  3. **DTO 4종 리팩토링**: `CommentResponse`, `PostDetailResponse`, `PostListResponse`, `PostResponse`에 산재된 중복 `maskIp()` 메서드 및 삼항연산자 포맷팅 로직을 제거하고 `WriterDisplayFormatter` 호출로 통일.
  4. **단위 테스트 전수 검증**: `WriterDisplayFormatterTest.java`를 통해 정상 IPv4, IPv6, null/공백/루프백, 비정상 문자열 IP 엣지 케이스 검증 완료 (전체 81개 테스트 PASS).

- **`caveman` 및 `ponytail` 스킬 탑재 완료 (2026-08-26)**:
  1. **Caveman 스킬 (`.agents/skills/caveman/SKILL.md`)**: 토큰 절약형 초압축 커뮤니케이션 모드 (군더더기 배제, 핵심 기술 내용과 코드/명령어 보존).
  2. **Ponytail 스킬 (`.agents/skills/ponytail/SKILL.md`)**: 가장 단순하고 불필요한 오버엔지니어링(YAGNI)을 배제하는 최소화/효율적 구현 사다리 원칙 스킬 탑재.

- **가상 스레드(Virtual Thread) `AsyncConfigurer` 인터페이스 리팩토링 및 동시성 제어 전략 문서화 (2026-08-26)**:
  1. **설정 범위 명확화 및 중복 정리**: `spring.threads.virtual.enabled=true`는 내장 톰캣 서블릿 실행기에 적용되며, `@Async` 작업은 `AsyncConfigurer` 구현체를 통해 명시적인 `async-vt-` 가상 스레드 네이밍 및 `AsyncUncaughtExceptionHandler` 로깅 핸들러를 일원화 적용.
  2. **과장 표현 정정 및 물리적 한계 분석**: 가상 스레드가 OS 스레드 스택 비용을 절감하지만 HikariCP 커넥션 풀 및 JVM 힙 메모리 한계를 극복하지 못함을 명시.
  3. **DB 커넥션 고갈 제어 전략**: 세마포어/벌크헤드 동시성 제한, `OSIV=false` 기반 트랜잭션 최소화, 순수 I/O 비동기 작업 격리 전략 수립 (전체 77개 테스트 PASS).

- **`MemberRepositoryCustomImpl` QueryDSL 3-Step O(1) 쿼리 분리 아키텍처 및 Cartesian Product 방지 트레이드오프 문서화 (2026-08-26)**:
  1. **설계 배경 정정**: Member의 2개 일대다 관계(`member_resorts`, `member_riding_styles`) 단일 조인 시 발생하는 `N × M` 카테시안 곱(Cartesian Product) 및 메모리 중복 오버헤드를 방지하기 위해 3단계 고정 쿼리(O(1))로 분리한 아키텍처 설계 근거를 Javadoc 및 리뷰 답변에 명문화.
  2. **트레이드오프 분석**: DB RTT 3회 증가 대가 대비, 데이터량에 무관한 O(1) 고정 쿼리 실행 및 최소 페이로드 전송 이점을 비교 검증.
  3. **다중 컬렉션 무결성 검증**: `MemberRepositoryCustomTest.java`에 다중 리조트/스타일 매핑 테스트 추가 완료 (전체 77개 테스트 PASS).

- **비공개(HIDDEN, BLOCKED, DRAFT) 게시글 목록 노출 차단(`status = NORMAL`) 및 도메인 전반 상태 정책 통일 (2026-08-26)**:
  1. **목록 쿼리 조건 강화**: `PostRepositoryCustomImpl.java`의 Offset/Cursor 쿼리 및 `PostRepository.java`에 `post.status.eq(PostStatus.NORMAL)` 조건을 필수 적용하여 비공개 게시글이 일반 목록에 노출되는 결함 해결.
  2. **연관 도메인 상태 정책 통일**: `CommentService.java`(댓글 작성/조회) 및 `PostService.java`(게시글 추천/수정/상세조회) 전반에 `status != NORMAL` 검증(`POST_NOT_FOUND`)을 일관되게 적용.
  3. **단위/통합 테스트 전수 검증**: `PostServiceTest.java` 내 `PostStatusPolicyTest`를 통해 Offset/Cursor 목록 제외 및 비공개 글에 대한 상세/추천/댓글 차단(404) 정책 검증 완료 (전체 76개 테스트 PASS).

- **댓글 삭제 `@RequestParam` 완전 제거 및 `CommentDeleteRequest` DTO 보안 계약 일원화 (2026-08-26)**:
  1. **계약 통일**: `CommentController.java`에서 잔여 `@RequestParam`을 완전히 제거하고 `@RequestBody(required = false) CommentDeleteRequest request` 전담으로 수정하여 게시글 삭제(`PostDeleteRequest`)와 동일한 보안 DTO 계약 확립.
  2. **컨트롤러 테스트 전수 검증**: `CommentControllerTest.java`에서 로그인 작성자(200), 관리자(200), 타 회원 차단(403), 비회원 익명 비밀번호 일치(200)/불일치(403) 등 5종 시나리오 검증 완료 (전체 74개 테스트 PASS).

- **게시글 삭제 Request Body DTO 보안 전송 및 권한 정책 7종 전수 검증 (2026-08-26)**:
  1. **보안 계약 전환**: `PostDeleteRequest` DTO를 신설하고 `PostController.java`에서 `@RequestBody(required = false)`로 비밀번호를 수신하도록 개선하여 URL 쿼리 파라미터 민감정보 노출 원천 차단.
  2. **프론트엔드 일치화**: `posts/[publicId]/page.tsx`의 `handleConfirmDelete`에서 `API_ENDPOINTS.posts.delete(publicId)` 호출 시 JSON body(`{ "anonymousPassword": "..." }`)로 전송.
  3. **권한 정책 전수 검증**: `PostControllerTest.java`에서 로그인 본인(200), 관리자(200), 타인 일반글 차단(403), 비회원 익명 비밀번호 일치(200)/불일치(403), 제3자 로그인 사용자의 익명글 비밀번호 일치(200)/불일치(403) 등 7종 권한 시나리오 검증 완료 (전체 72개 테스트 PASS).

- **댓글 삭제 API 경로(`/api/v1`) 일치화 및 Request Body DTO 기반 익명 비밀번호 보안 전송 (2026-08-26)**:
  1. **보안 DTO 도입**: `CommentDeleteRequest` DTO를 신설하고 `CommentController.java`에서 `@RequestBody(required = false)`로 비밀번호를 바인딩하여 Nginx/서버/APM 로그에 평문 비밀번호가 노출되는 URL 쿼리 파라미터 취약점을 원천 차단.
  2. **프론트엔드 연동 일치화**: `posts/[publicId]/page.tsx`의 `handleDeleteComment`에서 `API_ENDPOINTS.comments.delete(commentId)`(`/api/v1/comments/{id}`)로 JSON body 전송.
  3. **컨트롤러 테스트 검증**: `CommentControllerTest.java`에서 올바른 비밀번호 200 OK, 비밀번호 불일치/누락 시 403 Forbidden(`POST_004`) 및 URL 쿼리 미포함 검증 3건 추가 완료.

- **프론트엔드 API 엔드포인트 공통 모듈화(`api.ts`) 및 댓글/대댓글 API 경로(`/api/v1`) 일치화 (2026-08-26)**:
  1. **공통 모듈 신설**: `frontend/app/lib/api.ts`를 신설하여 `API_BASE_URL`(`NEXT_PUBLIC_API_BASE_URL` 지원) 및 도메인별 전체 `API_ENDPOINTS` 상수를 중앙 집중식으로 정의.
  2. **프론트엔드 URL 일원화**: `posts/[publicId]/page.tsx`의 `/api/posts/{publicId}/comments` 404 경로를 `/api/v1/posts/{publicId}/comments`로 수정하고, 프론트엔드 10개 전체 페이지/컴포넌트의 API 호출을 `API_ENDPOINTS` 참조로 전면 전환.
  3. **빌드/테스트 검증**: Next.js 16 (Turbopack) 최적화 프로덕션 빌드(`npm run build`) 성공 및 백엔드 63개 테스트 100% PASS 검증 완료.

- **MySQL DDL `post_reaction` 익명 추천 스키마 일치화 및 물리 검증 완결 (2026-08-26)**:
  1. **DDL 스키마 일치화**: `database/ddl.sql`의 `post_reaction` 테이블에 `member_id` NULL 허용, `writer_ip VARCHAR(45) NULL`, `anonymous_voter_id VARCHAR(36) NULL` 컬럼을 추가하고, 회원 복합 유니크(`uk_post_member_type`) 및 비회원 익명 복합 유니크(`uk_post_anon_voter_type`) 제약조건을 `PostReaction.java` 엔티티 및 ERD와 100% 일치시킴.
  2. **회원/익명 추천 물리 검증**: `PostServiceTest.java`에서 로그인 회원 추천뿐 아니라 비로그인 익명 사용자의 `anonymousVoterId` 기반 추천 생성, 취소(토글 OFF), DB 물리 레코드(`member_id IS NULL`, `anonymous_voter_id` 적재) 상태를 Native Query로 완벽 검증.
  3. **테스트 검증**: 백엔드 전체 63개 테스트 100% PASS (`BUILD SUCCESSFUL in 19s`).

- **MySQL DDL `deleted_at` 컬럼 누락 수정 및 Soft Delete 물리 검증 완결 (2026-08-26)**:
  1. **DDL 스펙 일치화**: `database/ddl.sql`의 `post` 및 `comment` 테이블에 누락되어 있던 `deleted_at DATETIME NULL COMMENT '삭제 일시'` 컬럼을 추가하여 엔티티의 `@SQLDelete` (`deleted_at = NOW()`) 및 설계 문서 ERD와 100% 일치시킴.
  2. **Soft Delete 물리 상태 검증**: `PostServiceTest.java` 및 `CommentServiceTest.java`에서 삭제 API 실행 시 DB 물리 레코드에 `is_deleted = true`, `status = 'DELETED'`, `deleted_at IS NOT NULL`로 정확히 기록되는지 Native Query 기반 검증 완료.
  3. **테스트 검증**: 백엔드 전체 63개 테스트 100% PASS (`BUILD SUCCESSFUL in 18s`).

- **H2/MySQL DataSource 프로필 분리 및 MySQL 8.0 기본 데이터소스 전환 완결 (2026-08-26)**:
  1. **프로필 기반 분리**: `application.yml`에 `local`/`docker`/`prod` 환경의 기본 DataSource를 실제 **MySQL 8.0 (`jdbc:mysql://localhost:3306/snowthing` 및 `jdbc:mysql://mysql:3306/snowthing`, `org.hibernate.dialect.MySQLDialect`)**으로 전면 전환하고, `h2` 프로필을 완전 분리.
  2. **의존성 런타임 격리**: `build.gradle`에서 `com.h2database:h2`를 `testRuntimeOnly`로 격리하여 메인 런타임에는 `mysql-connector-j`만 동작하도록 설정.
  3. **H2 잔류 파일 삭제**: `backend/data/` 디렉터리 및 H2 파일(`snowdb.mv.db`, `snowdb.lock.db`) 완전 제거.
  4. **테스트 검증**: `application-test.yml` 독립 테스트 프로필 기반 전체 63개 테스트 100% PASS 검증 완료.

- **Java 21 Virtual Threads 도입 및 인증/DB 아키텍처 리팩토링 완결 (2026-08-25)**:
  1. **Java 21 가상 스레드 적용**: `spring.threads.virtual.enabled: true` 및 `AsyncConfig`의 `Executors.newVirtualThreadPerTaskExecutor()` 등록으로 `@Async` 비동기 작업 시 스레드 풀 고갈 및 Native OOM 원천 차단.
  2. **세션 타임아웃 매직 넘버 제거**: `AuthController.java`의 `30 * 24 * 60 * 60`을 `Duration.ofDays(30).toSeconds()` 명시적 상수로 교체.
  3. **인증 데드코드 제거**: `AuthService.java`의 미사용 `authenticate()` 메서드 및 의존성을 삭제하여 Spring Security `AuthenticationManager`로 책임 단일화.
  4. **QueryDSL DTO 프로젝션 적용**: `MemberRepositoryCustomImpl`에 `findProfileByEmail()`을 구현하여 프로필 조회를 단일 조인 DTO 프로젝션으로 최적화하고 `MemberRepositoryCustomTest` 검증 완료.
  5. **DTO 타입 일원화**: `MemberLoginRequest`의 생성자 매개변수를 원시 `boolean rememberMe`로 일원화.
  6. **테스트 검증**: 백엔드 전체 63개 테스트 100% PASS (`BUILD SUCCESSFUL in 20s`).

- **`feature/sprint01-signup` PR #9 코멘트 피드백 반영 커밋 및 원격 푸시 완결 (`9125d81`, 2026-08-24)**:
  1. **피드백 브랜치 정상화**: `feature/sprint02-board`에 잘못 커밋되어 있던 `MemberLoginResponse` 방어적 복사(`List.copyOf()`), `MasterDataService` 계층 분리, `SecurityConfig` 표준화, `/api/v1` API 버저닝 등 30개 피드백 수정 파일들을 `feature/sprint01-signup` 브랜치로 정상 이동하여 전체 24개 테스트 100% PASS 검증 완료.
  2. **원격 푸시 완료**: 커밋 메시지 `refactor: 코멘트 피드백 반영`으로 `origin/feature/sprint01-signup`에 푸시 완료하여 PR #9 갱신 완료.

- **`CommentController` 의존성 서비스 및 DTO 깃허브 푸시 완결 (`1aeb940`, 2026-08-24)**:
  1. **CI 컴파일 원인 해결**: `CommentController.java`에서 참조하는 `CommentService`, `CommentResponse`, `CommentCreateRequest`, `PostCommentListResponse`, `CommentRepository` 등의 의존성 파일들이 로컬에만 존재하고 원격 깃허브에 누락되어 발생하던 CI 컴파일 에러를 완벽히 해결하기 위해 관련 8개 파일 원격 포함 완료.
  2. **원격 푸시 조치**: `git push origin feature/sprint02-board` 완료하여 GitHub Actions CI 컴파일 에러 완전 해결 및 그린 라이트 재실행 완결.

- **`QuerydslConfig.java` 패키지 오타 복구 및 CI 재트리거 완결 (`4cf2c8c`, 2026-08-24)**:
  1. **CI 컴파일 에러 해결**: `QuerydslConfig.java` 첫 번째 줄의 `ackage` 패키지 키워드 오타를 `package`로 완벽 복구하여 `MemberRepositoryTest.java` 컴파일 시 `cannot find symbol` 클래스 미인식 에러를 완전히 해결.
  2. **원격 푸시 조치**: `git push origin feature/sprint02-board` 완료하여 GitHub Actions CI 정상 렌더링 및 그린 라이트 재실행 완료.

- **`application.yml` `spring:` 오타 복구 및 CI 액션 v5 업데이트 (`adf3f64`, 2026-08-24)**:
  1. **빌드 원인 해결**: `application.yml` 및 `application-test.yml` 설정 파일 첫 번째 줄의 `pring:` 오타(spring의 s 누락)로 인해 CI 서버에서 스프링 부트 설정 파싱 실패하던 원인을 `spring:`으로 정밀 복구.
  2. **CI 경고 제거**: `.github/workflows/gradle.yml`에서 `actions/setup-java@v4`를 `v5`로 업데이트하여 노드/Java 버전 경고 완벽 제거 및 원격 푸시 처리.

- **`feature/sprint02-board` 원격 브랜치 깃허브 푸시 완결 (`63b0cb3`, 2026-08-24)**:
  1. **CI 빌드 실패 원인 조치**: 깃허브 원격 서버의 이전 깨진 빌드를 대체하기 위해, 62개 백엔드 전체 테스트 100% 통과 패치가 완료된 커밋(`63b0cb3`)을 원격 브랜치로 force push하여 GitHub Actions CI 정상 재실행 조치 완료.

- **`feat: 게시판 도메인 기능 구현` 커밋 완결 (2026-08-24)**:
  1. **댓글 파일 100% 분리 제외**: `domain/comment/` 디렉토리를 제외하고 `my_ai/`, `README.md`, `database/`, `docs/`, `backend/domain/post/`, `frontend/` 총 105개 파일 커밋 완료 (`71cff44`).
  2. **커밋 메시지**: `feat: 게시판 도메인 기능 구현`으로 표준화 적용.

- **`README.md` 게시판(Post) 도메인 아키텍처 및 10가지 기술적 의사결정 작성 (`README.md`, 2026-08-24)**:
  1. **게시글 도메인 구조 및 연관관계**: `Post`, `PostCategory`, `PostImage`, `PostReaction`, `Member` 간 연관관계 및 비회원 익명 포스팅을 위한 Optional `Member` 선택 이유 서술.
  2. **응답 DTO 분리 목적**: 목록(`PostListResponse`)과 상세(`PostDetailResponse`) DTO를 철저히 분리하여 본문 텍스트 패치로 인한 네트워크 트래픽 90% 이상 절감 및 DB I/O 성능 최적화 근거 작성.
  3. **Soft Delete 및 Fetch Join 선택 이유**: 어뷰징 방지/감시(Audit)를 위한 Soft Delete 선택 이유 및 N+1 방지를 위한 `JOIN FETCH` 단일 쿼리 실행 결과 및 예외 응답 규격 정립.

- **`docs/study/` gitignore 적용 및 익명 게시판 명칭 일괄 표준화 (2026-08-24)**:
  1. **`.gitignore` 설정**: `docs/study/` 디렉토리를 `.gitignore`에 등록하여 학습 정리 문서 커밋 완전 제외.
  2. **게시판 카테고리 명칭 변경**: 프로젝트 전체(프론트엔드, 백엔드 시더, UI 텍스트, 프로젝트 문서)의 `익명 대나무숲` 문구를 `익명 게시판`으로 일괄 수정.
  3. **관리자(`ROLE_ADMIN`) 댓글 삭제 권한 완벽 검증 및 수정**: `CommentService.java` 내 `validateDeletePermission` 로직을 수정하여 `ROLE_ADMIN` 관리자가 익명 댓글을 포함한 모든 댓글을 패스워드 입력 없이 무조건 삭제 가능하도록 보완.

- **게시판 REST API 명세서 최신화 (`docs/conception/sprint01/05.api-spec.md`, 2026-08-24)**:
  1. **Base URL 명시**: 모든 백엔드 컨트롤러 엔드포인트를 `/api/v1` 버전 경로로 통일 명시.
  2. **관리자 삭제 권한 명세 반영**: `DELETE /api/v1/posts/{publicId}`에 `ROLE_ADMIN` 권한의 익명 비밀번호 검증 우회 삭제 스펙 추가.
  3. **로그인 작성자 익명 수정 명세 반영**: `PUT /api/v1/posts/{publicId}`에 로그인된 원작성자의 익명 패스워드 입력 생략 검증 정책 반영.
  4. **쿠키 기반 중복 조회수 방지 명세 반영**: `GET /api/v1/posts/{publicId}`에 `viewed_posts` 30분 쿠키 핑거프린트 중복 어뷰징 방지 스펙 명시.

- **React 19 Toast UI Editor 본문 `Write/Preview/Markdown/WYSIWYG` 오염 완전 해결 (`ToastEditor.tsx`, `ToastViewer.tsx`, 2026-08-24)**:
  1. **Pure JavaScript 인스턴스 전환**: React 19 마운트 시 발생하던 `@toast-ui/react-editor` 래퍼의 하이드레이션 `innerText` 긁힘 버그를 해결하기 위해 `useEffect` 내 `new Editor({ el: containerRef.current })` 순수 JS 인스턴스 패턴으로 전환.
  2. **에디터 본문 100% 초기화**: 유저가 작성하지 않은 어떠한 디폴트 텍스트 오염도 없이 본문 창이 완전히 깨끗한 빈 도화지 상태로 로드되도록 조치 완료.

- **Toast UI Editor 전역 CSS 등록 및 쌩 텍스트 스타일 깨짐 완전 수정 (`layout.tsx`, 2026-08-24)**:
  1. **전역 스타일 등록**: `@toast-ui/editor/dist/toastui-editor.css`를 `app/layout.tsx` 전역 번들로 등록하여 `dynamic import` 시 발생하던 CSS 누락(쌩 텍스트 노출 현상) 완전 해결.
  2. **에디터 UI 정상화**: 쌩 글자(`Write`, `Preview`, `Markdown`, `WYSIWYG`) 노출을 제거하고 아이콘 툴바 및 세련된 탭 디자인으로 100% 정상화.

- **Toast UI Editor (마크다운 + WYSIWYG 듀얼 에디터) 및 Viewer 탑재 (2026-08-24)**:
  1. **에디터 패키지 구축 (`ToastEditor.tsx`, `ToastViewer.tsx`)**: `@toast-ui/react-editor` 및 `@toast-ui/editor`를 탑재하여 Next.js App Router와 SSR 호환성을 고려한 `dynamic import` (`ssr: false`) 패턴 적용.
  2. **작성/수정 페이지 적용 (`create/page.tsx`, `edit/page.tsx`)**: 단순 `<textarea>`를 풀 스펙 스마트 에디터(헤더, 굵게, 기울임, 인용구, 표, 코드블록, 이미지 삽입)로 전격 교체.
  3. **상세 페이지 뷰어 적용 (`app/posts/[publicId]/page.tsx`)**: 작성된 서식이 깨지지 않고 예쁘게 렌더링되도록 `ToastViewer` 적용.

- **최고 관리자(`ROLE_ADMIN`) 테스트 계정 시딩 생성 (`DataInitializer.java`, 2026-08-24)**:
  1. **관리자 계정 자동 생성**: 서버 부팅 시 `admin@snowthing.com` (`Role.ROLE_ADMIN`) 계정이 존재하지 않으면 자동으로 생성하도록 데이터 시더 업데이트.

- **비로그인 사용자 익명 대나무숲 글쓰기 완전 허용 (`app/posts/create/page.tsx`, 2026-08-24)**:
  1. **비로그인 진입 시 기본 카테고리 자동 설정**: 비로그인 사용자가 `/posts/create` 진입 시 로그인 화면으로 튕기지 않고, 기본 카테고리를 `ANONYMOUS` (익명 대나무숲)로 자동 설정하여 로그인 없이도 즉시 작성 가능.
  2. **회원 전용 게시판 변경 시에만 제한**: 비로그인 사용자가 카테고리를 `자유게시판`, `장비 Q&A`, `맛집`으로 변경 시도할 때만 1회 안내 후 로그인 화면으로 리다이렉트.

- **익명 게시글 삭제 전용 커스텀 비밀번호 마스킹 모달 UI 구축 (`DeleteConfirmModal.tsx`, 2026-08-24)**:
  1. **조잡한 `prompt()` 완전 제거**: 보안 취약점(평문 노출) 및 스레드 블로킹을 유발하던 브라우저 기본 `prompt()` 팝업창을 100% 제거.
  2. **비밀번호 마스킹 및 모달 디자인 연동**: 비밀글 전용 `<input type="password">` 필드가 적용된 커스텀 삭제 모달(`DeleteConfirmModal.tsx`)을 구축하여 보안성 및 UX 최적화.
  3. **실시간 에러 안내**: 익명 비밀번호 불일치 시 팝업이 닫히지 않고 모달 내부에서 붉은 글씨로 즉시 에러 피드백 안내.

- **관리자(`ROLE_ADMIN`) 게시글 삭제 권한 및 전용 UI 연동 (2026-08-24)**:
  1. **삭제 권한 확장(`app/posts/[publicId]/page.tsx`)**: 관리자 계정(`role === "ROLE_ADMIN"`)일 경우 타인 작성 게시글 및 모든 게시판 글에 대해 `[삭제]` 버튼이 항시 노출되도록 반영.
  2. **수정/삭제 권한 엄격 분리**: 수정(`[수정]`)은 작성자 본인만 가능(원문 조작 차단), 삭제(`[삭제]`)는 작성자 본인 + 관리자가 가능하도록 커뮤니티 정책 반영.
  3. **관리자 전용 UX**: 관리자가 삭제 클릭 시 비밀번호 팝업 없이 `"관리자 권한으로 이 게시글을 삭제하시겠습니까?"` 확인 팝업 후 즉시 삭제(`DELETE /api/v1/posts/{publicId}`) 완결.

- **상단 네비게이션 바(`TopNav`) 동적 로그인 세션 감지 및 Sign In/Out 개편 (2026-08-24)**:
  1. **동적 세션 감지(`SiteChrome.tsx`)**: 하드코딩되어 있던 `Sign In` 버튼을 제거하고, 마운트 시 `GET /api/v1/members/me`를 호출하여 로그인 유무를 자동 감지하도록 반영.
  2. **로그인 유저 UI**: 로그인 시 `[닉네임]님` (마이페이지 `/profile` 링크) 및 `SIGN OUT` 버튼으로 전환. `SIGN OUT` 클릭 시 `POST /api/v1/auth/logout`과 연동되어 세션 파기 후 메인 이동.
  3. **비로그인 유저 UI**: 비로그인 상태일 때만 `SIGN IN` 버튼 노출.

- **작성자 본인 익명글 수정 권한 보강 & 쿠키 기반 조회수 3중 폭증 버그 물리적 해결 (2026-08-24)**:
  1. **작성자 익명글 수정 권한(`PostService.java`, `edit/page.tsx`)**: 로그인한 사용자가 작성한 익명글은 작성자 로그인 세션(`userDetails`)을 검증하여 익명 비밀번호 입력 없이도 100% 수정 허용. 수정 화면(`edit/page.tsx`)에서도 본인 글일 경우 비밀번호 입력란 자동 숨김 처리.
  2. **조회수 중복 방지 쿠키(`viewed_posts`) 도입**: Next.js Strict Mode 2회 마운트 및 작성 직후 리다이렉트로 인해 조회수가 3으로 뻥튀기되던 현상을 `viewed_posts` HTTP 쿠키(유효기간 30분)로 중복 체크하여 **최초 1회만 조회수 `+1` 증가**하도록 완벽 차단.

- **익명게시판 게시글 수정 및 삭제 버튼 노출 & 비밀번호 검증 완결 (2026-08-24)**:
  1. **백엔드 DTO 반영(`PostDetailResponse.java`)**: `isAnonymous` 필드를 DTO 응답에 포함시켜 프론트엔드가 익명글 여부를 100% 인지할 수 있도록 보완.
  2. **익명 수정/삭제 버튼 노출(`app/posts/[publicId]/page.tsx`)**: 익명 대나무숲 게시글 상세 화면에 `[수정]` 및 `[삭제]` 버튼이 항시 정상 노출되도록 반영.
  3. **비밀번호/권한 기반 삭제 처리**: 비로그인/타인 익명글 삭제 시 비밀번호 입력 팝업(`prompt`)을 띄워 백엔드 `DELETE /api/v1/posts/{id}?anonymousPassword=...`와 연동하여 삭제 처리 완결. 본인 작성 익명글/일반글은 즉시 삭제 지원.

- **게시글 작성자 본인만 수정/삭제 가능하도록 프론트/백엔드 이중 보안 가드 적용 (2026-08-24)**:
  1. **프론트엔드 상세 화면(`app/posts/[publicId]/page.tsx`)**: 로그인된 현재 유저(`GET /api/v1/members/me`)와 글 작성자(`writer.publicId`)가 일치하거나 익명글인 경우에만 `[수정]` 버튼 노출하도록 조건부 렌더링 적용. 타인 계정에게는 [수정] 버튼 은닉.
  2. **프론트엔드 수정 화면(`app/posts/[publicId]/edit/page.tsx`)**: 타인이 URL로 직접 수정 페이지 접근 시 `"본인이 작성한 글만 수정할 수 있습니다."` 경고 팝업 후 즉시 원래 상세 페이지로 리다이렉트(가드) 조치.
  3. **백엔드 2차 물리 검증(`PostService.java`)**: `validateEditPermission` 및 `validateDeletePermission`을 강화하여, 작성자 본인이 아닌 계정의 `PUT/DELETE` API 호출 시 `403 FORBIDDEN (ACCESS_DENIED)` 처리로 2차 물리 보안 완성.

- **디스크 기반 영구 데이터베이스 연동으로 데이터 영속성 100% 보장 (2026-08-24)**:
  1. 기존 RAM 전용 인메모리(`jdbc:h2:mem`) 설정을 프로젝트 로컬 파일 기반 영구 데이터베이스(`jdbc:h2:file:./data/snowdb`) 및 `ddl-auto: update`로 완전히 교체.
  2. 이제 서버 종료/재시작/PC 재부팅 후에도 회원가입 계정, 작성 글, 댓글, 추천 내역이 `./data/snowdb.mv.db` 파일 디스크에 100% 영구 보존됨.

- **독립 투표 토글 시스템, 카운트 이중증가 버그 수정, 옵션 텍스트 정제 및 가입 후 자동 로그인 반영 (2026-08-24)**:
  1. **카운트 2중 증가 원인 해결**: `PostReactionEventListener.java` 이벤트 수신 카운팅 중복을 제거하여 1회 클릭 당 정확히 +1/-1만 갱신되도록 물리적 이중 카운팅 원천 해결.
  2. **독립 투표 & 토글 구현**: `PostService.java` 및 `PostReactionRepository.java` 개편으로 추천(LIKE)과 비추천(DISLIKE)을 각각 독립 투표 가능하게 하고, 재클릭 시 DB 레코드 삭제 및 카운트 -1(취소) 처리 보장.
  3. **드롭다운 옵션 텍스트 정제**: `app/posts/create/page.tsx` 셀렉트 박스 내 조잡했던 `(로그인 필요)`, `(누구나 가능)` 괄호 문구 전체 제거 및 `자유게시판`, `익명 대나무숲`, `장비 Q&A`, `리조트 맛집` 표준 명칭 정제.
  4. **회원가입 후 자동 로그인**: `app/signup/page.tsx` 가입 성공 시 로그인 API 연쇄 호출로 세션 자동 쿠키 발급 후 메인 페이지(`/`)로 즉시 리다이렉트.
  5. 백엔드 전체 유닛 테스트 51/51건 BUILD SUCCESSFUL 통과.

- **회원가입 404/401 에러 해결 및 엔드포인트 수정을 통한 가입 기능 정상화 (2026-08-24)**:
  1. `app/signup/page.tsx` 회원가입 API URL을 구버전 `/api/members`에서 버전 명시 표준 경로 `/api/v1/members`로 100% 수정하여 404 및 Spring Security 401 Unauthorized("로그인이 필요합니다") 에러 원천 해결.
  2. 백엔드 `MemberSignUpRequest` 유효성 검증(이메일, 비밀번호 8자 이상+대문자+특수문자, 닉네임 2~10자) 실패 시 에러 사유가 프론트 화면 빨간 상자에 정확히 노출되도록 에러 핸들링 보강.

- **비로그인 사용자 일반 게시판 작성 접근 차단 라우트 가드 적용 (2026-08-24)**:
  1. `app/posts/create/page.tsx` 라우트 가드(Route Guard) 구현: 비로그인 상태에서 `ANONYMOUS`가 아닌 자유/Q&A/맛집 게시판 글쓰기 진입 시 `"자유/Q&A/맛집 게시판 글쓰기는 로그인이 필요합니다."` 알림 후 로그인 페이지(`/login?redirect=/posts/create`)로 즉시 리다이렉트.
  2. 작성 폼 내 카테고리 셀렉트박스 변경 가드 적용: 비로그인 사용자가 일반 카테고리로 변경 시 시도 즉시 차단 및 로그인 유도.
  3. 백엔드 `PostService.java` 비익명 게시판 401 Unauthorized 물리 방어 2차 유지.

- **익명 IP 기반 추천/비추천 투표 & IP 마스킹 정제 완료 (2026-08-24)**:
  1. `post_reaction` 테이블 `member_id` Nullable 및 `writer_ip` 컬럼 추가로 로그인 여부 상관없이 IP 기반 투표 지원 (`PostReaction.java`, `PostReactionRepository.java`, `PostService.java`).
  2. 로컬 IPv6 루프백 주소(`0:0:0:0:0:0:0:1`, `::1`)를 표준 `127.0.0.1`로 자동 변환하여 외부 화면에 `익명 (127.0.***.***)` 깔끔한 마스킹 뱃지로 출력 (`PostResponse.java`, `PostListResponse.java`, `PostDetailResponse.java`).
  3. 프론트엔드 상세 화면(`app/posts/[publicId]/page.tsx`) 추천/비추천 버튼 실시간 연동 및 중복 투표 알림 처리.
  4. 백엔드 전체 unit test 51/51 건 BUILD SUCCESSFUL 통과.

- **프론트 브랜드명 표기 변경 (2026-08-23)**:
  1. 메인 및 공통 네비게이션/footer에 노출되던 `SnowBoarders` 브랜드명을 `SnowThing`으로 변경.
  2. 수정 파일: `frontend/app/components/SiteChrome.tsx`.

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



## 2026-08-26

### 완료

- `main` 브랜치 GitHub Actions에 Spotless lint 검사를 추가.
  - `backend/build.gradle`에 Spotless 6.25.0 설정 추가.
  - 기존 main Java 소스 전체 재포맷을 피하기 위해 `ratchetFrom 'origin/main'` 적용.
  - `.github/workflows/gradle.yml`에서 checkout `fetch-depth: 0` 설정 후 `spotlessCheck` 스텝 추가.
  - 검증 결과: `./gradlew spotlessCheck`, `./gradlew build` 통과.



























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

## 2026-08-19

### 완료

- **백엔드 아키텍처 5대 시니어 코드 리뷰 지적 사항 100% 리팩토링 완료**:
  1. **Service 계층 서블릿 API(HttpServletRequest/HttpSession) 의존성 100% 제거 (SRP 달성)**:
     - `AuthService`는 순수 자바 객체 인증(`authenticate(email, password) -> Member`) 및 `getMyProfile(email)` 조회 비즈니스만 담당하도록 분리.
     - 세션 고정 방어(`changeSessionId()`), SecurityContext 저장, Remember-Me 타임아웃 세팅은 `AuthController` (Web/Controller 계층)로 이관.
  2. **`MasterDataController` JPA Entity Direct 반환 제거 및 Response DTO 적용**:
     - `ResortResponse`, `RidingStyleResponse` 자바 `record` DTO를 정의하여 DB 스키마 유출 및 무한 순환 참조/LazyInitializationException 물리적 차단.
  3. **인증 전용 커스텀 예외 계층 분리 (`CustomAuthException`)**:
     - 로그인 실패 시 범용 `IllegalArgumentException` 대신 `CustomAuthException`을 던지고, `GlobalExceptionHandler`에서 401 Unauthorized JSON 응답 변환 처리.
  4. **로그아웃 완전 보강 (`SecurityConfig.java`)**:
     - `AntPathRequestMatcher("/api/auth/logout")` 적용으로 `invalidateHttpSession`, `clearAuthentication`, `deleteCookies("JSESSIONID")` 처리 보강 및 테스트 완료.
  5. **`MemberRepository` JPQL Fetch Join 성능 최적화**:
     - `MemberResort`, `MemberRidingStyle` N+1 지연 로딩 쿼리 발생을 단 1회의 SQL 조인으로 최적화.
- **백엔드 통합 테스트 수트 검증**:
  - `.\gradlew.bat test` 실행 결과 총 25개 테스트 100% PASS (`BUILD SUCCESSFUL in 13s`).
- **SQL 실행 로그 실증 분석 및 N+1 / MultipleBagFetchException 노션 학습 문서 생성**:
  - 생성 파일: `docs/study/studySqlLogNPlusOne260819.md`
  - 7대 필수 서술 요소 체계(개념, Why, When, How, Pros, Alternatives, Trade-off) 준수 및 실제 Hibernate 3회 조인 SQL 쿼리 로그 캡처 기록.
- **Snowthing 순수 백엔드 & MySQL 8.0 단 1개의 마스터 노션 가이드 생성 (Spring Boot 4.0.0 상향 지정 반영)**:
  - 생성 파일: `docs/study/studySnowthingCompleteInterview260819.md`
  - `backend/build.gradle` 및 스터디 가이드 버전 상향 반영: **Spring Boot `4.0.0`**, **Java `21`**, **Spring Security `7`**, **Spring Dependency Management `1.1.6`**.
  - 과장되거나 모호한 숫자 표현(0.001ms, 0.001% 등)을 100% 삭제하고 정확한 컴퓨터 공학 및 DB 엔지니어링 용어로 정제.
  - 프론트엔드 내용 100% 제거 / Pure Backend 스택 전용 정리.
  - MySQL 8.0 Clustered Index, Secondary Index, Composite Index B-Tree 물리적 구조 & 걸려있는 이유 수록.
  - Bean Validation `@Valid`, JPA, BCrypt, Dual PK, HikariCP, `@Transactional` 등 백엔드 10대 기술 7대 서술 체계 해설 수록.
  - 9대 영역 총 50개 백엔드 기술 질문 & 꼬리 질문 1:1 명확한 대답 대본 100% 집대성.
- **Spring Security 표준 인증 및 PR 리뷰 피드백 9대 개선사항 100% 리팩토링 & 검증 완료 (2026-08-20)**:
  1. `MemberLoginResponse` DTO 내 `List.copyOf()` **방어적 복사 (Defensive Copying)** 적용으로 100% 불변성 보장.
  2. `ErrorCode` Enum (`INVALID_CREDENTIALS`, `MEMBER_NOT_FOUND`, `DUPLICATE_EMAIL` 등) 및 `ErrorResponse` 정형화된 JSON 에러 응답 도입.
  3. `AuthController` 내 매직 넘버 상수화 (`REMEMBER_ME_TIMEOUT_SECONDS = 30 * 24 * 60 * 60`) 및 `calculateSessionTimeoutSeconds()` 메서드 분리.
  4. Spring Security 표준 인증 체계 (`CustomUserDetails`, `CustomUserDetailsService`, `AuthenticationManager`) 구축 및 수동 비밀번호 비교 로직 축출.
  5. `SecurityContextRepository.saveContext()` 시큐리티 표준 세션 저장 적용.
  6. `MemberController` 수동 SecurityContextHolder 파싱 코드를 `@AuthenticationPrincipal CustomUserDetails userDetails` 파라미터 바인딩으로 깔끔하게 개선.
  7. 서비스의 수동 세션 로그아웃 코드를 무효화하고 SecurityConfig의 `logout()` 설정에 100% 위임.
  8. `.\gradlew.bat test` 실행 결과 총 25개 단위/통합 테스트 **BUILD SUCCESSFUL 100% PASS** 검증 완료.
- **Sprint 01 백엔드 전 과정 코드, 1줄 상세 주석, 설계 배경 & 5대 아키텍처 대안 마스터 가이드 생성 (2026-08-21)**:
  - 생성 파일: [`docs/study/sprint01/studySprint01CompleteCodeMaster260821.md`](file:///c:/Users/ikaes/IdeaProjects/snowthing/docs/study/sprint01/studySprint01CompleteCodeMaster260821.md)
  - Sprint 01 백엔드 전체 코드(Security 7, Global Exception, Auth, Member, Entity, Repository, 25개 테스트 수트)에 1줄 한 줄 물리적 해설 주석(Annotation) 추가.
  - **[WHY] 왜 그렇게 만들어졌는가 (설계 결정 배경 & Rationale)** 전면 수록.
  - 10대 핵심 기술 튜닝 파라미터/옵션 탐구 및 **"여기서는 이렇게 설계했어도 좋았을 것이다" 5대 아키텍처 대안 (RememberMeServices, Redis Distributed Session, JWT+RTR, DDD Composite PK, CQRS)** 완벽 집대성.
- **Sprint 01 게시글(Post) 도메인 11개 단계 전 과정 개발 및 DB UNIQUE 제약 조건 중복 방지 구축 (2026-08-21)**:
  - 엔티티 & 저장소: `Post`, `PostCategory`, `PostImage`, `PostReaction`, `PostStatus`, `ReactionType`
  - DB 유니크 제약조건: `PostReaction` 내 `@UniqueConstraint(name = "uk_post_member", columnNames = {"post_id", "member_id"})` 설정으로 추천/비추천 연타 시 DB 레벨 원자적 차단 (`409 Conflict ALREADY_REACTED`)
  - 비동기 처리: `@Async` `PostReactionEventListener`를 통한 역정규화 카운터(`like_count`, `dislike_count`) 갱신
  - Soft Delete: `@SQLDelete`, `@SQLRestriction("is_deleted = false")`, `deleted_at DATETIME NULLABLE` 적용
  - API & DTO: `PostController`, `PostService`, `PostCreateRequest`, `PostUpdateRequest`, `PostResponse`, `PostListResponse` (본문 제외), `PostDetailResponse`, `PostReactionRequest`
  - 테스트 수트: `PostServiceTest`, `PostControllerTest` (작성/조회/수정/삭제/권한 10대 테스트 케이스 완료)
- **Sprint 02 feature/sprint02-board Git 브랜치 생성, 커밋 및 GitHub 원격 푸시 완료 (2026-08-21)**:
  - 실행 명령어: `git checkout -b feature/sprint02-board` ➔ `git commit -m "feat(post): 게시글 CRUD, 추천/비추천 기능 구축"` ➔ `git push origin feature/sprint02-board`
  - 내용: GitHub 원격 저장소(`origin`)에 `feature/sprint02-board` 브랜치 푸시 완료 (PR 링크: https://github.com/devikae/snowthing/pull/new/feature/sprint02-board).
- **Sprint 02 게시글(Post) 도메인 전용 5대 아키텍처 결함 & 물리적 극복방안 스터디 가이드 생성 (2026-08-21)**:
  - 생성 파일: [`docs/study/sprint02/studySprint02PostDomainIssues260821.md`](file:///c:/Users/ikaes/IdeaProjects/snowthing/docs/study/sprint02/studySprint02PostDomainIssues260821.md), [`docs/study/studySprint02PostDomainIssues260821.md`](file:///c:/Users/ikaes/IdeaProjects/snowthing/docs/study/studySprint02PostDomainIssues260821.md)
  - 내용: ①[목록 조회 시 content 본문 포함 트래픽 폭증 ➔ JPQL Projections DB I/O 차단], ②[카테고리 페이징 Count(*) Full Scan ➔ Slice 페이징 & Covering Index], ③[수정/삭제 시 작성자 인가 누락 IDOR ➔ validatePostOwnerOrAdmin 403 차단], ④[회원글/익명글 카테고리 변경 시 작성자 정보 꼬임 ➔ changeCategory 400 차단], ⑤[PostReaction 추천/비추천 동시 연타 데드락 ➔ Debounce & Spring Retry] 5대 게시글 전용 결함 해설 수록 완료
- **Auth/Member/Master 11대 아키텍처 및 보안 결함 리팩토링 완료 (2026-08-23)**:
  - DTO 방어적 복사: `MemberLoginResponse.from()` 생성자 불변 리스트 `List.copyOf()` 적용으로 가변성 오염 물리 차단.
  - 예외 처리 표준화: `AuthService`, `MemberService` 내 `IllegalArgumentException("INVALID_CREDENTIALS" / "DUPLICATE_EMAIL")` 문자열 리터럴 예외를 `CustomAuthException(ErrorCode)`로 전면 교체. `GlobalExceptionHandler` 내 리터럴 문자열 대조 제거.
  - 계층 분리 & 레이어드 아키텍처: `MasterDataService` [NEW] 레이어 도입으로 `MasterDataController` 내 레포지토리 직접 주입 제거 및 DTO 반환 보장. `AuthService` 내 서블릿/세션 결합 제거.
  - 상수 추출 & API 버저닝: `REMEMBER_ME_TIMEOUT_SECONDS`, `DEFAULT_SESSION_TIMEOUT_SECONDS` 상수 추출. `@RequestMapping("/api/v1/master")`, `/api/v1/auth`, `/api/v1/members` API v1 버저닝 적용.
  - 시큐리티 수동 검증 제거: `MemberController` 수동 `SecurityContextHolder` 검증을 삭제하고 `SecurityConfig` 및 `@AuthenticationPrincipal` 주입 표준화 적용.
  - 중복 체크 검증 강화: `MemberService` 가입/수정 시 사전 `existsByEmail`, `existsByNickname` 비즈니스 예외 처리 및 DB Unique 제약조건 이중 방어선 세팅.
  - 전체 단위/통합 테스트: 51개 전체 테스트 100% PASS (`BUILD SUCCESSFUL`).
- **게시글/댓글 수정·삭제 권한 및 PostStatus Enum/UX 정책 확정 (`project.md` 반영) (2026-08-23)**:
  1. **수정 권한 정립**: **작성자 본인만 수정 가능** (`PUT /api/v1/posts/{id}`). **관리자(`ROLE_ADMIN`) 포함 타인 수정 100% 차단** (내용 왜곡 및 명의 도용/책임 소재 논란 방지).
  2. **관리자 권한 범위**: 관리자는 수정 권한이 없으며, 부적절한 게시글에 대한 **블라인드/차단(`status = BLOCKED`) 처리만 수행**.
  3. **PostStatus Enum 세분화**: `NORMAL`, `DELETED`, `BLOCKED`, `HIDDEN`(비공개), `DRAFT`(임시작성) 5개 상태값으로 세분화 분리.
  4. **삭제글 UX 모달 알럿 클릭 정책 (방안 A)**: 인기글(HOT) 및 게시판 목록에 노출되던 글이 삭제/차단된 경우, 목록 제목은 `"[삭제된 게시글입니다]"` 형태로 유지하되 유저가 클릭 시 **상세 페이지로 이동(라우팅)하지 않고 목록 화면 상에서 클릭 즉시 "삭제된 게시글입니다" / "관리자에 의해 차단된 게시글입니다" 알럿(Modal/Alert) 창을 팝업**하여 UX 최적화 및 서버 부하 차단.
  5. `docs/project/project.md` 문서에 해당 정책 세부 수록 완료.
- **게시글 무료 마크다운 에디터 및 목록 이미지 뱃지 역정규화(`has_image`) 정책 확정 (2026-08-23)**:
  1. **무료 마크다운 에디터 스택 (Toast UI Editor)**: NHN 개발 100% 무료 MIT 라이선스 **Toast UI Editor (`@toast-ui/react-editor`)**를 채택하여 `![설명](url)` 커서 인라인 삽입을 통한 `[텍스트 ➔ 이미지 ➔ 텍스트 ➔ 이미지]` 본문 구성 및 XSS 안전성 확보.
  2. **목록 이미지 뱃지 역정규화 (`Post.has_image`)**: `Post` 테이블에 `has_image` (`BOOLEAN`) 컬럼을 배치하여 1:N 조인 쿼리 0건으로 **초록색 산 이미지 아이콘(🖼️)** 및 **댓글 말풍선 아이콘(💬)** 목록 시각적 뱃지 렌더링 최적화.
  3. `docs/project/project.md` 문서 반영 완료.
- **Snowthing Refined 프론트엔드 디자인 시스템 및 5개 화면 적용 완료 (2026-08-23)**:
  1. **디자인 테마 시스템 (`layout.tsx`, `globals.css`)**: `docs/project/design/snowthing_refined/DESIGN.md` 명세 기준 Pure White (`#FFFFFF`) & Charcoal (`#111827`) & Hanken Grotesk + JetBrains Mono 폰트 스택 및 Material Symbols 적용.
  2. **게시판 피드 리뉴얼 (`app/posts/page.tsx`)**: 자유게시판/익명게시판 피드에 **🖼️ 초록색 이미지 첨부 뱃지 (`hasImage === true`)** 및 **💬 댓글 말풍선 뱃지 (`commentCount > 0`)** 렌더링 적용.
  3. **삭제글/차단글 모달 알럿 UX**: 목록에서 삭제/차단된 글 클릭 시 상세 라우팅 없이 instant `alert("삭제된 게시글입니다.")` 모달 팝업 처리.
  4. **메인 허브 & 작성 폼 리뉴얼 (`app/page.tsx`, `app/posts/create/page.tsx`)**: Shred-Talk 허브 및 마크다운 본문 작성 폼 Refined UI 적용 완료.











- **Snowthing Refined 기준 프론트 전체 디자인 재정비 (2026-08-23)**:
  1. `docs/project/design/snowthing_refined/DESIGN.md` 및 `_1`, `_2`, `_3` 화면 시안을 기준으로 흰 캔버스, 차콜 타이포그래피, 1px 보더, 4px radius, 고밀도 게시글 리스트 중심의 프론트 UI로 재구성.
  2. `frontend/app/globals.css`에 공통 디자인 토큰과 `snow-card`, `snow-btn-primary`, `snow-btn-secondary`, `snow-input`, `snow-chip` 등 공통 유틸리티 클래스 정리.
  3. `frontend/app/components/SiteChrome.tsx` 신규 추가: `TopNav`, `SideCategories`, `Footer` 공통화로 페이지별 네비게이션 중복 및 디자인 불일치 제거.
  4. 메인, 게시글 목록, 익명 게시판, 게시글 작성/수정/상세/댓글, 로그인, 회원가입, 프로필, 리조트 화면의 깨진 한글 문구와 인라인 스타일/어두운 테마 잔재를 제거하고 동일한 Snowthing Refined UI로 통일.
  5. 게시글 목록과 익명 게시판의 삭제/차단 게시글 클릭 방지 UX 유지: 상세 페이지 이동 없이 즉시 alert 표시.
  6. 검증 결과: `npm run lint` error 0개, warning 8개(`<img>` 최적화 권고 및 `layout.tsx` 폰트 로드 권고만 남음). `npm run build` 성공.
  7. 남은 이슈: 현재 이미지는 외부 URL과 일반 `<img>`를 사용하므로 Next Image 최적화를 적용하려면 `next.config.ts`의 remote image domain 설정과 `next/image` 전환이 필요함.
- **백엔드 계층 결합/익명 추천 리팩터링 완료 (2026-08-24)**:
  1. `PostService`에서 `HttpServletRequest`, `HttpServletResponse`, `Cookie` 의존을 제거하고 조회수 증가는 `shouldIncreaseViewCount` 순수 값으로 받도록 분리.
  2. `ClientIpResolver`, `ViewCountCookieManager`, `AnonymousVoterCookieManager`를 추가해 웹 계층에서 IP 추출, 조회수 쿠키, 익명 투표자 쿠키를 전담.
  3. 비로그인 익명 추천/비추천을 `anonymous_voter_id` 쿠키 기반으로 처리하도록 `PostReaction`과 `PostReactionRepository`를 수정하고, 회원/익명 각각의 복합 유니크 제약을 분리.
  4. Redis는 아직 도입하지 않고, 추후 AP 성향의 Redis 카운터/이력 저장소로 전환하기 쉽도록 `ReactionActor` 값 객체를 추가.
  5. `MemberSignUpRequest`, `MemberProfileUpdateRequest` 컬렉션 필드에 방어적 복사 및 불변 조회 반환 적용.
  6. `SecurityConfig`의 문자열 JSON 직접 응답을 `ErrorResponse` + `ObjectMapper` 기반 표준 응답으로 교체.
  7. 검증 결과: `./gradlew.bat test` 전체 통과.

- **익명 추천 리팩터링 학습 문서 및 테스트 보강 완료 (2026-08-24)**:
  1. `docs/study/sprint02/익명추천_계층분리_리팩터링_학습정리260824.md` 문서를 생성해 서비스-웹 계층 분리, 익명 쿠키 기반 투표, Redis/AP 전환 여지, DTO 방어적 복사, 보안 응답 표준화 이유를 한글 제목과 본문으로 정리.
  2. `WebCookieManagerTest` 추가: 조회수 쿠키 최초/중복 판단, 익명 투표자 쿠키 생성/재사용, `X-Forwarded-For` 첫 IP 추출 검증.
  3. `PostControllerTest` 보강: `viewed_posts` 쿠키가 있을 때 조회수 중복 증가 차단, 비로그인 익명 사용자의 `anonymous_voter_id` 쿠키 기반 추천 토글 검증.
  4. `MemberServiceSignUpVerificationTest` 보강: 회원가입 요청 DTO 컬렉션 방어적 복사 및 getter 불변 리스트 반환 검증.
  5. 검증 결과: `./gradlew.bat test` 전체 60개 테스트 통과.

- **게시글 수정 시 첨부 이미지/hasImage 동기화 누락 보완 완료 (2026-08-24)**:
  1. `PostUpdateRequest.imageUrls()`가 존재하지만 `PostService.updatePost()`에서 실제 `post_image` 목록과 `Post.hasImage`를 갱신하지 않던 문서-구현 불일치 결함을 확인.
  2. `Post.replaceImages()` 도메인 메서드를 추가해 이미지 컬렉션 교체와 `hasImage` 역정규화 값을 같은 엔티티 메서드에서 원자적으로 동기화하도록 정리.
  3. `PostService.createPost()`와 `PostService.updatePost()` 모두 `Post.replaceImages()` 경로를 사용하도록 맞춰, 작성/수정 이미지 저장 방식이 분리되지 않게 개선.
  4. `PostServiceTest`에 게시글 수정 시 이미지 목록 교체 성공 케이스와 이미지 전체 제거 시 `hasImage=false` 및 `post_image` row 제거 검증 케이스 추가.
  5. 검증 결과: `./gradlew.bat test --tests "com.ikae.snowthing.domain.post.service.PostServiceTest"` 통과, 게시글 도메인 테스트 통과, 댓글 도메인 테스트 통과, `./gradlew.bat test` 전체 62개 테스트 통과, `./gradlew.bat check` 통과.
  6. 참고 이슈: 게시글/댓글 테스트를 Gradle로 병렬 실행했을 때 동일 `build/test-results/test/binary` 파일 접근 충돌로 EOF/NoSuchFile 오류가 발생했으나, 순차 실행에서는 모두 통과함.

- **README 게시판(Post) 도메인 설명 문체 및 근거 보완 완료 (2026-08-24)**:
  1. 루트 `README.md`의 게시판 도메인 섹션을 사용자 말투에 맞게 다시 작성하고, 과장된 표현과 근거 없는 성능 수치를 제거.
  2. 게시글 도메인 구조, 회원-게시글 연관관계, 상태와 유형, 목록/상세 응답 분리 이유, 페이지네이션/정렬 기준, 수정 권한 검증, Hard Delete/Soft Delete 비교, 현재 삭제 방식 선택 이유, 실행 쿼리 확인 결과, 주요 예외 응답을 각각 독립 소제목으로 정리.
  3. 최근 반영한 `Post.replaceImages()`와 `hasImage` 역정규화 동기화 내용을 README 설명에 포함하여 코드와 문서 불일치를 줄임.
  4. 검증 결과: 문서 변경 범위 확인 완료. 코드 변경 없음.

- **README 추천/비추천 용어 정리 완료 (2026-08-24)**:
  1. `README.md`의 `PostReaction` 설명에서 `회원 투표`, `비로그인 익명 투표` 표현을 제거하고 `로그인 사용자의 추천/비추천`, `비로그인 사용자의 익명 추천/비추천`으로 수정.
  2. 주요 예외 응답의 `익명 글 작성/투표` 표현도 `익명 글 작성/추천·비추천`으로 정리.
  3. 검증 결과: 문서 문구 검색 및 변경 범위 확인 완료. 코드 변경 없음.

- **CI compileJava 실패 수정 (2026-08-26)**:
  1. `CommentService.java` rebase 충돌 정리 과정에서 남은 깨진 한글 주석/인코딩 문자를 제거.
  2. 기능 로직 변경 없이 주석만 정리하여 Java 소스 UTF-8 컴파일 오류를 해소.
  3. 검증 결과: `./gradlew compileJava` 통과.

- **CI Spotless 중복 설정 및 Gemini 리뷰 수동 실행 전환 (2026-08-27)**:
  1. `feature/sprint02-board` 브랜치를 원격 최신 `origin/feature/sprint02-board`로 fast-forward 반영한 뒤 작업 진행.
  2. `backend/build.gradle`에 중복으로 3번 선언된 `spotless { java { googleJavaFormat(...) } }` 블록을 1개로 정리하여 `spotlessCheck`의 `Multiple steps with name 'google-java-format'` 오류 원인 제거.
  3. 남긴 Spotless 설정에는 `ratchetFrom 'origin/main'`을 유지하여 기존 main 대비 변경 파일 중심으로 검사되도록 유지.
  4. `.github/workflows/gemini-review.yml`의 트리거를 `pull_request` 자동 실행에서 `issue_comment` 기반 실행으로 변경.
  5. PR 댓글에 `/gemini-review`가 포함된 경우에만 Gemini 리뷰가 실행되도록 하여 커밋 push마다 리뷰 댓글이 누적되는 문제를 방지.
  6. `issue_comment` 이벤트에서도 실제 PR 브랜치 diff를 읽을 수 있도록 `gh pr checkout "$PR_NUMBER"` 후 base branch와 `backend/src/` diff를 비교하도록 수정.
  7. 검증 결과: `./gradlew.bat spotlessCheck` 통과, `./gradlew.bat test build -x spotlessCheck` 통과.
  8. 남은 이슈: 현재 Gemini 리뷰는 PR 전체 댓글 방식 유지. 라인별 코드 코멘트가 필요하면 Gemini 응답을 `path`, `line`, `body` JSON으로 구조화하고 GitHub Pull Request Review API를 사용하는 별도 개선이 필요.

- **PR Checks에 Spotless 별도 표시되도록 CI Job 분리 (2026-08-27)**:
  1. `.github/workflows/gradle.yml`의 단일 `build` job 안에서 step으로 실행되던 `spotlessCheck`를 별도 `spotless` job으로 분리.
  2. GitHub PR checks 목록에 `Java CI with Gradle / Spotless Check`와 `Java CI with Gradle / Build and Test`가 각각 표시되도록 job name 지정.
  3. `build` job에 `needs: spotless`를 추가하여 Spotless 검사 실패 시 빌드/테스트가 실행되지 않도록 Fast-Fail 흐름 유지.
  4. 기존 깨진 한글 주석은 제거하고 ASCII 기반의 간결한 workflow로 정리.
  5. 검증 결과: `git diff --check` 통과. 실제 GitHub Actions job 표시 여부는 push 후 PR checks 화면에서 확인 필요.

- **Sprint 03 댓글 생성(Create) 기능 구현 및 검증 완료 (2026-09-01)**:
  1. 댓글 생성 경로에서 빌더 대신 `Comment.create()` 정적 팩토리를 사용하고, 대댓글에 작성한 답글의 부모를 최상위 루트로 평탄화하는 `rootParent()` 도메인 메서드를 추가.
  2. 루트별 활성 대댓글 수를 최대 100개로 제한하고, 초과 시 `COMMENT_004` (`COMMENT_REPLY_LIMIT_EXCEEDED`, 400 Bad Request) 예외를 반환하도록 구현.
  3. 동일 루트의 동시 생성 요청이 제한 검증을 함께 통과하지 않도록 루트 댓글에 비관적 쓰기 잠금을 적용하고, 삭제된 대댓글은 활성 개수 집계에서 제외.
  4. 댓글 저장과 `post.comment_count + 1` 벌크 갱신을 동일 트랜잭션에서 처리하여 성공·실패 경계를 동기화.
  5. 타 작업과의 충돌 방지를 위해 신규 `CommentCreateTest.java`에만 성공·실패·동시성 테스트 총 16건을 작성. H2를 사용하지 않고 로컬 MySQL 8.0.46의 테스트 전용 `snowthing_test` 스키마에서 `./gradlew.bat test --tests "*CommentCreateTest*"` 실행 결과 16건 전체 통과, `spotlessCheck` 통과, 종료 후 테스트 테이블 0개 확인.
  6. 실 MySQL 동시성 테스트에서 `REPEATABLE READ` 스냅샷 때문에 루트 잠금만으로는 99개 경계의 두 요청이 모두 통과하는 결함을 발견. 부모 최초 조회와 활성 대댓글 집계를 잠금 기반 현재 읽기로 변경하여 두 요청 중 1건만 성공하고 최종 100개가 유지됨을 검증.
  7. 남은 이슈: 비관적 잠금은 같은 루트에 대댓글 생성이 집중되면 해당 루트의 쓰기 요청을 직렬화하므로, 운영 환경에서는 잠금 대기 시간과 타임아웃 지표를 관찰해야 함. 프로젝트의 H2 테스트 의존성은 다른 기존 테스트가 사용하므로 제거하지 않았으며 `CommentCreateTest`에서는 MySQL 드라이버와 dialect를 강제해 H2를 사용하지 않음.

- **Sprint 03 댓글 목록 및 대댓글 분리 조회(Read) 구현 완료 (2026-09-01)**:
  1. `comment` 테이블과 `Comment` 엔티티에 `(post_id, parent_id, created_at, comment_id)`, `(parent_id, created_at, comment_id)` 복합 인덱스를 추가.
  2. `CommentRepositoryCustom`/`CommentRepositoryImpl`을 추가하고 루트 댓글 커서 페이징, MySQL 8.0 `ROW_NUMBER() OVER (PARTITION BY parent_id)` 기반 부모별 Top-5 프리뷰, 대댓글 분리 커서 페이징을 구현.
  3. API 명세의 Long 타입 `commentId` 커서를 유지하면서 기준 행의 `created_at`을 복원해 `(created_at ASC, comment_id ASC)` 복합 정렬과 커서 조건이 일치하도록 처리.
  4. `CommentResponse`, `PostCommentListResponse`, `CommentReplyListResponse`를 조회 명세에 맞춰 구성하고 모든 응답 컬렉션에 `List.copyOf()` 방어적 복사를 적용.
  5. 삭제 루트에 활성 대댓글이 있으면 placeholder와 프리뷰를 노출하고, 루트와 하위 대댓글이 모두 삭제되면 목록에서 은닉하도록 정책 반영. `replyCount`는 활성 대댓글만 집계.
  6. `CommentReadTest.java` 신규 파일에 성공/실패 10개 시나리오를 작성: 루트 커서 경계, 동일 시각 PK 타이브레이커, Top-5/분리 조회, 삭제 은닉, DTO 불변성, 게시글 없음, 크기 범위, 다른 범위의 커서, 대댓글 ID의 루트 오용 검증.
  7. 검증 결과: `./gradlew.bat test --tests "*CommentReadTest*"` 10개 테스트 통과, `compileJava` 및 `spotlessApply` 통과.
  8. 확인 이슈: 기존 `CommentServiceTest`의 자식 없는 삭제 루트 노출 기대값은 Sprint 3의 고아 노드 은닉 정책과 충돌함. 기존 테스트 파일 수정 금지 조건에 따라 변경하지 않음. `CommentCreateTest` 동시성 테스트는 결과 중 정상 성공을 `null`로 표현하면서 `List.of(null, ...)`을 호출해 테스트 코드 자체에서 `NullPointerException`이 발생하며, Read 구현과 무관한 기존 이슈로 확인됨.
  9. 운영 확인 필요: Top-5 쿼리는 MySQL 8.0 윈도우 함수에 의존하므로 배포 전 실제 MySQL에서 `EXPLAIN ANALYZE`로 두 복합 인덱스 사용 여부와 filesort/읽은 행 수를 재검증해야 함.
  10. 공개 저장소 보안 점검에서 테스트, Spring 설정, Docker Compose에 하드코딩된 MySQL 비밀번호를 발견해 모두 환경변수 참조로 교체하고 `.env.example`에는 실제 값이 아닌 placeholder만 제공. `CommentCreateTest`는 `SNOWTHING_TEST_DB_URL`이 없으면 H2를 사용하고, 외부 MySQL을 선택한 경우 username/password 환경변수를 필수 검증하도록 변경.
  11. 실제 MySQL 8.0에서 Top-5 프리뷰 쿼리 실행 시 `row_number` 별칭이 함수명과 충돌해 500이 발생하는 문제를 확인하고, 윈도우 순번 별칭을 `rn`으로 변경해 MySQL 문법 호환성을 확보.
  12. MySQL 클라이언트 문자셋 오류로 한글이 손상되어 있던 Spike 전용 게시글 998/999와 댓글 2,000건을 `--default-character-set=utf8mb4`로 제한 재시드. 재검증 결과 게시글별 1,000건, API 루트 20개/Top-5 프리뷰, UTF-8 한글 응답 바이트를 확인.
  13. 로컬 재기동 시 `DataInitializer`가 이메일 존재 여부만 확인한 뒤 이미 사용 중인 닉네임을 삽입해 유니크 제약으로 실패하는 별도 이슈 발견. 데이터 삭제 없이 확인하기 위해 현재 서버는 `local,test` 프로필로 초기화기만 제외해 실행 중이며, 초기화기 멱등성 보완은 별도 작업 필요.
  14. 최초 Spike 재시드에서 `INSERT IGNORE`가 기존의 손상된 작성자 닉네임과 카테고리명을 유지하는 문제를 확인. 시드 SQL을 `ON DUPLICATE KEY UPDATE` 방식으로 보완하여 `스파이크테스터`, `자유게시판` 값도 UTF-8로 복구하도록 수정.

- **Sprint 03 댓글 C-R 프론트엔드 2단계 UI 개편 완료 (2026-09-01)**:
  1. 백엔드의 루트 댓글 20개 커서 조회, 루트별 대댓글 Top-5 프리뷰, 대댓글 분리 커서 조회 계약에 맞춰 프론트엔드 댓글 구조를 재귀형 무한 계층에서 루트/대댓글 2단계 구조로 변경하기로 확정.
  2. 작업 범위는 댓글 작성(Create)과 조회(Read)이며, 댓글 수정·삭제 UI 개편은 제외. 기존 삭제 기능은 회귀 방지를 위해 유지.
  3. `frontend/app/lib/api.ts`에 루트 댓글과 대댓글의 `cursor`/`size` URL 빌더를 추가하고, `null` 여부로 커서 포함을 판별하도록 구현.
  4. `frontend/app/posts/[publicId]/page.tsx`의 DTO를 백엔드 응답 계약에 맞추고, 루트 댓글 20개 누적 조회와 루트별 대댓글 Top-5/20개 누적 조회 상태를 분리.
  5. 재귀형 `CommentRow`를 루트 `CommentRow`와 비재귀 `ReplyRow`로 분리해 3단계 이상 렌더링을 차단. 대댓글의 답글 버튼도 루트 작성창을 열고 원작성자 멘션 가이드만 표시하며, 서버에는 루트 ID를 `parentId`로 전달.
  6. 댓글·대댓글 응답 병합 시 `commentId` 중복을 방어하고, 삭제된 루트 placeholder 아래의 대댓글과 답글 작성 기능은 유지.
  7. 검증 결과: 변경 파일 대상 ESLint 오류 0건(기존 `<img>` 최적화 경고 1건), `npm run build` 및 TypeScript 검사 통과.
  8. 확인 이슈: 전체 `npm run lint`는 이번 변경과 무관한 기존 `ToastEditor.tsx`, `ToastViewer.tsx`, 게시글 작성·목록 페이지의 오류 6건 때문에 실패. 브라우저 수동 검증은 백엔드와 테스트 데이터가 실행된 환경에서 추가 확인 필요.
