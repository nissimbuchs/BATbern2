package ch.batbern.migration;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Abstract Integration Test Base Class
 *
 * Provides PostgreSQL Testcontainer for integration tests.
 * Singleton container reused across all tests for performance.
 *
 * Story: 3.2.1 - Migration Tool Implementation
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
public abstract class AbstractIntegrationTest {

    /**
     * Singleton PostgreSQL container - reused across all tests
     * Uses PostgreSQL 16 Alpine for production parity
     */
    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
        .withDatabaseName("migration_test")
        .withUsername("test")
        .withPassword("test")
        .withReuse(true);

    /**
     * Configure Spring Boot to use Testcontainers PostgreSQL
     */
    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }
}
