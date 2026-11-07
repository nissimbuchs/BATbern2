package ch.batbern.companyuser.service;

import ch.batbern.companyuser.domain.User;
import ch.batbern.companyuser.exception.UserNotFoundException;
import ch.batbern.companyuser.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * Profile pairs that are probably one person, for the organizer merge (#1183 C). Computed on the
 * live data each time; organizers dismiss namesakes ({@code user_duplicate_dismissals}, V26).
 *
 * <p>Rules, measured on production 2026-11-07 (2583 active profiles): {@code SAME_NAME} 257 pairs,
 * {@code SWAPPED_NAME} 24, {@code SAME_ADDRESS_NAME} 22. A pair matching several rules is listed once,
 * under the first. Names are compared lower-case and trimmed, with umlauts as ae/oe/ue, ß as ss,
 * accents removed and hyphens or underscores as spaces.
 */
@Service
@RequiredArgsConstructor
public class DuplicateCandidateService {

    public enum Rule { SAME_NAME, SWAPPED_NAME, SAME_ADDRESS_NAME }

    public enum Confidence { HIGH, MEDIUM, LOW }

    public record Profile(UUID id, String username, String firstName, String lastName, String email,
            String companyId, boolean hasLogin, boolean placeholderAddress, Instant createdAt) {
        String localPart() {
            int at = email.indexOf('@');
            return (at < 0 ? email : email.substring(0, at)).toLowerCase(Locale.ROOT);
        }
    }

    public record Candidate(Rule rule, Confidence confidence, Profile target, Profile source) {
        public boolean organizerCanMerge() {
            return !(target.hasLogin() && source.hasLogin());
        }
    }

    private static String norm(String column) {
        return "regexp_replace(translate(replace(replace(replace(replace(lower(trim(" + column + ")),"
                + "'ä','ae'),'ö','oe'),'ü','ue'),'ß','ss'),"
                + "'éèêëàâáçïîíôóñ-_','eeeeaaaciiioon  '),'\\s+',' ','g')";
    }

    private static final String SQL = """
            WITH n AS (
              SELECT id, username, first_name, last_name, email, company_id,
                     cognito_user_id IS NOT NULL AS login, created_at,
                     %s AS fn, %s AS ln,
                     lower(split_part(email, '@', 1)) AS lp, lower(split_part(email, '@', 2)) AS dom,
                     email ~* '^[^@\\s]+\\.[^@\\s]+@batbern\\.ch$' AS placeholder
                FROM user_profiles
               WHERE is_active AND email IS NOT NULL
                 AND nullif(trim(first_name), '') IS NOT NULL AND nullif(trim(last_name), '') IS NOT NULL
            ), pairs AS (
              SELECT 1 AS prio, 'SAME_NAME' AS rule, a.id AS ida, b.id AS idb
                FROM n a JOIN n b ON a.id < b.id AND a.fn = b.fn AND a.ln = b.ln
              UNION ALL
              SELECT 2, 'SWAPPED_NAME', a.id, b.id
                FROM n a JOIN n b ON a.id < b.id AND a.fn = b.ln AND a.ln = b.fn AND a.fn <> a.ln
              UNION ALL
              SELECT 3, 'SAME_ADDRESS_NAME', a.id, b.id
                FROM n a JOIN n b ON a.id < b.id AND a.lp = b.lp AND a.dom <> b.dom AND a.lp LIKE '%%.%%'
                 AND NOT (a.fn = b.fn AND a.ln = b.ln)
            ), best AS (
              SELECT DISTINCT ON (ida, idb) rule, ida, idb FROM pairs ORDER BY ida, idb, prio
            )
            SELECT best.rule,
                   a.id a_id, a.username a_username, a.first_name a_first, a.last_name a_last, a.email a_email,
                   a.company_id a_company, a.login a_login, a.placeholder a_placeholder, a.created_at a_created,
                   b.id b_id, b.username b_username, b.first_name b_first, b.last_name b_last, b.email b_email,
                   b.company_id b_company, b.login b_login, b.placeholder b_placeholder, b.created_at b_created
              FROM best JOIN n a ON a.id = best.ida JOIN n b ON b.id = best.idb
             WHERE NOT EXISTS (SELECT 1 FROM user_duplicate_dismissals d
                                WHERE d.user_a_id = best.ida AND d.user_b_id = best.idb)
            """.formatted(norm("first_name"), norm("last_name"));

    private final JdbcTemplate jdbc;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<Candidate> candidates() {
        return jdbc.query(SQL, this::row).stream()
                .sorted(Comparator.comparing(Candidate::confidence)
                        .thenComparing(c -> lower(c.target().lastName()))
                        .thenComparing(c -> lower(c.target().firstName())))
                .toList();
    }

    /** Marks the pair as not a duplicate; idempotent. */
    @Transactional
    public void dismiss(String usernameA, String usernameB, String by) {
        User a = load(usernameA);
        User b = load(usernameB);
        if (a.getId().equals(b.getId())) {
            throw new IllegalArgumentException("A profile is no duplicate of itself");
        }
        jdbc.update("""
                INSERT INTO user_duplicate_dismissals (user_a_id, user_b_id, dismissed_by)
                VALUES (LEAST(?, ?), GREATEST(?, ?), ?)
                ON CONFLICT DO NOTHING
                """, a.getId(), b.getId(), a.getId(), b.getId(), by);
    }

    private Candidate row(ResultSet rs, int i) throws SQLException {
        Profile a = profile(rs, "a_");
        Profile b = profile(rs, "b_");
        Rule rule = Rule.valueOf(rs.getString("rule"));
        boolean aFirst = keepFirst(a, b);
        Profile target = aFirst ? a : b;
        Profile source = aFirst ? b : a;
        return new Candidate(rule, confidence(rule, a, b), target, source);
    }

    /** The profile to keep: the one with a login, else the one without a placeholder, else the older. */
    private static boolean keepFirst(Profile a, Profile b) {
        if (a.hasLogin() != b.hasLogin()) {
            return a.hasLogin();
        }
        if (a.placeholderAddress() != b.placeholderAddress()) {
            return !a.placeholderAddress();
        }
        if (a.createdAt() == null || b.createdAt() == null) {
            return a.createdAt() != null || b.createdAt() == null;
        }
        return !a.createdAt().isAfter(b.createdAt());
    }

    private static Confidence confidence(Rule rule, Profile a, Profile b) {
        return switch (rule) {
            case SWAPPED_NAME -> Confidence.HIGH;
            case SAME_ADDRESS_NAME -> Confidence.LOW;
            case SAME_NAME -> a.placeholderAddress() || b.placeholderAddress()
                    || (a.companyId() != null && Objects.equals(a.companyId(), b.companyId()))
                    || a.localPart().equals(b.localPart())
                    ? Confidence.HIGH : Confidence.MEDIUM;
        };
    }

    private static Profile profile(ResultSet rs, String p) throws SQLException {
        Timestamp created = rs.getTimestamp(p + "created");
        return new Profile(rs.getObject(p + "id", UUID.class), rs.getString(p + "username"),
                rs.getString(p + "first"), rs.getString(p + "last"), rs.getString(p + "email"),
                rs.getString(p + "company"), rs.getBoolean(p + "login"), rs.getBoolean(p + "placeholder"),
                created == null ? null : created.toInstant());
    }

    private User load(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException("User not found: " + username, true));
    }

    private static String lower(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT);
    }
}
