package ch.batbern.shared.test;

import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Abstract base class for integration tests using Testcontainers PostgreSQL.
 *
 * This ensures production parity by running tests against a real PostgreSQL database
 * instead of H2, which allows us to:
 * - Test PostgreSQL-specific features (JSONB, functions, etc.)
 * - Verify Flyway migrations work correctly
 * - Catch database-specific issues early
 *
 * The container is shared across all test classes for performance (singleton pattern).
 *
 * Usage:
 * ```java
 * @Transactional
 * @Import(TestSecurityConfig.class) // If needed
 * public class MyServiceIntegrationTest extends AbstractIntegrationTest {
 *     // Test implementation
 * }
 * ```
 *
 * Architecture Reference: docs/architecture/06-backend-architecture.md (Production Parity Testing)
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
public abstract class AbstractIntegrationTest {

    /**
     * Shared PostgreSQL container for all tests.
     * Using singleton pattern with reuse=true for optimal performance.
     *
     * PostgreSQL 16 Alpine is used to match production environment.
     */
    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test")
            .withReuse(true);

    /**
     * Dynamically configure Spring datasource properties from the Testcontainers instance.
     * This overrides any static configuration in application-test.yml.
     */
    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }
}
