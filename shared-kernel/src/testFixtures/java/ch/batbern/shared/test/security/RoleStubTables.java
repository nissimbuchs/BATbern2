package ch.batbern.shared.test.security;

import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

/**
 * TEST-ONLY stubs of the CUMS identity tables for EMS and PCS integration tests (agentic interaction):
 * {@code JwtRolesConverter} resolves an agent token's roles from {@code role_assignments} by its
 * {@code sub}, as production does on the one shared database. EMS has a {@code user_profiles} stub
 * (test migration) but no {@code role_assignments}; PCS owns neither. {@link #create} makes whichever
 * is missing, {@link #grant} gives a person a role, {@link #remove} deletes the rows of the given subs.
 */
public final class RoleStubTables {

    private RoleStubTables() {
    }

    /** Creates {@code user_profiles} and {@code role_assignments} where they do not exist. */
    public static void create(JdbcTemplate jdbc) {
        jdbc.execute("CREATE TABLE IF NOT EXISTS user_profiles ("
                + "id UUID PRIMARY KEY, cognito_user_id VARCHAR(255), username VARCHAR(100))");
        jdbc.execute("CREATE TABLE IF NOT EXISTS role_assignments (user_id UUID NOT NULL, role VARCHAR(50) NOT NULL)");
    }

    /** A profile for {@code username} with Cognito {@code sub} holding {@code role}. */
    public static void grant(JdbcTemplate jdbc, String username, String sub, String role) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO user_profiles (id, cognito_user_id, username) VALUES (?, ?, ?)", id, sub, username);
        jdbc.update("INSERT INTO role_assignments (user_id, role) VALUES (?, ?)", id, role);
    }

    /** Deletes the role assignments and profiles of {@code subs}. */
    public static void remove(JdbcTemplate jdbc, String... subs) {
        for (String sub : subs) {
            jdbc.update("DELETE FROM role_assignments WHERE user_id IN "
                    + "(SELECT id FROM user_profiles WHERE cognito_user_id = ?)", sub);
            jdbc.update("DELETE FROM user_profiles WHERE cognito_user_id = ?", sub);
        }
    }
}
