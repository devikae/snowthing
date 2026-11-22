package com.ikae.snowthing.schema;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * database/production 마이그레이션 체인으로 만든 스키마에서 엔티티 매핑이 통과하는지 확인한다.
 *
 * <p>운영은 ddl-auto=validate로 뜨지만 일반 테스트는 create-drop이라 엔티티와 마이그레이션이 어긋나도 잡지 못한다. 이 테스트는 마이그레이션을 먼저
 * 적용한 스키마를 대상으로 validate 기동만 해 본다. 스키마를 만들지도 지우지도 않으므로 일반 테스트와 따로 {@code ./gradlew schemaValidate}로
 * 실행한다.
 */
@Tag("schema-validate")
@SpringBootTest(
        properties = {
            "spring.jpa.hibernate.ddl-auto=validate",
            "spring.sql.init.mode=never",
        })
class MigrationSchemaValidationTest {

    @Test
    void 마이그레이션_스키마가_엔티티_매핑과_일치한다() {}
}
