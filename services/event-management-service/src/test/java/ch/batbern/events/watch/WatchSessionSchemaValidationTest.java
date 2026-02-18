package ch.batbern.events.watch;

import ch.batbern.events.AbstractIntegrationTest;
import ch.batbern.events.config.TestAwsConfig;
import ch.batbern.events.config.TestSecurityConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Schema validation test for V56 Flyway migration.
 * W4.1 Task 5: Verifies watch session control fields added to sessions table.
 */
@Import({TestSecurityConfig.class, TestAwsConfig.class})
class WatchSessionSchemaValidationTest extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("should_haveWatchSessionFields_when_V56MigrationApplied")
    void should_haveWatchSessionFields_when_V56MigrationApplied() {
        String query = """
                SELECT column_name, data_type, is_nullable
                FROM information_schema.columns
                WHERE table_name = 'sessions'
                ORDER BY ordinal_position
                """;

        List<Map<String, Object>> columns = jdbcTemplate.queryForList(query);
        List<String> columnNames = columns.stream()
                .map(col -> (String) col.get("column_name"))
                .toList();

        assertThat(columnNames).contains(
                "actual_start_time",
                "actual_end_time",
                "overrun_minutes",
                "completed_by_username"
        );
    }

    @Test
    @DisplayName("should_haveCorrectTypes_when_V56MigrationApplied")
    void should_haveCorrectTypes_when_V56MigrationApplied() {
        String query = """
                SELECT column_name, data_type, is_nullable
                FROM information_schema.columns
                WHERE table_name = 'sessions'
                  AND column_name IN ('actual_start_time', 'actual_end_time',
                                      'overrun_minutes', 'completed_by_username')
                ORDER BY column_name
                """;

        List<Map<String, Object>> columns = jdbcTemplate.queryForList(query);
        assertThat(columns).hasSize(4);

        Map<String, Map<String, Object>> byName = new java.util.HashMap<>();
        for (Map<String, Object> col : columns) {
            byName.put((String) col.get("column_name"), col);
        }

        assertThat(byName.get("actual_start_time").get("data_type")).isEqualTo("timestamp without time zone");
        assertThat(byName.get("actual_start_time").get("is_nullable")).isEqualTo("YES");

        assertThat(byName.get("actual_end_time").get("data_type")).isEqualTo("timestamp without time zone");
        assertThat(byName.get("actual_end_time").get("is_nullable")).isEqualTo("YES");

        assertThat(byName.get("overrun_minutes").get("data_type")).isEqualTo("integer");
        assertThat(byName.get("overrun_minutes").get("is_nullable")).isEqualTo("YES");

        assertThat(byName.get("completed_by_username").get("data_type")).isEqualTo("character varying");
        assertThat(byName.get("completed_by_username").get("is_nullable")).isEqualTo("YES");
    }
}
