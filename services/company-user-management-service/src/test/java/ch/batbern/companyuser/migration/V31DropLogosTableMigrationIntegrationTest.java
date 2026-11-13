package ch.batbern.companyuser.migration;

import ch.batbern.companyuser.config.TestAwsConfig;
import ch.batbern.shared.test.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V31 (#1307, owner go 2026-11-13): the shared {@code logos} table is gone. Story 17.11 moved uploads
 * to {@code company_user_uploads}; nothing reads or writes {@code logos} any more.
 */
@Import(TestAwsConfig.class)
@DisplayName("V31 drops the logos table")
class V31DropLogosTableMigrationIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("should_haveNoLogosTable_when_migrationsRun")
    void should_haveNoLogosTable_when_migrationsRun() {
        assertThat(jdbcTemplate.queryForObject("SELECT to_regclass('public.logos') IS NULL", Boolean.class))
                .isTrue();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT to_regclass('public.company_user_uploads') IS NOT NULL", Boolean.class)).isTrue();
    }
}
