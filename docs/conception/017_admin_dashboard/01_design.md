# 관리자 시스템 및 대시보드 (2026-11-02)

## 1. 승인 범위

본 문서는 SnowThing 서비스의 통합 관리자 백오피스(`/admin`) 및 런타임 통제 시스템의 공식 설계 명세서이다.
비로그인 사용자 라이브톡 참여 제어, 채팅 악성 IP 차단 및 원클릭 정화, 회원 프로필/계정 제재, 사용자 삭제글 열람 및 불량글 강제 블라인드, 메인 배너 관리를 포함한다.

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

2. **`chat_banned_ip` (채팅 차단 IP 목록)**:
   - `id` BIGINT AUTO_INCREMENT PRIMARY KEY
   - `banned_ip` VARCHAR(45) NOT NULL: 차단 대상 IPv4 / IPv6
   - `reason` VARCHAR(255) NOT NULL: 차단 사유
   - `banned_until` DATETIME(6) NULL: 차단 만료 일시 (NULL이면 영구 차단)
   - `created_by` VARCHAR(50) NOT NULL: 차단 실행 관리자
   - `created_at` DATETIME(6) NOT NULL: 차단 등록 시각
   - INDEX `idx_chat_banned_ip_lookup` (`banned_ip`, `banned_until`)

3. **`banner` (메인 상단 배너 관리)**:
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
  - 반환: 누적 회원수, 오늘 가입자수, 비로그인 라이브톡 허용 여부.
- 방문자 수는 Cloudflare 통계를 쓰기로 했다.

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

---

## 6. 최신 main에 맞춘 변경 (IKA-68, 2026-11-09)

PR #48이 main보다 222커밋 뒤처진 사이 main에 신고 처리, 회원 정지, 답변 초안, API 토큰, 검색 인덱스 관리자 화면이 들어왔다. 겹치는 부분은 main 구현을 기준으로 남기고 #48의 새 기능만 붙였다. 위 2~4절에서 아래와 다른 내용은 이 절이 우선한다.

### 6.1 마이그레이션

- 027을 `047_migration_admin_operations.sql`로 옮겼다. main의 027은 검색 문서 테이블이다.
- `system_config`, `chat_banned_ip`, `banner`, `post.deleted_by_member_id`만 만든다. main에 같은 이름의 테이블이나 컬럼은 없다.
- `post.is_blinded`는 만들지 않는다 (6.2). `deleted_by_member_id` 인덱스도 뺐다. 이 컬럼으로 찾는 조회가 없다.

### 6.2 블라인드는 `post.status = 'BLOCKED'`

| 선택지 | 장점 | 단점 |
|---|---|---|
| #48의 `is_blinded` 컬럼 + `@SQLRestriction` 조건 | 기존 조회가 자동으로 거른다 | 검색 반영(outbox), 신고 처리, 사진 정리, 관리자 상세 조회가 블라인드 글을 엔티티로 못 읽는다. 컬럼 추가 마이그레이션 필요 |
| main의 `status` 재사용 (채택) | main 공개 조회가 이미 `status = NORMAL`만 보여 준다. 시장 글 관리자 조치가 같은 방식이다. 마이그레이션 불필요 | 신고 임시조치(TEMPORARY_HOLD)와 겹치지 않게 막아야 한다 |

- 블라인드: NORMAL에서 BLOCKED, 해제: BLOCKED에서 NORMAL. 이미 그 상태면 그대로 성공한다. 임시조치 등 다른 상태면 409(`CONTENT_REPORT_004`).
- 바꿀 때 글 행을 잠그고(`FOR UPDATE`), `contentVersion`을 올리고 검색 outbox에 알린다.

### 6.3 API 변경

- 회원 관리(3.3)는 main의 `GET /api/v1/admin/members`, `POST .../{publicId}/suspend`, `POST .../{publicId}/reinstate`를 쓴다. #48의 상태 변경, 닉네임 변경, 프로필 사진 삭제 API는 뺐다.
- `GET /api/v1/admin/posts?filter=ALL|DELETED|BLINDED&page=&size=`: 크기는 최대 50. 정렬은 글 ID 역순, DELETED는 삭제 시각 역순 다음 ID 역순.
  - 응답에 회원 내부 ID(`authorMemberId`, `deletedByMemberId`)를 내보내지 않는다. 작성자 삭제 여부는 `isDeletedByAuthor`로 준다.
- `DELETE /api/v1/admin/posts/{publicId}`: 일반 삭제 흐름(`PostService.deletePost`)을 그대로 탄다. 첨부 사진 정리와 검색 반영이 함께 된다. 지운 회원은 `deleted_by_member_id`에 남는다 (작성자 삭제도 같다).
- `PATCH /api/v1/admin/posts/{publicId}/restore`(복구)는 뺐다. main은 삭제 때 사진을 정리 대기열에 넣으므로 복구하면 사진이 깨진 글이 다시 보인다.

### 6.4 비회원 라이브톡

- 도배 제한은 main 규칙(30초 안 같은 문장, 1초 간격, 분당 20건)을 그대로 쓴다. 회원은 `member:{id}`, 비회원은 `ip:{ip}` 키로 센다. #48의 비회원 전용 캐시는 없앴다.
- 비회원 닉네임은 IP 해시로 정하므로 같은 IP는 같은 닉네임을 받는다. IP 원문은 내보내지 않는다.
- IP 차단은 회원에게도 적용된다. AI 회원 메시지는 IP가 없어 차단 버튼을 눌러도 그 메시지만 지워진다 (관리자에게만 보이는 버튼).

### 6.5 화면

- 관리자 레이아웃은 main의 `AdminGate` 탭을 쓰고 대시보드, 게시글, 라이브톡 차단, 배너 탭을 더했다. #48의 사이드바 레이아웃과 회원 화면은 뺐다.
- 게시글 화면은 main 관리자 화면 스타일(`admin.css`)로 다시 썼다. 대시보드, 라이브톡 차단, 배너 화면은 #48 그대로다.
