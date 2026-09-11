-- ====================================================================
-- Snowthing Production Initial Schema (MySQL 8.0)
-- Version: 001
-- Description: 운영 RDS 최초 스키마 생성 스크립트 (비파괴 CREATE TABLE IF NOT EXISTS)
-- ====================================================================

-- 1. 크루 마스터 테이블 (1인 1크루 소속)
CREATE TABLE IF NOT EXISTS `crew` (
    `crew_id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '크루 고유 식별자',
    `public_id` VARCHAR(36) NOT NULL UNIQUE COMMENT '외부 노출용 UUID v7',
    `name` VARCHAR(100) NOT NULL COMMENT '크루 이름',
    `description` TEXT NULL COMMENT '크루 소개',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성 일시'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='크루 마스터';

-- 2. 회원 마스터 테이블
CREATE TABLE IF NOT EXISTS `member` (
    `member_id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT 'DB 내부 조인용 PK',
    `public_id` VARCHAR(36) NOT NULL UNIQUE COMMENT '외부 API/URL 노출용 UUID v7',
    `email` VARCHAR(100) NOT NULL UNIQUE COMMENT '로그인 이메일 계정',
    `password` VARCHAR(255) NULL COMMENT 'BCrypt 암호화 비밀번호 (OAuth2 대비 NULLABLE)',
    `nickname` VARCHAR(50) NOT NULL UNIQUE COMMENT '활동 닉네임',
    `profile_image_url` VARCHAR(500) NULL COMMENT '프로필 이미지 URL',
    `bio` VARCHAR(255) NULL COMMENT '자기소개 한마디',
    `departure_region` VARCHAR(100) NULL COMMENT '주 출발/거주 지역',
    `crew_id` BIGINT NULL COMMENT '소속 크루 ID',
    `crew_role` VARCHAR(20) NULL COMMENT '크루 내 권한 (OWNER, MANAGER, MEMBER)',
    `role` VARCHAR(20) NOT NULL DEFAULT 'ROLE_USER' COMMENT '전역 권한 (ROLE_USER, ROLE_ADMIN)',
    `status` VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT '계정 상태 (ACTIVE, SUSPENDED, WITHDRAWN)',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '가입 일시',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정 일시',
    CONSTRAINT `fk_member_crew` FOREIGN KEY (`crew_id`) REFERENCES `crew` (`crew_id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='회원 마스터';

-- 3. 리조트 마스터 테이블 (Resort.java: length=100, unique=true)
CREATE TABLE IF NOT EXISTS `resort` (
    `resort_id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '리조트 고유 식별자',
    `name` VARCHAR(100) NOT NULL UNIQUE COMMENT '스키장 이름',
    `region` VARCHAR(50) NOT NULL COMMENT '소재 지역'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='리조트 마스터';

-- 4. 회원-리조트 N:M 중계 테이블
CREATE TABLE IF NOT EXISTS `member_resort` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '단일 대리키 PK',
    `member_id` BIGINT NOT NULL COMMENT '회원 ID',
    `resort_id` BIGINT NOT NULL COMMENT '리조트 ID',
    `is_main` BOOLEAN NOT NULL DEFAULT FALSE COMMENT '주 베이스 여부',
    CONSTRAINT `fk_mr_member` FOREIGN KEY (`member_id`) REFERENCES `member` (`member_id`) ON DELETE CASCADE,
    CONSTRAINT `fk_mr_resort` FOREIGN KEY (`resort_id`) REFERENCES `resort` (`resort_id`) ON DELETE CASCADE,
    CONSTRAINT `uk_member_resort` UNIQUE (`member_id`, `resort_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='회원-리조트 N:M 중계';

-- 5. 라이딩 스타일 마스터 테이블 (RidingStyle.java: description, name UNIQUE)
CREATE TABLE IF NOT EXISTS `riding_style` (
    `style_id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '스타일 고유 식별자',
    `name` VARCHAR(50) NOT NULL UNIQUE COMMENT '라이딩 스타일명',
    `description` VARCHAR(255) NULL COMMENT '스타일 상세 설명'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='라이딩 스타일 마스터';

-- 6. 회원-라이딩스타일 N:M 중계 테이블
CREATE TABLE IF NOT EXISTS `member_riding_style` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '단일 대리키 PK',
    `member_id` BIGINT NOT NULL COMMENT '회원 ID',
    `style_id` BIGINT NOT NULL COMMENT '라이딩 스타일 ID',
    CONSTRAINT `fk_mrs_member` FOREIGN KEY (`member_id`) REFERENCES `member` (`member_id`) ON DELETE CASCADE,
    CONSTRAINT `fk_mrs_style` FOREIGN KEY (`style_id`) REFERENCES `riding_style` (`style_id`) ON DELETE CASCADE,
    CONSTRAINT `uk_member_riding_style` UNIQUE (`member_id`, `style_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='회원-라이딩스타일 N:M 중계';

-- 7. 게시판 카테고리 테이블
CREATE TABLE IF NOT EXISTS `post_category` (
    `category_id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '카테고리 식별자',
    `name` VARCHAR(50) NOT NULL COMMENT '카테고리명',
    `code` VARCHAR(50) NOT NULL UNIQUE COMMENT '카테고리 고유 코드'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='게시판 카테고리';

-- 8. 게시글 테이블
CREATE TABLE IF NOT EXISTS `post` (
    `post_id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '게시글 고유 식별자',
    `public_id` VARCHAR(36) NOT NULL UNIQUE COMMENT '외부 API/URL 노출용 UUID v7',
    `member_id` BIGINT NULL COMMENT '작성자 회원 ID (비회원 작성 시 NULL)',
    `category_id` BIGINT NOT NULL COMMENT '카테고리 ID',
    `title` VARCHAR(200) NOT NULL COMMENT '게시글 제목',
    `content` LONGTEXT NOT NULL COMMENT '게시글 본문',
    `writer_ip` VARCHAR(45) NOT NULL COMMENT '작성자 IP 주소',
    `is_anonymous` BOOLEAN NOT NULL DEFAULT FALSE COMMENT '익명 작성 여부',
    `anonymous_password` VARCHAR(255) NULL COMMENT '비회원 익명 수정/삭제용 비밀번호 (BCrypt)',
    `view_count` INT NOT NULL DEFAULT 0 COMMENT '조회수',
    `comment_count` INT NOT NULL DEFAULT 0 COMMENT '댓글 수 역정규화',
    `like_count` INT NOT NULL DEFAULT 0 COMMENT '추천 수 역정규화',
    `dislike_count` INT NOT NULL DEFAULT 0 COMMENT '비추천 수 역정규화',
    `has_image` BOOLEAN NOT NULL DEFAULT FALSE COMMENT '이미지 첨부 여부 역정규화 (목록 뱃지 아이콘 렌더링용)',
    `status` VARCHAR(20) NOT NULL DEFAULT 'NORMAL' COMMENT '게시글 상태 (NORMAL, DELETED, BLOCKED, HIDDEN, DRAFT)',
    `is_deleted` BOOLEAN NOT NULL DEFAULT FALSE COMMENT 'Soft Delete 여부',
    `deleted_at` DATETIME NULL COMMENT '삭제 일시',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '작성 일시',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정 일시',
    CONSTRAINT `fk_post_member` FOREIGN KEY (`member_id`) REFERENCES `member` (`member_id`) ON DELETE SET NULL,
    CONSTRAINT `fk_post_category` FOREIGN KEY (`category_id`) REFERENCES `post_category` (`category_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='게시글';

-- 9. 게시글 첨부 이미지 테이블 (PostImage.java: BaseTimeEntity 상속으로 updated_at 포함)
CREATE TABLE IF NOT EXISTS `post_image` (
    `image_id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '이미지 고유 식별자',
    `post_id` BIGINT NOT NULL COMMENT '게시글 ID',
    `image_url` VARCHAR(500) NOT NULL COMMENT '이미지 파일 URL',
    `sort_order` INT NOT NULL DEFAULT 1 COMMENT '표시 순서',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '등록 일시',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정 일시',
    CONSTRAINT `fk_pi_post` FOREIGN KEY (`post_id`) REFERENCES `post` (`post_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='게시글 첨부 이미지';

-- 10. 게시글 추천/비추천 중계 테이블
CREATE TABLE IF NOT EXISTS `post_reaction` (
    `reaction_id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '반응 식별자',
    `post_id` BIGINT NOT NULL COMMENT '게시글 ID',
    `member_id` BIGINT NULL COMMENT '회원 ID (비회원 투표 시 NULL)',
    `writer_ip` VARCHAR(45) NULL COMMENT '투표자 IP 주소',
    `anonymous_voter_id` VARCHAR(36) NULL COMMENT '비회원 익명 투표자 식별 쿠키 ID',
    `type` VARCHAR(10) NOT NULL COMMENT '반응 종류 (LIKE, DISLIKE)',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '투표 일시',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정 일시',
    CONSTRAINT `fk_pr_post` FOREIGN KEY (`post_id`) REFERENCES `post` (`post_id`) ON DELETE CASCADE,
    CONSTRAINT `fk_pr_member` FOREIGN KEY (`member_id`) REFERENCES `member` (`member_id`) ON DELETE CASCADE,
    CONSTRAINT `uk_post_member_type` UNIQUE (`post_id`, `member_id`, `type`),
    CONSTRAINT `uk_post_anon_voter_type` UNIQUE (`post_id`, `anonymous_voter_id`, `type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='게시글 추천/비추천';

-- 11. 댓글 및 계층형 대댓글 테이블 (Comment.java: @Version version 컬럼 포함)
CREATE TABLE IF NOT EXISTS `comment` (
    `comment_id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '댓글 식별자',
    `version` BIGINT NOT NULL DEFAULT 0 COMMENT '낙관적 락 동시성 제어용 버전',
    `post_id` BIGINT NOT NULL COMMENT '게시글 ID',
    `member_id` BIGINT NULL COMMENT '작성자 회원 ID (비회원 작성 시 NULL)',
    `parent_id` BIGINT NULL COMMENT '부모 댓글 ID (대댓글 구현용)',
    `content` VARCHAR(1000) NOT NULL COMMENT '댓글 내용',
    `writer_ip` VARCHAR(45) NOT NULL COMMENT '작성자 IP 주소',
    `is_anonymous` BOOLEAN NOT NULL DEFAULT FALSE COMMENT '익명 작성 여부',
    `anonymous_password` VARCHAR(255) NULL COMMENT '비회원 익명 수정/삭제용 비밀번호 (BCrypt)',
    `is_deleted` BOOLEAN NOT NULL DEFAULT FALSE COMMENT 'Soft Delete 여부',
    `deleted_at` DATETIME NULL COMMENT '삭제 일시',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '작성 일시',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정 일시',
    INDEX `idx_comment_post_parent_id` (`post_id`, `parent_id`, `comment_id`),
    INDEX `idx_comment_parent_deleted_id` (`parent_id`, `is_deleted`, `comment_id`),
    CONSTRAINT `fk_comment_post` FOREIGN KEY (`post_id`) REFERENCES `post` (`post_id`) ON DELETE CASCADE,
    CONSTRAINT `fk_comment_member` FOREIGN KEY (`member_id`) REFERENCES `member` (`member_id`) ON DELETE SET NULL,
    CONSTRAINT `fk_comment_parent` FOREIGN KEY (`parent_id`) REFERENCES `comment` (`comment_id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='댓글 및 계층형 대댓글';
