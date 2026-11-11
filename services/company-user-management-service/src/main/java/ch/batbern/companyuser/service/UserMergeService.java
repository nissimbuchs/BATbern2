package ch.batbern.companyuser.service;

import ch.batbern.companyuser.client.UserReferenceContract;
import ch.batbern.shared.types.Role;
import ch.batbern.companyuser.domain.User;
import ch.batbern.companyuser.domain.UserAdditionalEmail;
import ch.batbern.companyuser.exception.UserNotFoundException;
import ch.batbern.companyuser.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * #1183 C/D: merges two profiles of the same person. The source profile is folded into the target and
 * deleted.
 *
 * <p>Order, so a failure leaves nothing half done that a retry cannot finish:
 * <ol>
 *   <li>both profiles are locked and checked (the source must have no login);</li>
 *   <li>every holder of the user-reference contract previews the merge
 *       ({@link UserReferenceContract}); a blocker at any holder refuses the merge before any holder
 *       moves a row;</li>
 *   <li>every holder moves its rows, in configured order, each in a transaction of its own
 *       (event-management also logs the merge, with a snapshot of the source profile);</li>
 *   <li>here: roles, activity, watch pairings and addresses move to the target, the source is
 *       deleted, and its addresses become verified additional addresses of the target.</li>
 * </ol>
 * If a holder or step 4 fails, the source still exists; earlier holders have moved their rows, and
 * running the merge again finishes it (every holder's part is idempotent).
 *
 * <p>The source must have no login: a profile with a Cognito user is somebody's account, and deleting
 * it would lock that person out. A login profile is always the target. Two login profiles of one person
 * are not merged here.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserMergeService {

    public static final String SAME_PROFILE = "SAME_PROFILE";
    public static final String SOURCE_HAS_LOGIN = "SOURCE_HAS_LOGIN";
    public static final String BOTH_HAVE_LOGIN = "BOTH_HAVE_LOGIN";
    public static final String BLOCKED = "BLOCKED";

    private final UserRepository userRepository;
    private final UserReferenceContract holders;
    private final JdbcTemplate jdbc;

    /** {@code holders}: the previews of all holders, summed ({@code table.column} keys do not overlap). */
    public record Preview(User target, User source, UserReferenceContract.Preview holders,
            List<String> reasons) {
        public boolean canMerge() {
            return reasons.isEmpty();
        }
    }

    public record Result(String targetUsername, String sourceUsername, Map<String, Integer> moved,
            Map<String, Integer> removed) {
    }

    @Transactional(readOnly = true)
    public Preview preview(String targetUsername, String sourceUsername) {
        User target = load(targetUsername);
        User source = load(sourceUsername);
        List<String> reasons = new ArrayList<>(profileReasons(target, source));
        UserReferenceContract.Preview held = target.getId().equals(source.getId())
                ? new UserReferenceContract.Preview(Map.of(), Map.of(), List.of())
                : sum(holders.preview(source.getUsername(), target.getUsername()));
        if (!held.blockers().isEmpty()) {
            reasons.add(BLOCKED);
        }
        // touch the lazy collections for the controller's mapping
        target.getRoles().size();
        source.getRoles().size();
        target.getAdditionalEmails().size();
        source.getAdditionalEmails().size();
        return new Preview(target, source, held, reasons);
    }

    /**
     * @param trigger ORGANIZER (an organizer merged) or SELF_CLAIM (the person confirmed a claim link)
     * @throws MergeNotAllowedException when the source has a login or both are the same profile
     */
    @Transactional
    public Result merge(String targetUsername, String sourceUsername, String performedBy, String trigger) {
        return merge(targetUsername, sourceUsername, performedBy, trigger, true);
    }

    /**
     * As {@link #merge(String, String, String, String)}; with {@code carryAddresses = false} none of
     * the source's addresses become addresses of the target (#1237: a merge request is approved
     * without proof of the old mailbox, so its address must not turn into a verified login address).
     * They stay in the merge log snapshot.
     */
    @Transactional
    public Result merge(String targetUsername, String sourceUsername, String performedBy, String trigger,
            boolean carryAddresses) {
        return merge(targetUsername, sourceUsername, performedBy, trigger, carryAddresses, false);
    }

    /**
     * #1244 step 3 (owner decision 2026-11-07): two accounts of one person that both have a login. Only
     * after the person proved both mailboxes ({@link ProfileClaimService#confirm}); the caller removes
     * the source's login. Recorded as SELF_CLAIM; the source's addresses become confirmed addresses of
     * the target. Organizers never reach this.
     */
    @Transactional
    public Result mergeLoginAccount(String targetUsername, String sourceUsername) {
        return merge(targetUsername, sourceUsername, targetUsername, ProfileClaimService.TRIGGER, true, true);
    }

    private Result merge(String targetUsername, String sourceUsername, String performedBy, String trigger,
            boolean carryAddresses, boolean allowSourceLogin) {
        User target = userRepository.findByUsernameForUpdate(targetUsername)
                .orElseThrow(() -> new UserNotFoundException("User not found: " + targetUsername, true));
        User source = userRepository.findByUsernameForUpdate(sourceUsername)
                .orElseThrow(() -> new UserNotFoundException("User not found: " + sourceUsername, true));
        List<String> reasons = profileReasons(target, source);
        if (allowSourceLogin) {
            reasons = reasons.stream().filter(SAME_PROFILE::equals).toList();
        }
        if (!reasons.isEmpty()) {
            throw new MergeNotAllowedException(reasons);
        }

        List<UserAdditionalEmail> sourceAddresses = List.copyOf(source.getAdditionalEmails());
        Set<Role> sourceRoles = Set.copyOf(source.getRoles());
        Map<String, Object> snapshot = snapshot(source, sourceRoles, sourceAddresses);

        if (!sum(holders.preview(source.getUsername(), target.getUsername())).blockers().isEmpty()) {
            throw new MergeNotAllowedException(List.of(BLOCKED));
        }
        UserReferenceContract.MergeCall call = new UserReferenceContract.MergeCall(
                source.getUsername(), target.getUsername(), performedBy, trigger, snapshot);
        List<UserReferenceContract.Result> moved = holders.merge(call);

        target.getRoles().addAll(sourceRoles);
        if (target.getCompanyId() == null) {
            target.setCompanyId(source.getCompanyId());
        }
        if (target.getBio() == null) {
            target.setBio(source.getBio());
        }
        jdbc.update("UPDATE activity_history SET user_id = ? WHERE user_id = ?", target.getId(), source.getId());
        jdbc.update("UPDATE role_assignments SET granted_by = ? WHERE granted_by = ?", target.getId(), source.getId());
        jdbc.update("UPDATE watch_pairings SET username = ? WHERE username = ?", target.getUsername(),
                source.getUsername());

        String sourceEmail = source.getEmail();
        userRepository.delete(source);
        userRepository.flush();

        Set<String> held = target.getAdditionalEmails().stream()
                .map(e -> e.getEmail().toLowerCase(Locale.ROOT)).collect(Collectors.toSet());
        held.add(target.getEmail().toLowerCase(Locale.ROOT));
        Instant now = Instant.now();
        if (carryAddresses) {
            addAddress(target, held, sourceEmail, "merged profile", now);
            for (UserAdditionalEmail extra : sourceAddresses) {
                addAddress(target, held, extra.getEmail(), extra.getLabel(), extra.getVerifiedAt());
            }
        }
        userRepository.flush();

        log.info("USER_MERGE profile source={} target={} trigger={} by={}", source.getUsername(),
                target.getUsername(), trigger, performedBy);
        return new Result(target.getUsername(), source.getUsername(),
                sumCounts(moved.stream().map(UserReferenceContract.Result::moved).toList()),
                sumCounts(moved.stream().map(UserReferenceContract.Result::removed).toList()));
    }

    /** All holders' previews as one: counts added per key, blockers in holder order. */
    static UserReferenceContract.Preview sum(List<UserReferenceContract.Preview> previews) {
        List<String> blockers = new ArrayList<>();
        previews.forEach(p -> blockers.addAll(p.blockers()));
        return new UserReferenceContract.Preview(
                sumCounts(previews.stream().map(UserReferenceContract.Preview::moves).toList()),
                sumCounts(previews.stream().map(UserReferenceContract.Preview::conflicts).toList()),
                List.copyOf(blockers));
    }

    private static Map<String, Integer> sumCounts(List<Map<String, Integer>> counts) {
        Map<String, Integer> sum = new LinkedHashMap<>();
        counts.forEach(c -> c.forEach((key, n) -> sum.merge(key, n, Integer::sum)));
        return sum;
    }

    private static void addAddress(User target, Set<String> held, String email, String label, Instant verifiedAt) {
        // An import placeholder reaches nobody; carried, it would become a "verified" address. It stays in the log.
        if (email == null || ProfileClaimService.isPlaceholder(email) || !held.add(email.toLowerCase(Locale.ROOT))) {
            return;
        }
        target.addAdditionalEmail(UserAdditionalEmail.builder()
                .email(email.toLowerCase(Locale.ROOT))
                .label(label)
                .verifiedAt(verifiedAt)
                .createdAt(Instant.now())
                .build());
    }

    static List<String> profileReasons(User target, User source) {
        if (Objects.equals(target.getId(), source.getId())) {
            return List.of(SAME_PROFILE);
        }
        boolean sourceLogin = source.getCognitoUserId() != null;
        boolean targetLogin = target.getCognitoUserId() != null;
        if (sourceLogin && targetLogin) {
            return List.of(BOTH_HAVE_LOGIN);
        }
        if (sourceLogin) {
            return List.of(SOURCE_HAS_LOGIN);
        }
        return List.of();
    }

    private static Map<String, Object> snapshot(User source, Set<Role> roles, List<UserAdditionalEmail> addresses) {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("id", source.getId().toString());
        s.put("username", source.getUsername());
        s.put("email", source.getEmail());
        s.put("hadLogin", source.getCognitoUserId() != null);
        s.put("firstName", source.getFirstName());
        s.put("lastName", source.getLastName());
        s.put("companyId", source.getCompanyId());
        s.put("bio", source.getBio());
        s.put("isActive", source.isActive());
        s.put("createdAt", source.getCreatedAt() == null ? null : source.getCreatedAt().toString());
        s.put("roles", roles.stream().map(Enum::name).collect(Collectors.toCollection(TreeSet::new)));
        s.put("additionalEmails", addresses.stream().map(a -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("email", a.getEmail());
            m.put("label", a.getLabel());
            m.put("verifiedAt", a.getVerifiedAt() == null ? null : a.getVerifiedAt().toString());
            return m;
        }).toList());
        return s;
    }

    private User load(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException("User not found: " + username, true));
    }

    /** 409 MERGE_NOT_ALLOWED. */
    public static class MergeNotAllowedException extends RuntimeException {
        private final List<String> reasons;

        public MergeNotAllowedException(List<String> reasons) {
            super("Merge not allowed: " + String.join(", ", reasons));
            this.reasons = reasons;
        }

        public List<String> reasons() {
            return reasons;
        }
    }
}
