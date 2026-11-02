-- Migration 023: Index member.profile_image_url for efficient lifecycle lookup
DELIMITER $$

DROP PROCEDURE IF EXISTS `apply_member_profile_image_url_index`$$
CREATE PROCEDURE `apply_member_profile_image_url_index`()
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'member'
          AND index_name = 'idx_member_profile_image_url'
    ) THEN
        ALTER TABLE `member`
            ADD INDEX `idx_member_profile_image_url` (`profile_image_url`);
    END IF;
END$$

DELIMITER ;

CALL `apply_member_profile_image_url_index`();
DROP PROCEDURE IF EXISTS `apply_member_profile_image_url_index`;
