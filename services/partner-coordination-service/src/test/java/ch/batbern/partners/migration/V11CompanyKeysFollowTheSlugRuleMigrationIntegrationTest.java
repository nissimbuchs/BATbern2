package ch.batbern.partners.migration;

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

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V11 (owner decision 2026-11-09, 17.0 deferred work): a partner, its topic suggestions and its
 * votes follow their company's new key. All services share one database and migrate in any order, so this test plays
 * the shared database: a minimal {@code companies} table stands in for company-user-management's,
 * and the two orders are run: partner coordination migrates first (it computes the mapping), or
 * company-user-management already did (the mapping and the renamed companies are there).
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class V11CompanyKeysFollowTheSlugRuleMigrationIntegrationTest {

    private static final String HISTORY = "flyway_schema_history_partner_coordination";

    private PostgreSQLContainer<?> postgres;
    private DriverManagerDataSource dataSource;
    private JdbcTemplate jdbc;
    private UUID topicId;

    @BeforeAll
    void startContainer() {
        @SuppressWarnings("resource") // stopped in @AfterAll
        PostgreSQLContainer<?> container = new PostgreSQLContainer<>("postgres:16-alpine")
                .withDatabaseName("v11testdb")
                .withUsername("v11test")
                .withPassword("v11test");
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
    void migrateToV10AndSeed() {
        jdbc.execute("DROP SCHEMA public CASCADE");
        jdbc.execute("CREATE SCHEMA public");
        migrate("10");

        jdbc.update("INSERT INTO partners (company_name, partnership_level, partnership_start_date) "
                + "VALUES ('Swisscom AG', 'GOLD', CURRENT_DATE), ('testco', 'SILVER', CURRENT_DATE)");
        topicId = UUID.randomUUID();
        jdbc.update("INSERT INTO topic_suggestions (id, company_name, suggested_by, title) "
                + "VALUES (?, 'Swisscom AG', 'anna.muster', 'Platform engineering')", topicId);
        jdbc.update("INSERT INTO topic_votes (topic_id, company_name) VALUES (?, 'Swisscom AG'), (?, 'testco')",
                topicId, topicId);
    }

    @Test
    void should_computeTheMappingAndMovePartnerReferences_when_partnerCoordinationMigratesFirst() {
        jdbc.execute("CREATE TABLE companies (name VARCHAR(255) PRIMARY KEY)");
        jdbc.update("INSERT INTO companies (name) VALUES ('Swisscom AG'), ('swisscomag'), ('testco')");

        migrate("11");

        assertThat(jdbc.queryForObject(
                "SELECT new_key FROM company_key_changes WHERE old_key = 'Swisscom AG'", String.class))
                .isEqualTo("swisscomag2");
        assertMovedReferences();
        // The companies table belongs to company-user-management (V28 renames it)
        assertThat(jdbc.queryForList("SELECT name FROM companies ORDER BY name", String.class))
                .containsExactly("Swisscom AG", "swisscomag", "testco");
    }

    @Test
    void should_useTheMappingCompanyUserManagementRecorded_when_itMigratedFirst() {
        jdbc.execute("CREATE TABLE companies (name VARCHAR(255) PRIMARY KEY)");
        jdbc.update("INSERT INTO companies (name) VALUES ('swisscomag2'), ('swisscomag'), ('testco')");
        jdbc.execute("CREATE TABLE company_key_changes (old_key VARCHAR(255) PRIMARY KEY, "
                + "new_key VARCHAR(12) NOT NULL UNIQUE, changed_at TIMESTAMPTZ NOT NULL DEFAULT now())");
        jdbc.update("INSERT INTO company_key_changes (old_key, new_key) VALUES ('Swisscom AG', 'swisscomag2')");

        migrate("11");

        assertThat(jdbc.queryForObject("SELECT count(*) FROM company_key_changes", Integer.class)).isEqualTo(1);
        assertMovedReferences();
    }

    @Test
    void should_changeNothing_when_noCompaniesTableExists() {
        migrate("11");

        assertThat(jdbc.queryForObject("SELECT count(*) FROM company_key_changes", Integer.class)).isZero();
        assertThat(jdbc.queryForList("SELECT company_name FROM partners ORDER BY company_name", String.class))
                .containsExactly("Swisscom AG", "testco");
    }

    @Test
    void should_carryTheCanonicalReKeyBlock() {
        CompanyKeyChangesContract.verify("db/migration/V11__company_keys_follow_the_slug_rule.sql");
    }

    private void assertMovedReferences() {
        assertThat(jdbc.queryForList("SELECT company_name FROM partners ORDER BY company_name", String.class))
                .containsExactly("swisscomag2", "testco");
        assertThat(jdbc.queryForObject("SELECT company_name FROM topic_suggestions", String.class))
                .isEqualTo("swisscomag2");
        assertThat(jdbc.queryForList("SELECT company_name FROM topic_votes ORDER BY company_name", String.class))
                .containsExactly("swisscomag2", "testco");
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
