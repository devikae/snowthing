ALTER TABLE `carpool_detail`
    ADD COLUMN `equipment_load_available` BOOLEAN NOT NULL DEFAULT FALSE
        COMMENT '장비 적재 가능 여부'
        AFTER `contact_public_to_guest`;
