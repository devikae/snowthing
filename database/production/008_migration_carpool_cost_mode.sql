ALTER TABLE `carpool_detail`
    ADD COLUMN `cost_mode` VARCHAR(20) NOT NULL DEFAULT 'AUTO' AFTER `fuel_efficiency`;
