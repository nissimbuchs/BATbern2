package ch.batbern.events.migration;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.internal.sqlscript.DefaultSqlScriptExecutor;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V157 (#1287, owner option 1): outreach history rows written before Story 4.10 hold the
 * organizer's e-mail address in {@code organizer_username}. Each address moves to the username of
 * the one account that owns it (primary or verified additional address, any case); everything
 * else stays as it is.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class V157OutreachHistoryNamesTheOrganizerByUsernameMigrationIntegrationTest {

    private static final String HISTORY = "flyway_schema_history_event_management";

    private PostgreSQLContainer<?> postgres;
    private DriverManagerDataSource dataSource;
    private JdbcTemplate jdbc;
    private UUID speakerId;

    @BeforeAll
    void startContainer() {
        @SuppressWarnings("resource") // stopped in @AfterAll
        PostgreSQLContainer<?> container = new PostgreSQLContainer<>("postgres:16-alpine")
                .withDatabaseName("v157testdb");
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
    void migrateToV156() {
        jdbc.execute("DROP SCHEMA public CASCADE");
        jdbc.execute("CREATE SCHEMA public");
        migrate("156");
        UUID eventId = UUID.randomUUID();
        jdbc.update("INSERT INTO events (id, event_code, event_number, title, event_date, registration_deadline, "
                + "venue_name, venue_address, venue_capacity, organizer_username, event_type, workflow_state) "
                + "VALUES (?, 'BATbern70', 70, 'T', NOW(), NOW(), 'V', 'A', 100, 'org', 'evening', 'created')",
                eventId);
        speakerId = UUID.randomUUID();
        jdbc.update("INSERT INTO speaker_pool (id, event_id, speaker_name, status, created_at, updated_at) "
                + "VALUES (?, ?, 'S', 'identified', NOW(), NOW())", speakerId, eventId);
    }

    @Test
    void should_nameTheOwningAccount_when_addressMatchesExactlyOneAccount() {
        withCumsAddressTables();
        insertUser("anna.muster", "anna@example.com");
        UUID beat = insertUser("beat.beispiel", "beat@example.com");
        insertAdditional(beat, "Beat.Work@Example.org", true);
        UUID carla = insertUser("carla.unverified", "carla@example.com");
        insertAdditional(carla, "carla.other@example.com", false);
        // CUMS keeps an additional address off every primary one (V16 trigger), but its unique
        // index on the primary address is case-sensitive: two primaries differing in case is the
        // ambiguity production can hold.
        insertUser("dora.one", "shared@example.com");
        insertUser("eva.two", "Shared@Example.com");

        insertOutreach("ANNA@example.com");
        insertOutreach("beat.work@example.org");
        insertOutreach("nobody@example.com");
        insertOutreach("carla.other@example.com");
        insertOutreach("shared@example.com");
        insertOutreach("already.username");

        ListAppender<ILoggingEvent> flywayLog = captureFlywayLog();
        migrate("157");

        assertThat(flywayLog.list).extracting(ILoggingEvent::getFormattedMessage).anySatisfy(line -> assertThat(line)
                .contains("V157: 2 outreach rows now name the organizer by username, 3 rows with an address left"));
        assertThat(organizers()).containsExactly(
                "anna.muster",
                "beat.beispiel",
                "nobody@example.com",
                "carla.other@example.com",
                "shared@example.com",
                "already.username");
    }

    @Test
    void should_leaveEveryRow_when_cumsAddressTablesAreMissing() {
        insertOutreach("anna@example.com");

        migrate("157");

        assertThat(organizers()).containsExactly("anna@example.com");
    }

    /** The NOTICE with both counts is how production reports them; Flyway logs it at INFO. */
    private ListAppender<ILoggingEvent> captureFlywayLog() {
        Logger logger = (Logger) LoggerFactory.getLogger(DefaultSqlScriptExecutor.class);
        logger.setLevel(Level.INFO);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        return appender;
    }

    /** The EMS test stub of user_profiles has no address; production has CUMS's full tables. */
    private void withCumsAddressTables() {
        jdbc.execute("ALTER TABLE user_profiles ADD COLUMN email VARCHAR(255)");
        jdbc.execute("CREATE TABLE user_additional_emails (id UUID PRIMARY KEY DEFAULT gen_random_uuid(), "
                + "user_id UUID NOT NULL REFERENCES user_profiles(id), email VARCHAR(255) NOT NULL, "
                + "verified_at TIMESTAMP WITH TIME ZONE)");
    }

    private UUID insertUser(String username, String email) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO user_profiles (id, username, email) VALUES (?, ?, ?)", id, username, email);
        return id;
    }

    private void insertAdditional(UUID userId, String email, boolean verified) {
        jdbc.update("INSERT INTO user_additional_emails (user_id, email, verified_at) VALUES (?, ?, "
                + (verified ? "NOW()" : "NULL") + ")", userId, email);
    }

    private void insertOutreach(String organizer) {
        jdbc.update("INSERT INTO speaker_outreach_history (speaker_pool_id, contact_date, contact_method, "
                + "organizer_username, created_at) VALUES (?, NOW(), 'email', ?, clock_timestamp())",
                speakerId, organizer);
    }

    private List<String> organizers() {
        return jdbc.queryForList(
                "SELECT organizer_username FROM speaker_outreach_history ORDER BY created_at", String.class);
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
