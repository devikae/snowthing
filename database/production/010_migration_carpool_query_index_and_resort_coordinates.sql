-- Carpool list lookup index and verified ski-area route anchors.
-- Existing migrations are immutable because their checksums are recorded in schema_migration.

ALTER TABLE `resort`
    ADD COLUMN `route_latitude` DECIMAL(10,7) NULL AFTER `is_active`,
    ADD COLUMN `route_longitude` DECIMAL(10,7) NULL AFTER `route_latitude`;

UPDATE `resort`
SET
    `route_latitude` = CASE `code`
        WHEN 'PHOENIX' THEN 37.5805715
        WHEN 'VIVALDI' THEN 37.6450833
        WHEN 'HIGH1' THEN 37.2040383
        WHEN 'YONGPYONG' THEN 37.6457553
        WHEN 'WELLI_HILLI' THEN 37.4855523
        WHEN 'JISAN' THEN 37.2167759
        WHEN 'KONJIAM' THEN 37.3369199
        WHEN 'MUJU' THEN 35.8909032
        WHEN 'EDEN_VALLEY' THEN 35.4248266
        WHEN 'ELYSIAN' THEN 37.8211533
        WHEN 'ALPENSIA' THEN 37.6563744
        WHEN 'OAK_VALLEY' THEN 37.4031965
        WHEN 'O2_RESORT' THEN 37.1775331
        ELSE `route_latitude`
    END,
    `route_longitude` = CASE `code`
        WHEN 'PHOENIX' THEN 128.3224144
        WHEN 'VIVALDI' THEN 127.6820210
        WHEN 'HIGH1' THEN 128.8388351
        WHEN 'YONGPYONG' THEN 128.6805217
        WHEN 'WELLI_HILLI' THEN 128.2477908
        WHEN 'JISAN' THEN 127.3451839
        WHEN 'KONJIAM' THEN 127.2935199
        WHEN 'MUJU' THEN 127.7368635
        WHEN 'EDEN_VALLEY' THEN 128.9852354
        WHEN 'ELYSIAN' THEN 127.5889848
        WHEN 'ALPENSIA' THEN 128.6733956
        WHEN 'OAK_VALLEY' THEN 127.8170565
        WHEN 'O2_RESORT' THEN 128.9480017
        ELSE `route_longitude`
    END
WHERE `code` IN (
    'PHOENIX', 'VIVALDI', 'HIGH1', 'YONGPYONG', 'WELLI_HILLI', 'JISAN',
    'KONJIAM', 'MUJU', 'EDEN_VALLEY', 'ELYSIAN', 'ALPENSIA', 'OAK_VALLEY', 'O2_RESORT'
);

ALTER TABLE `carpool_detail`
    ADD INDEX `idx_carpool_detail_departure_created_post`
        (`departure_at`, `created_at` DESC, `post_id` DESC);
