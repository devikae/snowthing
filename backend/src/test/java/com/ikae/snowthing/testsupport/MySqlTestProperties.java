package com.ikae.snowthing.testsupport;

import org.springframework.test.context.DynamicPropertyRegistry;

public final class MySqlTestProperties {

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
            throw new IllegalStateException(
                    name
                            + " is required when "
                            + TEST_DB_URL
                            + " is set. "
                            + "Configure all test database environment variables together.");
        }
        return value;
    }
}
