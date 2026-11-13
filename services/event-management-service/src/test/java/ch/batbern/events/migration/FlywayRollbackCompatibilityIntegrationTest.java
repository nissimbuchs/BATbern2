package ch.batbern.events.migration;

import ch.batbern.events.config.TestAwsConfig;
import ch.batbern.events.config.TestSecurityConfig;
import ch.batbern.shared.test.AbstractIntegrationTest;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.flyway.autoconfigure.FlywayProperties;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * A rollback must be able to start the previous image after a deploy applied a newer migration.
 *
 * <p>2026-11-13, PR #1309: the auto-rollback after a failed Bruno run could not start EMS. Its
 * {@code ignore-migration-patterns: "*:missing"} replaced Flyway's default {@code *:future}, so the
 * previous image failed validation on V154 ("Migrations have failed validation") and ECS kept the new
 * image. This test applies one migration the code does not know and validates with the service's own
 * Flyway settings, as the previous image would on start.
 */
@Import({TestSecurityConfig.class, TestAwsConfig.class})
@DisplayName("Flyway settings let a previous image start after a newer migration")
class FlywayRollbackCompatibilityIntegrationTest extends AbstractIntegrationTest {

    private static final String PROBE_VERSION = "99999";

    @Autowired
    private DataSource dataSource;

    @Autowired
    private FlywayProperties flywayProperties;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("should_validate_when_databaseHasAMigrationTheCodeDoesNotKnow")
    void should_validate_when_databaseHasAMigrationTheCodeDoesNotKnow() {
        List<String> newerLocations = new ArrayList<>(flywayProperties.getLocations());
        newerLocations.add("classpath:db/rollback-probe");
        try {
            // The "newer image": its migrations plus one more.
            Flyway.configure()
                    .dataSource(dataSource)
                    .table(flywayProperties.getTable())
                    .locations(newerLocations.toArray(String[]::new))
                    .ignoreMigrationPatterns(flywayProperties.getIgnoreMigrationPatterns().toArray(String[]::new))
                    .load()
                    .migrate();

            // The "previous image": the service's own settings, without the newer migration.
            Flyway previous = Flyway.configure()
                    .dataSource(dataSource)
                    .table(flywayProperties.getTable())
                    .locations(flywayProperties.getLocations().toArray(String[]::new))
                    .ignoreMigrationPatterns(flywayProperties.getIgnoreMigrationPatterns().toArray(String[]::new))
                    .validateOnMigrate(flywayProperties.isValidateOnMigrate())
                    .load();

            assertThatCode(previous::validate).doesNotThrowAnyException();
        } finally {
            jdbcTemplate.update("DELETE FROM " + flywayProperties.getTable() + " WHERE version = ?", PROBE_VERSION);
        }
    }
}
