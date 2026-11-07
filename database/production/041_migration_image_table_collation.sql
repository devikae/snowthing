-- IKA-57 사진 관련 테이블 세 개의 collation을 다른 테이블과 맞춘다.
-- 024, 025, 026이 COLLATE 없이 만들어져 서버 기본값 utf8mb4_0900_ai_ci가 됐고, 나머지 테이블은 utf8mb4_unicode_ci다.
-- 그래서 market_thumbnail.post_public_id와 post.public_id를 비교하는 장터 썸네일 정리 쿼리가
-- "Illegal mix of collations"(1267)로 실패한다.
-- 세 테이블은 행이 적다(운영 기준 수 건). 이미 맞으면 건너뛰므로 두 번 실행해도 안전하다.
-- 칼럼 길이와 인덱스는 그대로라 이전 앱 이미지에도 영향이 없다.
DELIMITER $$

DROP PROCEDURE IF EXISTS `apply_image_table_collation`$$
CREATE PROCEDURE `apply_image_table_collation`()
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_schema = DATABASE() AND table_name = 'post_image_upload'
          AND table_collation <> 'utf8mb4_unicode_ci'
    ) THEN
        ALTER TABLE `post_image_upload` CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
    END IF;

    IF EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_schema = DATABASE() AND table_name = 'market_thumbnail'
          AND table_collation <> 'utf8mb4_unicode_ci'
    ) THEN
        ALTER TABLE `market_thumbnail` CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
    END IF;

    IF EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_schema = DATABASE() AND table_name = 'post_content_image_reference'
          AND table_collation <> 'utf8mb4_unicode_ci'
    ) THEN
        ALTER TABLE `post_content_image_reference` CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
    END IF;
END$$

DELIMITER ;

CALL `apply_image_table_collation`();
DROP PROCEDURE IF EXISTS `apply_image_table_collation`;
