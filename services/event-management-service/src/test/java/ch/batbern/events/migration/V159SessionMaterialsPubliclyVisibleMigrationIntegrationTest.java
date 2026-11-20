package ch.batbern.events.migration;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V159 (#1260): {@code session_materials.publicly_visible}. Existing PDFs (by extension or MIME type,
 * any case) stay public, every other type becomes internal, and the migration names the non-PDF count
 * per event in a NOTICE. Owns its container so it can stop at V158, seed, then apply V159.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("V159 session materials publicly_visible migration")
class V159SessionMaterialsPubliclyVisibleMigrationIntegrationTest {

    private PostgreSQLContainer<?> postgres;
    private JdbcTemplate jdbcTemplate;
    private DriverManagerDataSource dataSource;
    private ListAppender<ILoggingEvent> flywayLog;

    private UUID pdf;
    private UUID upperCasePdf;
    private UUID pdfByMimeOnly;
    private UUID pptx;
    private UUID otherEventKeynote;

    @BeforeAll
    void startContainer() {
        @SuppressWarnings("resource")  // Container lifecycle managed in @AfterAll.
        PostgreSQLContainer<?> container = new PostgreSQLContainer<>("postgres:16-alpine")
                .withDatabaseName("v159testdb");
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
        migrateTo("158");

        UUID talk = insertSession(insertEvent("BATbernV159A", 9159), "BATbernV159A", "talk");
        UUID keynote = insertSession(insertEvent("BATbernV159B", 9160), "BATbernV159B", "keynote");
        pdf = insertMaterial(talk, "slides.pdf", "pdf", "application/pdf");
        upperCasePdf = insertMaterial(talk, "HANDOUT.PDF", "PDF", "application/octet-stream");
        pdfByMimeOnly = insertMaterial(talk, "export", "bin", "application/pdf");
        pptx = insertMaterial(talk, "slides.pptx", "pptx",
                "application/vnd.openxmlformats-officedocument.presentationml.presentation");
        otherEventKeynote = insertMaterial(keynote, "talk.key", "key", "application/vnd.apple.keynote");

        flywayLog = new ListAppender<>();
        flywayLog.start();
        ((Logger) LoggerFactory.getLogger("org.flywaydb")).addAppender(flywayLog);
    }

    @AfterEach
    void detachLog() {
        ((Logger) LoggerFactory.getLogger("org.flywaydb")).detachAppender(flywayLog);
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

    private UUID insertEvent(String code, int number) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO events (id, event_code, event_number, title, description, "
                        + "event_date, registration_deadline, venue_name, venue_address, venue_capacity, "
                        + "organizer_username, event_type, workflow_state, created_at, updated_at) "
                        + "VALUES (?, ?, ?, 'V159 Test Event', 'Test', NOW() - interval '30 days', "
                        + "NOW() - interval '40 days', 'Test Venue', 'Test Address', 100, "
                        + "'organizer.v159', 'evening', 'created', NOW(), NOW())",
                id, code, number);
        return id;
    }

    private UUID insertSession(UUID eventId, String eventCode, String slug) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO sessions (id, event_id, event_code, session_slug, title, session_type, "
                        + "created_at, updated_at) VALUES (?, ?, ?, ?, ?, 'presentation', NOW(), NOW())",
                id, eventId, eventCode, slug + "-" + id, slug);
        return id;
    }

    private UUID insertMaterial(UUID sessionId, String fileName, String extension, String mimeType) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO session_materials (id, session_id, upload_id, s3_key, cloudfront_url, file_name, "
                        + "file_extension, file_size, mime_type, material_type, uploaded_by) "
                        + "VALUES (?, ?, ?, 'k', 'https://cdn.example.com/k', ?, ?, 1, ?, 'PRESENTATION', "
                        + "'speaker.v159')",
                id, sessionId, "upl-" + id, fileName, extension, mimeType);
        return id;
    }

    private Boolean visible(UUID materialId) {
        return jdbcTemplate.queryForObject(
                "SELECT publicly_visible FROM session_materials WHERE id = ?", Boolean.class, materialId);
    }

    @Test
    @DisplayName("PDFs by extension or MIME type, any case, stay public; other types become internal")
    void should_keepPdfsPublic_andHideOtherTypes() {
        migrateTo("159");

        assertThat(visible(pdf)).isTrue();
        assertThat(visible(upperCasePdf)).isTrue();
        assertThat(visible(pdfByMimeOnly)).isTrue();
        assertThat(visible(pptx)).isFalse();
        assertThat(visible(otherEventKeynote)).isFalse();
    }

    @Test
    @DisplayName("the column is NOT NULL, and a row inserted without it is internal")
    void should_defaultToInternal_forRowsInsertedWithoutTheFlag() {
        migrateTo("159");

        String nullable = jdbcTemplate.queryForObject("SELECT is_nullable FROM information_schema.columns "
                + "WHERE table_name = 'session_materials' AND column_name = 'publicly_visible'", String.class);
        assertThat(nullable).isEqualTo("NO");
        UUID session = jdbcTemplate.queryForObject(
                "SELECT session_id FROM session_materials WHERE id = ?", UUID.class, pdf);
        assertThat(visible(insertMaterial(session, "late.pdf", "pdf", "application/pdf"))).isFalse();
    }

    @Test
    @DisplayName("NOTICE names the non-PDF count per event and in total")
    void should_noticeNonPdfCountPerEvent() {
        migrateTo("159");

        assertThat(flywayLog.list).extracting(ILoggingEvent::getFormattedMessage)
                .anySatisfy(m -> assertThat(m).contains("V159: event BATbernV159A has 1 non-PDF material(s)"))
                .anySatisfy(m -> assertThat(m).contains("V159: event BATbernV159B has 1 non-PDF material(s)"))
                .anySatisfy(m -> assertThat(m).contains("V159: 2 non-PDF material(s) in total"));
    }
}
