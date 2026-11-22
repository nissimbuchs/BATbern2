package ch.batbern.companyuser.service;

import ch.batbern.companyuser.client.CompanyReferenceContract;
import ch.batbern.companyuser.domain.Company;
import ch.batbern.companyuser.exception.CompanyNotFoundException;
import ch.batbern.companyuser.repository.CompanyRepository;
import ch.batbern.companyuser.service.CompanyDuplicateRules.Confidence;
import ch.batbern.companyuser.service.CompanyDuplicateRules.Facts;
import ch.batbern.companyuser.service.CompanyDuplicateRules.Match;
import ch.batbern.companyuser.service.CompanyDuplicateRules.Named;
import ch.batbern.companyuser.service.CompanyDuplicateRules.Rule;
import ch.batbern.shared.security.Actors;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Company pairs that are probably one company, for the organizer company merge (#1251, story 5).
 * Computed on the live data each time by {@link CompanyDuplicateRules}; nothing is stored but the
 * pairs organizers dismissed ({@code company_duplicate_dismissals}, V38). The model is the profile
 * list ({@link DuplicateCandidateService}).
 *
 * <p>Each side carries what the merge decision needs: user profiles counted here, registrations and
 * the partner flag from every holder of the company-references contract, one counts call per holder
 * for all keys of the list (ADR-016: no reads of their tables). A holder that does not answer fails
 * the list ({@link CountsIncompleteException}, 502): the suggested target depends on the partner flag.
 */
@Service
@RequiredArgsConstructor
public class CompanyDuplicateCandidateService {

    private final CompanyRepository companyRepository;
    private final CompanyReferenceContract holders;
    private final JdbcTemplate jdbc;

    /** One company of a pair with its counts. */
    public record Side(Company company, long userCount, int registrationCount, boolean partner) {
    }

    public record Candidate(Rule rule, Side target, Side source) {
        public Confidence confidence() {
            return rule.confidence();
        }

        /** Two partners cannot be merged by an organizer: the partners are merged first. */
        public boolean organizerCanMerge() {
            return !(target.partner() && source.partner());
        }
    }

    /**
     * Every candidate pair not dismissed, sorted by confidence, then the target's display name.
     *
     * @throws CountsIncompleteException when a holder did not answer the counts
     */
    public List<Candidate> candidates() {
        List<Company> companies = companyRepository.findAll();
        Map<String, Company> byKey = companies.stream()
                .collect(Collectors.toMap(Company::getName, Function.identity()));
        Set<Set<UUID>> dismissed = new HashSet<>(jdbc.query(
                "SELECT company_a_id, company_b_id FROM company_duplicate_dismissals",
                (rs, i) -> Set.of(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class))));
        List<Match> pairs = CompanyDuplicateRules.pairs(companies.stream()
                        .map(c -> new Named(c.getName(), c.getDisplayName(), c.getWebsite())).toList())
                .stream()
                .filter(m -> !dismissed.contains(Set.of(byKey.get(m.a().key()).getId(),
                        byKey.get(m.b().key()).getId())))
                .toList();
        if (pairs.isEmpty()) {
            return List.of();
        }

        Set<String> keys = new LinkedHashSet<>();
        pairs.forEach(m -> {
            keys.add(m.a().key());
            keys.add(m.b().key());
        });
        Map<String, Long> users = userCounts(keys);
        Map<String, Map<String, Integer>> held;
        try {
            held = holders.counts(keys);
        } catch (CompanyReferenceContract.CallFailedException e) {
            throw new CountsIncompleteException(e);
        }

        return pairs.stream()
                .map(m -> candidate(m, side(byKey.get(m.a().key()), users, held),
                        side(byKey.get(m.b().key()), users, held)))
                .sorted(Comparator.comparing(Candidate::confidence)
                        .thenComparing(c -> sortName(c.target().company()))
                        .thenComparing(c -> c.target().company().getName()))
                .toList();
    }

    /** Marks the pair as no duplicate, in either order; idempotent. */
    @Transactional
    public void dismiss(String keyA, String keyB, String by) {
        Company a = load(keyA);
        Company b = load(keyB);
        if (a.getId().equals(b.getId())) {
            throw new InvalidDismissalException("A company is no duplicate of itself: " + keyA);
        }
        jdbc.update("""
                INSERT INTO company_duplicate_dismissals (company_a_id, company_b_id, dismissed_by)
                VALUES (LEAST(?, ?), GREATEST(?, ?), ?)
                ON CONFLICT DO NOTHING
                """, a.getId(), b.getId(), a.getId(), b.getId(), Actors.forUsername(by));
    }

    private static Candidate candidate(Match match, Side a, Side b) {
        boolean aStays = CompanyDuplicateRules.firstIsTarget(facts(a), facts(b));
        return new Candidate(match.rule(), aStays ? a : b, aStays ? b : a);
    }

    private static Facts facts(Side s) {
        Company c = s.company();
        return new Facts(c.getName(), s.partner(), c.getLogoUrl() != null && !c.getLogoUrl().isBlank(),
                s.userCount(), c.getCreatedAt());
    }

    private static Side side(Company company, Map<String, Long> users,
            Map<String, Map<String, Integer>> held) {
        Map<String, Integer> rows = held.getOrDefault(company.getName(), Map.of());
        return new Side(company, users.getOrDefault(company.getName(), 0L),
                rows.getOrDefault(CompanyReferenceContract.REGISTRATIONS, 0),
                rows.getOrDefault(CompanyReferenceContract.PARTNERS, 0) > 0);
    }

    private Map<String, Long> userCounts(Set<String> keys) {
        Map<String, Long> counts = new HashMap<>();
        new NamedParameterJdbcTemplate(jdbc).query("SELECT company_id, COUNT(*) AS n FROM user_profiles "
                + "WHERE company_id IN (:keys) GROUP BY company_id", Map.of("keys", keys), rs -> {
                    counts.put(rs.getString("company_id"), rs.getLong("n"));
                });
        return counts;
    }

    private static String sortName(Company c) {
        String name = c.getDisplayName() == null || c.getDisplayName().isBlank() ? c.getName() : c.getDisplayName();
        return name.toLowerCase(Locale.ROOT);
    }

    private Company load(String key) {
        return companyRepository.findByName(key).orElseThrow(() -> new CompanyNotFoundException(key));
    }

    /** A holder did not answer the counts (502 {@code DUPLICATE_COUNTS_INCOMPLETE}). */
    public static class CountsIncompleteException extends RuntimeException {
        public CountsIncompleteException(Throwable cause) {
            super("Not every holder answered the company counts: " + cause.getMessage(), cause);
        }
    }

    /** The same company twice (400 {@code INVALID_DISMISSAL}). */
    public static class InvalidDismissalException extends RuntimeException {
        public InvalidDismissalException(String message) {
            super(message);
        }
    }
}
