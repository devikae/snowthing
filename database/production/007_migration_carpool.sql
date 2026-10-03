-- 카풀 모집글 확장 테이블
-- 사전 점검: destination_resort_id가 resort에 존재하는지 확인한 뒤 실행합니다.

CREATE TABLE IF NOT EXISTS `carpool_detail` (
    `post_id` BIGINT NOT NULL,
    `departure_region` VARCHAR(100) NOT NULL,
    `meeting_place` VARCHAR(200) NOT NULL,
    `departure_latitude` DECIMAL(10,7) NULL,
    `departure_longitude` DECIMAL(10,7) NULL,
    `destination_resort_id` BIGINT NOT NULL,
    `trip_type` VARCHAR(20) NOT NULL,
    `departure_at` DATETIME NOT NULL,
    `return_at` DATETIME NULL,
    `passenger_capacity` INT NOT NULL,
    `fuel_type` VARCHAR(30) NOT NULL,
    `fuel_efficiency` DECIMAL(6,2) NOT NULL,
    `fuel_price` DECIMAL(10,2) NOT NULL,
    `fuel_price_observed_at` DATETIME NOT NULL,
    `route_distance_km` DECIMAL(8,2) NOT NULL,
    `route_toll_fee` INT NOT NULL,
    `estimated_fuel_cost` INT NOT NULL,
    `estimated_total_cost` INT NOT NULL,
    `estimated_cost_per_person` INT NOT NULL,
    `route_source` VARCHAR(20) NOT NULL,
    `route_calculated_at` DATETIME NULL,
    `contact_info` VARCHAR(500) NULL,
    `contact_public_to_guest` BOOLEAN NOT NULL DEFAULT FALSE,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`post_id`),
    CONSTRAINT `fk_carpool_detail_post` FOREIGN KEY (`post_id`) REFERENCES `post` (`post_id`) ON DELETE CASCADE,
    CONSTRAINT `fk_carpool_detail_resort` FOREIGN KEY (`destination_resort_id`) REFERENCES `resort` (`resort_id`),
    CONSTRAINT `chk_carpool_trip_type` CHECK (`trip_type` IN ('ONE_WAY', 'ROUND_TRIP')),
    CONSTRAINT `chk_carpool_fuel_type` CHECK (`fuel_type` IN ('GASOLINE', 'DIESEL', 'LPG', 'HYBRID_GASOLINE')),
    CONSTRAINT `chk_carpool_route_source` CHECK (`route_source` IN ('KAKAO', 'MANUAL')),
    CONSTRAINT `chk_carpool_passenger_capacity` CHECK (`passenger_capacity` > 0),
    CONSTRAINT `chk_carpool_fuel_efficiency` CHECK (`fuel_efficiency` > 0),
    CONSTRAINT `chk_carpool_fuel_price` CHECK (`fuel_price` >= 0),
    CONSTRAINT `chk_carpool_distance` CHECK (`route_distance_km` > 0),
    CONSTRAINT `chk_carpool_toll_fee` CHECK (`route_toll_fee` >= 0),
    CONSTRAINT `chk_carpool_estimated_fuel_cost` CHECK (`estimated_fuel_cost` >= 0),
    CONSTRAINT `chk_carpool_estimated_total_cost` CHECK (`estimated_total_cost` >= 0),
    CONSTRAINT `chk_carpool_estimated_cost_per_person` CHECK (`estimated_cost_per_person` >= 0),
    CONSTRAINT `chk_carpool_round_trip_return` CHECK (
        (`trip_type` = 'ONE_WAY' AND `return_at` IS NULL)
        OR (`trip_type` = 'ROUND_TRIP' AND `return_at` > `departure_at`)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='카풀 모집글 상세';

INSERT INTO `post_category` (`name`, `code`)
SELECT '카풀·동행', 'CARPOOL'
WHERE NOT EXISTS (SELECT 1 FROM `post_category` WHERE `code` = 'CARPOOL');
