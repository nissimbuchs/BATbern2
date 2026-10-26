package ch.batbern.events.migration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration test for V127__unify_session_type_and_materials_status.sql (#1123).
 *
 * <p>V122 added {@code aperitif} as a second value for the same slot as {@code networking}, and
 * the V41 trigger stores {@code PARTIAL} for a session whose files include no PRESENTATION. V127
 * folds both back: {@code networking} is the one apéro type, and a session with any file is
 * {@code COMPLETE}. Like the V93 test, it owns its container so it can stop at V126, seed the old
 * shapes, then apply V127.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("V127 session type + materials status migration")
class V127SessionTypeAndMaterialsStatusMigrationIntegrationTest {

    private PostgreSQLContainer<?> postgres;
    private JdbcTemplate jdbcTemplate;
    private DriverManagerDataSource dataSource;

    private UUID eventId;
    private UUID aperitifSessionId;
    private UUID pdfOnlySessionId;

    @BeforeAll
    void startContainer() {
        @SuppressWarnings("resource")  // Container lifecycle managed in @AfterAll.
        PostgreSQLContainer<?> container = new PostgreSQLContainer<>("postgres:16-alpine")
                .withDatabaseName("v127testdb")
                .withUsername("v127test")
                .withPassword("v127test");
        container.start();
        this.postgres = container;
        this.dataSource = new DriverManagerDataSource(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        this.jdbcTemplate = new JdbcTemplate(dataSource);
    }

    @AfterAll
    void stopContainer() {
        if (postgres != null) {
            postgres.stop();
        }
    }

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("DROP SCHEMA public CASCADE");
        jdbcTemplate.execute("CREATE SCHEMA public");
        migrateTo("126");

        eventId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO events (id, event_code, event_number, title, description, "
                        + "event_date, registration_deadline, venue_name, venue_address, venue_capacity, "
                        + "organizer_username, event_type, workflow_state, created_at, updated_at) "
                        + "VALUES (?, 'BATbernV127', 9127, 'V127 Test Event', 'Test', NOW() + interval '30 days', "
                        + "NOW() + interval '20 days', 'Test Venue', 'Test Address', 100, "
                        + "'organizer.v127', 'evening', 'created', NOW(), NOW())",
                eventId);

        aperitifSessionId = insertSession("apero", "aperitif");
        pdfOnlySessionId = insertSession("talk-with-pdf", "presentation");
        // V41 trigger: files but no PRESENTATION → PARTIAL (the rule V127 removes).
        insertMaterial(pdfOnlySessionId, "DOCUMENT");
    }

    private void migrateTo(String version) {
        Flyway.configure()
                .dataSource(dataSource)
                // testmigration: user_profiles/companies stubs that V116 and others join (cross-service tables)
                .locations("classpath:db/migration", "classpath:db/testmigration")
                .table("flyway_schema_history_event_management")
                .target(version)
                .load()
                .migrate();
    }

    private UUID insertSession(String slug, String sessionType) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO sessions (id, event_id, event_code, session_slug, title, session_type, "
                        + "created_at, updated_at) VALUES (?, ?, 'BATbernV127', ?, ?, ?, NOW(), NOW())",
                id, eventId, slug + "-" + id, slug, sessionType);
        return id;
    }

    private void insertMaterial(UUID sessionId, String materialType) {
        String uploadId = "upl-" + UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO session_materials (session_id, upload_id, s3_key, cloudfront_url, file_name, "
                        + "file_extension, file_size, mime_type, material_type, uploaded_by) "
                        + "VALUES (?, ?, 'k', 'https://cdn.example.com/k', 'f.pdf', 'pdf', 1, "
                        + "'application/pdf', ?, 'speaker.v127')",
                sessionId, uploadId, materialType);
    }

    private String column(String column, UUID sessionId) {
        return jdbcTemplate.queryForObject(
                "SELECT " + column + " FROM sessions WHERE id = ?", String.class, sessionId);
    }

    @Test
    @DisplayName("seed reproduces the pre-V127 shapes (aperitif stored, PARTIAL computed)")
    void should_haveOldShapes_beforeV127() {
        assertThat(column("session_type", aperitifSessionId)).isEqualTo("aperitif");
        assertThat(column("materials_status", pdfOnlySessionId)).isEqualTo("PARTIAL");
    }

    @Test
    @DisplayName("aperitif sessions become networking")
    void should_rewriteAperitifToNetworking() {
        migrateTo("127");

        assertThat(column("session_type", aperitifSessionId)).isEqualTo("networking");
    }

    @Test
    @DisplayName("aperitif is no longer an allowed session type")
    void should_rejectAperitif_afterV127() {
        migrateTo("127");

        assertThatThrownBy(() -> insertSession("late-apero", "aperitif"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("sessions_session_type_check");
    }

    @Test
    @DisplayName("PARTIAL sessions become COMPLETE")
    void should_rewritePartialToComplete() {
        migrateTo("127");

        assertThat(column("materials_status", pdfOnlySessionId)).isEqualTo("COMPLETE");
    }

    @Test
    @DisplayName("trigger: any file makes the session COMPLETE, none makes it NONE")
    void should_markCompleteOnAnyMaterial_afterV127() {
        migrateTo("127");
        UUID fresh = insertSession("fresh-talk", "presentation");

        insertMaterial(fresh, "DOCUMENT");
        assertThat(column("materials_status", fresh)).isEqualTo("COMPLETE");

        jdbcTemplate.update("DELETE FROM session_materials WHERE session_id = ?", fresh);
        assertThat(column("materials_status", fresh)).isEqualTo("NONE");
    }

    @Test
    @DisplayName("PARTIAL is no longer an allowed materials status")
    void should_rejectPartial_afterV127() {
        migrateTo("127");

        assertThatThrownBy(() -> jdbcTemplate.update(
                "UPDATE sessions SET materials_status = 'PARTIAL' WHERE id = ?", pdfOnlySessionId))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("sessions_materials_status_check");
    }
}
