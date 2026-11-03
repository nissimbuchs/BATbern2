package ch.batbern.events.usermerge;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * #1183 C/D: moves everything event-management holds under one username to another, because both
 * profiles belong to the same person. company-user-management decides that and calls this through
 * {@code POST /internal/users/merge}; it deletes the source profile afterwards.
 *
 * <p>What moves: the person's own rows (registrations, speaker slots, arrivals, notifications, Q&A,
 * proposals, thanks, newsletter rows). What does not: audit columns that record
 * who did something at the time ({@code created_by}, {@code changed_by_username}, ...), and organizer
 * columns, since the source of a merge never has a login and so never acted as organizer. Partner
 * topic suggestions live in partner-coordination and are made by partner logins, so they never
 * belong to a source either.
 *
 * <p>Where the target already holds a row with the same key (the same session, the same event), the
 * source row is removed. Registrations are the exception: the better one stays (attended, then
 * confirmed, registered, waitlist, cancelled; the target's on a tie), because two registrations for
 * one event and one user break every single-result lookup. Every removed row is kept in full in
 * {@code user_merge_log.removed_rows}, every moved row by its key in {@code moved}, so a merge can be
 * undone by hand.
 *
 * <p>Refused (409): both lead a different talk at the same event. Picking one would silently
 * drop a speaker from a session; an organizer has to resolve that first.
 *
 * <p>Idempotent: run again after a failed profile step, it moves nothing and succeeds.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserMergeService {

    /** A column that holds the person's username. {@code key} is the column that must stay unique per user. */
    record Move(String table, String column, String idExpr, String key) {
        String name() {
            return table + "." + column;
        }
    }

    static final List<Move> MOVES = List.of(
            new Move("registrations", "attendee_username", "id::text", "event_id"),
            new Move("session_users", "username", "id::text", "session_id"),
            new Move("speaker_arrivals", "speaker_username", "id::text", "event_code"),
            new Move("session_qna_notification", "recipient_username", "window_id::text", "window_id"),
            new Move("live_timing_presence", "username", "event_code", "event_code"),
            new Move("session_proposals", "proposed_by_username", "id::text", "event_id"),
            new Move("organizer_thanks", "thanked_by_username", "id::text", "event_id"),
            new Move("notifications", "recipient_username", "id::text", null),
            new Move("session_qna_post", "posted_by_username", "id::text", null),
            new Move("session_content_history", "submitted_by_username", "id::text", null),
            new Move("newsletter_subscribers", "username", "id::text", null));

    private static final String RANK = "CASE lower(%s.status) WHEN 'attended' THEN 5 WHEN 'confirmed' THEN 4 "
            + "WHEN 'registered' THEN 3 WHEN 'waitlist' THEN 2 ELSE 1 END";

    private static final String BLOCKERS = """
            SELECT DISTINCT s.event_code
            FROM session_users a JOIN sessions s ON s.id = a.session_id
            JOIN session_users b ON b.username = :target AND b.speaker_role = 'primary_speaker'
            JOIN sessions t ON t.id = b.session_id AND t.event_id = s.event_id AND t.id <> s.id
            WHERE a.username = :source AND a.speaker_role = 'primary_speaker'
            ORDER BY s.event_code
            """;

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final CacheManager cacheManager;

    public record Preview(Map<String, Integer> moves, Map<String, Integer> conflicts, List<String> blockers) {
    }

    public record Result(Map<String, Integer> moved, Map<String, Integer> removed) {
    }

    /** Counts what a merge would move and which rows collide; changes nothing. */
    @Transactional(readOnly = true)
    public Preview preview(String source, String target) {
        requireDistinct(source, target);
        MapSqlParameterSource p = params(source, target);
        Map<String, Integer> moves = new LinkedHashMap<>();
        Map<String, Integer> conflicts = new LinkedHashMap<>();
        for (Move m : MOVES) {
            moves.put(m.name(), queryInt("SELECT COUNT(*) FROM " + m.table() + " WHERE " + m.column()
                    + " = :source", p));
            if (m.key() != null) {
                int clashes = queryInt("SELECT COUNT(*) FROM " + m.table() + " s WHERE s." + m.column()
                        + " = :source AND EXISTS (SELECT 1 FROM " + m.table() + " t WHERE t." + m.column()
                        + " = :target AND t." + m.key() + " = s." + m.key() + ")", p);
                if (clashes > 0) {
                    conflicts.put(m.table(), clashes);
                }
            }
        }
        return new Preview(moves, conflicts, jdbc.queryForList(BLOCKERS, p, String.class));
    }

    @Transactional
    public Result merge(String source, String target, String performedBy, String trigger, Object sourceProfile) {
        requireDistinct(source, target);
        MapSqlParameterSource p = params(source, target);
        List<String> blockers = jdbc.queryForList(BLOCKERS, p, String.class);
        if (!blockers.isEmpty()) {
            throw new MergeBlockedException("Both profiles lead a different talk at " + String.join(", ", blockers)
                    + ". Remove one of them from the talk first.");
        }

        List<Map<String, Object>> removedRows = new ArrayList<>();
        Map<String, Integer> removed = new LinkedHashMap<>();
        Map<String, Object> moved = new LinkedHashMap<>();
        Map<String, Integer> movedCounts = new LinkedHashMap<>();

        // Registrations: where the source holds the better one, the target's goes.
        removeRows("registrations", "DELETE FROM registrations t WHERE t.attendee_username = :target AND EXISTS ("
                + "SELECT 1 FROM registrations s WHERE s.attendee_username = :source AND s.event_id = t.event_id "
                + "AND " + RANK.formatted("s") + " > " + RANK.formatted("t") + ") RETURNING to_jsonb(t)::text",
                p, removedRows, removed);

        for (Move m : MOVES) {
            if (m.key() != null) {
                removeRows(m.table(), "DELETE FROM " + m.table() + " s WHERE s." + m.column() + " = :source "
                        + "AND EXISTS (SELECT 1 FROM " + m.table() + " t WHERE t." + m.column() + " = :target "
                        + "AND t." + m.key() + " = s." + m.key() + ") RETURNING to_jsonb(s)::text",
                        p, removedRows, removed);
            }
            List<String> keys = jdbc.queryForList("UPDATE " + m.table() + " SET " + m.column() + " = :target WHERE "
                    + m.column() + " = :source RETURNING " + m.idExpr(), p, String.class);
            moved.put(m.name(), keys);
            movedCounts.put(m.name(), keys.size());
        }

        jdbc.update("INSERT INTO user_merge_log (source_username, target_username, performed_by, trigger, moved, "
                + "removed_rows, source_profile) VALUES (:source, :target, :by, :trigger, CAST(:moved AS jsonb), "
                + "CAST(:removed AS jsonb), CAST(:profile AS jsonb))",
                p.addValue("by", performedBy).addValue("trigger", trigger)
                        .addValue("moved", json(moved))
                        .addValue("removed", "[" + String.join(",", removedRows.stream()
                                .map(r -> "{\"table\": " + json(r.get("table")) + ", \"row\": " + r.get("row") + "}")
                                .toList()) + "]")
                        .addValue("profile", sourceProfile == null ? null : json(sourceProfile)));

        evictCaches();
        log.info("USER_MERGE source={} target={} trigger={} moved={} removed={}",
                source, target, trigger, movedCounts, removed);
        return new Result(movedCounts, removed);
    }

    private void removeRows(String table, String sql, MapSqlParameterSource p,
            List<Map<String, Object>> removedRows, Map<String, Integer> removed) {
        List<String> rows = jdbc.queryForList(sql, p, String.class);
        for (String row : rows) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("table", table);
            entry.put("row", row);
            removedRows.add(entry);
        }
        if (!rows.isEmpty()) {
            removed.merge(table, rows.size(), Integer::sum);
        }
    }

    /**
     * Everything cached per username or per event lists people by username: the user cache of
     * company-user-management lookups and the event pages with speakers. A merge is rare, so all of it.
     */
    private void evictCaches() {
        for (String name : cacheManager.getCacheNames()) {
            var cache = cacheManager.getCache(name);
            if (cache != null) {
                cache.clear();
            }
        }
    }

    private int queryInt(String sql, MapSqlParameterSource p) {
        Integer n = jdbc.queryForObject(sql, p, Integer.class);
        return n == null ? 0 : n;
    }

    private static MapSqlParameterSource params(String source, String target) {
        return new MapSqlParameterSource().addValue("source", source).addValue("target", target);
    }

    private static void requireDistinct(String source, String target) {
        if (source == null || source.isBlank() || target == null || target.isBlank() || source.equals(target)) {
            throw new IllegalArgumentException("Source and target must be two different usernames");
        }
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Merge log not serialisable", e);
        }
    }

    /** 409: the merge would drop a speaker from a talk. */
    public static class MergeBlockedException extends RuntimeException {
        public MergeBlockedException(String message) {
            super(message);
        }
    }
}
