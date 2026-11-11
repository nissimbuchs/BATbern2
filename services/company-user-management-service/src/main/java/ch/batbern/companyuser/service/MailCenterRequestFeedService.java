package ch.batbern.companyuser.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

/**
 * ADR-016 decision 4: the change feed of profile merge requests (#1237) and
 * company logo requests (#1246), which event-management pulls to open and close their Mail Center
 * conversations. CUMS no longer calls event-management for them.
 *
 * <p>Both tables, in one order: {@code (changed_at, id)}, set by the V30 trigger when a row is
 * created or decided. The cursor is that pair of the last item, opaque to the caller. A row whose
 * {@code changed_at} is younger than the settle window is held back: {@code changed_at} is taken
 * before the commit, so a transaction still running could otherwise commit a row behind a cursor
 * the caller already has. Request transactions are short; {@value #DEFAULT_SETTLE_SECONDS} s is
 * plenty, and the tests set it to 0.
 *
 * <p>Without a cursor the feed starts just before the oldest OPEN request, or at the settle
 * boundary when none is open: open requests are delivered, decided history is not (the former
 * push already handled it).
 *
 * <p>Requester name and address are read from the profile at feed time; the subject is the text
 * the former push sent. A request whose requester profile is gone is delivered with both null, so
 * its decision still reaches the Mail Center.
 */
@Service
@Slf4j
public class MailCenterRequestFeedService {

    static final int DEFAULT_SETTLE_SECONDS = 30;
    public static final int MAX_LIMIT = 200;
    public static final int DEFAULT_LIMIT = 100;
    private static final UUID NIL = new UUID(0L, 0L);

    private static final String FEED = """
            SELECT x.kind, x.id, x.status, x.note, x.decided_by, x.changed_at, x.company_name,
                   u.first_name, u.last_name, u.email, c.display_name
            FROM (
                SELECT 'MERGE' AS kind, r.id, r.status, r.requester_username, r.note, r.decided_by,
                       r.changed_at, NULL::varchar AS company_name
                FROM profile_merge_requests r
                WHERE (r.changed_at, r.id) > (:since, :sinceId) AND r.changed_at <= :until
                UNION ALL
                SELECT 'LOGO', r.id, r.status, r.requester_username, r.note, r.decided_by,
                       r.changed_at, r.company_name
                FROM company_logo_requests r
                WHERE (r.changed_at, r.id) > (:since, :sinceId) AND r.changed_at <= :until
            ) x
            LEFT JOIN user_profiles u ON u.username = x.requester_username
            LEFT JOIN companies c ON c.name = x.company_name
            ORDER BY x.changed_at, x.id
            LIMIT :limit
            """;

    private static final String OLDEST_OPEN = """
            SELECT min(changed_at) FROM (
                SELECT changed_at FROM profile_merge_requests WHERE status = 'OPEN'
                UNION ALL
                SELECT changed_at FROM company_logo_requests WHERE status = 'OPEN'
            ) o
            """;

    private final NamedParameterJdbcTemplate jdbc;
    private final int settleSeconds;

    public MailCenterRequestFeedService(NamedParameterJdbcTemplate jdbc,
            @Value("${batbern.mail-center-request-feed.settle-seconds:" + DEFAULT_SETTLE_SECONDS + "}")
            int settleSeconds) {
        this.jdbc = jdbc;
        this.settleSeconds = settleSeconds;
    }

    public enum Kind { MERGE, LOGO }

    public record Item(Kind kind, UUID requestId, String status, String requesterName, String requesterEmail,
            String subject, String note, String decidedBy, String changedAt) {
    }

    public record Page(List<Item> items, String nextCursor) {
    }

    /** {@code limit} is outside 1 to {@value #MAX_LIMIT}. */
    public static class InvalidLimitException extends RuntimeException {
        public InvalidLimitException(String message) {
            super(message);
        }
    }

    /** The cursor could not be read; the caller must send one it received, unchanged. */
    public static class InvalidCursorException extends RuntimeException {
        public InvalidCursorException(String message) {
            super(message);
        }
    }

    /** Up to {@code limit} items after {@code since} (no cursor: the start described above). */
    @Transactional(readOnly = true)
    public Page page(String since, int limit) {
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new InvalidLimitException("limit must be between 1 and " + MAX_LIMIT);
        }
        Instant until = dbNow().minusSeconds(settleSeconds);
        Cursor from = since == null || since.isBlank() ? start(until) : Cursor.decode(since);
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("since", Timestamp.from(from.changedAt()))
                .addValue("sinceId", from.id())
                .addValue("until", Timestamp.from(until))
                .addValue("limit", limit);
        List<Row> rows = jdbc.query(FEED, params, MailCenterRequestFeedService::row);
        List<Item> items = rows.stream().map(Row::item).toList();
        String next = rows.isEmpty() ? from.encode() : rows.get(rows.size() - 1).cursor().encode();
        log.debug("Mail Center request feed: since={} items={}", since, items.size());
        return new Page(items, next);
    }

    private Cursor start(Instant until) {
        Timestamp oldestOpen = jdbc.queryForObject(OLDEST_OPEN, new MapSqlParameterSource(), Timestamp.class);
        return new Cursor(oldestOpen == null ? until : earlier(oldestOpen.toInstant(), until), NIL);
    }

    /** Just before the oldest open request, never after the settle boundary. */
    private static Instant earlier(Instant oldestOpen, Instant until) {
        Instant justBefore = oldestOpen.minusNanos(1_000);
        return justBefore.isBefore(until) ? justBefore : until;
    }

    private Instant dbNow() {
        return jdbc.queryForObject("SELECT clock_timestamp()", new MapSqlParameterSource(), Timestamp.class)
                .toInstant();
    }

    private record Row(Item item, Cursor cursor) {
    }

    private static Row row(ResultSet rs, int n) throws SQLException {
        Kind kind = Kind.valueOf(rs.getString("kind"));
        UUID id = rs.getObject("id", UUID.class);
        Instant changedAt = rs.getTimestamp("changed_at").toInstant();
        String email = rs.getString("email");
        String name = email == null ? null
                : (nullToEmpty(rs.getString("first_name")) + " " + nullToEmpty(rs.getString("last_name"))).trim();
        String shown = name == null ? "?" : name;
        String subject = switch (kind) {
            case MERGE -> "Profil zusammenführen: " + shown;
            case LOGO -> "Neues Logo für " + companyDisplayName(rs.getString("display_name"),
                    rs.getString("company_name")) + ": " + shown;
        };
        Item item = new Item(kind, id, rs.getString("status"), name, email, subject,
                rs.getString("note"), rs.getString("decided_by"), changedAt.toString());
        return new Row(item, new Cursor(changedAt, id));
    }

    private static String companyDisplayName(String displayName, String name) {
        return displayName != null && !displayName.isBlank() ? displayName : name;
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    /** {@code (changed_at, id)}, sent as base64url of {@code <instant>|<uuid>}. */
    record Cursor(Instant changedAt, UUID id) {

        String encode() {
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString((changedAt + "|" + id).getBytes(StandardCharsets.UTF_8));
        }

        static Cursor decode(String value) {
            try {
                String raw = new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
                int bar = raw.indexOf('|');
                if (bar < 0) {
                    throw new InvalidCursorException("Unknown cursor");
                }
                return new Cursor(Instant.parse(raw.substring(0, bar)), UUID.fromString(raw.substring(bar + 1)));
            } catch (IllegalArgumentException | DateTimeParseException e) {
                throw new InvalidCursorException("Unknown cursor");
            }
        }
    }
}
