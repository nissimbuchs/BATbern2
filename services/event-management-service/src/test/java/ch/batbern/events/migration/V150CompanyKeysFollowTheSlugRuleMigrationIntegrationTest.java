package ch.batbern.events.migration;

import ch.batbern.shared.test.CompanyKeyChangesContract;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V150 (owner decision 2026-11-09, 17.0 deferred work): a registration's company follows its
 * company's new key. All services share one database and migrate in any order, so this test plays
 * the shared database: the {@code companies} stub of {@code db/testmigration} stands in for
 * company-user-management's table, and the two orders are run: event management migrates first (it computes the mapping), or
 * company-user-management already did (the mapping and the renamed companies are there).
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class V150CompanyKeysFollowTheSlugRuleMigrationIntegrationTest {

    private static final String HISTORY = "flyway_schema_history_event_management";

    private PostgreSQLContainer<?> postgres;
    private DriverManagerDataSource dataSource;
    private JdbcTemplate jdbc;
    private UUID eventId;

    @BeforeAll
    void startContainer() {
        @SuppressWarnings("resource") // stopped in @AfterAll
        PostgreSQLContainer<?> container = new PostgreSQLContainer<>("postgres:16-alpine")
                .withDatabaseName("v149testdb")
                .withUsername("v149test")
                .withPassword("v149test");
        container.start();
        postgres = container;
        dataSource = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        jdbc = new JdbcTemplate(dataSource);
    }

    @AfterAll
    void stopContainer() {
        if (postgres != null) {
            postgres.stop();
        }
    }

    @BeforeEach
    void migrateToV149AndSeed() {
        jdbc.execute("DROP SCHEMA public CASCADE");
        jdbc.execute("CREATE SCHEMA public");
        migrate("149");

        eventId = UUID.randomUUID();
        jdbc.update("INSERT INTO events (id, event_code, event_number, title, event_date, registration_deadline, "
                + "venue_name, venue_address, venue_capacity, organizer_username, event_type, workflow_state) "
                + "VALUES (?, 'BATbern149', 149, 'T', NOW(), NOW(), 'V', 'A', 100, 'anna.muster', 'evening', 'created')",
                eventId);
        insertRegistration("r1", "Swisscom AG");
        insertRegistration("r2", "testco");
        insertRegistration("r3", null);
    }

    @Test
    void should_computeTheMappingAndMoveRegistrations_when_eventManagementMigratesFirst() {
        jdbc.update("INSERT INTO companies (name) VALUES ('Swisscom AG'), ('swisscomag'), ('testco')");

        migrate("150");

        assertThat(jdbc.queryForObject(
                "SELECT new_key FROM company_key_changes WHERE old_key = 'Swisscom AG'", String.class))
                .isEqualTo("swisscomag2");
        assertThat(companiesOfRegistrations()).containsExactly("swisscomag2", "testco", null);
        // The companies table belongs to company-user-management (V28 renames it)
        assertThat(jdbc.queryForList("SELECT name FROM companies", String.class))
                .containsExactlyInAnyOrder("Swisscom AG", "swisscomag", "testco");
    }

    @Test
    void should_useTheMappingCompanyUserManagementRecorded_when_itMigratedFirst() {
        jdbc.update("INSERT INTO companies (name) VALUES ('swisscomag2'), ('swisscomag'), ('testco')");
        jdbc.execute("CREATE TABLE company_key_changes (old_key VARCHAR(255) PRIMARY KEY, "
                + "new_key VARCHAR(12) NOT NULL UNIQUE, changed_at TIMESTAMPTZ NOT NULL DEFAULT now())");
        jdbc.update("INSERT INTO company_key_changes (old_key, new_key) VALUES ('Swisscom AG', 'swisscomag2')");

        migrate("150");

        assertThat(jdbc.queryForObject("SELECT count(*) FROM company_key_changes", Integer.class)).isEqualTo(1);
        assertThat(companiesOfRegistrations()).containsExactly("swisscomag2", "testco", null);
    }

    @Test
    void should_changeNothing_when_noCompaniesTableExists() {
        jdbc.execute("DROP TABLE companies");

        migrate("150");

        assertThat(jdbc.queryForObject("SELECT count(*) FROM company_key_changes", Integer.class)).isZero();
        assertThat(companiesOfRegistrations()).containsExactly("Swisscom AG", "testco", null);
    }

    @Test
    void should_carryTheCanonicalReKeyBlock() {
        CompanyKeyChangesContract.verify("db/migration/V150__company_keys_follow_the_slug_rule.sql");
    }

    private List<String> companiesOfRegistrations() {
        return jdbc.queryForList(
                "SELECT attendee_company_id FROM registrations ORDER BY registration_code", String.class);
    }

    private void insertRegistration(String code, String companyId) {
        jdbc.update("INSERT INTO registrations (event_id, registration_code, attendee_username, status, "
                + "attendee_company_id) VALUES (?, ?, ?, 'confirmed', ?)", eventId, code, "user." + code, companyId);
    }

    private void migrate(String target) {
        Flyway.configure()
                .dataSource(dataSource)
                // testmigration: the user_profiles/companies stubs V116 and others join (shared database)
                .locations("classpath:db/migration", "classpath:db/testmigration")
                .table(HISTORY)
                .target(target)
                .load()
                .migrate();
    }
}
