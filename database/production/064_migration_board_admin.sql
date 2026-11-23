-- ====================================================================
-- Snowthing Production Migration: 064_migration_board_admin.sql
-- Version: 064
-- Description: IKA-114 어드민 게시판 관리. post_category에 순서, 공개 상태, 홈 탭, 글쓰기 규칙, 소프트 삭제 칸을 더하고
--              지금 코드에 박힌 규칙을 그대로 옮겨 적는다. 설계: docs/conception/037_board_admin/01_design.md
-- 칸
--   short_name: 홈 탭과 글 목록 분류 표시 이름
--   description: 게시판 위 설명과 검색 결과 설명
--   sort_order: 메뉴와 홈 탭 순서, 작을수록 앞 (같으면 ID 순)
--   status: OPEN, HIDDEN (닫힌 게시판은 메뉴, 홈 탭, 글쓰기에서 빠지고 새 글을 막는다)
--   show_on_home: 홈 탭에 보이기
--   write_permission: ALL(비회원 포함), MEMBER, ADMIN
--   anonymous_mode: FORCED(항상 익명), OPTIONAL(선택), FORBIDDEN(금지)
--   deleted_at: 소프트 삭제 시각
-- 인덱스: 20줄 안쪽 표라 정렬용 인덱스는 두지 않는다. code는 이미 UNIQUE다.
-- 실행 순서: 063 다음. 이 마이그레이션을 적용한 뒤 새 애플리케이션을 배포한다. 새 칸은 기본값이 있어 이전 애플리케이션과도 호환된다.
-- 여러 번 실행해도 안전하다. 칸이 없을 때만 칸을 더하고 값을 채우므로, 다시 실행해도 관리자가 바꾼 값을 덮지 않는다.
-- ====================================================================
SET NAMES utf8mb4;

DELIMITER $$

DROP PROCEDURE IF EXISTS `apply_064_board_admin`$$
CREATE PROCEDURE `apply_064_board_admin`()
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'post_category'
          AND column_name = 'sort_order'
    ) THEN
        ALTER TABLE `post_category`
            ADD COLUMN `short_name` VARCHAR(10) NOT NULL DEFAULT '' COMMENT '홈 탭과 글 목록 분류 표시 이름',
            ADD COLUMN `description` VARCHAR(200) NULL COMMENT '게시판 설명',
            ADD COLUMN `sort_order` INT NOT NULL DEFAULT 990 COMMENT '메뉴와 홈 탭 순서, 작을수록 앞',
            ADD COLUMN `status` VARCHAR(10) NOT NULL DEFAULT 'HIDDEN' COMMENT '공개 상태 (OPEN, HIDDEN)',
            ADD COLUMN `show_on_home` BOOLEAN NOT NULL DEFAULT FALSE COMMENT '홈 탭에 보이기',
            ADD COLUMN `write_permission` VARCHAR(10) NOT NULL DEFAULT 'MEMBER' COMMENT '글쓰기 권한 (ALL, MEMBER, ADMIN)',
            ADD COLUMN `anonymous_mode` VARCHAR(10) NOT NULL DEFAULT 'OPTIONAL' COMMENT '익명 규칙 (FORCED, OPTIONAL, FORBIDDEN)',
            ADD COLUMN `deleted_at` DATETIME(6) NULL COMMENT '소프트 삭제 시각';

        -- 지금 게시판: 코드에 박힌 규칙과 프론트 이름, 문구를 그대로 옮긴다. 이름은 프론트 메뉴에 보이던 이름으로 맞춘다.
        UPDATE `post_category` SET `name` = '공지사항', `short_name` = '공지', `sort_order` = 10, `status` = 'OPEN', `show_on_home` = TRUE,
            `write_permission` = 'ADMIN', `anonymous_mode` = 'FORBIDDEN',
            `description` = '눈팅 SnowThing 운영 소식과 이용 안내를 알려 드립니다.' WHERE `code` = 'NOTICE';
        UPDATE `post_category` SET `name` = '자유게시판', `short_name` = '자유', `sort_order` = 20, `status` = 'OPEN', `show_on_home` = TRUE,
            `write_permission` = 'MEMBER', `anonymous_mode` = 'OPTIONAL',
            `description` = '설질, 장비, 시즌 이야기를 자유롭게 나누는 게시판입니다.' WHERE `code` = 'FREE';
        UPDATE `post_category` SET `name` = '익명게시판', `short_name` = '익명', `sort_order` = 30, `status` = 'OPEN', `show_on_home` = TRUE,
            `write_permission` = 'ALL', `anonymous_mode` = 'FORCED',
            `description` = '닉네임 없이 편하게 이야기를 나누는 익명게시판입니다.' WHERE `code` = 'ANONYMOUS';
        UPDATE `post_category` SET `name` = '질문게시판', `short_name` = '질문', `sort_order` = 40, `status` = 'OPEN', `show_on_home` = TRUE,
            `write_permission` = 'MEMBER', `anonymous_mode` = 'OPTIONAL',
            `description` = '장비, 리조트, 기술에 대해 묻고 라이더에게 답을 듣는 질문게시판입니다.' WHERE `code` = 'QNA';
        UPDATE `post_category` SET `name` = '정보게시판', `short_name` = '정보', `sort_order` = 50, `status` = 'OPEN', `show_on_home` = TRUE,
            `write_permission` = 'MEMBER', `anonymous_mode` = 'FORBIDDEN',
            `description` = '보드, 스키 잘 타는 방법과 리조트 개장 같은 소식을 나누는 정보게시판입니다.' WHERE `code` = 'INFO';
        UPDATE `post_category` SET `name` = '리조트 맛집', `short_name` = '맛집', `sort_order` = 60, `status` = 'OPEN', `show_on_home` = TRUE,
            `write_permission` = 'MEMBER', `anonymous_mode` = 'OPTIONAL',
            `description` = '리조트 근처 맛집과 식당 정보를 나누는 게시판입니다.' WHERE `code` = 'FOOD';
        UPDATE `post_category` SET `name` = '동호회 모집', `short_name` = '동호회', `sort_order` = 70, `status` = 'OPEN', `show_on_home` = TRUE,
            `write_permission` = 'MEMBER', `anonymous_mode` = 'FORBIDDEN',
            `description` = '함께 탈 스키, 보드 동호회 회원을 모집하는 게시판입니다.' WHERE `code` = 'CREW';
        UPDATE `post_category` SET `name` = '시즌방 모집', `short_name` = '시즌방', `sort_order` = 80, `status` = 'OPEN', `show_on_home` = TRUE,
            `write_permission` = 'MEMBER', `anonymous_mode` = 'FORBIDDEN',
            `description` = '시즌 동안 함께 지낼 시즌방 멤버를 모집하는 게시판입니다.' WHERE `code` = 'SEASON_ROOM';

        -- 전용 화면이 있는 카테고리: 관리 대상이 아니다. 규칙은 각 화면 코드가 정한다.
        UPDATE `post_category` SET `short_name` = '장터', `sort_order` = 900, `status` = 'OPEN' WHERE `code` = 'MARKET';
        UPDATE `post_category` SET `short_name` = '카풀', `sort_order` = 910, `status` = 'OPEN' WHERE `code` = 'CARPOOL';
        UPDATE `post_category` SET `short_name` = '문의', `sort_order` = 920, `status` = 'OPEN',
            `anonymous_mode` = 'FORBIDDEN', `description` = '운영진에게 1:1로 문의하고 답변을 확인합니다.' WHERE `code` = 'INQUIRY';

        -- 그 밖의 옛 줄(GEAR_VS 등)은 기본값(닫힘, 순서 990, 홈 탭 끔)으로 둔다. 홈 탭 이름은 이름을 쓴다.
        UPDATE `post_category` SET `short_name` = LEFT(`name`, 10) WHERE `short_name` = '';
    END IF;
END$$

DELIMITER ;

CALL `apply_064_board_admin`();
DROP PROCEDURE IF EXISTS `apply_064_board_admin`;
