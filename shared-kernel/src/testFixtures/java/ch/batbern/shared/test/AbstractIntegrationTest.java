package ch.batbern.shared.test;

import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Abstract base class for integration tests using Testcontainers PostgreSQL.
 *
 * This ensures production parity by running tests against a real PostgreSQL database
 * instead of H2, which allows us to:
 * - Test PostgreSQL-specific features (JSONB, functions, etc.)
 * - Verify Flyway migrations work correctly
 * - Catch database-specific issues early
 *
 * The container is shared across all test classes for performance (manual singleton pattern).
 * We do NOT use @Testcontainers/@Container to avoid premature container shutdown.
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
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
public abstract class AbstractIntegrationTest {

    /**
     * Shared PostgreSQL container for all tests using manual singleton pattern.
     * The container is started once and reused across all test classes.
     * It will be automatically cleaned up by Testcontainers Ryuk when the JVM exits.
     *
     * PostgreSQL 16 Alpine is used to match production environment.
     */
    private static final PostgreSQLContainer<?> postgres;

    static {
        postgres = new PostgreSQLContainer<>("postgres:16-alpine")
                .withDatabaseName("testdb")
                .withUsername("test")
                .withPassword("test");
        postgres.start();
    }

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
