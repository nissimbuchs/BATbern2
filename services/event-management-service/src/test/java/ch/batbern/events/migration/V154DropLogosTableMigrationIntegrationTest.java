package ch.batbern.events.migration;

import ch.batbern.events.config.TestAwsConfig;
import ch.batbern.events.config.TestSecurityConfig;
import ch.batbern.shared.test.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V154 (#1307, owner go 2026-11-13): the shared {@code logos} table is gone. Story 17.11 moved uploads
 * to {@code event_management_uploads}; nothing reads or writes {@code logos} any more.
 */
@Import({TestSecurityConfig.class, TestAwsConfig.class})
@DisplayName("V154 drops the logos table")
class V154DropLogosTableMigrationIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("should_haveNoLogosTable_when_migrationsRun")
    void should_haveNoLogosTable_when_migrationsRun() {
        assertThat(jdbcTemplate.queryForObject("SELECT to_regclass('public.logos') IS NULL", Boolean.class))
                .isTrue();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT to_regclass('public.event_management_uploads') IS NOT NULL", Boolean.class)).isTrue();
    }
}
