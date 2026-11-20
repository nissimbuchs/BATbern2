package ch.batbern.partners.usermerge;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * #1251: partner-coordination's part of the company-reference contract CUMS owns (ADR-016 decision
 * 5, {@code /internal/company-references/*} in {@code docs/api/internal/cums-internal.openapi.yml}).
 * When CUMS merges two companies or renames one, the company keys partner-coordination keeps follow
 * to the target: who suggested a topic, who voted for one, and the partner itself.
 *
 * <p>A topic vote is unique per topic and company: a vote of the source on a topic the target voted
 * for too is removed and logged, the target's stays. The partner moves only when the source alone
 * is one; both companies partners refuses (409 {@code MERGE_BLOCKED_BOTH_PARTNERS}), because partner
 * notes, meetings and RSVPs hang on the partner id and merging two partners is a decision of its own
 * (owner 2026-11-07). Idempotent: a rerun finds nothing left to move. No log of its own: the moved
 * and removed ids go back to CUMS, which logs the merge.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CompanyReferenceService {

    static final String SUGGESTIONS = "topic_suggestions.company_name";
    static final String VOTES = "topic_votes.company_name";
    static final String PARTNERS = "partners.company_name";
    static final String BOTH_PARTNERS = "MERGE_BLOCKED_BOTH_PARTNERS";

    /** A company key (the slug rule of CUMS V28); {@code partners.company_name} holds 12 characters. */
    private static final Pattern COMPANY_KEY = Pattern.compile("^[a-zA-Z0-9]{1,12}$");

    private static final String VOTE_COLLISION = "s.company_name = ? AND EXISTS (SELECT 1 FROM topic_votes t "
            + "WHERE t.company_name = ? AND t.topic_id = s.topic_id)";

    private final JdbcTemplate jdbc;
    private final CacheManager cacheManager;

    public record Preview(Map<String, Integer> moves, Map<String, Integer> conflicts, List<String> blockers) {
    }

    public record Result(Map<String, Integer> moved, Map<String, Integer> removed,
            Map<String, List<String>> movedKeys, Map<String, List<String>> removedKeys) {
    }

    /** Counts what a move would change and which votes collide; changes nothing. */
    @Transactional(readOnly = true)
    public Preview preview(String source, String target) {
        requireValid(source, target);
        Map<String, Integer> moves = new LinkedHashMap<>();
        moves.put(SUGGESTIONS, count("SELECT COUNT(*) FROM topic_suggestions WHERE company_name = ?", source));
        moves.put(VOTES, count("SELECT COUNT(*) FROM topic_votes WHERE company_name = ?", source));
        moves.put(PARTNERS, count("SELECT COUNT(*) FROM partners WHERE company_name = ?", source));
        int collisions = count("SELECT COUNT(*) FROM topic_votes s WHERE " + VOTE_COLLISION, source, target);
        Map<String, Integer> conflicts = collisions == 0 ? Map.of() : Map.of("topic_votes", collisions);
        List<String> blockers = bothPartners(source, target) ? List.of(BOTH_PARTNERS) : List.of();
        return new Preview(moves, conflicts, blockers);
    }

    /** Rewrites the source key to the target, in one transaction; caches cleared after the commit. Idempotent. */
    @Transactional
    public Result merge(String source, String target, String performedBy, String trigger) {
        requireValid(source, target);
        if (bothPartners(source, target)) {
            throw new BothPartnersException("Both companies are partners (" + source + ", " + target
                    + "); merge the partners first");
        }
        Map<String, Integer> moved = new LinkedHashMap<>();
        Map<String, Integer> removed = new LinkedHashMap<>();
        Map<String, List<String>> movedKeys = new LinkedHashMap<>();
        Map<String, List<String>> removedKeys = new LinkedHashMap<>();

        List<String> dropped = jdbc.queryForList("DELETE FROM topic_votes s WHERE " + VOTE_COLLISION
                + " RETURNING topic_id::text", String.class, source, target);
        if (!dropped.isEmpty()) {
            removed.put(VOTES, dropped.size());
            removedKeys.put(VOTES, dropped);
            log.info("COMPANY_REFERENCES vote collision source={} target={} removedSourceVotesOnTopics={}",
                    source, target, dropped);
        }
        move(SUGGESTIONS, "UPDATE topic_suggestions SET company_name = ? WHERE company_name = ? RETURNING id::text",
                source, target, moved, movedKeys);
        move(VOTES, "UPDATE topic_votes SET company_name = ? WHERE company_name = ? RETURNING topic_id::text",
                source, target, moved, movedKeys);
        move(PARTNERS, "UPDATE partners SET company_name = ? WHERE company_name = ? RETURNING id::text",
                source, target, moved, movedKeys);

        clearCachesAfterCommit();
        log.info("COMPANY_REFERENCES_MOVED partner-coordination source={} target={} trigger={} by={} moved={} "
                + "removed={}", source, target, trigger, performedBy, moved, removed);
        return new Result(moved, removed, movedKeys, removedKeys);
    }

    private void move(String column, String sql, String source, String target, Map<String, Integer> moved,
            Map<String, List<String>> movedKeys) {
        List<String> ids = jdbc.queryForList(sql, String.class, target, source);
        moved.put(column, ids.size());
        if (!ids.isEmpty()) {
            movedKeys.put(column, ids);
        }
    }

    private boolean bothPartners(String source, String target) {
        return count("SELECT COUNT(*) FROM partners WHERE company_name IN (?, ?)", source, target) == 2;
    }

    private int count(String sql, Object... args) {
        Integer n = jdbc.queryForObject(sql, Integer.class, args);
        return n == null ? 0 : n;
    }

    /**
     * Partners, company reads, users by company and attendance are cached by company key; a merge
     * is rare, so all caches. After the commit: a clear inside the transaction lets a concurrent read
     * put the old state back before the commit.
     */
    private void clearCachesAfterCommit() {
        Runnable clear = () -> cacheManager.getCacheNames().stream().map(cacheManager::getCache)
                .filter(Objects::nonNull).forEach(Cache::clear);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    clear.run();
                }
            });
        } else {
            clear.run();
        }
    }

    private static void requireValid(String source, String target) {
        if (source == null || source.isBlank() || target == null || source.equals(target)) {
            throw new IllegalArgumentException("Source and target must be two different company keys");
        }
        if (!COMPANY_KEY.matcher(target).matches()) {
            throw new IllegalArgumentException("Target is not a company key: " + target);
        }
    }

    /** 409: both companies are partners. */
    public static class BothPartnersException extends RuntimeException {
        public BothPartnersException(String message) {
            super(message);
        }
    }
}
