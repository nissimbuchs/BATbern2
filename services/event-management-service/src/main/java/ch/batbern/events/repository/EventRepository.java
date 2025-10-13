package ch.batbern.events.repository;

import ch.batbern.events.domain.Event;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

/**
 * Event Repository
 * Story 1.15a.1: Events API Consolidation
 *
 * Extends JpaSpecificationExecutor for dynamic query support (filtering, sorting).
 * This enables use of Spring Data JPA Specifications with FilterParser from Story 1.15a.
 */
@Repository
public interface EventRepository extends JpaRepository<Event, String>, JpaSpecificationExecutor<Event> {
    // JpaRepository provides standard CRUD operations
    // JpaSpecificationExecutor enables dynamic queries for filtering and sorting
}
