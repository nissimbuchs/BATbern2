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
     * Performance Optimization: Now supports search across denormalized attendee fields
     *
     * @param eventId Event UUID (required)
     * @param statuses List of status values to filter by (optional)
     * @param search Search term for attendee name/email (optional, searches denormalized fields)
     * @param companyId Company ID to filter by (optional, uses denormalized field)
     * @return Specification for filtering registrations
     */
    public static Specification<Registration> buildSpecification(
            UUID eventId,
            List<String> statuses,
            String search,
            String companyId
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

            // Search across denormalized attendee fields (username, firstName, lastName, email)
            if (search != null && !search.isEmpty()) {
                String searchLower = "%" + search.toLowerCase() + "%";
                Predicate usernamePredicate = criteriaBuilder.like(
                        criteriaBuilder.lower(root.get("attendeeUsername")), searchLower);
                Predicate firstNamePredicate = criteriaBuilder.like(
                        criteriaBuilder.lower(root.get("attendeeFirstName")), searchLower);
                Predicate lastNamePredicate = criteriaBuilder.like(
                        criteriaBuilder.lower(root.get("attendeeLastName")), searchLower);
                Predicate emailPredicate = criteriaBuilder.like(
                        criteriaBuilder.lower(root.get("attendeeEmail")), searchLower);

                predicates.add(criteriaBuilder.or(
                        usernamePredicate, firstNamePredicate, lastNamePredicate, emailPredicate));
            }

            // Filter by company ID (uses denormalized field)
            if (companyId != null && !companyId.isEmpty()) {
                predicates.add(criteriaBuilder.equal(root.get("attendeeCompanyId"), companyId));
            }

            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }
}
