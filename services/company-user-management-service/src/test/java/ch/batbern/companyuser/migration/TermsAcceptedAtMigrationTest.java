package ch.batbern.companyuser.migration;

import ch.batbern.companyuser.config.TestAwsConfig;
import ch.batbern.shared.test.AbstractIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Migration verification tests for V17 (Story 12.11 AC1):
 * {@code terms_accepted_at} consent column + backfill semantics.
 *
 * <p>The backfill in V17 runs at migration time (before test data exists), so its
 * row-level semantics are verified by replaying the EXACT same UPDATE predicate
 * against freshly inserted rows. Keep the statement in
 * {@link #replayV17Backfill()} byte-identical to the one in
 * {@code V17__add_terms_accepted_at_to_user_profiles.sql}.
 *
 * <p>Discriminator background (verified against the live DB, 2026-06-04):
 * {@code cognito_user_id} stores the Cognito sub UUID for ALL users — federated
 * included — so no username-shape discriminator exists. The cutoff timestamp
 * {@code 2026-06-04 16:00:00+00} is the SSO go-live moment; the first federated
 * row was created 2026-06-04 16:04:51 UTC.
 */
@Import(TestAwsConfig.class)
@DisplayName("V17 terms_accepted_at Migration Tests")
class TermsAcceptedAtMigrationTest extends AbstractIntegrationTest {

    private static final String PRE_CUTOFF_NATIVE = "consent.native.901";
    private static final String POST_CUTOFF_FEDERATED = "consent.federated.902";
    private static final String ANONYMOUS = "consent.anonymous.903";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update(
                "DELETE FROM user_profiles WHERE username IN (?, ?, ?)",
                PRE_CUTOFF_NATIVE, POST_CUTOFF_FEDERATED, ANONYMOUS);
    }

    @Test
    @DisplayName("should_haveNullableTermsAcceptedAtColumn_when_migrationsRun")
    void should_haveNullableTermsAcceptedAtColumn_when_migrationsRun() {
        List<Map<String, Object>> columns = jdbcTemplate.queryForList(
                "SELECT column_name, data_type, is_nullable FROM information_schema.columns "
                        + "WHERE table_name = 'user_profiles' AND column_name = 'terms_accepted_at'");

        assertThat(columns).hasSize(1);
        assertThat(columns.get(0).get("data_type")).isEqualTo("timestamp with time zone");
        assertThat(columns.get(0).get("is_nullable")).isEqualTo("YES");
    }

    @Test
    @DisplayName("should_backfillOnlyPreCutoffCognitoRows_when_v17PredicateReplayed")
    void should_backfillOnlyPreCutoffCognitoRows_when_v17PredicateReplayed() {
        // Given — a pre-cutoff native row, a post-cutoff (federated-era) row, an anonymous row
        Instant preCutoff = Instant.parse("2026-01-15T10:00:00Z");
        Instant postCutoff = Instant.parse("2026-06-04T17:52:08Z");

        insertProfile(PRE_CUTOFF_NATIVE, "cognito-" + System.nanoTime(), preCutoff);
        insertProfile(POST_CUTOFF_FEDERATED, "cognito-" + System.nanoTime(), postCutoff);
        insertProfile(ANONYMOUS, null, preCutoff);

        // When — replay the V17 backfill statement
        replayV17Backfill();

        // Then — only the pre-cutoff row with a Cognito account is backfilled, to its created_at
        Timestamp nativeConsent = queryTermsAcceptedAt(PRE_CUTOFF_NATIVE);
        assertThat(nativeConsent).isNotNull();
        assertThat(nativeConsent.toInstant()).isEqualTo(preCutoff);

        assertThat(queryTermsAcceptedAt(POST_CUTOFF_FEDERATED)).isNull();
        assertThat(queryTermsAcceptedAt(ANONYMOUS)).isNull();
    }

    @Test
    @DisplayName("should_notMoveExistingConsent_when_v17PredicateReplayed")
    void should_notMoveExistingConsent_when_v17PredicateReplayed() {
        // Given — a pre-cutoff row that already carries an explicit consent timestamp
        Instant preCutoff = Instant.parse("2026-01-15T10:00:00Z");
        Instant explicitConsent = Instant.parse("2026-03-01T08:30:00Z");
        insertProfile(PRE_CUTOFF_NATIVE, "cognito-" + System.nanoTime(), preCutoff);
        jdbcTemplate.update(
                "UPDATE user_profiles SET terms_accepted_at = ? WHERE username = ?",
                Timestamp.from(explicitConsent), PRE_CUTOFF_NATIVE);

        // When
        replayV17Backfill();

        // Then — the explicit timestamp is preserved (backfill only fills NULLs)
        Timestamp consent = queryTermsAcceptedAt(PRE_CUTOFF_NATIVE);
        assertThat(consent).isNotNull();
        assertThat(consent.toInstant()).isEqualTo(explicitConsent);
    }

    private void insertProfile(String username, String cognitoUserId, Instant createdAt) {
        jdbcTemplate.update(
                "INSERT INTO user_profiles "
                        + "(username, cognito_user_id, email, first_name, last_name, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?)",
                username, cognitoUserId, username + "@example.com", "Consent", "Test",
                Timestamp.from(createdAt), Timestamp.from(createdAt));
    }

    /**
     * Byte-identical replay of the V17 backfill UPDATE, including its
     * {@code terms_accepted_at IS NULL} write-once guard (part of the migration SQL itself).
     */
    private void replayV17Backfill() {
        jdbcTemplate.update(
                "UPDATE user_profiles SET terms_accepted_at = created_at "
                        + "WHERE cognito_user_id IS NOT NULL "
                        + "AND created_at < TIMESTAMP WITH TIME ZONE '2026-06-04 16:00:00+00' "
                        + "AND terms_accepted_at IS NULL");
    }

    private Timestamp queryTermsAcceptedAt(String username) {
        return jdbcTemplate.queryForObject(
                "SELECT terms_accepted_at FROM user_profiles WHERE username = ?",
                Timestamp.class, username);
    }
}
