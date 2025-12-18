package ch.batbern.speakers;

import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Abstract base class for integration tests using Testcontainers PostgreSQL.
 *
 * Story 5.4: Speaker Status Management
 *
 * This ensures production parity by running tests against a real PostgreSQL database
 * instead of H2, which allows us to:
 * - Test PostgreSQL-specific features (JSONB, VARCHAR constraints, etc.)
 * - Verify Flyway migrations work correctly (V19 for speaker_status_history)
 * - Catch database-specific issues early
 *
 * The container is a TRUE SINGLETON shared across ALL test classes for performance.
 *
 * IMPORTANT: We use a static initializer block for manual singleton management.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

    /**
     * Shared PostgreSQL container for ALL tests - true singleton pattern.
     * Started once in static block, stopped only at JVM shutdown.
     */
    static final PostgreSQLContainer<?> POSTGRES;

    static {
        // Initialize singleton container once for entire test suite
        @SuppressWarnings("resource") // Container managed by Testcontainers Ryuk lifecycle
        PostgreSQLContainer<?> container = new PostgreSQLContainer<>("postgres:16-alpine")
                .withDatabaseName("testdb")
                .withUsername("test")
                .withPassword("test");
        POSTGRES = container;

        // Start container once
        POSTGRES.start();

        // Register shutdown hook to stop container only when JVM exits
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            POSTGRES.stop();
        }));
    }

    /**
     * Dynamically configure Spring datasource properties from the Testcontainers instance.
     */
    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }
}
