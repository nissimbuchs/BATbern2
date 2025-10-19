package ch.batbern.events.repository;

import ch.batbern.events.domain.Event;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Event Repository
 * Story 1.16.2: Eliminate UUIDs from API
 *
 * Extends JpaSpecificationExecutor for dynamic query support (filtering, sorting).
 * This enables use of Spring Data JPA Specifications with FilterParser from Story 1.15a.
 */
@Repository
public interface EventRepository extends JpaRepository<Event, UUID>, JpaSpecificationExecutor<Event> {
    // JpaRepository provides standard CRUD operations by UUID (internal)
    // JpaSpecificationExecutor enables dynamic queries for filtering and sorting

    /**
     * Find an event by its event code (public API identifier)
     * Story 1.16.2: eventCode is the public-facing identifier
     *
     * @param eventCode The event code (e.g., "BATbern56")
     * @return Optional containing the event if found
     */
    Optional<Event> findByEventCode(String eventCode);

    /**
     * Check if an event with the given code exists
     *
     * @param eventCode The event code to check
     * @return true if an event with this code exists
     */
    boolean existsByEventCode(String eventCode);
}
