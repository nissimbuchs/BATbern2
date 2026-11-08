package ch.batbern.companyuser.specification;

import ch.batbern.companyuser.domain.User;
import ch.batbern.companyuser.domain.UserAdditionalEmail;
import ch.batbern.shared.search.SearchPredicates;
import ch.batbern.shared.search.SearchTerms;
import ch.batbern.shared.types.Role;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

/**
 * Filters for the paginated user list, composed in SQL so that paging, {@code totalItems} and
 * every filter agree (#1153). The active filter used to run in Java on the already-cut page,
 * which returned short or empty pages while matching users sat on other pages. An absent
 * filter is {@link Specification#unrestricted()}: Spring Data 4 rejects null specifications.
 */
public final class UserSpecification {

    private UserSpecification() {
    }

    /**
     * Every word of {@code search} is a substring of at least one of username, first
     * name, last name, primary e-mail, an additional e-mail (verified or not) or company id, after
     * {@code batbern_fold} on both sides. Words match in any order, so "muster hans" finds
     * "Hans Peter Muster". A blank search is unrestricted.
     */
    public static Specification<User> matchesSearch(String search) {
        SearchTerms terms = SearchTerms.parse(search);
        if (terms.isEmpty()) {
            return Specification.unrestricted();
        }
        return (root, query, cb) -> SearchPredicates.allTerms(cb, terms, term -> cb.or(
                SearchPredicates.contains(cb, root.get("username"), term),
                SearchPredicates.contains(cb, root.get("firstName"), term),
                SearchPredicates.contains(cb, root.get("lastName"), term),
                SearchPredicates.contains(cb, root.get("email"), term),
                SearchPredicates.contains(cb, root.get("companyId"), term),
                cb.exists(additionalEmailContains(root, query.subquery(Integer.class), cb, term))));
    }

    /**
     * {@link #matchesSearch} ordered for an autocomplete, so the database cuts the best rows
     * (2026-09-09, #1153): exact full name in either order, then an exact single field, then a
     * name prefix, then any other match; last name, first name and the unique username break ties.
     * The order is only applied to the row query, never to Spring Data's count query.
     */
    public static Specification<User> matchesSearchRanked(String search) {
        SearchTerms terms = SearchTerms.parse(search);
        return matchesSearch(search).and((root, query, cb) -> {
            if (!Long.class.equals(query.getResultType()) && !long.class.equals(query.getResultType())) {
                List<Order> order = new ArrayList<>();
                if (!terms.isEmpty()) {
                    order.add(cb.asc(rank(root, cb, terms.phrase())));
                }
                order.add(cb.asc(root.get("lastName")));
                order.add(cb.asc(root.get("firstName")));
                order.add(cb.asc(root.get("username")));
                query.orderBy(order);
            }
            return cb.conjunction();
        });
    }

    private static Expression<Integer> rank(Root<User> root, CriteriaBuilder cb, String phrase) {
        Expression<String> first = root.get("firstName");
        Expression<String> last = root.get("lastName");
        Expression<String> firstLast = cb.concat(cb.concat(first, " "), last);
        Expression<String> lastFirst = cb.concat(cb.concat(last, " "), first);
        Predicate exactFullName = cb.or(
                SearchPredicates.foldedEquals(cb, firstLast, phrase),
                SearchPredicates.foldedEquals(cb, lastFirst, phrase));
        Predicate exactField = cb.or(
                SearchPredicates.foldedEquals(cb, last, phrase),
                SearchPredicates.foldedEquals(cb, first, phrase),
                SearchPredicates.foldedEquals(cb, root.get("username"), phrase),
                SearchPredicates.foldedEquals(cb, root.get("email"), phrase));
        Predicate prefix = cb.or(
                SearchPredicates.foldedStartsWith(cb, last, phrase),
                SearchPredicates.foldedStartsWith(cb, first, phrase),
                SearchPredicates.foldedStartsWith(cb, firstLast, phrase));
        return cb.<Integer>selectCase()
                .when(exactFullName, 0)
                .when(exactField, 1)
                .when(prefix, 2)
                .otherwise(3);
    }

    private static Subquery<Integer> additionalEmailContains(Root<User> root, Subquery<Integer> sub,
            CriteriaBuilder cb, String term) {
        Root<UserAdditionalEmail> additional = sub.from(UserAdditionalEmail.class);
        return sub.select(cb.literal(1)).where(
                cb.equal(additional.get("user"), root),
                SearchPredicates.contains(cb, additional.get("email"), term));
    }

    public static Specification<User> hasRole(Role role) {
        if (role == null) {
            return Specification.unrestricted();
        }
        return (root, query, cb) -> cb.isMember(role, root.get("roles"));
    }

    public static Specification<User> inCompany(String companyId) {
        if (companyId == null) {
            return Specification.unrestricted();
        }
        return (root, query, cb) -> cb.equal(root.get("companyId"), companyId);
    }

    public static Specification<User> isActive(Boolean active) {
        if (active == null) {
            return Specification.unrestricted();
        }
        return (root, query, cb) -> cb.equal(root.get("isActive"), active);
    }
}
