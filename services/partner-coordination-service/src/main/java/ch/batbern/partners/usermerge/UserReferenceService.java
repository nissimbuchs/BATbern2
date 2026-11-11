package ch.batbern.partners.usermerge;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * partner-coordination's part of the user-reference contract CUMS owns (ADR-016 decision 5, Epic 17
 * entry 7b): when CUMS merges two profiles of one person, the usernames partner-coordination keeps
 * follow to the target. Three columns hold a username: who suggested a topic, who wrote a partner
 * note, who created a partner meeting. None of them is part of a unique key, so a merge never
 * conflicts and never blocks; a rerun finds nothing left to move. No merge log here: event-management's
 * {@code user_merge_log} is the record of the merge.
 *
 * <p>partner-coordination keeps nothing by e-mail address that follows a primary address, so a
 * primary-address change moves nothing (meeting RSVPs record the address the invite went to).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserReferenceService {

    /** The columns that hold a username, as {@code table.column}. */
    static final List<String> COLUMNS = List.of(
            "topic_suggestions.suggested_by",
            "partner_notes.author_username",
            "partner_meetings.created_by");

    private final JdbcTemplate jdbc;

    public record Preview(Map<String, Integer> moves, Map<String, Integer> conflicts, List<String> blockers) {
    }

    public record Result(Map<String, Integer> moved, Map<String, Integer> removed) {
    }

    /** Counts what a merge would move; changes nothing. */
    @Transactional(readOnly = true)
    public Preview preview(String source, String target) {
        requireDistinct(source, target);
        Map<String, Integer> moves = new LinkedHashMap<>();
        for (String column : COLUMNS) {
            Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM " + table(column) + " WHERE " + name(column)
                    + " = ?", Integer.class, source);
            moves.put(column, n == null ? 0 : n);
        }
        return new Preview(moves, Map.of(), List.of());
    }

    /** Rewrites the source username to the target in every column, in one transaction. Idempotent. */
    @Transactional
    public Result merge(String source, String target, String performedBy, String trigger) {
        requireDistinct(source, target);
        Map<String, Integer> moved = new LinkedHashMap<>();
        for (String column : COLUMNS) {
            moved.put(column, jdbc.update("UPDATE " + table(column) + " SET " + name(column) + " = ? WHERE "
                    + name(column) + " = ?", target, source));
        }
        log.info("USER_MERGE partner-coordination source={} target={} trigger={} by={} moved={}", source, target,
                trigger, performedBy, moved);
        return new Result(moved, Map.of());
    }

    private static String table(String column) {
        return column.substring(0, column.indexOf('.'));
    }

    private static String name(String column) {
        return column.substring(column.indexOf('.') + 1);
    }

    private static void requireDistinct(String source, String target) {
        if (source == null || source.isBlank() || target == null || target.isBlank() || source.equals(target)) {
            throw new IllegalArgumentException("Source and target must be two different usernames");
        }
    }
}
