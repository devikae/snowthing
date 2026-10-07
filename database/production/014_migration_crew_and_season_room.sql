-- ====================================================================
-- Snowthing Production Migration: 014_migration_crew_and_season_room.sql
-- Version: 014
-- Description: 동호회 모집(CREW) 및 시즌방 모집(SEASON_ROOM) 게시판 카테고리 추가
-- ====================================================================

INSERT INTO `post_category` (`name`, `code`)
SELECT '동호회 모집', 'CREW'
WHERE NOT EXISTS (SELECT 1 FROM `post_category` WHERE `code` = 'CREW');

INSERT INTO `post_category` (`name`, `code`)
SELECT '시즌방 모집', 'SEASON_ROOM'
WHERE NOT EXISTS (SELECT 1 FROM `post_category` WHERE `code` = 'SEASON_ROOM');
