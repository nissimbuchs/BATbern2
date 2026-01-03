package ch.batbern.events.specification;

import ch.batbern.events.domain.Registration;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * JPA Specification builder for Registration filtering
 * Story 3.3: Event Participants Tab - Filter support
 */
public class RegistrationSpecification {

    /**
     * Build specification for filtering registrations by event and optional status
     *
     * @param eventId Event UUID (required)
     * @param statuses List of status values to filter by (optional)
     * @return Specification for filtering registrations
     */
    public static Specification<Registration> filterByEventAndStatus(
            UUID eventId,
            List<String> statuses
    ) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            // Always filter by event ID
            predicates.add(criteriaBuilder.equal(root.get("eventId"), eventId));

            // Filter by status if provided
            if (statuses != null && !statuses.isEmpty()) {
                // Convert to uppercase for case-insensitive matching
                List<String> upperStatuses = statuses.stream()
                        .map(String::toUpperCase)
                        .toList();
                predicates.add(criteriaBuilder.upper(root.get("status")).in(upperStatuses));
            }

            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }

    /**
     * Build specification for filtering registrations by event and attendee username search
     *
     * @param eventId Event UUID (required)
     * @param searchTerm Search term for attendee username (optional)
     * @return Specification for filtering registrations
     */
    public static Specification<Registration> filterByEventAndUsernameSearch(
            UUID eventId,
            String searchTerm
    ) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            // Always filter by event ID
            predicates.add(criteriaBuilder.equal(root.get("eventId"), eventId));

            // Search in attendee username if provided
            if (searchTerm != null && !searchTerm.isEmpty()) {
                predicates.add(
                        criteriaBuilder.like(
                                criteriaBuilder.lower(root.get("attendeeUsername")),
                                "%" + searchTerm.toLowerCase() + "%"
                        )
                );
            }

            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }

    /**
     * Build combined specification for all database-level filters
     * (Status can be filtered in DB, search/companyId require user enrichment)
     *
     * @param eventId Event UUID (required)
     * @param statuses List of status values to filter by (optional)
     * @param usernameSearch Search term for attendee username (optional, DB-level only)
     * @return Specification for filtering registrations
     */
    public static Specification<Registration> buildSpecification(
            UUID eventId,
            List<String> statuses,
            String usernameSearch
    ) {
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

            // Search in attendee username if provided
            if (usernameSearch != null && !usernameSearch.isEmpty()) {
                predicates.add(
                        criteriaBuilder.like(
                                criteriaBuilder.lower(root.get("attendeeUsername")),
                                "%" + usernameSearch.toLowerCase() + "%"
                        )
                );
            }

            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }
}
