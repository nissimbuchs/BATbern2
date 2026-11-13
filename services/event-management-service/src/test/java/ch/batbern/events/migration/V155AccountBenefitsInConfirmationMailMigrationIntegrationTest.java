package ch.batbern.events.migration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V155 (story "Visitors see what a BATbern account gives them"): the account block of the
 * registration-confirmation mail names the four concrete benefits and links the public page. The seed
 * service never updates a row, so the rows already in the database are rewritten by the migration, but
 * only where they still hold the shipped block. A row whose block an organizer edited stays as it is.
 *
 * <p>The test plays a database at V154 holding the shipped body (the classpath file as it was before this
 * story) and migrates to V155: the result must equal today's classpath file, so file and migration
 * cannot drift apart.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class V155AccountBenefitsInConfirmationMailMigrationIntegrationTest {

    private static final String HISTORY = "flyway_schema_history_event_management";

    static final String OLD_BLOCK_EN = """
            <div class="info-box">
              <h3>Create a Free Account</h3>
              <ul>
                <li>Manage registrations</li>
                <li>Receive event updates</li>
                <li>Access exclusive content</li>
              </ul>
              <a href="{{createAccountUrl}}" class="btn btn-primary">Create Free Account</a>
            </div>""";

    static final String OLD_BLOCK_DE = """
            <div class="info-box">
              <h3>Kostenloses Konto erstellen</h3>
              <ul>
                <li>Registrierungen verwalten</li>
                <li>Event-Updates erhalten</li>
                <li>Exklusive Inhalte entdecken</li>
              </ul>
              <a href="{{createAccountUrl}}" class="btn btn-primary">Konto erstellen</a>
            </div>""";

    private PostgreSQLContainer<?> postgres;
    private DriverManagerDataSource dataSource;
    private JdbcTemplate jdbc;

    @BeforeAll
    void startContainer() {
        @SuppressWarnings("resource") // stopped in @AfterAll
        PostgreSQLContainer<?> container = new PostgreSQLContainer<>("postgres:16-alpine")
                .withDatabaseName("v155testdb");
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
    void migrateToV154() {
        jdbc.execute("DROP SCHEMA public CASCADE");
        jdbc.execute("CREATE SCHEMA public");
        migrate("154");
    }

    @Test
    void should_writeTheFourBenefits_when_rowsHoldTheShippedBlock() throws IOException {
        for (String locale : new String[] {"de", "en"}) {
            insert(locale, shippedBody(locale));
        }

        migrate("155");

        for (String locale : new String[] {"de", "en"}) {
            assertThat(body(locale)).as("migrated %s body equals the classpath file", locale)
                    .isEqualTo(currentBody(locale));
        }
        assertThat(body("en")).contains("What a free account gives you")
                .contains("{{baseUrl}}/why-an-account")
                .doesNotContain("Access exclusive content");
        assertThat(body("de")).contains("Was Ihnen ein kostenloses Konto bietet")
                .contains("{{baseUrl}}/why-an-account")
                .doesNotContain("Exklusive Inhalte entdecken");
    }

    @Test
    void should_leaveTheRowAlone_when_anOrganizerEditedTheBlock() throws IOException {
        String editedDe = shippedBody("de").replace("<li>Event-Updates erhalten</li>",
                "<li>Event-Updates per SMS erhalten</li>");
        String editedEn = shippedBody("en").replace("Create Free Account</a>", "Sign up</a>");
        insert("de", editedDe);
        insert("en", editedEn);

        migrate("155");

        assertThat(body("de")).isEqualTo(editedDe);
        assertThat(body("en")).isEqualTo(editedEn);
    }

    @Test
    void should_changeOnlyTheAccountBlock_when_restOfTheMailWasEdited() throws IOException {
        String edited = shippedBody("en").replace("We look forward to seeing you at the event!",
                "See you soon!");
        insert("en", edited);

        migrate("155");

        assertThat(body("en")).isEqualTo(currentBody("en").replace(
                "We look forward to seeing you at the event!", "See you soon!"));
    }

    @Test
    void should_doNothing_when_noRowsExistYet() {
        migrate("155");

        assertThat(jdbc.queryForObject("SELECT count(*) FROM email_templates WHERE template_key = "
                + "'registration-confirmation'", Integer.class)).isZero();
    }

    /** The body as the seeder stores it today: the classpath file without its subject comment. */
    private static String currentBody(String locale) throws IOException {
        String raw = new ClassPathResource("email-templates/registration-confirmation-" + locale + ".html")
                .getContentAsString(StandardCharsets.UTF_8);
        return raw.replaceFirst("^<!--\\s*subject:\\s*(.+?)\\s*-->\\r?\\n?", "");
    }

    /** The body shipped before this story: today's file with the account block as it was. */
    private static String shippedBody(String locale) throws IOException {
        String current = currentBody(locale);
        int start = current.indexOf("<div class=\"info-box\">");
        int end = current.indexOf("</div>", start) + "</div>".length();
        assertThat(start).as("classpath %s template has an info-box", locale).isNotNegative();
        return current.substring(0, start) + ("de".equals(locale) ? OLD_BLOCK_DE : OLD_BLOCK_EN)
                + current.substring(end);
    }

    private void insert(String locale, String body) {
        jdbc.update("INSERT INTO email_templates (template_key, category, locale, subject, html_body, layout_key) "
                + "VALUES ('registration-confirmation', 'REGISTRATION', ?, 's', ?, 'batbern-default')", locale, body);
    }

    private String body(String locale) {
        return jdbc.queryForObject("SELECT html_body FROM email_templates WHERE template_key = "
                + "'registration-confirmation' AND locale = ?", String.class, locale);
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
