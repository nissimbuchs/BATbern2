package ch.batbern.companyuser.migration;

import ch.batbern.companyuser.config.TestAwsConfig;
import ch.batbern.shared.test.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Migration verification tests for User Management schema (Story 1.14-2)
 * Verifies Story 1.16.2 constraints:
 * - Username format validation
 * - Company name format validation
 * - All required indexes exist
 * - Embedded preferences/settings columns exist
 *
 * NOTE: @Transactional removed from class level to prevent transaction abort issues
 * when testing constraint violations. Each test manages its own transaction scope.
 */
@Import(TestAwsConfig.class)
@DisplayName("User Management Migrations Tests")
class UserManagementMigrationsTest extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("should_createUserProfilesTable_when_migrationsRun")
    void should_createUserProfilesTable_when_migrationsRun() {
        // When
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = 'user_profiles'",
                Integer.class
        );

        // Then
        assertThat(count).isEqualTo(1);
    }

    @Test
    @DisplayName("should_createRoleAssignmentsTable_when_migrationsRun")
    void should_createRoleAssignmentsTable_when_migrationsRun() {
        // When
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = 'role_assignments'",
                Integer.class
        );

        // Then
        assertThat(count).isEqualTo(1);
    }

    @Test
    @DisplayName("should_createActivityHistoryTable_when_migrationsRun")
    void should_createActivityHistoryTable_when_migrationsRun() {
        // When
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = 'activity_history'",
                Integer.class
        );

        // Then
        assertThat(count).isEqualTo(1);
    }

    @Test
    @DisplayName("should_haveUsernameColumn_when_userProfilesTableExists")
    void should_haveUsernameColumn_when_userProfilesTableExists() {
        // When
        List<Map<String, Object>> columns = jdbcTemplate.queryForList(
                "SELECT column_name, data_type FROM information_schema.columns "
                        + "WHERE table_name = 'user_profiles' AND column_name = 'username'"
        );

        // Then
        assertThat(columns).isNotEmpty();
        assertThat(columns.get(0).get("column_name")).isEqualTo("username");
        assertThat(columns.get(0).get("data_type")).isEqualTo("character varying");
    }

    @Test
    @DisplayName("should_haveEmbeddedPreferencesColumns_when_userProfilesTableExists")
    void should_haveEmbeddedPreferencesColumns_when_userProfilesTableExists() {
        // When
        List<String> prefColumns = jdbcTemplate.queryForList(
                "SELECT column_name FROM information_schema.columns "
                        + "WHERE table_name = 'user_profiles' AND column_name LIKE 'pref_%'",
                String.class
        );

        // Then
        assertThat(prefColumns).contains(
                "pref_theme",
                "pref_language",
                "pref_email_notifications",
                "pref_in_app_notifications",
                "pref_push_notifications",
                "pref_notification_frequency"
        );
    }

    @Test
    @DisplayName("should_haveEmbeddedSettingsColumns_when_userProfilesTableExists")
    void should_haveEmbeddedSettingsColumns_when_userProfilesTableExists() {
        // When
        List<String> settingsColumns = jdbcTemplate.queryForList(
                "SELECT column_name FROM information_schema.columns "
                        + "WHERE table_name = 'user_profiles' AND column_name LIKE 'settings_%'",
                String.class
        );

        // Then
        assertThat(settingsColumns).contains(
                "settings_profile_visibility",
                "settings_show_email",
                "settings_show_company",
                "settings_timezone",
                "settings_two_factor_enabled"
        );
    }

    @Test
    @DisplayName("should_enforceUsernameFormat_when_invalidUsernameInserted")
    void should_enforceUsernameFormat_when_invalidUsernameInserted() {
        // Given - Invalid username formats (Story 1.16.2)
        String[] invalidUsernames = {
                "John.Doe",      // Uppercase not allowed
                "john_doe",      // Underscores not allowed
                "johndoe",       // Missing dot separator
                "john.doe.abc"   // Invalid suffix (must be numeric)
        };

        for (String invalidUsername : invalidUsernames) {
            // When/Then
            assertThatThrownBy(() ->
                    jdbcTemplate.update(
                            "INSERT INTO user_profiles (username, cognito_user_id, email, first_name, last_name) "
                                    + "VALUES (?, ?, ?, ?, ?)",
                            invalidUsername, "cognito-" + System.nanoTime(), "test@example.com", "John", "Doe"
                    )
            ).satisfiesAnyOf(
                    e -> assertThat(e.getMessage()).containsIgnoringCase("violates check constraint"),
                    e -> assertThat(e.getMessage()).containsIgnoringCase("chk_username_format")
            );
        }
    }

    @Test
    @DisplayName("should_acceptValidUsername_when_correctFormatProvided")
    void should_acceptValidUsername_when_correctFormatProvided() {
        // Given - Valid username formats (Story 1.16.2)
        String[] validUsernames = {
                "john.doe",
                "jane.smith",
                "max.mueller.1",
                "anna.schmidt.123"
        };

        for (String validUsername : validUsernames) {
            // When/Then - Should NOT throw exception
            assertThatCode(() ->
                    jdbcTemplate.update(
                            "INSERT INTO user_profiles (username, cognito_user_id, email, first_name, last_name) "
                                    + "VALUES (?, ?, ?, ?, ?)",
                            validUsername,
                            "cognito-" + System.nanoTime(),
                            validUsername + "@example.com",
                            "First",
                            "Last"
                    )
            ).doesNotThrowAnyException();

            // Cleanup
            jdbcTemplate.update("DELETE FROM user_profiles WHERE username = ?", validUsername);
        }
    }

    @Test
    @DisplayName("should_enforceCompanyIdFormat_when_invalidCompanyIdProvided")
    void should_enforceCompanyIdFormat_when_invalidCompanyIdProvided() {
        // Given - Invalid company IDs (Story 1.16.2: max 12 alphanumeric chars)
        String[] invalidCompanyIds = {
                "Company-Name-123",   // Hyphens not allowed
                "VeryLongCompanyName123456",  // Exceeds 12 chars
                "Company_ID"          // Underscores not allowed
        };

        for (int i = 0; i < invalidCompanyIds.length; i++) {
            final int index = i + 100;
            final String invalidCompanyId = invalidCompanyIds[i];

            // When/Then
            assertThatThrownBy(() ->
                    jdbcTemplate.update(
                            "INSERT INTO user_profiles (username, cognito_user_id, email, first_name, last_name, company_id) "
                                    + "VALUES (?, ?, ?, ?, ?, ?)",
                            "test.user." + index,
                            "cognito-" + System.nanoTime(),
                            "testuser" + index + "@example.com",
                            "Test",
                            "User",
                            invalidCompanyId
                    )
            ).satisfiesAnyOf(
                    e -> assertThat(e.getMessage()).containsIgnoringCase("violates check constraint"),
                    e -> assertThat(e.getMessage()).containsIgnoringCase("chk_company_id_format"),
                    e -> assertThat(e.getMessage()).containsIgnoringCase("value too long for type character varying")
            );
        }
    }

    @Test
    @DisplayName("should_acceptValidCompanyId_when_correctFormatProvided")
    void should_acceptValidCompanyId_when_correctFormatProvided() {
        // Given - Valid company IDs (Story 1.16.2)
        String[] validCompanyIds = {
                "GoogleZH",
                "MicrosoftBE",
                "IBM",
                "SAP123"
        };

        for (int i = 0; i < validCompanyIds.length; i++) {
            final int index = i + 1;
            final String validCompanyId = validCompanyIds[i];
            final String username = "test.user." + index;
            final String email = "testuser" + index + "@example.com";

            // When/Then - Should NOT throw exception
            assertThatCode(() ->
                    jdbcTemplate.update(
                            "INSERT INTO user_profiles (username, cognito_user_id, email, first_name, last_name, company_id) "
                                    + "VALUES (?, ?, ?, ?, ?, ?)",
                            username,
                            "cognito-" + System.nanoTime(),
                            email,
                            "Test",
                            "User",
                            validCompanyId
                    )
            ).doesNotThrowAnyException();

            // Cleanup
            jdbcTemplate.update("DELETE FROM user_profiles WHERE username = ?", username);
        }
    }

    @Test
    @DisplayName("should_haveUsernameIndex_when_migrationsComplete")
    void should_haveUsernameIndex_when_migrationsComplete() {
        // When
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM pg_indexes WHERE tablename = 'user_profiles' AND indexname = 'idx_users_username'",
                Integer.class
        );

        // Then
        assertThat(count).isEqualTo(1);
    }

    @Test
    @DisplayName("should_haveCompanyIndex_when_migrationsComplete")
    void should_haveCompanyIndex_when_migrationsComplete() {
        // When
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM pg_indexes WHERE tablename = 'user_profiles' AND indexname = 'idx_users_company'",
                Integer.class
        );

        // Then
        assertThat(count).isEqualTo(1);
    }

    @Test
    @DisplayName("should_cascadeDeleteRoles_when_userDeleted")
    void should_cascadeDeleteRoles_when_userDeleted() {
        // Given - Create user with role
        String username = "delete.test.200";
        String email = "deletetest200@example.com";

        jdbcTemplate.update(
                "INSERT INTO user_profiles (username, cognito_user_id, email, first_name, last_name) VALUES (?, ?, ?, ?, ?)",
                username, "cognito-" + System.nanoTime(), email, "Delete", "Test"
        );

        String userId = jdbcTemplate.queryForObject(
                "SELECT id FROM user_profiles WHERE username = ?",
                String.class,
                username
        );

        jdbcTemplate.update(
                "INSERT INTO role_assignments (user_id, role) VALUES (?, ?)",
                java.util.UUID.fromString(userId), "ATTENDEE"
        );

        // When - Delete user
        jdbcTemplate.update("DELETE FROM user_profiles WHERE username = ?", username);

        // Then - Role should be cascade deleted
        Integer roleCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM role_assignments WHERE user_id = ?",
                Integer.class,
                java.util.UUID.fromString(userId)
        );

        assertThat(roleCount).isEqualTo(0);
    }
}
