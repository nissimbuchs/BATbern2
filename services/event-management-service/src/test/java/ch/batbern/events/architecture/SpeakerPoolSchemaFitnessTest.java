package ch.batbern.events.architecture;

import ch.batbern.shared.test.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Schema-fitness guard for ADR-012 / Epic 11.E.8: {@code speaker_pool} is the speaker WORKFLOW
 * STATE MACHINE only (+ a {@code source} provenance flag). Content — title / abstract / materials
 * — never lives on it. A self-nomination's proposed talk lives in {@code session_proposals}
 * (pre-READY pitch) and, post-promote, in {@code sessions} + {@code session_content_history}.
 *
 * <p>This test fails in CI — not in review — if anyone re-adds content columns to
 * {@code speaker_pool} (the exact regression V109's first cut introduced). Runs against the real
 * migrated PostgreSQL schema (Testcontainers).
 */
class SpeakerPoolSchemaFitnessTest extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // Substrings that signal content has crept onto the workflow-state table. Deliberately
    // specific: the bare token "content" is excluded because `content_deadline` is a legitimate
    // workflow scheduling column (the deadline by which content is due), not content itself.
    private static final List<String> FORBIDDEN_CONTENT_TOKENS =
            List.of("title", "abstract", "material", "proposed_");

    @Test
    @DisplayName("ADR-012: speaker_pool carries no title/abstract/materials content columns")
    void speakerPool_hasNoContentColumns() {
        List<String> columns = jdbcTemplate.queryForList(
                "SELECT column_name FROM information_schema.columns WHERE table_name = 'speaker_pool'",
                String.class);

        assertThat(columns).isNotEmpty();

        List<String> offenders = columns.stream()
                .filter(c -> {
                    String lower = c.toLowerCase(Locale.ROOT);
                    return FORBIDDEN_CONTENT_TOKENS.stream().anyMatch(lower::contains);
                })
                .toList();

        assertThat(offenders)
                .as("speaker_pool must hold no content columns (ADR-012). The proposed talk belongs "
                        + "in session_proposals; post-READY content in sessions/session_content_history.")
                .isEmpty();

        // Positive guard: the provenance flag stays.
        assertThat(columns).contains("source");
    }

    @Test
    @DisplayName("ADR-012: session_proposals exists and holds the pitch keyed to a pool row")
    void sessionProposals_holdsThePitch() {
        List<String> columns = jdbcTemplate.queryForList(
                "SELECT column_name FROM information_schema.columns WHERE table_name = 'session_proposals'",
                String.class);

        assertThat(columns).contains(
                "speaker_pool_id", "event_id", "proposed_by_username",
                "proposed_title", "proposed_abstract");
        // No second state machine — acceptance is read from the linked speaker_pool row.
        assertThat(columns).doesNotContain("status");
    }
}
