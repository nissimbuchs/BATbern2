package ch.batbern.events.usermerge;

import ch.batbern.events.config.CacheConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * #1251: event-management's part of the company-reference contract CUMS owns (ADR-016 decision 5,
 * {@code /internal/company-references/*} in {@code docs/api/internal/cums-internal.openapi.yml}).
 * When CUMS merges two companies or renames one, the company key event-management keeps follows to
 * the target: {@code registrations.attendee_company_id}, the attendee's company written at
 * registration (search and badges). {@code speaker_pool.company} is free text (display names), not
 * a key, and stays. Nothing here is unique per company, so a move never conflicts and never blocks;
 * a rerun finds nothing left to move. No log of its own: the moved ids go back to CUMS, which logs
 * the merge.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CompanyReferenceService {

    /** The column that holds a company key, as {@code table.column}. */
    static final String REGISTRATIONS = "registrations.attendee_company_id";

    /** A company key (the slug rule of CUMS V28). */
    private static final Pattern COMPANY_KEY = Pattern.compile("^[a-zA-Z0-9]{1,12}$");

    private final JdbcTemplate jdbc;
    private final CacheManager cacheManager;

    public record Preview(Map<String, Integer> moves, Map<String, Integer> conflicts, List<String> blockers) {
    }

    public record Result(Map<String, Integer> moved, Map<String, Integer> removed,
            Map<String, List<String>> movedKeys, Map<String, List<String>> removedKeys) {
    }

    /** Counts what a move would change; changes nothing. */
    @Transactional(readOnly = true)
    public Preview preview(String source, String target) {
        requireValid(source, target);
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM registrations WHERE attendee_company_id = ?",
                Integer.class, source);
        return new Preview(Map.of(REGISTRATIONS, n == null ? 0 : n), Map.of(), List.of());
    }

    /** Rewrites the source key to the target, in one transaction; caches cleared after the commit. Idempotent. */
    @Transactional
    public Result merge(String source, String target, String performedBy, String trigger) {
        requireValid(source, target);
        List<String> ids = jdbc.queryForList("UPDATE registrations SET attendee_company_id = ?, updated_at = NOW() "
                + "WHERE attendee_company_id = ? RETURNING id::text", String.class, target, source);
        Map<String, Integer> moved = new LinkedHashMap<>();
        moved.put(REGISTRATIONS, ids.size());
        Map<String, List<String>> movedKeys = ids.isEmpty() ? Map.of() : Map.of(REGISTRATIONS, ids);
        // Cached user and company reads carry the company key; a merge is rare, so all of them.
        CacheConfig.clearAfterCommit(cacheManager, cacheManager.getCacheNames().toArray(String[]::new));
        log.info("COMPANY_REFERENCES_MOVED event-management source={} target={} trigger={} by={} moved={}",
                source, target, trigger, performedBy, moved);
        return new Result(moved, Map.of(), movedKeys, Map.of());
    }

    private static void requireValid(String source, String target) {
        if (source == null || source.isBlank() || target == null || source.equals(target)) {
            throw new IllegalArgumentException("Source and target must be two different company keys");
        }
        if (!COMPANY_KEY.matcher(target).matches()) {
            throw new IllegalArgumentException("Target is not a company key: " + target);
        }
    }
}
