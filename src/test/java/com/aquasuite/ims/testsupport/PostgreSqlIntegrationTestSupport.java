package com.aquasuite.ims.testsupport;

import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

@ActiveProfiles("test")
public abstract class PostgreSqlIntegrationTestSupport {

    private static final String IMAGE_NAME = "postgres:16-alpine";
    private static PostgreSQLContainer<?> postgresContainer;
    private static final String EXTERNAL_URL = System.getenv("TEST_DATABASE_URL");
    private static final String EXTERNAL_USERNAME = System.getenv("TEST_DATABASE_USERNAME");
    private static final String EXTERNAL_PASSWORD = System.getenv("TEST_DATABASE_PASSWORD");

    static {
        boolean hasUrl = EXTERNAL_URL != null && !EXTERNAL_URL.isBlank();
        boolean hasUser = EXTERNAL_USERNAME != null && !EXTERNAL_USERNAME.isBlank();
        boolean hasPass = EXTERNAL_PASSWORD != null && !EXTERNAL_PASSWORD.isBlank();

        if (hasUrl || hasUser || hasPass) {
            if (!(hasUrl && hasUser && hasPass)) {
                throw new IllegalStateException("Partial TEST_DATABASE_* environment variables detected. Must supply all three: URL, USERNAME, PASSWORD.");
            }
        } else {
            postgresContainer = new PostgreSQLContainer<>(IMAGE_NAME)
                    .withDatabaseName("aquasuite_test")
                    .withUsername("testuser")
                    .withPassword("testpass");
            postgresContainer.start();
        }
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        if (postgresContainer != null && postgresContainer.isRunning()) {
            registry.add("spring.datasource.url", postgresContainer::getJdbcUrl);
            registry.add("spring.datasource.username", postgresContainer::getUsername);
            registry.add("spring.datasource.password", postgresContainer::getPassword);
        } else if (EXTERNAL_URL != null) {
            registry.add("spring.datasource.url", () -> EXTERNAL_URL);
            registry.add("spring.datasource.username", () -> EXTERNAL_USERNAME);
            registry.add("spring.datasource.password", () -> EXTERNAL_PASSWORD);
        } else {
            throw new IllegalStateException("No valid PostgreSQL test database configured.");
        }
        registry.add("spring.mail.username", () -> "");
        registry.add("spring.mail.password", () -> "");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.flyway.schemas", () -> "public");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.jpa.properties.hibernate.default_schema", () -> "public");
    }

    public static String getJdbcUrl() {
        return postgresContainer != null ? postgresContainer.getJdbcUrl() : EXTERNAL_URL;
    }

    public static String getUsername() {
        return postgresContainer != null ? postgresContainer.getUsername() : EXTERNAL_USERNAME;
    }

    public static String getPassword() {
        return postgresContainer != null ? postgresContainer.getPassword() : EXTERNAL_PASSWORD;
    }
}
