package ch.batbern.events.mailcenter;

import ch.batbern.events.exception.EventNotFoundException;
import ch.batbern.events.repository.EventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Story 16.3: what went out for an event, newest first. Reads {@code mail_message} (Story 16.1a,
 * delivery status from 16.1b) and, for newsletter sends made before the record existed,
 * {@code newsletter_sends} (read-time union, no data migration, AC6).
 *
 * <p>Automatic mails (no sender, from a template, not a newsletter) are collapsed per template and
 * day (Europe/Zurich) unless {@code grouped=false}. Newsletter counts come from
 * {@code newsletter_recipients}: failed = refused by SES or bounced, delivered = the rest, because the
 * newsletter configuration set reports no deliveries.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MailMessageQueryService {

    /** Key prefix of a newsletter send without a record; the rest is the send id. */
    static final String NEWSLETTER_KEY_PREFIX = "newsletter-";

    private static final String ZONE = "Europe/Zurich";

    /** One row per recorded mail, with its counts; newsletter counts from newsletter_recipients. */
    private static final String RECORDED = """
            SELECT m.mail_key, m.created_at AS sent_at, m.sender_username, m.audience, m.template_key,
                   m.content_source, m.subject, m.newsletter_send_id, m.is_test,
                   COALESCE(ns.recipient_count, m.recipient_count) AS recipient_count,
                   CASE WHEN m.newsletter_send_id IS NULL THEN COALESCE(r.delivered, 0)
                        ELSE COALESCE(nr.total, 0) - COALESCE(nr.failed, 0) END AS delivered,
                   CASE WHEN m.newsletter_send_id IS NULL THEN COALESCE(r.failed, 0)
                        ELSE COALESCE(nr.failed, 0) END AS failed,
                   ns.status AS newsletter_status,
                   CASE WHEN :grouped AND m.sender_username IS NULL AND m.content_source = 'TEMPLATE'
                             AND m.newsletter_send_id IS NULL AND m.template_key IS NOT NULL
                        THEN m.template_key || '|' || to_char(m.created_at AT TIME ZONE '%1$s', 'YYYY-MM-DD')
                        ELSE m.mail_key END AS grp
              FROM mail_message m
              LEFT JOIN (SELECT mail_message_id,
                                count(*) FILTER (WHERE status = 'DELIVERED') AS delivered,
                                count(*) FILTER (WHERE status = 'FAILED') AS failed
                           FROM mail_recipient GROUP BY mail_message_id) r ON r.mail_message_id = m.id
              LEFT JOIN newsletter_sends ns ON ns.id = m.newsletter_send_id
              LEFT JOIN (SELECT send_id, count(*) AS total,
                                count(*) FILTER (WHERE delivery_status = 'failed' OR bounce_type IS NOT NULL) AS failed
                           FROM newsletter_recipients GROUP BY send_id) nr ON nr.send_id = m.newsletter_send_id
             WHERE m.event_code = :eventCode AND m.direction = 'OUT'
            """.formatted(ZONE);

    /** Newsletter-table sends (newsletter and registrant notices) the record does not know (AC6). */
    private static final String BACKFILL = """
            SELECT '%1$s' || s.id AS mail_key, s.sent_at, s.sent_by_username,
                   CASE WHEN t.category = 'REGISTRANT_NOTICE' THEN 'REGISTRANTS' ELSE 'NEWSLETTER' END AS audience,
                   s.template_key, 'TEMPLATE' AS content_source, NULL AS subject, s.id AS newsletter_send_id,
                   s.test_mode AS is_test, COALESCE(s.recipient_count, 0) AS recipient_count,
                   COALESCE(nr.total, 0) - COALESCE(nr.failed, 0) AS delivered, COALESCE(nr.failed, 0) AS failed,
                   s.status AS newsletter_status, '%1$s' || s.id AS grp
              FROM newsletter_sends s
              JOIN events e ON e.id = s.event_id
              LEFT JOIN (SELECT DISTINCT ON (template_key) template_key, category FROM email_templates
                          ORDER BY template_key) t ON t.template_key = s.template_key
              LEFT JOIN (SELECT send_id, count(*) AS total,
                                count(*) FILTER (WHERE delivery_status = 'failed' OR bounce_type IS NOT NULL) AS failed
                           FROM newsletter_recipients GROUP BY send_id) nr ON nr.send_id = s.id
             WHERE e.event_code = :eventCode
               AND NOT EXISTS (SELECT 1 FROM mail_message mm WHERE mm.newsletter_send_id = s.id)
            """.formatted(NEWSLETTER_KEY_PREFIX);

    /** Recorded + backfill, filtered; collapsed per grp, newest first. */
    private static final String ENTRIES = """
            WITH rows AS (%s UNION ALL %s),
                 filtered AS (
                   SELECT * FROM rows
                    WHERE (CAST(:includeTests AS BOOLEAN) OR NOT is_test)
                      AND (CAST(:audience AS TEXT) IS NULL OR audience = :audience)
                      AND (CAST(:templateKey AS TEXT) IS NULL OR template_key = :templateKey)
                      AND (CAST(:day AS DATE) IS NULL
                           OR CAST(sent_at AT TIME ZONE '%s' AS DATE) = CAST(:day AS DATE))),
                 entries AS (
                   SELECT (array_agg(mail_key ORDER BY sent_at DESC))[1] AS mail_key,
                          max(sent_at) AS sent_at,
                          (array_agg(sender_username ORDER BY sent_at DESC))[1] AS sender_username,
                          (array_agg(audience ORDER BY sent_at DESC))[1] AS audience,
                          (array_agg(template_key ORDER BY sent_at DESC))[1] AS template_key,
                          (array_agg(content_source ORDER BY sent_at DESC))[1] AS content_source,
                          (array_agg(subject ORDER BY sent_at DESC))[1] AS subject,
                          (array_agg(newsletter_send_id ORDER BY sent_at DESC))[1] AS newsletter_send_id,
                          (array_agg(newsletter_status ORDER BY sent_at DESC))[1] AS newsletter_status,
                          bool_or(is_test) AS is_test,
                          sum(recipient_count) AS recipient_count, sum(delivered) AS delivered,
                          sum(failed) AS failed, count(*) AS message_count
                     FROM filtered GROUP BY grp)
            """.formatted(RECORDED, BACKFILL, ZONE);

    private final NamedParameterJdbcTemplate jdbc;
    private final EventRepository events;

    public MailPage list(String eventCode, MailFilter filter, int page, int limit) {
        requireEvent(eventCode);
        MapSqlParameterSource params = params(eventCode, filter.grouped(), filter.includeTests())
                .addValue("audience", filter.audience())
                .addValue("templateKey", filter.templateKey())
                .addValue("day", filter.day())
                .addValue("limit", limit)
                .addValue("offset", (long) (page - 1) * limit);
        long total = Optional.ofNullable(jdbc.queryForObject(ENTRIES + " SELECT count(*) FROM entries",
                params, Long.class)).orElse(0L);
        List<MailEntry> data = jdbc.query(ENTRIES
                + " SELECT * FROM entries ORDER BY sent_at DESC, mail_key DESC LIMIT :limit OFFSET :offset",
                params, MailMessageQueryService::entry);
        return new MailPage(data, total);
    }

    /** Failed deliveries of the event's mail, newsletter included (AC3); test sends only on request. */
    public long failedDeliveries(String eventCode, boolean includeTests) {
        requireEvent(eventCode);
        return Optional.ofNullable(jdbc.queryForObject(ENTRIES + " SELECT COALESCE(sum(failed), 0) FROM entries",
                params(eventCode, false, includeTests).addValue("audience", null).addValue("templateKey", null)
                        .addValue("day", null), Long.class)).orElse(0L);
    }

    public Optional<MailDetail> get(String eventCode, String mailKey) {
        requireEvent(eventCode);
        // A test send opens by its key even though the list hides it (Story 16.8 AC9).
        MapSqlParameterSource params = params(eventCode, false, true).addValue("audience", null)
                .addValue("templateKey", null).addValue("day", null).addValue("mailKey", mailKey);
        List<MailEntry> found = jdbc.query(ENTRIES + " SELECT * FROM entries WHERE mail_key = :mailKey",
                params, MailMessageQueryService::entry);
        if (found.isEmpty()) {
            return Optional.empty();
        }
        MailEntry entry = found.get(0);
        MailProgress progress = progress(entry);
        if (mailKey.startsWith(NEWSLETTER_KEY_PREFIX)) {
            return Optional.of(new MailDetail(entry, null, failedNewsletterRecipients(entry.newsletterSendId()),
                    progress));
        }
        String body = jdbc.queryForObject("SELECT body_html FROM mail_message WHERE mail_key = :mailKey",
                params, String.class);
        List<RecipientEntry> recipients = entry.newsletterSendId() != null
                ? failedNewsletterRecipients(entry.newsletterSendId())
                : jdbc.query("""
                        SELECT r.address, r.kind, r.status, r.failure_reason
                          FROM mail_recipient r JOIN mail_message m ON m.id = r.mail_message_id
                         WHERE m.mail_key = :mailKey
                         ORDER BY r.kind DESC, r.address
                        """, params, (rs, n) -> new RecipientEntry(rs.getString("address"), rs.getString("kind"),
                        rs.getString("status"), rs.getString("failure_reason")));
        return Optional.of(new MailDetail(entry, body, recipients, progress));
    }

    /**
     * Story 16.8 AC8: progress of a newsletter or registrant notice from its send row. Retryable: a
     * newsletter that ended PARTIAL or FAILED while no other send of the event is running.
     */
    private MailProgress progress(MailEntry entry) {
        if (entry.newsletterSendId() == null) {
            return null;
        }
        List<MailProgress> found = jdbc.query("""
                SELECT s.status, COALESCE(s.recipient_count, 0) AS total, s.sent_count, s.failed_count,
                       s.status IN ('PARTIAL', 'FAILED')
                         AND NOT EXISTS (SELECT 1 FROM newsletter_sends o WHERE o.event_id = s.event_id
                                          AND o.status IN ('PENDING', 'IN_PROGRESS')) AS idle_and_unfinished
                  FROM newsletter_sends s WHERE s.id = :sendId
                """, new MapSqlParameterSource("sendId", entry.newsletterSendId()), (rs, n) -> new MailProgress(
                rs.getString("status"), rs.getInt("total"), rs.getInt("sent_count"), rs.getInt("failed_count"),
                rs.getBoolean("idle_and_unfinished") && "NEWSLETTER".equals(entry.audience())));
        return found.isEmpty() ? null : found.get(0);
    }

    private List<RecipientEntry> failedNewsletterRecipients(UUID sendId) {
        return jdbc.query("""
                SELECT email, delivery_status, bounce_type FROM newsletter_recipients
                 WHERE send_id = :sendId AND (delivery_status = 'failed' OR bounce_type IS NOT NULL)
                 ORDER BY email
                """, new MapSqlParameterSource("sendId", sendId), (rs, n) -> new RecipientEntry(
                rs.getString("email"), "TO", "FAILED",
                rs.getString("bounce_type") != null ? "bounce " + rs.getString("bounce_type") : "not sent"));
    }

    private void requireEvent(String eventCode) {
        if (!events.existsByEventCode(eventCode)) {
            throw new EventNotFoundException("Event not found: " + eventCode);
        }
    }

    private static MapSqlParameterSource params(String eventCode, boolean grouped, boolean includeTests) {
        return new MapSqlParameterSource("eventCode", eventCode).addValue("grouped", grouped)
                .addValue("includeTests", includeTests);
    }

    private static MailEntry entry(ResultSet rs, int n) throws SQLException {
        return new MailEntry(rs.getString("mail_key"), rs.getTimestamp("sent_at").toInstant(),
                rs.getString("sender_username"), rs.getString("audience"), rs.getString("template_key"),
                rs.getString("content_source"), rs.getString("subject"), rs.getInt("recipient_count"),
                rs.getInt("delivered"), rs.getInt("failed"), rs.getInt("message_count"),
                rs.getObject("newsletter_send_id", UUID.class), rs.getString("newsletter_status"),
                rs.getBoolean("is_test"));
    }

    /** Filters of the list operation; null means no filter. Test sends are hidden unless asked for. */
    public record MailFilter(String audience, String templateKey, LocalDate day, boolean grouped,
            boolean includeTests) {
    }

    public record MailPage(List<MailEntry> data, long total) {
    }

    public record MailEntry(String mailKey, Instant sentAt, String senderUsername, String audience,
            String templateKey, String contentSource, String subject, int recipientCount, int deliveredCount,
            int failedCount, int messageCount, UUID newsletterSendId, String newsletterStatus, boolean test) {
    }

    public record RecipientEntry(String address, String kind, String status, String failureReason) {
    }

    public record MailDetail(MailEntry entry, String bodyHtml, List<RecipientEntry> recipients,
            MailProgress progress) {
    }

    /** Send progress from {@code newsletter_sends} (Story 16.8). */
    public record MailProgress(String status, int total, int sent, int failed, boolean retryable) {
    }
}
