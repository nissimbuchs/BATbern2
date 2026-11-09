package ch.batbern.shared.test;

import ch.batbern.shared.test.fixtures.ProductionShapedPeople;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestExecutionListeners;
import org.testcontainers.containers.PostgreSQLContainer;

import java.util.List;

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
 * Every test runs the service's production security chain and the real {@code EmailService}
 * against the {@code SesRecorder} (17.0b3). {@link FlushBeforeRollback} flushes a
 * {@code @Transactional} test before its rollback, so DB constraints, triggers and flush-time
 * validation run. Still prefer a non-transactional write IT that commits (see
 * docs/architecture/coding-standards.md, "Test fidelity rules").
 *
 * Usage:
 * ```java
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
// Epic 13 (SB4): re-apply springSecurity() to the auto-configured MockMvc — SB4 dropped the
// auto-config that used to do this, which otherwise drops the @WithMockUser SecurityContext and
// makes every @PreAuthorize request return 403. Inherited by all integration tests.
// Every 2xx JSON response must match the service's specs (response conformance gate, see
// ResponseConformance and docs/architecture/coding-standards.md "Test fidelity rules").
@Import({MockMvcSecuritySetup.class, ResponseConformanceSetup.class})
// 17.0b3: send the pending SQL before every rollback, so a constraint violation fails the test.
@TestExecutionListeners(listeners = FlushBeforeRollback.class,
        mergeMode = TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS)
public abstract class AbstractIntegrationTest {

    /**
     * The production-shaped people (Story 17.0d): unicode names ({@code Hông Ân}, {@code O’Neill},
     * {@code 王}), a mixed-case and an IDN e-mail, two identical full names and more than 20
     * "Matthias", each with the username the backend generates. Use it for every new or touched
     * test that stores, searches, sorts or pages people; see {@link ProductionShapedPeople} for the
     * named shapes ({@code VIETNAMESE}, {@code NAMESAKES}, ...).
     *
     * <p>Exposed, not seeded: the database of an IT stays empty until the test persists what it
     * needs, so existing row counts do not change.
     */
    protected static final List<ProductionShapedPeople.Person> PEOPLE = ProductionShapedPeople.ALL;

    /**
     * Shared PostgreSQL container for all tests using manual singleton pattern.
     * The container is started once and reused across all test classes.
     * It will be automatically cleaned up by Testcontainers Ryuk when the JVM exits.
     *
     * PostgreSQL 16 Alpine is used to match production environment.
     */
    private static final PostgreSQLContainer<?> POSTGRES;

    static {
        @SuppressWarnings("resource") // Container managed by Testcontainers Ryuk lifecycle
        PostgreSQLContainer<?> container = new PostgreSQLContainer<>("postgres:16-alpine")
                .withDatabaseName("testdb")
                .withUsername("test")
                .withPassword("test");
        POSTGRES = container;
        POSTGRES.start();
    }

    /**
     * Dynamically configure Spring datasource properties from the Testcontainers instance.
     * This overrides any static configuration in application-test.yml.
     */
    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }
}
