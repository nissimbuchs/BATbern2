package ch.batbern.events.repository;

import ch.batbern.events.domain.Event;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
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

    /**
     * Find the first event with given workflow state ordered by date ascending
     * Used for getting the current/next upcoming event for the public website
     * Story 4.1.3: Public event landing page
     * V17 Migration: Changed from status to workflowState
     *
     * @param workflowState The event workflow state (e.g., AGENDA_PUBLISHED)
     * @return Optional containing the next event with this workflow state if found
     */
    Optional<Event> findFirstByWorkflowStateOrderByDateAsc(ch.batbern.shared.types.EventWorkflowState workflowState);

    /**
     * Find the first event matching any of the given workflow states, ordered by date ascending
     * Used for getting the current/next upcoming event for the public website
     * V17 Migration: Changed from statuses to workflowStates
     *
     * @param workflowStates List of workflow states to match
     * @return Optional containing the next event matching any workflow state if found
     */
    Optional<Event> findFirstByWorkflowStateInOrderByDateAsc(
            List<ch.batbern.shared.types.EventWorkflowState> workflowStates);

    /**
     * Find an event by its event number
     *
     * @param eventNumber The event number to search for
     * @return Optional containing the event if found
     */
    Optional<Event> findByEventNumber(Integer eventNumber);

    /**
     * Find events by workflow state where event date is on the given date
     * Used by scheduled jobs to find events going live today
     * GAP-2: Automatic workflow transitions
     *
     * @param workflowState The event workflow state
     * @param startOfDay Start of the day (00:00:00)
     * @param endOfDay End of the day (23:59:59)
     * @return List of events matching the criteria
     */
    List<Event> findByWorkflowStateAndDateBetween(
            ch.batbern.shared.types.EventWorkflowState workflowState,
            Instant startOfDay,
            Instant endOfDay
    );

    /**
     * Find events by workflow state where event date is before the given date
     * Used by scheduled jobs to find events that have completed
     * GAP-2: Automatic workflow transitions
     *
     * @param workflowState The event workflow state
     * @param beforeDate Events before this date
     * @return List of events matching the criteria
     */
    List<Event> findByWorkflowStateAndDateBefore(
            ch.batbern.shared.types.EventWorkflowState workflowState,
            Instant beforeDate
    );
}
