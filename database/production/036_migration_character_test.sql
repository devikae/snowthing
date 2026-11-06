-- IKA-27 스키장 캐릭터 테스트와 관계 지도.
-- 1) character_group: 모임 링크 하나가 행 하나다. updated_at은 새 참여가 있을 때만 바꾸고, 7일 동안 그대로면 스케줄러가 지운다.
-- 2) character_participant: 답변 15자리와 내부 유형 코드, 접근 토큰의 SHA-256만 저장한다. 토큰 원문과 IP는 저장하지 않는다.
--    UNIQUE(character_group_id, nickname)는 모임 안 닉네임 중복을 막고,
--    INDEX(character_group_id, character_participant_id)는 지도 조회(ID 보조 정렬)와 인원수 계산에 쓴다.
-- 회원 테이블과 연결하지 않는다. 시각은 KST DATETIME(6)이다.
-- 새 테이블이라 이전 앱 이미지에는 영향이 없다. 앱 배포(ddl-auto validate) 전에 적용한다. 두 번 실행해도 안전하다.
-- 설계: docs/conception/024_character_test/01_design.md

CREATE TABLE IF NOT EXISTS character_group (
    character_group_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    public_id CHAR(36) NOT NULL,
    name VARCHAR(30) NOT NULL COMMENT '만든 사람 닉네임으로 자동 생성',
    owner_participant_id BIGINT NULL COMMENT '만든 사람. 만든 사람이 나가면 NULL',
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL COMMENT '마지막 새 참여 시각. 7일 보관 판단 기준',
    CONSTRAINT uk_character_group_public_id UNIQUE (public_id),
    INDEX idx_character_group_updated_at (updated_at),
    INDEX idx_character_group_owner (owner_participant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='캐릭터 테스트 모임';

CREATE TABLE IF NOT EXISTS character_participant (
    character_participant_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    public_id CHAR(36) NOT NULL,
    character_group_id BIGINT NOT NULL,
    nickname VARCHAR(10) NOT NULL COMMENT '모임 안에서만 고유',
    sport VARCHAR(10) NOT NULL COMMENT 'SKI 또는 BOARD. 유형에는 영향 없음',
    answers CHAR(15) NOT NULL COMMENT '유형 질문 12개는 A 또는 B, 습관 질문 3개는 A, B, C',
    type_code CHAR(4) NOT NULL COMMENT '내부 유형 코드. API 응답에 내보내지 않는다',
    access_token_hash CHAR(64) NOT NULL COMMENT '접근 토큰의 SHA-256',
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT uk_character_participant_public_id UNIQUE (public_id),
    CONSTRAINT uk_character_participant_token UNIQUE (access_token_hash),
    CONSTRAINT uk_character_participant_group_nickname UNIQUE (character_group_id, nickname),
    INDEX idx_character_participant_group_id (character_group_id, character_participant_id),
    CONSTRAINT fk_character_participant_group FOREIGN KEY (character_group_id) REFERENCES character_group (character_group_id),
    CONSTRAINT chk_character_participant_sport CHECK (sport IN ('SKI', 'BOARD'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='캐릭터 테스트 참여자';

-- 두 테이블이 서로를 가리켜서 만든 사람 FK는 테이블을 만든 뒤에 붙인다. 이미 있으면 건너뛴다.
DELIMITER $$
DROP PROCEDURE IF EXISTS `apply_character_group_owner_fk`$$
CREATE PROCEDURE `apply_character_group_owner_fk`()
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.table_constraints
        WHERE table_schema = DATABASE()
          AND table_name = 'character_group'
          AND constraint_name = 'fk_character_group_owner'
    ) THEN
        ALTER TABLE character_group
            ADD CONSTRAINT fk_character_group_owner
                FOREIGN KEY (owner_participant_id) REFERENCES character_participant (character_participant_id)
                ON DELETE SET NULL;
    END IF;
END$$
CALL `apply_character_group_owner_fk`()$$
DROP PROCEDURE `apply_character_group_owner_fk`$$
DELIMITER ;
