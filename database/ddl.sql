-- ====================================================================
-- Snowthing 데이터베이스 스키마 (MySQL 8.0)
--
-- 이 파일은 손으로 고치지 않는다. database/production 마이그레이션 체인을 적용한 스키마에서 뽑은 결과다.
-- 기준 마이그레이션: 060_migration_spam_filter (001_initial_schema 다음 003~060 적용)
--
-- 생성 방법
--   1. 빈 스키마에 001_initial_schema.sql을 넣는다. 002_reference_data.sql은 데이터만 있고 지금 체인에서는
--      005의 resort.code NOT NULL 변경과 부딪히므로 넣지 않는다(스키마에는 영향이 없다).
--      mysql 클라이언트 기본 문자셋을 utf8mb4로 둔다. 아니면 한글 COMMENT가 깨진다.
--   2. scripts/run-production-migrations.sh database/production 으로 나머지를 적용한다.
--   3. mysqldump --default-character-set=utf8mb4 --no-data --skip-comments --skip-dump-date --skip-set-charset
--      --compact --skip-add-drop-table --ignore-table=<스키마>.schema_migration <스키마>
--      결과에서 AUTO_INCREMENT=값과 character_set_client 저장/복원 줄을 지운다.
--
-- 쓰는 곳: docker-compose.yml 로컬 MySQL 첫 기동 초기화. 운영 스키마는 이 파일이 아니라 마이그레이션으로 바뀐다.
-- 기준 데이터(002의 리조트, 카테고리 등)와 schema_migration 표는 넣지 않는다.
-- 새 마이그레이션을 추가하면 같은 방법으로 다시 뽑고 기준 마이그레이션 번호를 고친다.
-- ====================================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

CREATE TABLE `admin_action_log` (
  `admin_action_log_id` bigint NOT NULL AUTO_INCREMENT,
  `actor_member_id` bigint DEFAULT NULL COMMENT '조치한 관리자 회원 ID (스케줄러 등 사람이 아닌 조치는 NULL)',
  `action` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '조치 종류 (MEMBER_SUSPEND, POST_BLIND 등)',
  `target_type` varchar(30) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '대상 종류 (MEMBER, POST, COMMENT 등)',
  `target_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '대상 식별자 (공개 ID 또는 번호)',
  `reason` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '사유나 바뀐 상태. 글 본문은 넣지 않는다',
  `created_at` datetime(6) NOT NULL,
  PRIMARY KEY (`admin_action_log_id`),
  KEY `idx_admin_action_log_action` (`action`,`admin_action_log_id`),
  KEY `idx_admin_action_log_target` (`target_type`,`target_id`,`admin_action_log_id`),
  KEY `idx_admin_action_log_actor` (`actor_member_id`,`admin_action_log_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='관리자 조치 기록';
CREATE TABLE `answer_draft` (
  `answer_draft_id` bigint NOT NULL AUTO_INCREMENT,
  `public_id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL,
  `target_post_id` bigint NOT NULL,
  `target_post_public_id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL,
  `body` varchar(1000) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '게시될 댓글 본문. 댓글 제한과 같은 1,000자',
  `status` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `idempotency_key` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '작성자별 유일, 대소문자 구분',
  `request_hash` char(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '같은 키로 다른 내용을 보냈는지 판별하는 요청 해시',
  `created_by_member_id` bigint NOT NULL,
  `created_by_token_id` bigint DEFAULT NULL COMMENT '토큰으로 저장했으면 api_token_id, 관리자 세션이면 NULL',
  `reviewed_by_member_id` bigint DEFAULT NULL,
  `reviewed_at` datetime(6) DEFAULT NULL,
  `approved_by_member_id` bigint DEFAULT NULL,
  `approved_at` datetime(6) DEFAULT NULL,
  `approved_post_content_version` bigint DEFAULT NULL COMMENT '승인 시점 post.content_version. 게시 직전 값과 다르면 게시 거절',
  `reject_reason` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `published_comment_id` bigint DEFAULT NULL,
  `published_by_member_id` bigint DEFAULT NULL,
  `published_at` datetime(6) DEFAULT NULL,
  `version` bigint NOT NULL DEFAULT '0' COMMENT '낙관적 잠금(@Version)',
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`answer_draft_id`),
  UNIQUE KEY `uk_answer_draft_public_id` (`public_id`),
  UNIQUE KEY `uk_answer_draft_creator_idempotency` (`created_by_member_id`,`idempotency_key`),
  UNIQUE KEY `uk_answer_draft_published_comment` (`published_comment_id`),
  KEY `idx_answer_draft_status_created` (`status`,`created_at`,`answer_draft_id`),
  KEY `idx_answer_draft_target_post` (`target_post_id`),
  KEY `fk_answer_draft_created_token` (`created_by_token_id`),
  KEY `fk_answer_draft_reviewed_by` (`reviewed_by_member_id`),
  KEY `fk_answer_draft_approved_by` (`approved_by_member_id`),
  KEY `fk_answer_draft_published_by` (`published_by_member_id`),
  CONSTRAINT `fk_answer_draft_approved_by` FOREIGN KEY (`approved_by_member_id`) REFERENCES `member` (`member_id`),
  CONSTRAINT `fk_answer_draft_created_by` FOREIGN KEY (`created_by_member_id`) REFERENCES `member` (`member_id`),
  CONSTRAINT `fk_answer_draft_created_token` FOREIGN KEY (`created_by_token_id`) REFERENCES `api_token` (`api_token_id`),
  CONSTRAINT `fk_answer_draft_published_by` FOREIGN KEY (`published_by_member_id`) REFERENCES `member` (`member_id`),
  CONSTRAINT `fk_answer_draft_published_comment` FOREIGN KEY (`published_comment_id`) REFERENCES `comment` (`comment_id`),
  CONSTRAINT `fk_answer_draft_reviewed_by` FOREIGN KEY (`reviewed_by_member_id`) REFERENCES `member` (`member_id`),
  CONSTRAINT `fk_answer_draft_target_post` FOREIGN KEY (`target_post_id`) REFERENCES `post` (`post_id`),
  CONSTRAINT `chk_answer_draft_published` CHECK (((`status` <> _utf8mb4'PUBLISHED') or (`published_comment_id` is not null))),
  CONSTRAINT `chk_answer_draft_status` CHECK ((`status` in (_utf8mb4'DRAFT',_utf8mb4'REVIEW_REQUIRED',_utf8mb4'APPROVED',_utf8mb4'PUBLISHED',_utf8mb4'REJECTED')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='답변 초안';
CREATE TABLE `answer_draft_audit` (
  `answer_draft_audit_id` bigint NOT NULL AUTO_INCREMENT,
  `answer_draft_id` bigint NOT NULL,
  `action` varchar(30) COLLATE utf8mb4_unicode_ci NOT NULL,
  `actor_member_id` bigint DEFAULT NULL,
  `actor_token_id` bigint DEFAULT NULL COMMENT '토큰으로 한 작업이면 api_token_id',
  `reason_code` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '게시 거절 사유 등 짧은 코드. 본문을 넣지 않는다',
  `created_at` datetime(6) NOT NULL,
  PRIMARY KEY (`answer_draft_audit_id`),
  KEY `idx_answer_draft_audit_draft_created` (`answer_draft_id`,`created_at`),
  KEY `fk_answer_draft_audit_actor` (`actor_member_id`),
  KEY `fk_answer_draft_audit_token` (`actor_token_id`),
  CONSTRAINT `fk_answer_draft_audit_actor` FOREIGN KEY (`actor_member_id`) REFERENCES `member` (`member_id`),
  CONSTRAINT `fk_answer_draft_audit_draft` FOREIGN KEY (`answer_draft_id`) REFERENCES `answer_draft` (`answer_draft_id`),
  CONSTRAINT `fk_answer_draft_audit_token` FOREIGN KEY (`actor_token_id`) REFERENCES `api_token` (`api_token_id`),
  CONSTRAINT `chk_answer_draft_audit_action` CHECK ((`action` in (_utf8mb4'SAVE',_utf8mb4'APPROVE',_utf8mb4'REJECT',_utf8mb4'PUBLISH',_utf8mb4'PUBLISH_REJECTED')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='답변 초안 감사 기록';
CREATE TABLE `answer_draft_evidence` (
  `answer_draft_evidence_id` bigint NOT NULL AUTO_INCREMENT,
  `answer_draft_id` bigint NOT NULL,
  `evidence_id` varchar(80) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'P:{post_id}:{chunk_no} 또는 C:{comment_id}:0',
  `chunk_hash` char(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '저장 시점에 확인한 구간 SHA-256 (hex)',
  `sort_order` int NOT NULL,
  PRIMARY KEY (`answer_draft_evidence_id`),
  UNIQUE KEY `uk_answer_draft_evidence` (`answer_draft_id`,`evidence_id`),
  CONSTRAINT `fk_answer_draft_evidence_draft` FOREIGN KEY (`answer_draft_id`) REFERENCES `answer_draft` (`answer_draft_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='답변 초안 근거';
CREATE TABLE `api_token` (
  `api_token_id` bigint NOT NULL AUTO_INCREMENT,
  `public_id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL,
  `member_id` bigint NOT NULL COMMENT '토큰 소유자. 인증할 때마다 활성 관리자인지 다시 확인한다',
  `name` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  `token_prefix` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '화면 표시용 앞부분(snt_ + 8자). 인증에는 쓰지 않는다',
  `token_hash` char(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '평문 토큰의 SHA-256 (hex). 평문은 저장하지 않는다',
  `scopes` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '쉼표 구분 READ, DRAFT',
  `expires_at` datetime(6) NOT NULL COMMENT '발급 후 최대 30일',
  `revoked_at` datetime(6) DEFAULT NULL,
  `last_used_at` datetime(6) DEFAULT NULL COMMENT '1분 간격으로만 갱신',
  `created_at` datetime(6) NOT NULL,
  PRIMARY KEY (`api_token_id`),
  UNIQUE KEY `uk_api_token_hash` (`token_hash`),
  UNIQUE KEY `uk_api_token_public_id` (`public_id`),
  KEY `idx_api_token_member_created` (`member_id`,`created_at`),
  CONSTRAINT `fk_api_token_member` FOREIGN KEY (`member_id`) REFERENCES `member` (`member_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='제한 API 토큰';
CREATE TABLE `banned_word` (
  `banned_word_id` bigint NOT NULL AUTO_INCREMENT,
  `word` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '관리자가 입력한 금칙어 표기',
  `normalized_word` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '비교 값 (HTML 제거, NFKC, 소문자, 글자와 숫자만)',
  `created_at` datetime(6) NOT NULL,
  PRIMARY KEY (`banned_word_id`),
  UNIQUE KEY `uk_banned_word_normalized` (`normalized_word`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='금칙어 (걸리면 글과 댓글을 조용히 보류)';
CREATE TABLE `banner` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `title` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `image_url` varchar(500) COLLATE utf8mb4_unicode_ci NOT NULL,
  `link_url` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `display_order` int NOT NULL DEFAULT '0',
  `is_active` tinyint(1) NOT NULL DEFAULT '1',
  `start_at` datetime(6) DEFAULT NULL,
  `end_at` datetime(6) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_banner_active_order` (`is_active`,`display_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE `carpool_detail` (
  `post_id` bigint NOT NULL,
  `departure_region` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `meeting_place` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL,
  `departure_latitude` decimal(10,7) DEFAULT NULL,
  `departure_longitude` decimal(10,7) DEFAULT NULL,
  `destination_resort_id` bigint NOT NULL,
  `destination_latitude` decimal(10,7) NOT NULL,
  `destination_longitude` decimal(10,7) NOT NULL,
  `trip_type` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `departure_at` datetime NOT NULL,
  `return_at` datetime DEFAULT NULL,
  `passenger_capacity` int NOT NULL,
  `fuel_type` varchar(30) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '연료 종류. 1인 금액 직접 입력이면 NULL',
  `fuel_efficiency` decimal(6,2) DEFAULT NULL COMMENT '차량 연비 km/L',
  `cost_mode` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'AUTO',
  `fuel_price` decimal(10,2) DEFAULT NULL COMMENT '계산에 사용한 유가',
  `fuel_price_source` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '유가 출처',
  `fuel_price_observed_at` datetime DEFAULT NULL COMMENT '유가 조회 시각',
  `route_distance_km` decimal(8,2) DEFAULT NULL COMMENT '최종 적용 거리',
  `route_toll_fee` int DEFAULT NULL COMMENT '최종 적용 통행료',
  `estimated_fuel_cost` int DEFAULT NULL COMMENT '예상 연료비',
  `estimated_total_cost` int DEFAULT NULL COMMENT '예상 총비용',
  `estimated_cost_per_person` int NOT NULL COMMENT '1인 금액. 자동 계산은 운전자 포함 1인 예상 비용',
  `route_source` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '경로 계산 출처',
  `route_calculated_at` datetime DEFAULT NULL,
  `contact_info` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `contact_public_to_guest` tinyint(1) NOT NULL DEFAULT '0',
  `equipment_load_available` tinyint(1) NOT NULL DEFAULT '0' COMMENT '장비 적재 가능 여부',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`post_id`),
  KEY `fk_carpool_detail_resort` (`destination_resort_id`),
  KEY `idx_carpool_detail_departure_created_post` (`departure_at`,`created_at` DESC,`post_id` DESC),
  KEY `idx_carpool_detail_created_post` (`created_at` DESC,`post_id` DESC),
  CONSTRAINT `fk_carpool_detail_post` FOREIGN KEY (`post_id`) REFERENCES `post` (`post_id`) ON DELETE CASCADE,
  CONSTRAINT `fk_carpool_detail_resort` FOREIGN KEY (`destination_resort_id`) REFERENCES `resort` (`resort_id`),
  CONSTRAINT `chk_carpool_auto_cost_snapshot` CHECK (((`cost_mode` <> _utf8mb4'AUTO') or ((`fuel_type` is not null) and (`fuel_efficiency` is not null) and (`fuel_price` is not null) and (`fuel_price_source` is not null) and (`fuel_price_observed_at` is not null) and (`route_distance_km` is not null) and (`route_toll_fee` is not null) and (`estimated_fuel_cost` is not null) and (`estimated_total_cost` is not null) and (`route_source` is not null)))),
  CONSTRAINT `chk_carpool_distance` CHECK ((`route_distance_km` > 0)),
  CONSTRAINT `chk_carpool_estimated_cost_per_person` CHECK ((`estimated_cost_per_person` >= 0)),
  CONSTRAINT `chk_carpool_estimated_fuel_cost` CHECK ((`estimated_fuel_cost` >= 0)),
  CONSTRAINT `chk_carpool_estimated_total_cost` CHECK ((`estimated_total_cost` >= 0)),
  CONSTRAINT `chk_carpool_fuel_efficiency` CHECK ((`fuel_efficiency` > 0)),
  CONSTRAINT `chk_carpool_fuel_price` CHECK ((`fuel_price` >= 0)),
  CONSTRAINT `chk_carpool_fuel_price_source` CHECK ((`fuel_price_source` in (_utf8mb4'OPINET',_utf8mb4'CACHE',_utf8mb4'USER_INPUT'))),
  CONSTRAINT `chk_carpool_fuel_type` CHECK ((`fuel_type` in (_utf8mb4'GASOLINE',_utf8mb4'DIESEL',_utf8mb4'LPG',_utf8mb4'HYBRID_GASOLINE'))),
  CONSTRAINT `chk_carpool_passenger_capacity` CHECK ((`passenger_capacity` > 0)),
  CONSTRAINT `chk_carpool_round_trip_return` CHECK ((((`trip_type` = _utf8mb4'ONE_WAY') and (`return_at` is null)) or ((`trip_type` = _utf8mb4'ROUND_TRIP') and (`return_at` > `departure_at`)))),
  CONSTRAINT `chk_carpool_route_source` CHECK ((`route_source` in (_utf8mb4'KAKAO',_utf8mb4'MANUAL'))),
  CONSTRAINT `chk_carpool_toll_fee` CHECK ((`route_toll_fee` >= 0)),
  CONSTRAINT `chk_carpool_trip_type` CHECK ((`trip_type` in (_utf8mb4'ONE_WAY',_utf8mb4'ROUND_TRIP')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='카풀 모집글 상세';
CREATE TABLE `carpool_review` (
  `carpool_review_id` bigint NOT NULL AUTO_INCREMENT,
  `post_id` bigint NOT NULL COMMENT '카풀 글',
  `reviewer_id` bigint NOT NULL COMMENT '작성자',
  `reviewee_id` bigint NOT NULL COMMENT '대상',
  `rating` varchar(10) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'GOOD, BAD',
  `comment` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '대상 본인에게만 보이는 짧은 글',
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`carpool_review_id`),
  UNIQUE KEY `uk_carpool_review_post_reviewer_reviewee` (`post_id`,`reviewer_id`,`reviewee_id`),
  KEY `idx_carpool_review_reviewee_rating` (`reviewee_id`,`rating`),
  KEY `idx_carpool_review_reviewer` (`reviewer_id`),
  CONSTRAINT `fk_carpool_review_post` FOREIGN KEY (`post_id`) REFERENCES `carpool_detail` (`post_id`),
  CONSTRAINT `fk_carpool_review_reviewee` FOREIGN KEY (`reviewee_id`) REFERENCES `member` (`member_id`),
  CONSTRAINT `fk_carpool_review_reviewer` FOREIGN KEY (`reviewer_id`) REFERENCES `member` (`member_id`),
  CONSTRAINT `chk_carpool_review_not_self` CHECK ((`reviewer_id` <> `reviewee_id`)),
  CONSTRAINT `chk_carpool_review_rating` CHECK ((`rating` in (_utf8mb4'GOOD',_utf8mb4'BAD')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='카풀 후기';
CREATE TABLE `carpool_review_tag` (
  `carpool_review_id` bigint NOT NULL,
  `tag` varchar(30) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'ON_TIME, KIND, FAIR_SETTLEMENT, SAFE_DRIVING, CLEAN_CAR',
  PRIMARY KEY (`carpool_review_id`,`tag`),
  CONSTRAINT `fk_carpool_review_tag_review` FOREIGN KEY (`carpool_review_id`) REFERENCES `carpool_review` (`carpool_review_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='카풀 후기의 좋았던 점';
CREATE TABLE `carpool_rider` (
  `carpool_rider_id` bigint NOT NULL AUTO_INCREMENT,
  `post_id` bigint NOT NULL COMMENT '카풀 글',
  `member_id` bigint NOT NULL COMMENT '탑승자',
  `status` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'REQUESTED, CONFIRMED, REJECTED',
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`carpool_rider_id`),
  UNIQUE KEY `uk_carpool_rider_post_member` (`post_id`,`member_id`),
  KEY `idx_carpool_rider_member_status` (`member_id`,`status`),
  CONSTRAINT `fk_carpool_rider_member` FOREIGN KEY (`member_id`) REFERENCES `member` (`member_id`),
  CONSTRAINT `fk_carpool_rider_post` FOREIGN KEY (`post_id`) REFERENCES `carpool_detail` (`post_id`),
  CONSTRAINT `chk_carpool_rider_status` CHECK ((`status` in (_utf8mb4'REQUESTED',_utf8mb4'CONFIRMED',_utf8mb4'REJECTED')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='카풀 탑승 확인';
CREATE TABLE `character_group` (
  `character_group_id` bigint NOT NULL AUTO_INCREMENT,
  `public_id` char(36) COLLATE utf8mb4_unicode_ci NOT NULL,
  `name` varchar(30) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '만든 사람 닉네임으로 자동 생성',
  `owner_participant_id` bigint DEFAULT NULL COMMENT '만든 사람. 만든 사람이 나가면 NULL',
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL COMMENT '마지막 새 참여 시각. 7일 보관 판단 기준',
  PRIMARY KEY (`character_group_id`),
  UNIQUE KEY `uk_character_group_public_id` (`public_id`),
  KEY `idx_character_group_updated_at` (`updated_at`),
  KEY `idx_character_group_owner` (`owner_participant_id`),
  CONSTRAINT `fk_character_group_owner` FOREIGN KEY (`owner_participant_id`) REFERENCES `character_participant` (`character_participant_id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='캐릭터 테스트 모임';
CREATE TABLE `character_participant` (
  `character_participant_id` bigint NOT NULL AUTO_INCREMENT,
  `public_id` char(36) COLLATE utf8mb4_unicode_ci NOT NULL,
  `character_group_id` bigint NOT NULL,
  `nickname` varchar(10) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '모임 안에서만 고유',
  `sport` varchar(10) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'SKI 또는 BOARD. 유형에는 영향 없음',
  `answers` char(15) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '유형 질문 12개는 A 또는 B, 습관 질문 3개는 A, B, C',
  `type_code` char(4) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '내부 유형 코드. API 응답에 내보내지 않는다',
  `access_token_hash` char(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '접근 토큰의 SHA-256',
  `created_at` datetime(6) NOT NULL,
  PRIMARY KEY (`character_participant_id`),
  UNIQUE KEY `uk_character_participant_public_id` (`public_id`),
  UNIQUE KEY `uk_character_participant_token` (`access_token_hash`),
  UNIQUE KEY `uk_character_participant_group_nickname` (`character_group_id`,`nickname`),
  KEY `idx_character_participant_group_id` (`character_group_id`,`character_participant_id`),
  CONSTRAINT `fk_character_participant_group` FOREIGN KEY (`character_group_id`) REFERENCES `character_group` (`character_group_id`),
  CONSTRAINT `chk_character_participant_sport` CHECK ((`sport` in (_utf8mb4'SKI',_utf8mb4'BOARD')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='캐릭터 테스트 참여자';
CREATE TABLE `chat_banned_ip` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `banned_ip` varchar(45) COLLATE utf8mb4_unicode_ci NOT NULL,
  `reason` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `banned_until` datetime(6) DEFAULT NULL,
  `created_by` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  `created_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_chat_banned_ip` (`banned_ip`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='IP 차단 목록 (라이브톡, 게시글, 댓글 작성 공통). banned_ip는 단일 IP 또는 CIDR 대역';
CREATE TABLE `comment` (
  `comment_id` bigint NOT NULL AUTO_INCREMENT COMMENT '댓글 식별자',
  `version` bigint NOT NULL DEFAULT '0' COMMENT '낙관적 락 동시성 제어용 버전',
  `post_id` bigint NOT NULL COMMENT '게시글 ID',
  `member_id` bigint DEFAULT NULL COMMENT '작성자 회원 ID (비회원 작성 시 NULL)',
  `parent_id` bigint DEFAULT NULL COMMENT '부모 댓글 ID (대댓글 구현용)',
  `content` varchar(1000) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '댓글 내용',
  `writer_ip` varchar(45) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '작성자 IP 주소',
  `is_anonymous` tinyint(1) NOT NULL DEFAULT '0' COMMENT '익명 작성 여부',
  `anonymous_password` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '비회원 익명 수정/삭제용 비밀번호 (BCrypt)',
  `is_deleted` tinyint(1) NOT NULL DEFAULT '0' COMMENT 'Soft Delete 여부',
  `deleted_at` datetime DEFAULT NULL COMMENT '삭제 일시',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '작성 일시',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정 일시',
  `moderation_held` tinyint(1) NOT NULL DEFAULT '0' COMMENT '임시조치로 가린 댓글이면 TRUE',
  `filter_state` varchar(10) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '금칙어 보류 상태 (NULL 보통, HELD 보류, REJECTED 보류 뒤 삭제)',
  PRIMARY KEY (`comment_id`),
  KEY `idx_comment_post_parent_id` (`post_id`,`parent_id`,`comment_id`),
  KEY `idx_comment_parent_deleted_id` (`parent_id`,`is_deleted`,`comment_id`),
  KEY `fk_comment_member` (`member_id`),
  KEY `idx_comment_created_at` (`created_at`),
  CONSTRAINT `fk_comment_member` FOREIGN KEY (`member_id`) REFERENCES `member` (`member_id`) ON DELETE SET NULL,
  CONSTRAINT `fk_comment_parent` FOREIGN KEY (`parent_id`) REFERENCES `comment` (`comment_id`) ON DELETE SET NULL,
  CONSTRAINT `fk_comment_post` FOREIGN KEY (`post_id`) REFERENCES `post` (`post_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='댓글 및 계층형 대댓글';
CREATE TABLE `content_filter_hold` (
  `content_filter_hold_id` bigint NOT NULL AUTO_INCREMENT,
  `target_type` varchar(10) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '보류 대상 (POST, COMMENT)',
  `target_id` bigint NOT NULL COMMENT '글 번호 또는 댓글 번호',
  `post_id` bigint NOT NULL COMMENT '글 번호 (댓글이면 댓글이 달린 글)',
  `member_id` bigint DEFAULT NULL COMMENT '작성 회원 (비회원이면 NULL). 탈퇴 회원 기록도 남기려고 FK를 두지 않는다',
  `writer_ip` varchar(45) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '작성자 IP',
  `matched_word` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '걸린 금칙어',
  `held_at` datetime(6) NOT NULL COMMENT '보류한 시각 (금칙어가 든 내용으로 고치면 다시 쓴다)',
  PRIMARY KEY (`content_filter_hold_id`),
  UNIQUE KEY `uk_content_filter_hold_target` (`target_type`,`target_id`),
  KEY `idx_content_filter_hold_list` (`held_at`,`content_filter_hold_id`),
  KEY `idx_content_filter_hold_member` (`member_id`),
  KEY `idx_content_filter_hold_ip` (`writer_ip`,`held_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='금칙어 보류 중인 글과 댓글';
CREATE TABLE `content_moderation_action` (
  `content_moderation_action_id` bigint NOT NULL AUTO_INCREMENT,
  `target_type` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `target_id` bigint NOT NULL,
  `action_type` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'TEMPORARY_HOLD, RESTORE, DELETE, DISMISS',
  `memo` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `admin_id` bigint DEFAULT NULL COMMENT '기간 만료 자동 복구는 NULL',
  `expires_at` datetime DEFAULT NULL COMMENT '임시조치 만료 시각',
  `released_at` datetime DEFAULT NULL COMMENT '임시조치 해제 시각',
  `created_at` datetime NOT NULL,
  PRIMARY KEY (`content_moderation_action_id`),
  KEY `idx_moderation_action_target` (`target_type`,`target_id`,`content_moderation_action_id`),
  KEY `idx_moderation_action_expiry` (`action_type`,`released_at`,`expires_at`),
  KEY `fk_moderation_action_admin` (`admin_id`),
  CONSTRAINT `fk_moderation_action_admin` FOREIGN KEY (`admin_id`) REFERENCES `member` (`member_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE `content_report` (
  `content_report_id` bigint NOT NULL AUTO_INCREMENT,
  `target_type` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'POST, COMMENT',
  `target_id` bigint NOT NULL COMMENT 'post_id 또는 comment_id. 삭제되어도 이력이 남도록 FK를 두지 않는다',
  `reporter_id` bigint DEFAULT NULL COMMENT '신고한 회원. 비회원 신고는 NULL',
  `reporter_ip` varchar(45) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '비회원 신고자의 IP. 회원 신고는 NULL',
  `reason` varchar(30) COLLATE utf8mb4_unicode_ci NOT NULL,
  `detail` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `status` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'PENDING, RESOLVED',
  `created_at` datetime NOT NULL,
  `resolved_at` datetime DEFAULT NULL,
  PRIMARY KEY (`content_report_id`),
  UNIQUE KEY `uk_content_report_target_reporter` (`target_type`,`target_id`,`reporter_id`),
  KEY `idx_content_report_status_id` (`status`,`content_report_id`),
  KEY `fk_content_report_reporter` (`reporter_id`),
  KEY `idx_content_report_target_ip` (`target_type`,`target_id`,`reporter_ip`),
  CONSTRAINT `fk_content_report_reporter` FOREIGN KEY (`reporter_id`) REFERENCES `member` (`member_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE `crew` (
  `crew_id` bigint NOT NULL AUTO_INCREMENT COMMENT '크루 고유 식별자',
  `public_id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '외부 노출용 UUID v7',
  `name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '크루 이름',
  `description` text COLLATE utf8mb4_unicode_ci COMMENT '크루 소개',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성 일시',
  PRIMARY KEY (`crew_id`),
  UNIQUE KEY `public_id` (`public_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='크루 마스터';
CREATE TABLE `direct_message` (
  `direct_message_id` bigint NOT NULL AUTO_INCREMENT,
  `sender_id` bigint NOT NULL,
  `receiver_id` bigint NOT NULL,
  `market_listing_id` bigint DEFAULT NULL COMMENT '판매글에서 보낸 경우의 판매글',
  `content` varchar(1000) COLLATE utf8mb4_unicode_ci NOT NULL,
  `read_at` datetime(6) DEFAULT NULL COMMENT 'NULL이면 안 읽음. 수신자가 처음 열 때 기록',
  `sender_deleted_at` datetime(6) DEFAULT NULL COMMENT '발신자 쪽 삭제',
  `receiver_deleted_at` datetime(6) DEFAULT NULL COMMENT '수신자 쪽 삭제',
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`direct_message_id`),
  KEY `idx_direct_message_receiver` (`receiver_id`,`receiver_deleted_at`,`direct_message_id`),
  KEY `idx_direct_message_sender` (`sender_id`,`sender_deleted_at`,`direct_message_id`),
  KEY `idx_direct_message_market_listing` (`market_listing_id`),
  CONSTRAINT `fk_direct_message_market_listing` FOREIGN KEY (`market_listing_id`) REFERENCES `market_listing` (`market_listing_id`),
  CONSTRAINT `fk_direct_message_receiver` FOREIGN KEY (`receiver_id`) REFERENCES `member` (`member_id`),
  CONSTRAINT `fk_direct_message_sender` FOREIGN KEY (`sender_id`) REFERENCES `member` (`member_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='회원 간 1:1 쪽지';
CREATE TABLE `dm_mail_outbox` (
  `event_id` bigint NOT NULL AUTO_INCREMENT,
  `direct_message_id` bigint NOT NULL COMMENT '알릴 쪽지',
  `recipient_member_id` bigint NOT NULL COMMENT '받는 회원',
  `event_type` varchar(30) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'DM_RECEIVED',
  `channel` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'EMAIL',
  `status` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `attempts` int NOT NULL,
  `due_at` datetime(6) NOT NULL COMMENT '이 시각부터 선점 대상',
  `lease_until` datetime(6) DEFAULT NULL COMMENT 'PROCESSING 소유 만료 시각',
  `claim_token` char(36) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '선점 토큰(UUID)',
  `provider_message_id` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'SES MessageId',
  `skip_reason` varchar(30) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `last_error_code` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`event_id`),
  UNIQUE KEY `uk_dm_mail_outbox_intent` (`direct_message_id`,`recipient_member_id`,`event_type`,`channel`),
  KEY `idx_dm_mail_outbox_due` (`status`,`due_at`,`event_id`),
  KEY `idx_dm_mail_outbox_recipient` (`recipient_member_id`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='쪽지 도착 메일 발송 요청';
CREATE TABLE `dm_mail_recipient_state` (
  `recipient_member_id` bigint NOT NULL,
  `last_attempt_at` datetime(6) DEFAULT NULL COMMENT '마지막 SES 호출 시작 시각',
  PRIMARY KEY (`recipient_member_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='쪽지 메일 받는 사람별 발송 상태';
CREATE TABLE `email_verification` (
  `verification_id` bigint NOT NULL AUTO_INCREMENT,
  `public_id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL,
  `email` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `purpose` varchar(30) COLLATE utf8mb4_unicode_ci NOT NULL,
  `code_digest` char(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `token_digest` char(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `status` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `failed_attempt_count` int NOT NULL DEFAULT '0',
  `send_count` int NOT NULL DEFAULT '1',
  `send_window_started_at` datetime NOT NULL,
  `request_ip` varchar(45) COLLATE utf8mb4_unicode_ci NOT NULL,
  `sent_at` datetime DEFAULT NULL,
  `expires_at` datetime NOT NULL,
  `verified_at` datetime DEFAULT NULL,
  `token_expires_at` datetime DEFAULT NULL,
  `consumed_at` datetime DEFAULT NULL,
  `ses_message_id` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`verification_id`),
  UNIQUE KEY `uk_email_verification_public_id` (`public_id`),
  UNIQUE KEY `uk_email_verification_email_purpose` (`email`,`purpose`),
  KEY `idx_email_verification_token_purpose` (`token_digest`,`purpose`),
  KEY `idx_email_verification_status_expires_at` (`status`,`expires_at`),
  KEY `idx_email_verification_expires_at` (`expires_at`),
  CONSTRAINT `chk_email_verification_failed_attempt_count` CHECK ((`failed_attempt_count` between 0 and 5)),
  CONSTRAINT `chk_email_verification_purpose` CHECK ((`purpose` in (_utf8mb4'SIGN_UP',_utf8mb4'PASSWORD_RESET'))),
  CONSTRAINT `chk_email_verification_send_count` CHECK ((`send_count` >= 0)),
  CONSTRAINT `chk_email_verification_status` CHECK ((`status` in (_utf8mb4'PENDING',_utf8mb4'SENT',_utf8mb4'VERIFIED',_utf8mb4'CONSUMED',_utf8mb4'SEND_FAILED',_utf8mb4'FAILED',_utf8mb4'EXPIRED')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='회원가입 및 비밀번호 재설정 이메일 인증 상태';
CREATE TABLE `idempotency_record` (
  `idempotency_record_id` bigint NOT NULL AUTO_INCREMENT,
  `author_key` varchar(80) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT 'member:{회원 ID} 또는 ip:{IP HMAC}',
  `target_type` varchar(30) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'POST, COMMENT, RESORT_REPORT, CARPOOL, MARKET_LISTING',
  `idempotency_key` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '소문자로 맞춘 UUID',
  `request_hash` char(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '같은 키로 다른 내용을 보냈는지 판별하는 요청 HMAC-SHA256',
  `response_body` mediumtext COLLATE utf8mb4_unicode_ci COMMENT '처음 201 응답 JSON. 같은 트랜잭션 끝에서 채우므로 커밋된 행에는 항상 있다',
  `created_at` datetime(6) NOT NULL COMMENT 'KST',
  PRIMARY KEY (`idempotency_record_id`),
  UNIQUE KEY `uk_idempotency_record_author_target_key` (`author_key`,`target_type`,`idempotency_key`),
  KEY `idx_idempotency_record_created` (`created_at`,`idempotency_record_id`),
  CONSTRAINT `chk_idempotency_record_target` CHECK ((`target_type` in (_utf8mb4'POST',_utf8mb4'COMMENT',_utf8mb4'RESORT_REPORT',_utf8mb4'CARPOOL',_utf8mb4'MARKET_LISTING')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='작성 API Idempotency-Key 기록';
CREATE TABLE `market_category` (
  `market_category_id` bigint NOT NULL AUTO_INCREMENT,
  `code` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  `name` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  `sort_order` int NOT NULL,
  `is_active` tinyint(1) NOT NULL DEFAULT '1',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`market_category_id`),
  UNIQUE KEY `uk_market_category_code` (`code`),
  KEY `idx_market_category_active_sort` (`is_active`,`sort_order`,`market_category_id`),
  CONSTRAINT `chk_market_category_sort_order` CHECK ((`sort_order` >= 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE `market_listing` (
  `market_listing_id` bigint NOT NULL AUTO_INCREMENT,
  `post_id` bigint NOT NULL,
  `market_category_id` bigint NOT NULL,
  `product_condition` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `transaction_method` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `trade_status` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'ON_SALE',
  `price` bigint NOT NULL,
  `is_negotiable` tinyint(1) NOT NULL DEFAULT '0',
  `is_free` tinyint(1) NOT NULL DEFAULT '0',
  `contact` varchar(30) COLLATE utf8mb4_unicode_ci NOT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`market_listing_id`),
  UNIQUE KEY `uk_market_listing_post` (`post_id`),
  KEY `idx_market_listing_trade_created` (`trade_status`,`created_at`,`market_listing_id`),
  KEY `idx_market_listing_category_trade_created` (`market_category_id`,`trade_status`,`created_at`,`market_listing_id`),
  KEY `idx_market_listing_condition_trade_created` (`product_condition`,`trade_status`,`created_at`,`market_listing_id`),
  CONSTRAINT `fk_market_listing_category` FOREIGN KEY (`market_category_id`) REFERENCES `market_category` (`market_category_id`),
  CONSTRAINT `fk_market_listing_post` FOREIGN KEY (`post_id`) REFERENCES `post` (`post_id`),
  CONSTRAINT `chk_market_listing_condition` CHECK ((`product_condition` in (_utf8mb4'NEW',_utf8mb4'LIKE_NEW',_utf8mb4'GOOD',_utf8mb4'USED',_utf8mb4'DAMAGED'))),
  CONSTRAINT `chk_market_listing_contact` CHECK ((char_length(trim(`contact`)) between 1 and 30)),
  CONSTRAINT `chk_market_listing_price_mode` CHECK ((((`is_free` = true) and (`price` = 0) and (`is_negotiable` = false)) or ((`is_free` = false) and (`price` between 1 and 20000000)))),
  CONSTRAINT `chk_market_listing_price_non_negative` CHECK ((`price` >= 0)),
  CONSTRAINT `chk_market_listing_trade_status` CHECK ((`trade_status` in (_utf8mb4'ON_SALE',_utf8mb4'RESERVED',_utf8mb4'SOLD'))),
  CONSTRAINT `chk_market_listing_transaction_method` CHECK ((`transaction_method` in (_utf8mb4'DIRECT',_utf8mb4'DELIVERY',_utf8mb4'BOTH')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE `market_thumbnail` (
  `post_public_id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL,
  `source_key` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `ready_source_key` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `revision` bigint NOT NULL DEFAULT '0',
  `attempts` int NOT NULL DEFAULT '0',
  `due_at` datetime(6) DEFAULT NULL,
  `lease_until` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`post_public_id`),
  KEY `idx_market_thumbnail_due` (`due_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE `member` (
  `member_id` bigint NOT NULL AUTO_INCREMENT COMMENT 'DB 내부 조인용 PK',
  `public_id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '외부 API/URL 노출용 UUID v7',
  `email` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '로그인 이메일 계정',
  `password` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'BCrypt 암호화 비밀번호 (OAuth2 대비 NULLABLE)',
  `nickname` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '활동 닉네임',
  `profile_image_url` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '프로필 이미지 URL',
  `bio` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '자기소개 한마디',
  `departure_region` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '주 출발/거주 지역',
  `email_verified_at` datetime DEFAULT NULL,
  `crew_id` bigint DEFAULT NULL COMMENT '소속 크루 ID',
  `crew_role` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '크루 내 권한 (OWNER, MANAGER, MEMBER)',
  `role` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'ROLE_USER' COMMENT '전역 권한 (ROLE_USER, ROLE_ADMIN)',
  `status` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'ACTIVE' COMMENT '계정 상태 (ACTIVE, SUSPENDED, WITHDRAWN)',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '가입 일시',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정 일시',
  PRIMARY KEY (`member_id`),
  UNIQUE KEY `public_id` (`public_id`),
  UNIQUE KEY `email` (`email`),
  UNIQUE KEY `nickname` (`nickname`),
  KEY `fk_member_crew` (`crew_id`),
  KEY `idx_member_profile_image_url` (`profile_image_url`),
  KEY `idx_member_created_at` (`created_at`),
  CONSTRAINT `fk_member_crew` FOREIGN KEY (`crew_id`) REFERENCES `crew` (`crew_id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='회원 마스터';
CREATE TABLE `member_block` (
  `member_block_id` bigint NOT NULL AUTO_INCREMENT,
  `blocker_id` bigint NOT NULL COMMENT '차단한 회원',
  `blocked_id` bigint NOT NULL COMMENT '차단당한 회원',
  `created_at` datetime(6) NOT NULL,
  PRIMARY KEY (`member_block_id`),
  UNIQUE KEY `uk_member_block_blocker_blocked` (`blocker_id`,`blocked_id`),
  KEY `idx_member_block_blocked_blocker` (`blocked_id`,`blocker_id`),
  CONSTRAINT `fk_member_block_blocked` FOREIGN KEY (`blocked_id`) REFERENCES `member` (`member_id`),
  CONSTRAINT `fk_member_block_blocker` FOREIGN KEY (`blocker_id`) REFERENCES `member` (`member_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='회원 차단';
CREATE TABLE `member_marketing_consent_history` (
  `member_marketing_consent_history_id` bigint NOT NULL AUTO_INCREMENT,
  `member_id` bigint NOT NULL,
  `agreed` tinyint(1) NOT NULL,
  `changed_at` datetime NOT NULL,
  PRIMARY KEY (`member_marketing_consent_history_id`),
  KEY `idx_marketing_consent_member_id` (`member_id`,`member_marketing_consent_history_id`),
  CONSTRAINT `fk_marketing_consent_member` FOREIGN KEY (`member_id`) REFERENCES `member` (`member_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE `member_notification_setting` (
  `member_id` bigint NOT NULL,
  `dm_email_enabled` tinyint(1) NOT NULL COMMENT '쪽지 도착 메일 알림',
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`member_id`),
  CONSTRAINT `fk_member_notification_setting_member` FOREIGN KEY (`member_id`) REFERENCES `member` (`member_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='회원 알림 설정';
CREATE TABLE `member_registration_consent` (
  `member_id` bigint NOT NULL,
  `privacy_version` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  `terms_version` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '동의한 이용약관 버전. 약관 도입 전 가입자는 NULL',
  `age_confirmed` tinyint(1) NOT NULL,
  `marketing_email_agreed` tinyint(1) NOT NULL,
  `confirmed_at` datetime NOT NULL,
  PRIMARY KEY (`member_id`),
  CONSTRAINT `fk_registration_consent_member` FOREIGN KEY (`member_id`) REFERENCES `member` (`member_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE `member_resort` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '단일 대리키 PK',
  `member_id` bigint NOT NULL COMMENT '회원 ID',
  `resort_id` bigint NOT NULL COMMENT '리조트 ID',
  `is_main` tinyint(1) NOT NULL DEFAULT '0' COMMENT '주 베이스 여부',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_member_resort` (`member_id`,`resort_id`),
  KEY `fk_mr_resort` (`resort_id`),
  CONSTRAINT `fk_mr_member` FOREIGN KEY (`member_id`) REFERENCES `member` (`member_id`) ON DELETE CASCADE,
  CONSTRAINT `fk_mr_resort` FOREIGN KEY (`resort_id`) REFERENCES `resort` (`resort_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='회원-리조트 N:M 중계';
CREATE TABLE `member_riding_style` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '단일 대리키 PK',
  `member_id` bigint NOT NULL COMMENT '회원 ID',
  `style_id` bigint NOT NULL COMMENT '라이딩 스타일 ID',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_member_riding_style` (`member_id`,`style_id`),
  KEY `fk_mrs_style` (`style_id`),
  CONSTRAINT `fk_mrs_member` FOREIGN KEY (`member_id`) REFERENCES `member` (`member_id`) ON DELETE CASCADE,
  CONSTRAINT `fk_mrs_style` FOREIGN KEY (`style_id`) REFERENCES `riding_style` (`style_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='회원-라이딩스타일 N:M 중계';
CREATE TABLE `member_social_account` (
  `social_account_id` bigint NOT NULL AUTO_INCREMENT COMMENT '소셜 계정 연동 PK',
  `member_id` bigint NOT NULL COMMENT '연결된 회원 ID (member.member_id)',
  `provider` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '소셜 제공자 (GOOGLE, NAVER, KAKAO)',
  `provider_user_id` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '소셜 제공자 측 고유 회원 식별자',
  `provider_email` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '소셜 제공자 측 인증 이메일 스냅샷',
  `connected_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '소셜 연동 일시',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성 일시',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정 일시',
  PRIMARY KEY (`social_account_id`),
  UNIQUE KEY `uk_member_social_provider_user` (`provider`,`provider_user_id`),
  UNIQUE KEY `uk_member_social_member_provider` (`member_id`,`provider`),
  CONSTRAINT `fk_member_social_member` FOREIGN KEY (`member_id`) REFERENCES `member` (`member_id`) ON DELETE CASCADE,
  CONSTRAINT `chk_member_social_provider` CHECK ((`provider` in (_utf8mb4'GOOGLE',_utf8mb4'NAVER',_utf8mb4'KAKAO')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='회원 소셜 계정 연동';
CREATE TABLE `notification` (
  `notification_id` bigint NOT NULL AUTO_INCREMENT,
  `receiver_id` bigint NOT NULL COMMENT '알림을 받는 회원',
  `type` varchar(30) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'POST_COMMENT, COMMENT_REPLY, INQUIRY_ANSWER',
  `post_id` bigint NOT NULL COMMENT '링크용 스냅샷. FK 없음',
  `post_public_id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '화면 이동용 글 공개 ID',
  `post_category_code` varchar(30) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '화면 이동 경로 선택용. MARKET이면 /market 상세',
  `comment_id` bigint DEFAULT NULL COMMENT '알림을 만든 댓글. 게시글 조치 알림은 NULL. FK 없음',
  `actor_name` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '만든 시점의 댓글 작성자 이름. 익명 댓글이면 익명',
  `preview` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '댓글 본문 앞 50자',
  `is_read` tinyint(1) NOT NULL DEFAULT '0',
  `created_at` datetime(6) NOT NULL,
  PRIMARY KEY (`notification_id`),
  KEY `idx_notification_receiver_read_id` (`receiver_id`,`is_read`,`notification_id`),
  CONSTRAINT `fk_notification_receiver` FOREIGN KEY (`receiver_id`) REFERENCES `member` (`member_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='사용자 알림';
CREATE TABLE `persistent_logins` (
  `series` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '기기별 토큰 묶음 식별자 (쿠키에 담김)',
  `member_public_id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '회원 공개 ID',
  `token_hash` char(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '현재 token의 SHA-256 해시',
  `previous_token_hash` char(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '바로 전 token의 SHA-256 해시',
  `last_used_at` datetime(6) NOT NULL COMMENT '발급 또는 마지막 자동 로그인 시각 (KST)',
  `created_at` datetime(6) NOT NULL COMMENT '발급 시각 (KST)',
  PRIMARY KEY (`series`),
  KEY `idx_persistent_logins_member` (`member_public_id`),
  KEY `idx_persistent_logins_last_used` (`last_used_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='로그인 상태 유지 토큰';
CREATE TABLE `post` (
  `post_id` bigint NOT NULL AUTO_INCREMENT COMMENT '게시글 고유 식별자',
  `public_id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '외부 API/URL 노출용 UUID v7',
  `member_id` bigint DEFAULT NULL COMMENT '작성자 회원 ID (비회원 작성 시 NULL)',
  `category_id` bigint NOT NULL COMMENT '카테고리 ID',
  `title` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '게시글 제목',
  `content` longtext COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '게시글 본문',
  `writer_ip` varchar(45) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '작성자 IP 주소',
  `is_anonymous` tinyint(1) NOT NULL DEFAULT '0' COMMENT '익명 작성 여부',
  `anonymous_password` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '비회원 익명 수정/삭제용 비밀번호 (BCrypt)',
  `view_count` int NOT NULL DEFAULT '0' COMMENT '조회수',
  `comment_count` int NOT NULL DEFAULT '0' COMMENT '댓글 수 역정규화',
  `like_count` int NOT NULL DEFAULT '0' COMMENT '추천 수 역정규화',
  `dislike_count` int NOT NULL DEFAULT '0' COMMENT '비추천 수 역정규화',
  `has_image` tinyint(1) NOT NULL DEFAULT '0' COMMENT '이미지 첨부 여부 역정규화 (목록 뱃지 아이콘 렌더링용)',
  `status` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'NORMAL' COMMENT '게시글 상태 (NORMAL, DELETED, BLOCKED, HIDDEN, DRAFT)',
  `is_deleted` tinyint(1) NOT NULL DEFAULT '0' COMMENT 'Soft Delete 여부',
  `deleted_at` datetime DEFAULT NULL COMMENT '삭제 일시',
  `deleted_by_member_id` bigint DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '작성 일시',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정 일시',
  `content_version` bigint NOT NULL DEFAULT '0',
  PRIMARY KEY (`post_id`),
  UNIQUE KEY `public_id` (`public_id`),
  KEY `fk_post_member` (`member_id`),
  KEY `idx_post_category_list` (`category_id`,`is_deleted`,`status`,`created_at` DESC,`post_id` DESC),
  KEY `idx_post_latest_list` (`is_deleted`,`status`,`created_at` DESC,`post_id` DESC),
  KEY `idx_post_created_at` (`created_at`),
  CONSTRAINT `fk_post_category` FOREIGN KEY (`category_id`) REFERENCES `post_category` (`category_id`),
  CONSTRAINT `fk_post_member` FOREIGN KEY (`member_id`) REFERENCES `member` (`member_id`) ON DELETE SET NULL,
  CONSTRAINT `chk_post_dislike_count_non_negative` CHECK ((`dislike_count` >= 0)),
  CONSTRAINT `chk_post_like_count_non_negative` CHECK ((`like_count` >= 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='게시글';
CREATE TABLE `post_category` (
  `category_id` bigint NOT NULL AUTO_INCREMENT COMMENT '카테고리 식별자',
  `name` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '카테고리명',
  `code` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '카테고리 고유 코드',
  PRIMARY KEY (`category_id`),
  UNIQUE KEY `code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='게시판 카테고리';
CREATE TABLE `post_content_image_reference` (
  `post_id` bigint NOT NULL,
  `image_key` varchar(160) COLLATE utf8mb4_unicode_ci NOT NULL,
  UNIQUE KEY `uk_post_content_image` (`post_id`,`image_key`),
  KEY `idx_content_image_key` (`image_key`),
  CONSTRAINT `fk_content_image_post` FOREIGN KEY (`post_id`) REFERENCES `post` (`post_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE `post_image` (
  `image_id` bigint NOT NULL AUTO_INCREMENT COMMENT '이미지 고유 식별자',
  `post_id` bigint NOT NULL COMMENT '게시글 ID',
  `image_url` varchar(500) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '이미지 파일 URL',
  `sort_order` int NOT NULL DEFAULT '1' COMMENT '표시 순서',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '등록 일시',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정 일시',
  PRIMARY KEY (`image_id`),
  KEY `fk_pi_post` (`post_id`),
  KEY `idx_post_image_url` (`image_url`),
  CONSTRAINT `fk_pi_post` FOREIGN KEY (`post_id`) REFERENCES `post` (`post_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='게시글 첨부 이미지';
CREATE TABLE `post_image_upload` (
  `image_key` varchar(160) COLLATE utf8mb4_unicode_ci NOT NULL,
  `owner_email` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `state` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `due_at` datetime(6) NOT NULL,
  PRIMARY KEY (`image_key`),
  KEY `idx_image_upload_due` (`due_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE `post_reaction` (
  `reaction_id` bigint NOT NULL AUTO_INCREMENT COMMENT '반응 식별자',
  `post_id` bigint NOT NULL COMMENT '게시글 ID',
  `member_id` bigint DEFAULT NULL COMMENT '회원 ID (비회원 투표 시 NULL)',
  `writer_ip` varchar(45) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '투표자 IP 주소',
  `anonymous_voter_id` varchar(36) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '비회원 익명 투표자 식별 쿠키 ID',
  `type` varchar(10) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '반응 종류 (LIKE, DISLIKE)',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '투표 일시',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정 일시',
  PRIMARY KEY (`reaction_id`),
  UNIQUE KEY `uk_post_member_type` (`post_id`,`member_id`,`type`),
  UNIQUE KEY `uk_post_anon_voter_type` (`post_id`,`anonymous_voter_id`,`type`),
  KEY `fk_pr_member` (`member_id`),
  CONSTRAINT `fk_pr_member` FOREIGN KEY (`member_id`) REFERENCES `member` (`member_id`) ON DELETE CASCADE,
  CONSTRAINT `fk_pr_post` FOREIGN KEY (`post_id`) REFERENCES `post` (`post_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='게시글 추천/비추천';
CREATE TABLE `post_scrap` (
  `post_scrap_id` bigint NOT NULL AUTO_INCREMENT,
  `member_id` bigint NOT NULL COMMENT '스크랩한 회원',
  `post_id` bigint NOT NULL COMMENT '스크랩한 글',
  `created_at` datetime(6) NOT NULL COMMENT '스크랩한 시각(KST)',
  PRIMARY KEY (`post_scrap_id`),
  UNIQUE KEY `uk_post_scrap_member_post` (`member_id`,`post_id`),
  KEY `idx_post_scrap_member_latest` (`member_id`,`created_at` DESC,`post_scrap_id` DESC),
  KEY `idx_post_scrap_post` (`post_id`),
  CONSTRAINT `fk_post_scrap_member` FOREIGN KEY (`member_id`) REFERENCES `member` (`member_id`),
  CONSTRAINT `fk_post_scrap_post` FOREIGN KEY (`post_id`) REFERENCES `post` (`post_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='글 스크랩';
CREATE TABLE `profile_image_cleanup` (
  `image_key` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `image_url` varchar(500) COLLATE utf8mb4_unicode_ci NOT NULL,
  `not_before` datetime(6) NOT NULL,
  `attempts` int NOT NULL DEFAULT '0',
  PRIMARY KEY (`image_key`),
  KEY `idx_profile_image_cleanup_due` (`not_before`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE `push_subscription` (
  `push_subscription_id` bigint NOT NULL AUTO_INCREMENT,
  `member_id` bigint NOT NULL COMMENT '구독한 회원',
  `endpoint` varchar(1024) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '푸시 서비스 주소',
  `endpoint_hash` char(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'endpoint의 SHA-256 16진수',
  `p256dh` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '브라우저 공개키(base64url)',
  `auth` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '브라우저 인증 비밀값(base64url)',
  `created_at` datetime(6) NOT NULL,
  `last_used_at` datetime(6) NOT NULL COMMENT '마지막 등록 갱신 또는 발송 성공 시각',
  PRIMARY KEY (`push_subscription_id`),
  UNIQUE KEY `uk_push_subscription_endpoint_hash` (`endpoint_hash`),
  KEY `idx_push_subscription_member` (`member_id`,`last_used_at`),
  CONSTRAINT `fk_push_subscription_member` FOREIGN KEY (`member_id`) REFERENCES `member` (`member_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='웹 푸시 구독';
CREATE TABLE `request_budget_window` (
  `budget_key` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `used_count` int NOT NULL DEFAULT '0',
  `expires_at_millis` bigint NOT NULL,
  PRIMARY KEY (`budget_key`),
  KEY `idx_request_budget_expiry` (`expires_at_millis`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE `resort` (
  `resort_id` bigint NOT NULL AUTO_INCREMENT COMMENT '리조트 고유 식별자',
  `code` varchar(30) COLLATE utf8mb4_unicode_ci NOT NULL,
  `name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '스키장 이름',
  `region` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '소재 지역',
  `display_order` int NOT NULL DEFAULT '0',
  `is_active` tinyint(1) NOT NULL DEFAULT '1',
  `country` varchar(2) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'KR' COMMENT '리조트가 있는 나라 (KR, JP). 커뮤니티 기능은 KR만 쓴다',
  `route_latitude` decimal(10,7) DEFAULT NULL,
  `route_longitude` decimal(10,7) DEFAULT NULL,
  PRIMARY KEY (`resort_id`),
  UNIQUE KEY `name` (`name`),
  UNIQUE KEY `uk_resort_code` (`code`),
  KEY `idx_resort_active_order` (`is_active`,`display_order`,`resort_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='리조트 마스터';
CREATE TABLE `resort_camera` (
  `resort_camera_id` bigint NOT NULL AUTO_INCREMENT,
  `resort_id` bigint NOT NULL,
  `code` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  `name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `source_type` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `source_url` varchar(1000) COLLATE utf8mb4_unicode_ci NOT NULL,
  `external_page_url` varchar(1000) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `display_order` int NOT NULL,
  `is_active` tinyint(1) NOT NULL DEFAULT '1',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`resort_camera_id`),
  UNIQUE KEY `uk_resort_camera_resort_code` (`resort_id`,`code`),
  KEY `idx_resort_camera_active_order` (`resort_id`,`is_active`,`display_order`,`resort_camera_id`),
  CONSTRAINT `fk_resort_camera_resort` FOREIGN KEY (`resort_id`) REFERENCES `resort` (`resort_id`),
  CONSTRAINT `chk_resort_camera_display_order` CHECK ((`display_order` >= 0)),
  CONSTRAINT `chk_resort_camera_source_type` CHECK ((`source_type` in (_utf8mb4'HLS',_utf8mb4'YOUTUBE',_utf8mb4'IFRAME',_utf8mb4'EXTERNAL_LINK')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE `resort_report` (
  `report_id` bigint NOT NULL AUTO_INCREMENT,
  `resort_id` bigint NOT NULL,
  `member_id` bigint NOT NULL,
  `content` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `status` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'NORMAL',
  `deleted_at` datetime(6) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`report_id`),
  KEY `idx_resort_report_status_created` (`status`,`created_at` DESC,`report_id` DESC),
  KEY `idx_resort_report_resort_status_created` (`resort_id`,`status`,`created_at` DESC,`report_id` DESC),
  KEY `idx_resort_report_member_created` (`member_id`,`created_at` DESC),
  CONSTRAINT `fk_resort_report_member` FOREIGN KEY (`member_id`) REFERENCES `member` (`member_id`) ON DELETE CASCADE,
  CONSTRAINT `fk_resort_report_resort` FOREIGN KEY (`resort_id`) REFERENCES `resort` (`resort_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE `resort_slope_status` (
  `resort_slope_status_id` bigint NOT NULL AUTO_INCREMENT,
  `resort_id` bigint NOT NULL,
  `source_url` varchar(500) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '사용자에게 보여 줄 공식 현황 페이지 주소',
  `slopes_json` mediumtext COLLATE utf8mb4_unicode_ci COMMENT '마지막 성공 슬로프 목록 JSON. 한 번도 성공하지 못했으면 NULL',
  `open_slope_count` int DEFAULT NULL COMMENT '마지막 성공 기준 한 시간대라도 연 슬로프 수',
  `total_slope_count` int DEFAULT NULL COMMENT '마지막 성공 기준 전체 슬로프 수',
  `operating_note` varchar(300) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '출처가 적은 운영 시간 문구',
  `collected_at` datetime(6) DEFAULT NULL COMMENT '마지막 성공 시각 (KST)',
  `last_attempt_at` datetime(6) NOT NULL COMMENT '마지막 시도 시각 (KST)',
  `last_result` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'SUCCESS, FAILED',
  `failure_reason` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '마지막 실패 사유. 운영 확인용',
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`resort_slope_status_id`),
  UNIQUE KEY `uk_resort_slope_status_resort` (`resort_id`),
  CONSTRAINT `fk_resort_slope_status_resort` FOREIGN KEY (`resort_id`) REFERENCES `resort` (`resort_id`),
  CONSTRAINT `chk_resort_slope_status_result` CHECK ((`last_result` in (_utf8mb4'SUCCESS',_utf8mb4'FAILED')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='리조트별 슬로프 운영 현황 최신 스냅샷';
CREATE TABLE `riding_style` (
  `style_id` bigint NOT NULL AUTO_INCREMENT COMMENT '스타일 고유 식별자',
  `name` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '라이딩 스타일명',
  `description` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '스타일 상세 설명',
  PRIMARY KEY (`style_id`),
  UNIQUE KEY `name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='라이딩 스타일 마스터';
CREATE TABLE `search_document` (
  `document_id` bigint NOT NULL AUTO_INCREMENT,
  `evidence_id` varchar(80) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'P:{post_id}:{chunk_no} 또는 C:{comment_id}:0',
  `source_type` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'POST, COMMENT',
  `source_id` bigint NOT NULL COMMENT 'post_id 또는 comment_id',
  `chunk_no` int NOT NULL,
  `post_id` bigint NOT NULL,
  `post_public_id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL,
  `parent_comment_id` bigint DEFAULT NULL,
  `category_code` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '인덱싱 시점 카테고리 (조회 필터는 현재 카테고리 기준)',
  `source_version` bigint NOT NULL COMMENT 'post.content_version 또는 comment.version',
  `chunk_hash` char(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '정규화한 구간 텍스트의 SHA-256 (hex)',
  `title` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '게시글 0번 구간에만 제목을 둔다',
  `body` text COLLATE utf8mb4_unicode_ci NOT NULL,
  `source_created_at` datetime(6) NOT NULL,
  `source_updated_at` datetime(6) NOT NULL,
  `indexed_at` datetime(6) NOT NULL,
  PRIMARY KEY (`document_id`),
  UNIQUE KEY `uk_search_document_evidence` (`evidence_id`),
  KEY `idx_search_document_post` (`post_id`),
  KEY `idx_search_document_source` (`source_type`,`source_id`),
  FULLTEXT KEY `ftx_search_document_title_body` (`title`,`body`) /*!50100 WITH PARSER `ngram` */ ,
  CONSTRAINT `chk_search_document_source_type` CHECK ((`source_type` in (_utf8mb4'POST',_utf8mb4'COMMENT')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='근거 검색용 문서 (재구축 가능한 복제본)';
CREATE TABLE `search_embedding` (
  `embedding_id` bigint NOT NULL AUTO_INCREMENT,
  `evidence_id` varchar(80) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'search_document.evidence_id',
  `model_id` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '임베딩 제공자와 모델 식별자. 모델을 바꾸면 새 행이 생긴다',
  `post_id` bigint NOT NULL COMMENT '게시글 또는 댓글의 부모 게시글. 원문 삭제 시 벡터를 지우는 데 쓴다',
  `source_type` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'POST, COMMENT',
  `source_id` bigint NOT NULL COMMENT 'post_id 또는 comment_id',
  `content_hash` char(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '벡터를 만든 구간의 chunk_hash',
  `dimension` int NOT NULL,
  `vector` blob NOT NULL COMMENT 'float32 little-endian 배열 (dimension x 4 바이트)',
  `created_at` datetime(6) NOT NULL,
  PRIMARY KEY (`embedding_id`),
  UNIQUE KEY `uk_search_embedding_evidence_model` (`evidence_id`,`model_id`),
  KEY `idx_search_embedding_post` (`post_id`),
  KEY `idx_search_embedding_source` (`source_type`,`source_id`),
  CONSTRAINT `chk_search_embedding_dimension` CHECK ((`dimension` between 1 and 4096)),
  CONSTRAINT `chk_search_embedding_source_type` CHECK ((`source_type` in (_utf8mb4'POST',_utf8mb4'COMMENT'))),
  CONSTRAINT `chk_search_embedding_vector_length` CHECK ((length(`vector`) = (`dimension` * 4)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='근거 검색 의미 검색 실험용 구간 벡터';
CREATE TABLE `search_index_state` (
  `source_type` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `source_id` bigint NOT NULL,
  `applied_version` bigint NOT NULL COMMENT '마지막으로 색인에 반영한 원문 버전. -1은 아직 반영 전',
  `tombstoned` tinyint(1) NOT NULL DEFAULT '0' COMMENT 'true면 원문이 없거나 볼 수 없어 문서를 지운 상태',
  `document_count` int NOT NULL DEFAULT '0' COMMENT '마지막 반영 때 넣은 이 원문의 문서(구간) 수. 대조에서 빠진 문서를 찾는 데 쓴다',
  `tombstoned_at` datetime(6) DEFAULT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`source_type`,`source_id`),
  CONSTRAINT `chk_search_index_state_source_type` CHECK ((`source_type` in (_utf8mb4'POST',_utf8mb4'COMMENT')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='근거 검색 원문별 적용 버전과 tombstone';
CREATE TABLE `search_outbox` (
  `event_id` bigint NOT NULL AUTO_INCREMENT,
  `source_type` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'POST, COMMENT',
  `source_id` bigint NOT NULL COMMENT 'post_id 또는 comment_id',
  `operation` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'UPSERT, DELETE (기록용. 처리기는 현재 원문 상태로 판단한다)',
  `source_version` bigint NOT NULL COMMENT '이벤트를 쓴 시점의 post.content_version 또는 comment.version',
  `status` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'PENDING, DONE, FAILED',
  `attempts` int NOT NULL DEFAULT '0',
  `created_at` datetime(6) NOT NULL,
  `due_at` datetime(6) NOT NULL,
  `lease_until` datetime(6) DEFAULT NULL,
  `processed_at` datetime(6) DEFAULT NULL,
  `last_error_code` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '예외 종류 등 짧은 코드. 원문 내용을 넣지 않는다',
  PRIMARY KEY (`event_id`),
  KEY `idx_search_outbox_status_due` (`status`,`due_at`),
  KEY `idx_search_outbox_source` (`source_type`,`source_id`),
  CONSTRAINT `chk_search_outbox_operation` CHECK ((`operation` in (_utf8mb4'UPSERT',_utf8mb4'DELETE'))),
  CONSTRAINT `chk_search_outbox_source_type` CHECK ((`source_type` in (_utf8mb4'POST',_utf8mb4'COMMENT'))),
  CONSTRAINT `chk_search_outbox_status` CHECK ((`status` in (_utf8mb4'PENDING',_utf8mb4'DONE',_utf8mb4'FAILED')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='근거 검색 색인 outbox';
CREATE TABLE `search_reindex_checkpoint` (
  `job_name` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL,
  `status` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'RUNNING, DONE',
  `last_source_id` bigint NOT NULL DEFAULT '0' COMMENT '마지막으로 outbox에 넣은 post_id',
  `enqueued_count` bigint NOT NULL DEFAULT '0',
  `started_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `finished_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`job_name`),
  CONSTRAINT `chk_search_reindex_status` CHECK ((`status` in (_utf8mb4'RUNNING',_utf8mb4'DONE')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='근거 검색 재인덱싱 진행 위치';
CREATE TABLE `season_opening` (
  `season_opening_id` bigint NOT NULL AUTO_INCREMENT,
  `resort_code` varchar(30) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '리조트 코드 (resort.code, 슬로프캠 코드와 같다)',
  `resort_name` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '개장 배너에 보이는 짧은 이름',
  `opening_date` date DEFAULT NULL COMMENT '개장일 (KST 달력 날짜). NULL이면 D-DAY를 보이지 않는다',
  `confirmed` tinyint(1) NOT NULL DEFAULT '0' COMMENT '리조트가 공식 발표한 날짜인지. 아니면 화면에 예상으로 보인다',
  `show_on_banner` tinyint(1) NOT NULL DEFAULT '0' COMMENT '메인 개장 배너에 넣을지',
  `banner_order` int NOT NULL COMMENT '메인 개장 배너 순서 (작을수록 앞)',
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`season_opening_id`),
  UNIQUE KEY `uk_season_opening_resort_code` (`resort_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='리조트 시즌 개장일';
CREATE TABLE `season_share` (
  `season_share_id` bigint NOT NULL AUTO_INCREMENT,
  `member_id` bigint NOT NULL,
  `season_start_year` int NOT NULL COMMENT '2026이면 2026-27 시즌',
  `share_token` char(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '공유 링크용 무작위 UUID',
  `created_at` datetime(6) NOT NULL,
  PRIMARY KEY (`season_share_id`),
  UNIQUE KEY `uk_season_share_member_season` (`member_id`,`season_start_year`),
  UNIQUE KEY `uk_season_share_token` (`share_token`),
  CONSTRAINT `fk_season_share_member` FOREIGN KEY (`member_id`) REFERENCES `member` (`member_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='시즌 결산 공유';
CREATE TABLE `season_visit` (
  `season_visit_id` bigint NOT NULL AUTO_INCREMENT,
  `member_id` bigint NOT NULL,
  `resort_id` bigint NOT NULL,
  `visit_date` date NOT NULL COMMENT 'KST 날짜',
  `created_at` datetime(6) NOT NULL,
  PRIMARY KEY (`season_visit_id`),
  UNIQUE KEY `uk_season_visit_member_date` (`member_id`,`visit_date`),
  KEY `idx_season_visit_resort` (`resort_id`),
  CONSTRAINT `fk_season_visit_member` FOREIGN KEY (`member_id`) REFERENCES `member` (`member_id`) ON DELETE CASCADE,
  CONSTRAINT `fk_season_visit_resort` FOREIGN KEY (`resort_id`) REFERENCES `resort` (`resort_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='시즌 방문 기록';
CREATE TABLE `slope` (
  `slope_id` bigint NOT NULL AUTO_INCREMENT,
  `resort_id` bigint NOT NULL COMMENT '리조트 ID',
  `name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '화면에 보이는 이름. 공식 한국어 이름이 있으면 그 이름',
  `name_local` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '현지 공식 이름 (일본 리조트의 일본어, 영어 이름)',
  `official_difficulty` varchar(30) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '리조트가 표기한 난이도 그대로',
  `difficulty` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '색 표시용 난이도 묶음 (BEGINNER, INTERMEDIATE, ADVANCED, EXPERT)',
  `length_m` int DEFAULT NULL COMMENT '공식 길이 (m). 미공개면 NULL',
  `avg_gradient` decimal(4,1) DEFAULT NULL COMMENT '공식 평균 경사 (도). 미공개면 NULL',
  `max_gradient` decimal(4,1) DEFAULT NULL COMMENT '공식 최대 경사 (도). 미공개면 NULL',
  `width_m` int DEFAULT NULL COMMENT '공식 폭 (m). 미공개면 NULL',
  `lift_name` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '이 슬로프로 올라가는 리프트 이름',
  `osm_way_ids` varchar(300) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '코스 맵 선과 연결하는 OSM way ID 목록 (쉼표 구분). 선이 없으면 NULL',
  `source_url` varchar(500) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '제원 출처 공식 페이지',
  `checked_at` date NOT NULL COMMENT '출처를 확인한 날짜 (KST)',
  `display_order` int NOT NULL COMMENT '리조트 안 표시 순서',
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`slope_id`),
  UNIQUE KEY `uk_slope_resort_name` (`resort_id`,`name`),
  KEY `idx_slope_resort_order` (`resort_id`,`display_order`,`slope_id`),
  CONSTRAINT `fk_slope_resort` FOREIGN KEY (`resort_id`) REFERENCES `resort` (`resort_id`),
  CONSTRAINT `chk_slope_difficulty` CHECK ((`difficulty` in (_utf8mb4'BEGINNER',_utf8mb4'INTERMEDIATE',_utf8mb4'ADVANCED',_utf8mb4'EXPERT'))),
  CONSTRAINT `chk_slope_gradient` CHECK ((((`avg_gradient` is null) or (`avg_gradient` between 0 and 60)) and ((`max_gradient` is null) or (`max_gradient` between 0 and 60))))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='슬로프 공식 제원';
CREATE TABLE `slope_connection` (
  `slope_connection_id` bigint NOT NULL AUTO_INCREMENT,
  `slope_id` bigint NOT NULL COMMENT '기준 슬로프 ID',
  `related_slope_id` bigint NOT NULL COMMENT '관계가 있는 슬로프 ID',
  `relation` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '기준 슬로프에서 본 관계 (MERGE, BRANCH, CONTINUE, STARTS_FROM, SHARE_START, SHARE_END, CROSS, PARALLEL)',
  `distance_m` int DEFAULT NULL COMMENT 'PARALLEL일 때 두 코스 사이 최소 거리 (m)',
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`slope_connection_id`),
  UNIQUE KEY `uk_slope_connection_pair` (`slope_id`,`related_slope_id`),
  KEY `idx_slope_connection_related` (`related_slope_id`),
  CONSTRAINT `fk_slope_connection_related` FOREIGN KEY (`related_slope_id`) REFERENCES `slope` (`slope_id`) ON DELETE CASCADE,
  CONSTRAINT `fk_slope_connection_slope` FOREIGN KEY (`slope_id`) REFERENCES `slope` (`slope_id`) ON DELETE CASCADE,
  CONSTRAINT `chk_slope_connection_relation` CHECK ((`relation` in (_utf8mb4'MERGE',_utf8mb4'BRANCH',_utf8mb4'CONTINUE',_utf8mb4'STARTS_FROM',_utf8mb4'SHARE_START',_utf8mb4'SHARE_END',_utf8mb4'CROSS',_utf8mb4'PARALLEL'))),
  CONSTRAINT `chk_slope_connection_self` CHECK ((`slope_id` <> `related_slope_id`))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='슬로프 사이 관계 (코스 맵 선으로 계산)';
CREATE TABLE `system_config` (
  `config_key` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  `config_value` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `value_type` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'BOOLEAN',
  `description` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `updated_by` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`config_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE `withdrawn_email` (
  `email_hash` char(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '정규화한 이메일의 HMAC-SHA256',
  `withdrawn_at` datetime(6) NOT NULL,
  `expires_at` datetime(6) NOT NULL COMMENT '재가입 차단이 풀리는 시각',
  PRIMARY KEY (`email_hash`),
  KEY `idx_withdrawn_email_expires_at` (`expires_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='탈퇴 후 재가입 차단 이메일 해시';

SET FOREIGN_KEY_CHECKS = 1;
