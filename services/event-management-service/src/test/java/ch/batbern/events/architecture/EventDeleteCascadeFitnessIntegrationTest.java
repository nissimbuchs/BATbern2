package ch.batbern.events.architecture;

import ch.batbern.shared.test.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Schema fitness for DELETE /events/{code} (#824 item 4, after #1158 found one case by hand).
 *
 * <p>The event delete relies on {@code ON DELETE CASCADE} from {@code events} down to every table
 * that hangs off an event. A single foreign key with {@code NO ACTION} or {@code RESTRICT} anywhere
 * in that tree turns the delete into a constraint violation, which the API answered with a bare 500
 * as soon as the event had such a row (newsletter sends in #1158). This walks the real migrated
 * schema: every table reachable from {@code events} through cascading foreign keys, and every
 * foreign key into one of those tables. Each such key must cascade or set null.
 */
class EventDeleteCascadeFitnessIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static final String BLOCKING_FOREIGN_KEYS = """
            WITH RECURSIVE fk AS (
                SELECT c.conname,
                       c.conrelid::regclass::text  AS child,
                       c.confrelid::regclass::text AS parent,
                       c.confdeltype               AS on_delete
                FROM pg_constraint c
                WHERE c.contype = 'f'
            ),
            deleted(tbl) AS (
                SELECT 'events'::text
                UNION
                SELECT fk.child FROM fk JOIN deleted d ON fk.parent = d.tbl WHERE fk.on_delete = 'c'
            )
            SELECT fk.child || '.' || fk.conname || ' -> ' || fk.parent
            FROM fk JOIN deleted d ON fk.parent = d.tbl
            WHERE fk.on_delete IN ('a', 'r')
            ORDER BY 1
            """;

    @Test
    @DisplayName("#824: every foreign key into the tables an event delete removes cascades or sets null")
    void eventDelete_isNotBlockedByAnyForeignKey() {
        List<String> blockers = jdbcTemplate.queryForList(BLOCKING_FOREIGN_KEYS, String.class);

        assertThat(blockers)
                .as("These foreign keys make DELETE /events/{code} fail with a constraint violation. "
                        + "Add ON DELETE CASCADE (history of the event) or SET NULL (a row that outlives "
                        + "the event) in a new migration.")
                .isEmpty();
    }

    @Test
    @DisplayName("#824: the cascade walk reaches the tables an event delete is known to remove")
    void cascadeWalk_reachesKnownEventTables() {
        List<String> reached = jdbcTemplate.queryForList("""
                WITH RECURSIVE deleted(tbl) AS (
                    SELECT 'events'::text
                    UNION
                    SELECT c.conrelid::regclass::text
                    FROM pg_constraint c JOIN deleted d ON c.confrelid::regclass::text = d.tbl
                    WHERE c.contype = 'f' AND c.confdeltype = 'c'
                )
                SELECT tbl FROM deleted
                """, String.class);

        // Guards the first test against passing vacuously (a wrong catalog join finds nothing).
        assertThat(reached).contains("sessions", "registrations", "speaker_pool", "newsletter_sends");
    }
}
