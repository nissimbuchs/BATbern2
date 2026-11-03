package ch.batbern.events.mailcenter.inbound;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;

/**
 * Counts automatic inbound mail per event (Story 16.2a AC7): one row per mail, without address,
 * subject or body. A redelivered mail has the same dedupe key and is counted once.
 */
@Component
@RequiredArgsConstructor
public class AutomaticMailCounter {

    /** What was counted; the V139/V143 CHECK lists the same values. */
    public enum Kind {
        OUT_OF_OFFICE, BOUNCE, CALENDAR_ACCEPTED, CALENDAR_DECLINED, CALENDAR_TENTATIVE,
        KEYWORD_ACCEPT, KEYWORD_CANCEL, UNSUBSCRIBE, LOOP,
        /** Story 16.7 (V143): SES verdicts FAIL. Spam is also stored (Spam list); a virus is only counted. */
        SPAM, VIRUS
    }

    private static final String INSERT = """
            INSERT INTO mail_automatic_event (dedupe_key, event_code, kind)
            VALUES (:dedupeKey, :eventCode, :kind)
            ON CONFLICT (dedupe_key) DO NOTHING
            """;

    private final NamedParameterJdbcTemplate jdbc;

    /**
     * Retention (Story 16.2a AC14): the same rule as recorded mail, on {@code received_at}. Runs in
     * the caller's transaction ({@code MailMessageRetentionScheduler}).
     */
    public int deleteExpired(Instant cutoff) {
        return jdbc.update("""
                DELETE FROM mail_automatic_event a WHERE a.received_at < :cutoff AND NOT EXISTS (
                    SELECT 1 FROM events e WHERE e.event_code = a.event_code AND e.event_date >= :cutoff)
                """, new MapSqlParameterSource("cutoff", Timestamp.from(cutoff)));
    }

    /** @return whether the mail was counted now (false: counted before) */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean count(Kind kind, String eventCode, String dedupeKey) {
        return jdbc.update(INSERT, new MapSqlParameterSource()
                .addValue("dedupeKey", dedupeKey)
                .addValue("eventCode", eventCode)
                .addValue("kind", kind.name())) > 0;
    }
}
