-- IKA-115 라이브톡 운영 도구와 회원 정지 기간. 설계: docs/conception/038_live_chat_ops/01_design.md
-- 1) system_config에 라이브톡 비상 닫기 스위치(CHAT_ENABLED) 행을 추가한다. 기본값 'true'로 지금처럼 열려 있다.
--    행이 없으면 애플리케이션은 'true'로 보지만, 관리자 화면에서 바꾸려면 행이 있어야 한다.
-- 2) member에 정지 종료 시각(suspended_until)과 사유(suspend_reason)를 추가한다. 둘 다 NULL 허용이다.
--    기존 정지 회원은 suspended_until이 NULL이라 지금처럼 무기한 정지로 남는다.
--    자동 해제 스케줄러는 status = 'SUSPENDED'인 몇 명만 보므로 인덱스를 따로 두지 않는다.
-- 실행 순서: 이 마이그레이션을 적용한 뒤 새 애플리케이션을 배포한다. 추가만 하므로 이전 애플리케이션과도 호환된다.
-- NULL 허용 컬럼 추가는 InnoDB 즉시 DDL(ALGORITHM=INSTANT)이라 표를 다시 쓰지 않는다.
-- 여러 번 실행해도 안전하다 (NOT EXISTS, 컬럼 존재 확인).

INSERT INTO `system_config` (`config_key`, `config_value`, `value_type`, `description`, `updated_at`, `updated_by`)
SELECT 'CHAT_ENABLED', 'true', 'BOOLEAN', '라이브톡 발신 허용 여부 (끄면 관리자만 보낼 수 있음)', NOW(6), 'SYSTEM'
WHERE NOT EXISTS (
    SELECT 1 FROM `system_config` WHERE `config_key` = 'CHAT_ENABLED'
);

DELIMITER $$

DROP PROCEDURE IF EXISTS `apply_066_member_column`$$
CREATE PROCEDURE `apply_066_member_column`(IN p_column VARCHAR(64), IN p_definition VARCHAR(255))
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'member'
          AND column_name = p_column
    ) THEN
        SET @ddl = CONCAT('ALTER TABLE `member` ADD COLUMN `', p_column, '` ', p_definition, ', ALGORITHM=INSTANT');
        PREPARE stmt FROM @ddl;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END$$

DELIMITER ;

CALL `apply_066_member_column`('suspended_until', 'DATETIME(6) NULL COMMENT ''정지 종료 시각(KST). NULL이면 무기한''');
CALL `apply_066_member_column`('suspend_reason', 'VARCHAR(255) NULL COMMENT ''관리자가 남긴 정지 사유''');
DROP PROCEDURE IF EXISTS `apply_066_member_column`;
