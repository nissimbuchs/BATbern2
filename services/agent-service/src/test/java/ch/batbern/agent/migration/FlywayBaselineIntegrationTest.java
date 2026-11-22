package ch.batbern.agent.migration;

import ch.batbern.shared.test.AbstractIntegrationTest;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;

/**
 * agent-service migrates into the shared production database, which is not empty and has no
 * {@code flyway_schema_history_agent} yet. With the service's own Flyway settings, Flyway must
 * baseline at 0 and then apply V1 and the later migrations (V2: the decision log, V3: the run's grant, V4: its principal kind, V5: the gate's held calls); with the default baseline 1 it would skip V1 and the service
 * would fail schema validation.
 *
 * <p>The settings are read from the base document of the production {@code application.yml}, not
 * from the test context: the test profile's {@code application-test.yml} repeats them (it must
 * shadow the shared-kernel testFixtures file), so the context alone would not notice a change to
 * production.
 */
@DisplayName("Flyway baselines a non-empty schema at 0 and applies every migration")
class FlywayBaselineIntegrationTest extends AbstractIntegrationTest {

    /** A schema of its own, so the probe never touches the one the test context migrated. */
    private static final String PROBE_SCHEMA = "agent_baseline_probe";

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcTemplate jdbc;

    @AfterEach
    void dropProbeSchema() {
        jdbc.execute("DROP SCHEMA IF EXISTS " + PROBE_SCHEMA + " CASCADE");
    }

    @Test
    @DisplayName("should_baselineAt0ThenApplyAll_when_schemaHoldsOtherServicesTables")
    void should_baselineAt0ThenApplyAll_when_schemaHoldsOtherServicesTables() throws IOException {
        PropertySource<?> production = new YamlPropertySourceLoader()
                .load("application.yml", new ClassPathResource("application.yml")).get(0);
        String table = String.valueOf(production.getProperty("spring.flyway.table"));
        String location = String.valueOf(production.getProperty("spring.flyway.locations"));
        String baselineVersion = String.valueOf(production.getProperty("spring.flyway.baseline-version"));
        boolean baselineOnMigrate = Boolean.parseBoolean(
                String.valueOf(production.getProperty("spring.flyway.baseline-on-migrate")));

        jdbc.execute("CREATE SCHEMA " + PROBE_SCHEMA);
        // Another service's table and history: the shared database as production has it
        jdbc.execute("CREATE TABLE " + PROBE_SCHEMA + ".partners (id UUID PRIMARY KEY)");
        jdbc.execute("CREATE TABLE " + PROBE_SCHEMA
                + ".flyway_schema_history_partner_coordination (installed_rank INT PRIMARY KEY)");

        assertThat(table).isEqualTo("flyway_schema_history_agent");
        assertThat(location).isEqualTo("classpath:db/agent-migration");
        assertThat(baselineVersion).isEqualTo("0");
        assertThat(baselineOnMigrate).isTrue();

        Flyway.configure()
                .dataSource(dataSource)
                .schemas(PROBE_SCHEMA)
                .defaultSchema(PROBE_SCHEMA)
                .createSchemas(false)
                .table(table)
                .locations(location)
                .baselineOnMigrate(baselineOnMigrate)
                .baselineVersion(baselineVersion)
                .load()
                .migrate();

        List<Map<String, Object>> history = jdbc.queryForList("SELECT version, type, success FROM "
                + PROBE_SCHEMA + ".flyway_schema_history_agent ORDER BY installed_rank");
        assertThat(history).extracting(row -> row.get("version"), row -> row.get("type"), row -> row.get("success"))
                .containsExactly(
                        tuple("0", "BASELINE", true),
                        tuple("1", "SQL", true),
                        tuple("2", "SQL", true),
                        tuple("3", "SQL", true),
                        tuple("4", "SQL", true),
                        tuple("5", "SQL", true));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM information_schema.tables "
                + "WHERE table_schema = ? AND table_name IN ('agent_runs', 'agent_inbound_messages', 'agent_steps', "
                + "'agent_model_calls', 'agent_tool_calls', 'agent_verdicts', 'agent_approvals', 'agent_undos')",
                Integer.class, PROBE_SCHEMA))
                .isEqualTo(8);
    }

    @Test
    @DisplayName("should_recordMigrationsInOwnHistory_when_serviceStarted")
    void should_recordMigrationsInOwnHistory_when_serviceStarted() {
        Integer applied = jdbc.queryForObject("SELECT count(*) FROM flyway_schema_history_agent "
                + "WHERE version IN ('1', '2', '3', '4', '5') AND success", Integer.class);
        assertThat(applied).isEqualTo(5);
    }

    @Test
    @DisplayName("should_enforceGrantAndDetailRules_when_V3Applied")
    void should_enforceGrantAndDetailRules_when_V3Applied() {
        UUID grant = UUID.randomUUID();
        jdbc.update("INSERT INTO agent_runs (id, status, started_at, ended_at, trigger, end_reason, end_detail, "
                + "grant_run_id, principal_kind) VALUES (?, 'HANDED_TO_ORGANIZERS', now(), now(), 'SCHEDULED', "
                + "'TOKEN_REFUSED', 'GRANT_INVALID', ?, 'ORGANIZER_DELEGATE')", UUID.randomUUID(), grant);
        try {
            // One grant, one run
            assertThatThrownBy(() -> jdbc.update("INSERT INTO agent_runs (id, status, started_at, trigger, "
                    + "grant_run_id, principal_kind) VALUES (?, 'RUNNING', now(), 'SCHEDULED', ?, "
                    + "'ORGANIZER_DELEGATE')", UUID.randomUUID(), grant))
                    .isInstanceOf(DataIntegrityViolationException.class);
            // The detail is a code, never free text
            assertThatThrownBy(() -> jdbc.update("INSERT INTO agent_runs (id, status, started_at, ended_at, trigger, "
                    + "end_reason, end_detail) VALUES (?, 'HANDED_TO_ORGANIZERS', now(), now(), 'SCHEDULED', "
                    + "'GRANT_REFUSED', 'refused for anna.meier')", UUID.randomUUID()))
                    .isInstanceOf(DataIntegrityViolationException.class);
        } finally {
            jdbc.execute("TRUNCATE agent_runs CASCADE");
        }
    }

    @Test
    @DisplayName("should_storePrincipalKindExactlyWithGrant_when_V4Applied")
    void should_storePrincipalKindExactlyWithGrant_when_V4Applied() {
        try {
            // Positive control: a grant with its principal kind is stored
            jdbc.update("INSERT INTO agent_runs (id, status, started_at, trigger, grant_run_id, principal_kind) "
                    + "VALUES (?, 'RUNNING', now(), 'ORGANIZER', ?, 'ORGANIZER')", UUID.randomUUID(), UUID.randomUUID());
            // A grant without a principal kind
            assertThatThrownBy(() -> jdbc.update("INSERT INTO agent_runs (id, status, started_at, trigger, "
                    + "grant_run_id) VALUES (?, 'RUNNING', now(), 'ORGANIZER', ?)", UUID.randomUUID(), UUID.randomUUID()))
                    .isInstanceOf(DataIntegrityViolationException.class);
            // A principal kind without a grant
            assertThatThrownBy(() -> jdbc.update("INSERT INTO agent_runs (id, status, started_at, trigger, "
                    + "principal_kind) VALUES (?, 'RUNNING', now(), 'ORGANIZER', 'ORGANIZER')", UUID.randomUUID()))
                    .isInstanceOf(DataIntegrityViolationException.class);
            // A kind CUMS does not issue
            assertThatThrownBy(() -> jdbc.update("INSERT INTO agent_runs (id, status, started_at, trigger, "
                    + "grant_run_id, principal_kind) VALUES (?, 'RUNNING', now(), 'ORGANIZER', ?, 'SPEAKER')",
                    UUID.randomUUID(), UUID.randomUUID()))
                    .isInstanceOf(DataIntegrityViolationException.class);
        } finally {
            jdbc.execute("TRUNCATE agent_runs CASCADE");
        }
    }

    @Test
    @DisplayName("should_holdCallsAndDecideOncePerCall_when_V5Applied")
    void should_holdCallsAndDecideOncePerCall_when_V5Applied() {
        UUID run = UUID.randomUUID();
        UUID step = UUID.randomUUID();
        try {
            jdbc.update("INSERT INTO agent_runs (id, status, started_at, trigger, grant_run_id, principal_kind) "
                    + "VALUES (?, 'RUNNING', now(), 'ORGANIZER', ?, 'ORGANIZER')", run, UUID.randomUUID());
            jdbc.update("INSERT INTO agent_steps (id, run_id, step_number, started_at) VALUES (?, ?, 1, now())",
                    step, run);
            // Positive control: the gate's held statuses are stored
            UUID held = UUID.randomUUID();
            for (String status : new String[] {"PENDING_APPROVAL", "REFUSED"}) {
                UUID call = status.equals("REFUSED") ? UUID.randomUUID() : held;
                jdbc.update("INSERT INTO agent_tool_calls (id, run_id, step_id, tool_use_id, tool_name, input, "
                        + "status, started_at) VALUES (?, ?, ?, 'tu', 'createCompany', '{}'::jsonb, ?, now())",
                        call, run, step, status);
            }
            assertThatThrownBy(() -> jdbc.update("INSERT INTO agent_tool_calls (id, run_id, step_id, tool_use_id, "
                    + "tool_name, input, status, started_at) VALUES (?, ?, ?, 'tu', 'x', '{}'::jsonb, 'APPROVED', "
                    + "now())", UUID.randomUUID(), run, step))
                    .isInstanceOf(DataIntegrityViolationException.class);
            // One verdict per call
            String verdict = "INSERT INTO agent_verdicts (id, run_id, tool_call_id, operation_class, outcome, "
                    + "inputs, cited_precedents, decided_at) VALUES (?, ?, ?, 'BINDING', 'PENDING_APPROVAL', "
                    + "'{}'::jsonb, '[]'::jsonb, now())";
            jdbc.update(verdict, UUID.randomUUID(), run, held);
            assertThatThrownBy(() -> jdbc.update(verdict, UUID.randomUUID(), run, held))
                    .isInstanceOf(DataIntegrityViolationException.class);
        } finally {
            jdbc.execute("TRUNCATE agent_runs CASCADE");
        }
    }
}
