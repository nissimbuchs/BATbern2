package ch.batbern.companyuser.migration;

import ch.batbern.shared.service.SlugGenerationService;
import ch.batbern.shared.test.CompanyKeyChangesContract;
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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V28 (owner decision 2026-11-09, 17.0 deferred work): every company key follows the slug rule
 * {@code ^[a-zA-Z0-9]{1,12}$}. Seeds keys as {@code POST /companies} stored them before (spaces,
 * longer than 12, umlauts, colliding slugs) together with every company-user-management reference,
 * runs V28, and checks the new keys against the Java rule ({@link SlugGenerationService}) and that
 * each reference followed its company.
 *
 * <p>Own container (not the shared singleton) so the test controls the target version: migrate to
 * V27, seed, then V28.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class V28CompanyKeysFollowTheSlugRuleMigrationIntegrationTest {

    private static final String HISTORY = "flyway_schema_history_company_user_management";

    /** Old keys as stored before V28, in the order the migration maps them (byte order). */
    private static final List<String> VIOLATING = List.of(
            "!!",
            "Gamma Innovations 1769018790753",
            "Müller & Söhne AG",
            "beckerinformatik",
            "waeberinformaitk",
            "waeberinformatik");

    /** Compliant keys: untouched, and taken for the collision rule. */
    private static final List<String> COMPLIANT = List.of("ABC", "beckerinform", "testco");

    private PostgreSQLContainer<?> postgres;
    private DriverManagerDataSource dataSource;
    private JdbcTemplate jdbc;
    private final SlugGenerationService slugs = new SlugGenerationService();

    @BeforeAll
    void startContainer() {
        @SuppressWarnings("resource") // stopped in @AfterAll
        PostgreSQLContainer<?> container = new PostgreSQLContainer<>("postgres:16-alpine")
                .withDatabaseName("v28testdb");
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
    void migrateToV27AndSeed() {
        jdbc.execute("DROP SCHEMA public CASCADE");
        jdbc.execute("CREATE SCHEMA public");
        migrate("27");

        for (String name : VIOLATING) {
            insertCompany(name);
        }
        for (String name : COMPLIANT) {
            insertCompany(name);
        }
        UUID userId = UUID.randomUUID();
        jdbc.update("INSERT INTO user_profiles (id, username, email, first_name, last_name, company_id) "
                + "VALUES (?, 'anna.muster', 'anna@example.com', 'Anna', 'Muster', 'testco')", userId);
        jdbc.update("INSERT INTO logos (upload_id, s3_key, file_extension, file_size, mime_type, status, "
                + "associated_entity_type, associated_entity_id) "
                + "VALUES ('up-1', 'logos/companies/gamma.png', 'png', 100, 'image/png', 'ASSOCIATED', 'COMPANY', ?)",
                "Gamma Innovations 1769018790753");
        // Another entity type with the same id is no company reference
        jdbc.update("INSERT INTO logos (upload_id, s3_key, file_extension, file_size, mime_type, status, "
                + "associated_entity_type, associated_entity_id) "
                + "VALUES ('up-2', 'logos/events/x.png', 'png', 100, 'image/png', 'ASSOCIATED', 'EVENT', ?)",
                "Gamma Innovations 1769018790753");
        jdbc.update("INSERT INTO company_logo_requests (requester_username, company_name, new_logo_upload_id, "
                + "new_logo_url) VALUES ('anna.muster', 'waeberinformatik', 'up-3', 'https://cdn.example.com/w.png')");
        jdbc.update("INSERT INTO activity_history (user_id, activity_type, entity_type, entity_id, description) "
                + "VALUES (?, 'COMPANY_UPDATE', 'COMPANY', 'Müller & Söhne AG', 'x')", userId);
        jdbc.update("INSERT INTO activity_history (user_id, activity_type, entity_type, entity_id, description) "
                + "VALUES (?, 'LOGIN', 'USER', 'Müller & Söhne AG', 'x')", userId);
    }

    @Test
    void should_giveEveryCompanyAKeyByTheJavaSlugRule_when_v28Runs() {
        migrate("28");

        Map<String, String> expected = expectedKeysByJavaRule();
        assertThat(mapping()).containsExactlyInAnyOrderEntriesOf(expected);
        assertThat(expected).containsEntry("Gamma Innovations 1769018790753", "gammainnovat")
                .containsEntry("Müller & Söhne AG", "muellersoehn")
                .containsEntry("beckerinformatik", "beckerinfor2")
                .containsEntry("waeberinformaitk", "waeberinform")
                .containsEntry("waeberinformatik", "waeberinfor2")
                .containsEntry("!!", "company");

        List<String> keys = jdbc.queryForList("SELECT name FROM companies", String.class);
        assertThat(keys).hasSize(VIOLATING.size() + COMPLIANT.size())
                .allMatch(k -> k.matches(SlugGenerationService.COMPANY_NAME_PATTERN))
                .containsAll(COMPLIANT)
                .containsAll(expected.values());
        // The display name is untouched
        assertThat(jdbc.queryForObject("SELECT display_name FROM companies WHERE name = 'gammainnovat'", String.class))
                .isEqualTo("Display of Gamma Innovations 1769018790753");
    }

    @Test
    void should_moveEveryReferenceWithItsCompany_when_v28Runs() {
        migrate("28");

        assertThat(jdbc.queryForObject(
                "SELECT associated_entity_id FROM logos WHERE upload_id = 'up-1'", String.class))
                .isEqualTo("gammainnovat");
        assertThat(jdbc.queryForObject(
                "SELECT associated_entity_id FROM logos WHERE upload_id = 'up-2'", String.class))
                .isEqualTo("Gamma Innovations 1769018790753");
        assertThat(jdbc.queryForObject("SELECT company_name FROM company_logo_requests", String.class))
                .isEqualTo("waeberinfor2");
        assertThat(jdbc.queryForList(
                "SELECT entity_type || ':' || entity_id FROM activity_history", String.class))
                .containsExactlyInAnyOrder("COMPANY:muellersoehn", "USER:Müller & Söhne AG");
        assertThat(jdbc.queryForObject("SELECT company_id FROM user_profiles", String.class)).isEqualTo("testco");
    }

    @Test
    void should_changeNothing_when_runAgain() throws IOException {
        migrate("28");
        Map<String, String> first = mapping();
        List<String> keysAfterFirst = jdbc.queryForList("SELECT name FROM companies ORDER BY name", String.class);

        jdbc.execute(new ClassPathResource("db/migration/V28__company_keys_follow_the_slug_rule.sql")
                .getContentAsString(StandardCharsets.UTF_8));

        assertThat(mapping()).isEqualTo(first);
        assertThat(jdbc.queryForList("SELECT name FROM companies ORDER BY name", String.class))
                .isEqualTo(keysAfterFirst);
    }

    @Test
    void should_carryTheCanonicalReKeyBlock() {
        CompanyKeyChangesContract.verify("db/migration/V28__company_keys_follow_the_slug_rule.sql");
    }

    /** The Java rule applied in the migration's order: slug, then the first free key. */
    private Map<String, String> expectedKeysByJavaRule() {
        Set<String> taken = new HashSet<>(COMPLIANT);
        Map<String, String> expected = new LinkedHashMap<>();
        for (String old : VIOLATING) {
            String base = old.equals("!!") ? "company" : slugs.generateCompanyName(old);
            String key = slugs.ensureUniqueCompanyName(base, taken::contains);
            taken.add(key);
            expected.put(old, key);
        }
        return expected;
    }

    private Map<String, String> mapping() {
        Map<String, String> result = new LinkedHashMap<>();
        jdbc.query("SELECT old_key, new_key FROM company_key_changes ORDER BY old_key",
                rs -> {
                    result.put(rs.getString(1), rs.getString(2));
                });
        return result;
    }

    private void insertCompany(String name) {
        jdbc.update("INSERT INTO companies (name, display_name, created_by) VALUES (?, ?, 'test')",
                name, "Display of " + name);
    }

    private void migrate(String target) {
        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .table(HISTORY)
                .target(target)
                .load()
                .migrate();
    }
}
