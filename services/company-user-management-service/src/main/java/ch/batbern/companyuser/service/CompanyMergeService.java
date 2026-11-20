package ch.batbern.companyuser.service;

import ch.batbern.companyuser.client.CompanyReferenceContract;
import ch.batbern.companyuser.domain.Company;
import ch.batbern.companyuser.domain.CompanyAlias;
import ch.batbern.companyuser.exception.CompanyNotFoundException;
import ch.batbern.companyuser.repository.CompanyRepository;
import ch.batbern.companyuser.repository.UserRepository;
import ch.batbern.shared.service.SlugGenerationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * #1251 (issue sweep epic 4, story 4): organizers fold a duplicate company ("Unic AG") into another
 * ("Unic"). The source is merged into the target and deleted.
 *
 * <p>Order, so a failure leaves nothing a retry cannot finish:
 * <ol>
 *   <li>both companies are locked, in key order (two opposite merges cannot deadlock), and checked;</li>
 *   <li>{@link CompanyKeyMover#move} with trigger MERGE, the one path a rename uses: every holder of
 *       the company-reference contract previews (a blocker refuses before any holder moves a row)
 *       and moves its rows, then the CUMS rows move;</li>
 *   <li>here, in the same transaction: the target's empty fields are filled from the source, the
 *       source's aliases lead to the target, the source is deleted, its key and display name become
 *       aliases of the target, and {@code company_merge_log} keeps every moved row id and a snapshot
 *       of the source.</li>
 * </ol>
 * If a holder or a step here fails, CUMS rolls back and the source still exists; earlier holders have
 * moved their rows, and running the merge again finishes it (every holder is idempotent). The rows those
 * holders moved are logged first as an INCOMPLETE {@code company_merge_log} row ({@link CompanyMergeLog}),
 * since the retry gets nothing from them; an undo reads every row of the pair. The target's
 * display name always stays. No mail is sent, no S3 key changes.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CompanyMergeService {

    public static final String SAME_COMPANY = "SAME_COMPANY";
    public static final String TARGET_KEY_TOO_LONG = "TARGET_KEY_TOO_LONG";
    public static final String BOTH_PARTNERS = "BOTH_PARTNERS";
    public static final String BLOCKED = "BLOCKED";
    /** The holder blocker for two partner companies (partner-coordination). */
    public static final String BOTH_PARTNERS_BLOCKER = "MERGE_BLOCKED_BOTH_PARTNERS";

    static final String FILL_WEBSITE = "website";
    static final String FILL_INDUSTRY = "industry";
    static final String FILL_DESCRIPTION = "description";
    static final String FILL_LOGO = "logo";

    private final CompanyRepository companyRepository;
    private final UserRepository userRepository;
    private final CompanyReferenceContract holders;
    private final CompanyKeyMover keyMover;
    private final CompanyAliasService aliasService;
    private final CompanyMergeLog mergeLog;

    /** One side of a merge: the company and its user profiles. */
    public record Side(Company company, long userCount) {
    }

    /**
     * {@code moves}: CUMS's rows and every holder's, summed per {@code table.column}; {@code fills}:
     * the target fields the merge would take from the source.
     */
    public record Preview(Side target, Side source, Map<String, Integer> moves, Map<String, Integer> conflicts,
            List<String> blockers, List<String> fills, List<String> reasons, boolean canSwap) {
        public boolean canMerge() {
            return reasons.isEmpty();
        }
    }

    public record Result(String targetName, String sourceName, Map<String, Integer> moved,
            Map<String, Integer> removed, List<String> filled) {
    }

    @Transactional(readOnly = true)
    public Preview preview(String targetKey, String sourceKey) {
        Company target = load(targetKey);
        Company source = load(sourceKey);
        List<String> reasons = new ArrayList<>(keyReasons(target, source));
        boolean same = reasons.contains(SAME_COMPANY);
        Map<String, Integer> moves = same ? new LinkedHashMap<>()
                : new LinkedHashMap<>(keyMover.countCums(source.getName()));
        Map<String, Integer> conflicts = same ? new LinkedHashMap<>()
                : new LinkedHashMap<>(keyMover.countCumsConflicts(source.getName(), target.getName()));
        List<String> blockers = new ArrayList<>();
        // The holders answer 400 for the same key or a target that is no company key: not asked then
        if (reasons.isEmpty()) {
            for (CompanyReferenceContract.Preview held : holders.preview(source.getName(), target.getName())) {
                held.moves().forEach((key, n) -> moves.merge(key, n, Integer::sum));
                held.conflicts().forEach((key, n) -> conflicts.merge(key, n, Integer::sum));
                blockers.addAll(held.blockers());
            }
            reasons.addAll(blockerReasons(blockers));
        }
        return new Preview(side(target), side(source), moves, conflicts, List.copyOf(blockers),
                same ? List.of() : fills(target, source), reasons, canSwap(target, source));
    }

    /**
     * Merges {@code sourceKey} into {@code targetKey}.
     *
     * @throws MergeNotAllowedException                         same company or a key reason
     * @throws CompanyReferenceContract.MoveBlockedException     a holder refused (preview blocker or 409)
     * @throws CompanyReferenceContract.CallFailedException      a holder did not complete its part
     * @throws CompanyKeyMover.MoveIncompleteException          the holders moved, the merge here failed
     */
    @Transactional
    public Result merge(String targetKey, String sourceKey, String performedBy) {
        // Locked in key order, so a merge of A into B and one of B into A cannot deadlock
        String first = targetKey.compareTo(sourceKey) <= 0 ? targetKey : sourceKey;
        String second = first.equals(targetKey) ? sourceKey : targetKey;
        Company firstLocked = lock(first);
        Company secondLocked = first.equals(second) ? firstLocked : lock(second);
        Company target = first.equals(targetKey) ? firstLocked : secondLocked;
        Company source = first.equals(targetKey) ? secondLocked : firstLocked;

        List<String> reasons = keyReasons(target, source);
        if (!reasons.isEmpty()) {
            throw new MergeNotAllowedException(reasons, canSwap(target, source));
        }
        Map<String, Object> snapshot = snapshot(source);
        String sourceDisplayName = source.getDisplayName();

        CompanyKeyMover.Moved moved;
        try {
            moved = keyMover.move(source.getName(), target.getName(), performedBy,
                    CompanyReferenceContract.Trigger.MERGE);
        } catch (CompanyReferenceContract.MoveBlockedException e) {
            throw e;
        } catch (CompanyReferenceContract.CallFailedException e) {
            logIncomplete(source, target, performedBy, e.completed(), snapshot);
            throw e;
        } catch (RuntimeException e) {
            throw incomplete(source, target, e);
        }

        try {
            List<String> filled = fill(target, source);
            companyRepository.saveAndFlush(target);
            snapshot.put("aliases", aliasService.repoint(source.getId(), target.getId()).stream()
                    .map(CompanyMergeService::aliasSnapshot).toList());
            companyRepository.delete(source);
            companyRepository.flush();
            if (isKey(source.getName())) {
                keyMover.keepAsAlias(target.getName(), source.getName(), sourceDisplayName,
                        CompanyAlias.Reason.MERGE, performedBy);
            } else {
                // company_aliases.alias_key has the key shape; the display name needs a key too
                log.info("COMPANY_MERGE source={} is no company key shape: kept as no alias", source.getName());
            }

            Map<String, List<String>> movedKeys = new LinkedHashMap<>(moved.cumsKeys());
            Map<String, List<String>> removedKeys = new LinkedHashMap<>(moved.cumsClosed());
            Map<String, Integer> movedCounts = new LinkedHashMap<>(moved.cums());
            Map<String, Integer> removedCounts = new LinkedHashMap<>();
            // closed logo requests moved and are kept: in the log's removed_keys, not in the answer's removed
            for (CompanyReferenceContract.Result held : moved.holders()) {
                held.moved().forEach((key, n) -> movedCounts.merge(key, n, Integer::sum));
                held.removed().forEach((key, n) -> removedCounts.merge(key, n, Integer::sum));
                held.movedKeys().forEach((key, ids) -> addAll(movedKeys, key, ids));
                held.removedKeys().forEach((key, ids) -> addAll(removedKeys, key, ids));
            }
            mergeLog.completed(new CompanyMergeLog.Entry(source.getName(), target.getName(), performedBy, movedKeys,
                    removedKeys, filled, snapshot));

            log.info("COMPANY_MERGE source={} target={} by={} moved={} removed={} filled={}", source.getName(),
                    target.getName(), performedBy, movedCounts, removedCounts, filled);
            return new Result(target.getName(), source.getName(), movedCounts, removedCounts, filled);
        } catch (RuntimeException e) {
            logIncomplete(source, target, performedBy, moved.holders(), snapshot);
            throw incomplete(source, target, e);
        }
    }

    /**
     * The rows the holders moved in a failed attempt; CUMS rolls back, and a retry finds nothing left to
     * move at those holders, so this row is the only record of them.
     */
    private void logIncomplete(Company source, Company target, String performedBy,
            List<CompanyReferenceContract.Result> completed, Map<String, Object> snapshot) {
        if (completed.isEmpty()) {
            return;
        }
        Map<String, List<String>> movedKeys = new LinkedHashMap<>();
        Map<String, List<String>> removedKeys = new LinkedHashMap<>();
        for (CompanyReferenceContract.Result held : completed) {
            held.movedKeys().forEach((key, ids) -> addAll(movedKeys, key, ids));
            held.removedKeys().forEach((key, ids) -> addAll(removedKeys, key, ids));
        }
        try {
            mergeLog.incomplete(new CompanyMergeLog.Entry(source.getName(), target.getName(), performedBy,
                    movedKeys, removedKeys, List.of(), snapshot));
        } catch (RuntimeException e) {
            log.error("COMPANY_MERGE incomplete attempt not logged source={} target={} moved={} removed={}",
                    source.getName(), target.getName(), movedKeys, removedKeys, e);
        }
    }

    private static Map<String, Object> aliasSnapshot(CompanyAlias alias) {
        Map<String, Object> a = new LinkedHashMap<>();
        a.put("aliasKey", alias.getAliasKey());
        a.put("aliasDisplayName", alias.getAliasDisplayName());
        a.put("reason", alias.getReason() == null ? null : alias.getReason().name());
        a.put("createdBy", alias.getCreatedBy());
        a.put("createdAt", alias.getCreatedAt() == null ? null : alias.getCreatedAt().toString());
        return a;
    }

    private static CompanyKeyMover.MoveIncompleteException incomplete(Company source, Company target,
            RuntimeException cause) {
        return new CompanyKeyMover.MoveIncompleteException("The references of " + source.getName() + " moved to "
                + target.getName() + ", but the merge did not complete", cause);
    }

    private static void addAll(Map<String, List<String>> keys, String key, List<String> ids) {
        keys.computeIfAbsent(key, k -> new ArrayList<>()).addAll(ids);
    }

    /** Why the two cannot be merged before any holder is asked: same company, a key no row can hold. */
    static List<String> keyReasons(Company target, Company source) {
        if (Objects.equals(target.getId(), source.getId())) {
            return List.of(SAME_COMPANY);
        }
        List<String> reasons = new ArrayList<>();
        if (!isKey(target.getName())) {
            reasons.add(TARGET_KEY_TOO_LONG); // user_profiles.company_id holds 1 to 12 letters or digits
        }
        return reasons;
    }

    /** The merge the other way round passes the key checks. */
    static boolean canSwap(Company target, Company source) {
        return !Objects.equals(target.getId(), source.getId()) && isKey(source.getName());
    }

    private static boolean isKey(String key) {
        return key != null && key.matches(SlugGenerationService.COMPANY_NAME_PATTERN);
    }

    private static List<String> blockerReasons(List<String> blockers) {
        List<String> reasons = new ArrayList<>();
        if (blockers.contains(BOTH_PARTNERS_BLOCKER)) {
            reasons.add(BOTH_PARTNERS);
        }
        if (blockers.stream().anyMatch(b -> !BOTH_PARTNERS_BLOCKER.equals(b))) {
            reasons.add(BLOCKED);
        }
        return reasons;
    }

    /** The target fields that are empty and the source has, in a fixed order. */
    static List<String> fills(Company target, Company source) {
        List<String> fills = new ArrayList<>();
        if (isBlank(target.getWebsite()) && !isBlank(source.getWebsite())) {
            fills.add(FILL_WEBSITE);
        }
        if (isBlank(target.getIndustry()) && !isBlank(source.getIndustry())) {
            fills.add(FILL_INDUSTRY);
        }
        if (isBlank(target.getDescription()) && !isBlank(source.getDescription())) {
            fills.add(FILL_DESCRIPTION);
        }
        if (isBlank(target.getLogoUrl()) && !isBlank(source.getLogoUrl())) {
            fills.add(FILL_LOGO);
        }
        return fills;
    }

    private static List<String> fill(Company target, Company source) {
        List<String> fills = fills(target, source);
        if (fills.contains(FILL_WEBSITE)) {
            target.setWebsite(source.getWebsite());
        }
        if (fills.contains(FILL_INDUSTRY)) {
            target.setIndustry(source.getIndustry());
        }
        if (fills.contains(FILL_DESCRIPTION)) {
            target.setDescription(source.getDescription());
        }
        if (fills.contains(FILL_LOGO)) {
            // The S3 object keeps its key; its upload row moved to the target with the references
            target.setLogoUrl(source.getLogoUrl());
            target.setLogoS3Key(source.getLogoS3Key());
            target.setLogoFileId(source.getLogoFileId());
        }
        if (!fills.isEmpty()) {
            target.setUpdatedAt(Instant.now());
        }
        return fills;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private Side side(Company company) {
        return new Side(company, userRepository.countByCompanyId(company.getName()));
    }

    private static Map<String, Object> snapshot(Company source) {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("id", source.getId().toString());
        s.put("name", source.getName());
        s.put("displayName", source.getDisplayName());
        s.put("website", source.getWebsite());
        s.put("industry", source.getIndustry());
        s.put("description", source.getDescription());
        s.put("logoUrl", source.getLogoUrl());
        s.put("logoS3Key", source.getLogoS3Key());
        s.put("logoFileId", source.getLogoFileId());
        s.put("isVerified", source.isVerified());
        s.put("createdAt", source.getCreatedAt() == null ? null : source.getCreatedAt().toString());
        s.put("createdBy", source.getCreatedBy());
        s.put("updatedAt", source.getUpdatedAt() == null ? null : source.getUpdatedAt().toString());
        return s;
    }

    private Company load(String key) {
        return companyRepository.findByName(key).orElseThrow(() -> new CompanyNotFoundException(key));
    }

    private Company lock(String key) {
        return companyRepository.findByNameForUpdate(key).orElseThrow(() -> new CompanyNotFoundException(key));
    }

    /** 400 (same company) or 409 (a key reason) MERGE_NOT_ALLOWED. */
    public static class MergeNotAllowedException extends RuntimeException {
        private final List<String> reasons;
        private final boolean canSwap;

        public MergeNotAllowedException(List<String> reasons, boolean canSwap) {
            super("Merge not allowed: " + String.join(", ", reasons));
            this.reasons = List.copyOf(reasons);
            this.canSwap = canSwap;
        }

        public List<String> reasons() {
            return reasons;
        }

        public boolean canSwap() {
            return canSwap;
        }

        public boolean sameCompany() {
            return reasons.contains(SAME_COMPANY);
        }
    }
}
