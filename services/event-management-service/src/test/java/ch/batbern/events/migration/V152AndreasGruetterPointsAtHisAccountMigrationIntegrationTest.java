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
 * V152 (owner decision 2026-11-09): BATbern 49 and 51 point at the organizer account
 * andreas.gruetter, whose first name is "Andy" (V151 matched by name and missed it).
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class V152AndreasGruetterPointsAtHisAccountMigrationIntegrationTest {

    private static final String HISTORY = "flyway_schema_history_event_management";

    private PostgreSQLContainer<?> postgres;
    private DriverManagerDataSource dataSource;
    private JdbcTemplate jdbc;

    @BeforeAll
    void startContainer() {
        @SuppressWarnings("resource") // stopped in @AfterAll
        PostgreSQLContainer<?> container = new PostgreSQLContainer<>("postgres:16-alpine")
                .withDatabaseName("v152testdb");
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
    void migrateToV151AndSeed() {
        jdbc.execute("DROP SCHEMA public CASCADE");
        jdbc.execute("CREATE SCHEMA public");
        migrate("151");
        // role_assignments belongs to company-user-management (shared database); no EMS stub has it
        jdbc.execute("CREATE TABLE role_assignments (user_id UUID NOT NULL REFERENCES user_profiles(id), "
                + "role VARCHAR(50) NOT NULL, PRIMARY KEY (user_id, role))");
        insertEvent(2, "daniel.kühni");
        insertEvent(54, "daniel.kühni");
        insertEvent(49, "andreas.grütter");
        insertEvent(60, "anna.muster");
    }

    @Test
    void should_pointAtAndreasGruetter_when_heHoldsTheOrganizerRole() {
        insertUser("andreas.gruetter", "Andy", "Grütter", "ORGANIZER");

        migrate("152");

        assertThat(organizers()).containsExactly("daniel.kühni", "andreas.gruetter", "daniel.kühni", "anna.muster");
    }

    @Test
    void should_keepTheOldValue_when_theAccountIsNoOrganizer() {
        insertUser("andreas.gruetter", "Andy", "Grütter", "SPEAKER");

        migrate("152");

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
