package ch.batbern.events.migration;

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
 * V151 (owner decision 2026-11-09): historical events whose organizer username carries an umlaut
 * point at the account of that person, when exactly one organizer account has the name.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class V151HistoricalOrganizersPointAtTheirAccountsMigrationIntegrationTest {

    private static final String HISTORY = "flyway_schema_history_event_management";

    private PostgreSQLContainer<?> postgres;
    private DriverManagerDataSource dataSource;
    private JdbcTemplate jdbc;

    @BeforeAll
    void startContainer() {
        @SuppressWarnings("resource") // stopped in @AfterAll
        PostgreSQLContainer<?> container = new PostgreSQLContainer<>("postgres:16-alpine")
                .withDatabaseName("v151testdb")
                .withUsername("v151test")
                .withPassword("v151test");
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
    void migrateToV150AndSeed() {
        jdbc.execute("DROP SCHEMA public CASCADE");
        jdbc.execute("CREATE SCHEMA public");
        migrate("150");
        // role_assignments belongs to company-user-management (shared database); no EMS stub has it
        jdbc.execute("CREATE TABLE role_assignments (user_id UUID NOT NULL REFERENCES user_profiles(id), "
                + "role VARCHAR(50) NOT NULL, PRIMARY KEY (user_id, role))");
        insertEvent(2, "daniel.kühni");
        insertEvent(54, "daniel.kühni");
        insertEvent(49, "andreas.grütter");
        insertEvent(60, "anna.muster");
    }

    @Test
    void should_pointAtTheOrganizerAccount_when_exactlyOneOrganizerHasTheName() {
        insertUser("daniel.kuehni", "Daniel", "Kühni", "ORGANIZER");
        insertUser("daniel.kuehni.2", "Daniel", "Kuehni", "ATTENDEE");
        insertUser("andreas.gruetter", "Andreas", "Gruetter", "ORGANIZER");

        migrate("151");

        assertThat(organizers()).containsExactly("daniel.kuehni", "andreas.gruetter", "daniel.kuehni", "anna.muster");
    }

    @Test
    void should_keepTheOldValue_when_noOrSeveralAccountsMatch() {
        insertUser("andreas.gruetter", "Andreas", "Grütter", "ORGANIZER");
        insertUser("andreas.gruetter.2", "Andreas", "Grutter", "ORGANIZER");
        insertUser("daniel.kuehni", "Daniel", "Kühni", "SPEAKER");

        migrate("151");

        assertThat(organizers()).containsExactly("daniel.kühni", "andreas.grütter", "daniel.kühni", "anna.muster");
    }

    private List<String> organizers() {
        return jdbc.queryForList("SELECT organizer_username FROM events ORDER BY event_number", String.class);
    }

    private void insertEvent(int number, String organizer) {
        jdbc.update("INSERT INTO events (id, event_code, event_number, title, event_date, registration_deadline, "
                + "venue_name, venue_address, venue_capacity, organizer_username, event_type, workflow_state) "
                + "VALUES (?, ?, ?, 'T', NOW(), NOW(), 'V', 'A', 100, ?, 'evening', 'archived')",
                UUID.randomUUID(), "BATbern" + number, number, organizer);
    }

    private void insertUser(String username, String firstName, String lastName, String role) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO user_profiles (id, username, first_name, last_name) VALUES (?, ?, ?, ?)",
                id, username, firstName, lastName);
        jdbc.update("INSERT INTO role_assignments (user_id, role) VALUES (?, ?)", id, role);
    }

    private void migrate(String target) {
        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration", "classpath:db/testmigration")
                .table(HISTORY)
                .target(target)
                .load()
                .migrate();
    }
}
