package ch.batbern.companyuser.service;

import ch.batbern.companyuser.client.CompanyReferenceContract;
import ch.batbern.companyuser.domain.CompanyAlias;
import ch.batbern.shared.security.Actors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Moves every reference of one company key to another (#1251): the one path of a rename (story 3)
 * and a merge (story 4).
 *
 * <p>Order, so a failure leaves nothing a retry cannot finish:
 * <ol>
 *   <li>every holder of the company-reference contract previews the move; a blocker at any holder
 *       refuses it before any holder moves a row ({@link CompanyReferenceContract.MoveBlockedException});</li>
 *   <li>every holder moves its rows, in configured order, each in a transaction of its own
 *       ({@link CompanyReferenceContract.CallFailedException} when one does not complete);</li>
 *   <li>here, in the caller's transaction: {@code user_profiles}, {@code company_logo_requests},
 *       {@code activity_history} (COMPANY rows) and the company-logo uploads move to the new key
 *       (the set CUMS V28 moved; one list, {@code CUMS_HOLDERS}, also counted by {@link #countCums});
 *       an open logo request of a person who has one at the new key too is closed first (merge only,
 *       {@link #countCumsConflicts}).</li>
 * </ol>
 * If a holder or the caller fails, CUMS rolls back and the company keeps its old key; earlier holders
 * have moved their rows, and running the same move again finishes it (every holder is idempotent).
 * After the caller renamed or deleted the old key, {@link #keepAsAlias} keeps it findable.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CompanyKeyMover {

    private final CompanyReferenceContract holders;
    private final CompanyAliasService aliasService;
    private final JdbcTemplate jdbc;
    private final CacheManager cacheManager;

    /**
     * The CUMS rows that hold a company key: {@code table.column} (the key of {@link Moved#cums()}),
     * the move of one key to another (returning the moved row ids) and the count for a preview. The
     * one list a rename, a merge and a merge preview read (the set CUMS V28 moved).
     */
    private record CumsHolder(String key, String update, String count) {
    }

    private static final List<CumsHolder> CUMS_HOLDERS = List.of(
            new CumsHolder("user_profiles.company_id",
                    "UPDATE user_profiles SET company_id = ? WHERE company_id = ? RETURNING id",
                    "SELECT COUNT(*) FROM user_profiles WHERE company_id = ?"),
            new CumsHolder("company_logo_requests.company_name",
                    "UPDATE company_logo_requests SET company_name = ? WHERE company_name = ? RETURNING id",
                    "SELECT COUNT(*) FROM company_logo_requests WHERE company_name = ?"),
            new CumsHolder("activity_history.entity_id",
                    "UPDATE activity_history SET entity_id = ? WHERE entity_type = 'COMPANY' AND entity_id = ?"
                            + " RETURNING id",
                    "SELECT COUNT(*) FROM activity_history WHERE entity_type = 'COMPANY' AND entity_id = ?"),
            new CumsHolder("company_user_uploads.target_id",
                    "UPDATE company_user_uploads SET target_id = ? WHERE target_type = '"
                            + CompanyLogoUploadHandler.TARGET_TYPE + "' AND target_id = ? RETURNING id",
                    "SELECT COUNT(*) FROM company_user_uploads WHERE target_type = '"
                            + CompanyLogoUploadHandler.TARGET_TYPE + "' AND target_id = ?"));

    /**
     * An open logo request of the source whose requester has an open one at the target too: the one
     * open request per person and company ({@code uq_company_logo_requests_open}) would collide. The
     * source's is closed (DECLINED, row kept) before it moves; the target's stays. Never on a rename
     * (the new key holds no rows).
     */
    private static final String LOGO_REQUEST_COLLISION = " FROM company_logo_requests s WHERE s.company_name = ?"
            + " AND s.status = 'OPEN' AND EXISTS (SELECT 1 FROM company_logo_requests t WHERE t.company_name = ?"
            + " AND t.status = 'OPEN' AND t.requester_username = s.requester_username)";

    /** The key of a closed colliding logo request in {@link Moved#cumsClosed()} and the merge log. */
    public static final String LOGO_REQUESTS_CLOSED = "company_logo_requests.status";

    /**
     * What moved: each holder's result, in configured order, and CUMS's rows per {@code table.column}:
     * counts in {@code cums}, the moved row ids in {@code cumsKeys} (for the merge log), and the open
     * logo requests closed because the target held one of the same person ({@code cumsClosed}, keyed
     * {@link #LOGO_REQUESTS_CLOSED}; they moved too).
     */
    public record Moved(List<CompanyReferenceContract.Result> holders, Map<String, Integer> cums,
            Map<String, List<String>> cumsKeys, Map<String, List<String>> cumsClosed) {
    }

    /**
     * Moves every reference of {@code oldKey} to {@code newKey}, at the holders and in CUMS. The
     * company row itself is the caller's: it renames or deletes it afterwards. The user and company
     * search caches are cleared after the commit.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Moved move(String oldKey, String newKey, String who, CompanyReferenceContract.Trigger trigger) {
        String performedBy = Actors.forUsername(who);
        List<String> blockers = holders.preview(oldKey, newKey).stream()
                .flatMap(preview -> preview.blockers().stream()).toList();
        if (!blockers.isEmpty()) {
            throw new CompanyReferenceContract.MoveBlockedException(
                    "Company references of " + oldKey + " cannot move to " + newKey + ": " + blockers, blockers);
        }
        List<CompanyReferenceContract.Result> moved = holders.merge(
                new CompanyReferenceContract.MergeCall(oldKey, newKey, performedBy, trigger));

        Map<String, List<String>> cumsClosed = new LinkedHashMap<>();
        List<String> closed = jdbc.queryForList("UPDATE company_logo_requests SET status = 'DECLINED',"
                + " decided_by = ?, decided_at = now(), reason = ? WHERE id IN (SELECT s.id" + LOGO_REQUEST_COLLISION
                + ") RETURNING id", String.class, performedBy,
                "Company merged into " + newKey + ", where this person has an open request", oldKey, newKey);
        if (!closed.isEmpty()) {
            cumsClosed.put(LOGO_REQUESTS_CLOSED, closed);
        }
        Map<String, Integer> cums = new LinkedHashMap<>();
        Map<String, List<String>> cumsKeys = new LinkedHashMap<>();
        for (CumsHolder holder : CUMS_HOLDERS) {
            List<String> ids = jdbc.queryForList(holder.update(), String.class, newKey, oldKey);
            cums.put(holder.key(), ids.size());
            cumsKeys.put(holder.key(), ids);
        }

        log.info("COMPANY_KEY_MOVE source={} target={} trigger={} by={} cums={}", oldKey, newKey, trigger,
                performedBy, cums);
        clearCachesAfterCommit();
        return new Moved(moved, cums, cumsKeys, cumsClosed);
    }

    /**
     * The CUMS rows of {@code oldKey} that collide with one of {@code newKey}, per table: the open logo
     * requests {@link #move} closes. Empty when there are none.
     */
    @Transactional(readOnly = true)
    public Map<String, Integer> countCumsConflicts(String oldKey, String newKey) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*)" + LOGO_REQUEST_COLLISION, Integer.class, oldKey, newKey);
        return n == null || n == 0 ? Map.of() : Map.of("company_logo_requests", n);
    }

    /** The CUMS rows that hold {@code key}, per {@code table.column}: what {@link #move} would move here. */
    @Transactional(readOnly = true)
    public Map<String, Integer> countCums(String key) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (CumsHolder holder : CUMS_HOLDERS) {
            Integer n = jdbc.queryForObject(holder.count(), Integer.class, key);
            counts.put(holder.key(), n == null ? 0 : n);
        }
        return counts;
    }

    /**
     * Keeps {@code oldKey} and the display name people knew as an alias of {@code companyKey}, once no
     * company holds {@code oldKey} any more (renamed or deleted in this transaction).
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void keepAsAlias(String companyKey, String oldKey, String oldDisplayName, CompanyAlias.Reason reason,
            String performedBy) {
        aliasService.record(companyKey, oldKey, oldDisplayName, reason, performedBy);
    }

    /**
     * The holders moved their rows, but the caller could not complete its part (rename or merge); CUMS
     * rolls back. Running the same move again finishes it.
     */
    public static class MoveIncompleteException extends RuntimeException {
        public MoveIncompleteException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private void clearCachesAfterCommit() {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                for (String name : List.of("companySearch", "userSearch")) {
                    var cache = cacheManager.getCache(name);
                    if (cache != null) {
                        cache.clear();
                    }
                }
            }
        });
    }
}
