-- ====================================================================
-- Snowthing Production Migration: 063_migration_info_board.sql
-- Version: 063
-- Description: IKA-111 정보게시판(INFO) 카테고리 추가
-- 실행 순서: 이 마이그레이션을 적용한 뒤 새 애플리케이션을 배포한다. 줄만 추가하므로 이전 애플리케이션과도 호환된다.
-- 여러 번 실행해도 안전하다 (NOT EXISTS).
-- ====================================================================
SET NAMES utf8mb4;

INSERT INTO `post_category` (`name`, `code`)
SELECT '정보게시판', 'INFO'
WHERE NOT EXISTS (SELECT 1 FROM `post_category` WHERE `code` = 'INFO');
