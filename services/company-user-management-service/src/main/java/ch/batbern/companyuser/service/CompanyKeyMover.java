package ch.batbern.companyuser.service;

import ch.batbern.companyuser.client.CompanyReferenceContract;
import ch.batbern.companyuser.domain.CompanyAlias;
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
 *       (the set CUMS V28 moved).</li>
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

    /** What moved: each holder's result, in configured order, and CUMS's rows per {@code table.column}. */
    public record Moved(List<CompanyReferenceContract.Result> holders, Map<String, Integer> cums) {
    }

    /**
     * Moves every reference of {@code oldKey} to {@code newKey}, at the holders and in CUMS. The
     * company row itself is the caller's: it renames or deletes it afterwards. The user and company
     * search caches are cleared after the commit.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Moved move(String oldKey, String newKey, String performedBy, CompanyReferenceContract.Trigger trigger) {
        List<String> blockers = holders.preview(oldKey, newKey).stream()
                .flatMap(preview -> preview.blockers().stream()).toList();
        if (!blockers.isEmpty()) {
            throw new CompanyReferenceContract.MoveBlockedException(
                    "Company references of " + oldKey + " cannot move to " + newKey + ": " + blockers);
        }
        List<CompanyReferenceContract.Result> moved = holders.merge(
                new CompanyReferenceContract.MergeCall(oldKey, newKey, performedBy, trigger));

        Map<String, Integer> cums = new LinkedHashMap<>();
        cums.put("user_profiles.company_id",
                jdbc.update("UPDATE user_profiles SET company_id = ? WHERE company_id = ?", newKey, oldKey));
        cums.put("company_logo_requests.company_name", jdbc.update(
                "UPDATE company_logo_requests SET company_name = ? WHERE company_name = ?", newKey, oldKey));
        cums.put("activity_history.entity_id", jdbc.update("UPDATE activity_history SET entity_id = ?"
                + " WHERE entity_type = 'COMPANY' AND entity_id = ?", newKey, oldKey));
        cums.put("company_user_uploads.target_id", jdbc.update("UPDATE company_user_uploads SET target_id = ?"
                + " WHERE target_type = ? AND target_id = ?", newKey, CompanyLogoUploadHandler.TARGET_TYPE, oldKey));

        log.info("COMPANY_KEY_MOVE source={} target={} trigger={} by={} cums={}", oldKey, newKey, trigger,
                performedBy, cums);
        clearCachesAfterCommit();
        return new Moved(moved, cums);
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
