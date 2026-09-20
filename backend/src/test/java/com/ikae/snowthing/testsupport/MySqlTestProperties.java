package com.ikae.snowthing.testsupport;

import org.springframework.test.context.DynamicPropertyRegistry;

import com.ikae.snowthing.global.error.ErrorCode;
import com.ikae.snowthing.global.exception.CustomAuthException;

public final class MySqlTestProperties {

    public static final String DEFAULT_TEST_DB_URL =
            "jdbc:mysql://localhost:3306/snowthing_test?useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=UTF-8&serverTimezone=Asia/Seoul";

    private static final String TEST_DB_URL = "SNOWTHING_TEST_DB_URL";
    private static final String TEST_DB_USERNAME = "SNOWTHING_TEST_DB_USERNAME";
    private static final String TEST_DB_PASSWORD = "SNOWTHING_TEST_DB_PASSWORD";
    private static final String MYSQL_DRIVER = "com.mysql.cj.jdbc.Driver";
    private static final String MYSQL_DIALECT = "org.hibernate.dialect.MySQLDialect";
    private static final String CREATE_DROP = "create-drop";

    private MySqlTestProperties() {}

    public static void register(DynamicPropertyRegistry registry) {
        String configuredUrl = System.getenv(TEST_DB_URL);
        if (configuredUrl == null || configuredUrl.isBlank()) {
            registry.add("spring.datasource.url", () -> DEFAULT_TEST_DB_URL);
            return;
        }

        registry.add("spring.datasource.url", () -> configuredUrl);
        registry.add(
                "spring.datasource.username", () -> requiredEnvironmentVariable(TEST_DB_USERNAME));
        registry.add(
                "spring.datasource.password", () -> requiredEnvironmentVariable(TEST_DB_PASSWORD));
        registry.add("spring.datasource.driver-class-name", () -> MYSQL_DRIVER);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> CREATE_DROP);
        registry.add("spring.jpa.database-platform", () -> MYSQL_DIALECT);
        registry.add("spring.jpa.properties.hibernate.dialect", () -> MYSQL_DIALECT);
    }

    private static String requiredEnvironmentVariable(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new CustomAuthException(ErrorCode.INVALID_INPUT);
        }
        return value;
    }
}
