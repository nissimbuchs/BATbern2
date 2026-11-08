package ch.batbern.events.specification;

import ch.batbern.events.domain.Registration;
import ch.batbern.shared.search.SearchPredicates;
import ch.batbern.shared.search.SearchTerms;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * JPA Specification builder for Registration filtering
 * Story 3.3: Event Participants Tab - Filter support
 */
public final class RegistrationSpecification {

    private RegistrationSpecification() {
    }

    /**
     * Build combined specification for all database-level filters, evaluated before paging.
     *
     * @param eventId Event UUID (required)
     * @param statuses List of status values to filter by (optional)
     * @param search Free-text search (optional): every word occurs in the attendee's
     *     username, first name, last name, e-mail or company id (denormalized columns), folded by
     *     {@code batbern_fold}, in any order; blank means no search
     * @param companyId Company ID to filter by (optional, uses denormalized field)
     * @return Specification for filtering registrations
     */
    public static Specification<Registration> buildSpecification(
            UUID eventId,
            List<String> statuses,
            String search,
            String companyId
    ) {
        SearchTerms terms = SearchTerms.parse(search);
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            // Always filter by event ID
            predicates.add(criteriaBuilder.equal(root.get("eventId"), eventId));

            // Filter by status if provided
            if (statuses != null && !statuses.isEmpty()) {
                List<String> upperStatuses = statuses.stream()
                        .map(String::toUpperCase)
                        .toList();
                predicates.add(criteriaBuilder.upper(root.get("status")).in(upperStatuses));
            }

            if (!terms.isEmpty()) {
                predicates.add(SearchPredicates.allTermsInAnyField(criteriaBuilder, terms,
                        root.get("attendeeUsername"),
                        root.get("attendeeFirstName"),
                        root.get("attendeeLastName"),
                        root.get("attendeeEmail"),
                        root.get("attendeeCompanyId")));
            }

            // Filter by company ID (uses denormalized field)
            if (companyId != null && !companyId.isEmpty()) {
                predicates.add(criteriaBuilder.equal(root.get("attendeeCompanyId"), companyId));
            }

            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }
}
