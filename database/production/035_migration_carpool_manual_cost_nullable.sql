-- 1인 금액을 직접 입력한 카풀 글은 경로와 유가를 계산하지 않으므로 계산 값을 NULL로 저장한다.
-- 기존 행은 컬럼이 NOT NULL이었으므로 CHECK 추가에 걸리지 않는다.
-- 실행 순서: 이 마이그레이션을 적용한 뒤 새 애플리케이션을 배포한다. 이전 애플리케이션과도 호환된다.
-- MODIFY COLUMN은 컬럼 정의를 통째로 바꾸므로 주석도 함께 적는다.

ALTER TABLE `carpool_detail`
    MODIFY COLUMN `fuel_type` VARCHAR(30) NULL COMMENT '연료 종류. 1인 금액 직접 입력이면 NULL',
    MODIFY COLUMN `fuel_efficiency` DECIMAL(6,2) NULL COMMENT '차량 연비 km/L',
    MODIFY COLUMN `fuel_price` DECIMAL(10,2) NULL COMMENT '계산에 사용한 유가',
    MODIFY COLUMN `fuel_price_source` VARCHAR(20) NULL COMMENT '유가 출처',
    MODIFY COLUMN `fuel_price_observed_at` DATETIME NULL COMMENT '유가 조회 시각',
    MODIFY COLUMN `route_distance_km` DECIMAL(8,2) NULL COMMENT '최종 적용 거리',
    MODIFY COLUMN `route_toll_fee` INT NULL COMMENT '최종 적용 통행료',
    MODIFY COLUMN `estimated_fuel_cost` INT NULL COMMENT '예상 연료비',
    MODIFY COLUMN `estimated_total_cost` INT NULL COMMENT '예상 총비용',
    MODIFY COLUMN `estimated_cost_per_person` INT NOT NULL COMMENT '1인 금액. 자동 계산은 운전자 포함 1인 예상 비용',
    MODIFY COLUMN `route_source` VARCHAR(20) NULL COMMENT '경로 계산 출처',
    ADD CONSTRAINT `chk_carpool_auto_cost_snapshot` CHECK (
        `cost_mode` <> 'AUTO'
        OR (
            `fuel_type` IS NOT NULL
            AND `fuel_efficiency` IS NOT NULL
            AND `fuel_price` IS NOT NULL
            AND `fuel_price_source` IS NOT NULL
            AND `fuel_price_observed_at` IS NOT NULL
            AND `route_distance_km` IS NOT NULL
            AND `route_toll_fee` IS NOT NULL
            AND `estimated_fuel_cost` IS NOT NULL
            AND `estimated_total_cost` IS NOT NULL
            AND `route_source` IS NOT NULL
        )
    );
