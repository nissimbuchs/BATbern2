package ch.batbern.events.specification;

import ch.batbern.events.domain.NewsletterSubscriber;
import ch.batbern.shared.search.SearchPredicates;
import ch.batbern.shared.search.SearchTerms;
import org.springframework.data.jpa.domain.Specification;

/**
 * Filters for the organizer's subscriber list (Story 10.28), in SQL before paging so that the page
 * and the total agree.
 */
public final class NewsletterSubscriberSpecification {

    private NewsletterSubscriberSpecification() {
    }

    /**
     * Every word of {@code search} occurs in the e-mail, the first name or the username,
     * folded by {@code batbern_fold}, in any order. The username of a subscriber with a profile
     * carries the last name ("hans.muster"; 1,978 of 1,978 measured locally on 2026-11-08), so a
     * last name is found without an EMS read of CUMS {@code user_profiles} (ADR-016). Blank:
     * unrestricted.
     */
    public static Specification<NewsletterSubscriber> matchesSearch(String search) {
        SearchTerms terms = SearchTerms.parse(search);
        if (terms.isEmpty()) {
            return Specification.unrestricted();
        }
        return (root, query, cb) -> SearchPredicates.allTermsInAnyField(cb, terms,
                root.get("email"), root.get("firstName"), root.get("username"));
    }

    /**
     * {@code active} (neither unsubscribed nor suppressed), {@code unsubscribed}, {@code suppressed};
     * {@code all} or null is unrestricted; any other value matches nothing (as the former JPQL).
     */
    public static Specification<NewsletterSubscriber> hasStatus(String status) {
        if (status == null) {
            return Specification.unrestricted();
        }
        return switch (status) {
            case "all" -> Specification.unrestricted();
            case "active" -> (root, query, cb) -> cb.and(
                    cb.isNull(root.get("unsubscribedAt")), cb.isNull(root.get("suppressedAt")));
            case "unsubscribed" -> (root, query, cb) -> cb.isNotNull(root.get("unsubscribedAt"));
            case "suppressed" -> (root, query, cb) -> cb.isNotNull(root.get("suppressedAt"));
            default -> (root, query, cb) -> cb.disjunction();
        };
    }
}
