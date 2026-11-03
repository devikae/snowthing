# 관리자 시스템 및 대시보드 (2026-11-02)

## 1. 승인 범위

본 문서는 SnowThing 서비스의 통합 관리자 백오피스(`/admin`) 및 런타임 통제 시스템의 공식 설계 명세서이다.
비로그인 사용자 라이브톡 참여 제어, 채팅 악성 IP 차단 및 원클릭 정화, 회원 프로필/계정 제재, 사용자 삭제글 열람 및 불량글 강제 블라인드, 일일 순수 방문자(DAU) 통계, 메인 배너 관리를 포함한다.

---

## 2. 데이터 및 스키마 (027 마이그레이션)

### 2.1 신규 테이블 정의

1. **`system_config` (동적 런타임 설정 / Feature Flag)**:
   - `config_key` VARCHAR(50) PRIMARY KEY: 설정 식별자 (예: `CHAT_ANONYMOUS_ENABLED`)
   - `config_value` VARCHAR(255) NOT NULL: 설정 값 (`true`, `false` 등)
   - `value_type` VARCHAR(20) NOT NULL: `BOOLEAN`, `STRING`, `JSON`, `NUMBER`
   - `description` VARCHAR(200) NOT NULL: 설정 설명
   - `updated_at` DATETIME(6) NOT NULL: 최종 수정 일시
   - `updated_by` VARCHAR(50) NOT NULL: 최종 수정한 관리자 계정 식별자

2. **`daily_visitor_log` (일별 고유 방문자 식별 로그)**:
   - `visit_date` DATE NOT NULL: 방문 일자 (KST 기준)
   - `visitor_hash` CHAR(64) NOT NULL: `SHA-256(client_ip + user_agent + daily_salt)` 익명 해시
   - `is_member` BOOLEAN NOT NULL DEFAULT FALSE: 로그인 회원 여부
   - `first_visited_at` DATETIME(6) NOT NULL: 당일 첫 방문 시각
   - PRIMARY KEY (`visit_date`, `visitor_hash`)
   - 복합 기본키 B-Tree 인덱스로 당일 중복 접근에 대한 `INSERT IGNORE`를 원자적으로 처리

3. **`daily_visitor_stats` (일별 방문자 집계 요약)**:
   - `stat_date` DATE NOT NULL PRIMARY KEY: 통계 일자
   - `total_visitors` INT NOT NULL DEFAULT 0: 당일 총 고유 방문자 수 (DAU)
   - `member_visitors` INT NOT NULL DEFAULT 0: 회원 고유 방문자 수
   - `guest_visitors` INT NOT NULL DEFAULT 0: 비로그인 고유 방문자 수
   - `pv_count` INT NOT NULL DEFAULT 0: 총 페이지 뷰
   - `updated_at` DATETIME(6) NOT NULL: 갱신 시각

4. **`chat_banned_ip` (채팅 차단 IP 목록)**:
   - `id` BIGINT AUTO_INCREMENT PRIMARY KEY
   - `banned_ip` VARCHAR(45) NOT NULL: 차단 대상 IPv4 / IPv6
   - `reason` VARCHAR(255) NOT NULL: 차단 사유
   - `banned_until` DATETIME(6) NULL: 차단 만료 일시 (NULL이면 영구 차단)
   - `created_by` VARCHAR(50) NOT NULL: 차단 실행 관리자
   - `created_at` DATETIME(6) NOT NULL: 차단 등록 시각
   - INDEX `idx_chat_banned_ip_lookup` (`banned_ip`, `banned_until`)

5. **`banner` (메인 상단 배너 관리)**:
   - `id` BIGINT AUTO_INCREMENT PRIMARY KEY
   - `title` VARCHAR(100) NOT NULL: 배너 명칭
   - `image_url` VARCHAR(500) NOT NULL: 배너 이미지 CDN URL
   - `link_url` VARCHAR(500) NULL: 클릭 시 랜딩 링크 URL
   - `display_order` INT NOT NULL DEFAULT 0: 노출 우선순위 (낮을수록 앞단)
   - `is_active` BOOLEAN NOT NULL DEFAULT TRUE: 노출 활성화 On/Off
   - `start_at` DATETIME(6) NULL: 노출 시작 일시
   - `end_at` DATETIME(6) NULL: 노출 종료 일시
   - `created_at` DATETIME(6) NOT NULL

### 2.2 기존 테이블 보완
- **`post` 테이블**:
  - `is_blinded` BOOLEAN NOT NULL DEFAULT FALSE 컬럼 추가 및 인덱스 부여.
  - 관리자가 불량/도배 글을 강제 숨김 처리하면 일반 목록 및 상세 조회에서 차단된다.

---

## 3. 백엔드 API 명세

모든 `/api/v1/admin/**` 경로는 Spring Security의 `hasRole('ADMIN')` 인가를 필수로 거친다.

### 3.1 대시보드 및 통계 API
- `GET /api/v1/admin/dashboard/stats`:
  - 반환: 오늘 DAU(전일 대비 증감율), 회원/게스트 비율, 누적 회원수, 오늘 가입자수, 최근 7일/30일 일별 추이 배열 `[ { date, total, member, guest } ]`.

### 3.2 런타임 설정 API
- `GET /api/v1/admin/configs`: 전체 시스템 설정 목록 반환.
- `PATCH /api/v1/admin/configs/{key}`: 설정 값 수정.
  - `CHAT_ANONYMOUS_ENABLED` 토글 시 `system_config` 갱신 및 WebSocket `/sub/chat/main`으로 `CONFIG_UPDATED` 시스템 알림 브로드캐스트.
- `GET /api/v1/public/configs`: 일반 클라이언트용 공개 설정 조회 (`CHAT_ANONYMOUS_ENABLED` 등).

### 3.3 회원 관리 및 제재 API
- `GET /api/v1/admin/members`: 회원 검색 및 목록 (이메일, 닉네임, 상태, 가입일, 페이징).
- `PATCH /api/v1/admin/members/{publicId}/status`: 계정 상태 변경 (`ACTIVE` ↔ `SUSPENDED`).
- `PATCH /api/v1/admin/members/{publicId}/nickname`: 음란/욕설 닉네임 강제 초기화 및 수정.
- `DELETE /api/v1/admin/members/{publicId}/profile-image`: 불쾌한 프로필 사진 강제 영구 삭제.
  - `ProfileImageLifecycleService.reset(email)`을 호출하여 S3 이미지 영구 삭제 저널 등록 및 즉시 null 처리.

### 3.4 컨텐츠 관리 API
- `GET /api/v1/admin/posts`: 게시글 통합 목록 (상태 필터: 정상, 관리자블라인드, 사용자삭제).
  - 유저 삭제글(`is_deleted = true`)의 경우 삭제 일시 및 당시 본문 조회 가능.
- `PATCH /api/v1/admin/posts/{publicId}/blind`: 게시글 관리자 강제 숨김/해제.

### 3.5 채팅 및 IP 차단 API
- `GET /api/v1/admin/chat/banned-ips`: 현재 차단된 IP 목록 조회.
- `POST /api/v1/admin/chat/banned-ips`: IP 수동 차단 등록.
- `DELETE /api/v1/admin/chat/banned-ips/{id}`: IP 차단 해제.
- `POST /api/v1/admin/chat/messages/{messageId}/ban-ip`:
  - 메인 라이브톡 메시지 기준 발신 IP 즉시 차단 + 해당 유저의 최근 메시지 일괄 삭제(`PURGE`).
  - WebSocket `/sub/chat/main`으로 `{ type: "PURGE_MESSAGES", messageIds: [...] }` 전파.

### 3.6 배너 관리 API
- `GET /api/v1/admin/banners`: 배너 전체 목록.
- `POST /api/v1/admin/banners`: 배너 신규 등록.
- `PUT /api/v1/admin/banners/{id}`: 배너 수정 (이미지, 링크, 순서, 활성화 여부).
- `DELETE /api/v1/admin/banners/{id}`: 배너 삭제.
- `GET /api/v1/public/banners`: 메인 화면 노출용 활성 배너 목록.

---

## 4. 프론트엔드 UI & 실시간 라이브톡 연동 명세

1. **관리자 레이아웃 (`frontend/app/admin/layout.tsx`)**:
   - 반응형 사이드바 (대시보드, 회원 관리, 컨텐츠 관리, 채팅/보안 관리, 배너 관리).
   - 비관리자 접근 시 친절한 403 안내 화면 및 관리자 로그인 링크 표시.
2. **실시간 라이브톡 (`frontend/app/components/LiveChatSection.tsx`)**:
   - `CHAT_ANONYMOUS_ENABLED = false`: 기존대로 비로그인 클릭 시 로그인 유도.
   - `CHAT_ANONYMOUS_ENABLED = true`:
     - 비로그인 상태여도 입력창 활성화.
     - 발신 시 서버가 `ClientIpResolver`로 세션/IP를 감지하여 랜덤 닉네임(`눈사람#4812`) 부여.
   - **관리자 기능**:
     - 관리자 로그인 시 메시지 옵션(⋮) 노출.
     - `[이 사용자 IP 차단 및 최근 메시지 일괄 삭제]` 모달 -> 클릭 시 즉시 밴 및 화면 정화.

---

## 5. 아키텍처 규칙 및 컴플라이언스 준수 (AGENTS.md)

1. **DTO 불변성 (Rule 22)**:
   - 모든 목록 반환 및 DTO 생성 시 `List.copyOf()` 방어적 복사 적용.
2. **예외 처리 일원화 (Rule 23)**:
   - 문자열 리터럴 예외 금지. `ErrorCode` Enum (`ADMIN_ACCESS_DENIED`, `CHAT_IP_BANNED`, `CONFIG_NOT_FOUND` 등) 및 `CustomException` 일원화.
3. **계층 분리 (Rule 24)**:
   - Service 계층에서 서블릿 세션 조작 금지, Controller의 Repository 직접 호출 금지.
4. **개인정보보호**:
   - 일일 방문자 로그는 IP 원문을 저장하지 않고 일자별 Salt 기반 단방향 SHA-256 해시로 익명화.
