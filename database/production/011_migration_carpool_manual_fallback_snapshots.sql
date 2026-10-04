-- Persist the source of the applied fuel price and the destination coordinates
-- used for each carpool calculation. Manual route values reuse route_source=MANUAL.

ALTER TABLE `carpool_detail`
    ADD COLUMN `destination_latitude` DECIMAL(10,7) NULL AFTER `destination_resort_id`,
    ADD COLUMN `destination_longitude` DECIMAL(10,7) NULL AFTER `destination_latitude`,
    ADD COLUMN `fuel_price_source` VARCHAR(20) NOT NULL DEFAULT 'OPINET' AFTER `fuel_price`;

UPDATE `carpool_detail` cd
JOIN `resort` r ON r.`resort_id` = cd.`destination_resort_id`
SET
    cd.`destination_latitude` = r.`route_latitude`,
    cd.`destination_longitude` = r.`route_longitude`;

ALTER TABLE `carpool_detail`
    MODIFY COLUMN `destination_latitude` DECIMAL(10,7) NOT NULL,
    MODIFY COLUMN `destination_longitude` DECIMAL(10,7) NOT NULL,
    ADD CONSTRAINT `chk_carpool_fuel_price_source`
        CHECK (`fuel_price_source` IN ('OPINET', 'CACHE', 'USER_INPUT'));
