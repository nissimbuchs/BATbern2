package ch.batbern.events.repository;

import ch.batbern.events.domain.EventTask;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Repository for EventTask entity (Story 5.5 AC19-27).
 *
 * Provides data access for event-specific tasks assigned to organizers.
 */
@Repository
public interface EventTaskRepository extends JpaRepository<EventTask, UUID> {

    /**
     * Find all tasks for a specific event.
     *
     * @param eventId the event ID
     * @return list of tasks for this event
     */
    List<EventTask> findByEventId(UUID eventId);

    /**
     * Find all tasks assigned to a specific organizer.
     *
     * @param username the organizer's username
     * @return list of tasks assigned to this organizer
     */
    List<EventTask> findByAssignedOrganizerUsername(String username);

    /**
     * Find all tasks by status.
     *
     * @param status the task status ('todo', 'in_progress', 'completed')
     * @return list of tasks with this status
     */
    List<EventTask> findByStatus(String status);

    /**
     * Find all tasks for an event with a specific status.
     *
     * @param eventId the event ID
     * @param status the task status
     * @return list of tasks
     */
    List<EventTask> findByEventIdAndStatus(UUID eventId, String status);

    /**
     * Find all tasks assigned to an organizer with a specific status.
     *
     * @param username the organizer's username
     * @param status the task status
     * @return list of tasks
     */
    List<EventTask> findByAssignedOrganizerUsernameAndStatus(String username, String status);

    /**
     * Check if a task already exists for an event from a specific template.
     * Used for idempotency (AC36) - prevent duplicate task creation.
     *
     * @param eventId the event ID
     * @param templateId the template ID
     * @return true if task exists
     */
    boolean existsByEventIdAndTemplateId(UUID eventId, UUID templateId);

    /**
     * Check if tasks already exist for an event and trigger state.
     * Used for idempotency at workflow transition level.
     *
     * @param eventId the event ID
     * @param triggerState the trigger state
     * @return true if tasks exist
     */
    boolean existsByEventIdAndTriggerState(UUID eventId, String triggerState);

    /**
     * Find tasks due within a time window that are not completed and have an assigned organizer.
     * Used by the deadline reminder scheduler (Story 10.3) to find tasks due "tomorrow".
     *
     * @param from start of the window (inclusive)
     * @param to end of the window (exclusive)
     * @return list of tasks due in the window, not completed, with an assignee
     */
    @Query("SELECT t FROM EventTask t WHERE t.dueDate >= :from AND t.dueDate < :to "
            + "AND t.status NOT IN ('completed', 'cancelled') AND t.assignedOrganizerUsername IS NOT NULL")
    List<EventTask> findTasksDueForReminder(@Param("from") Instant from, @Param("to") Instant to);

    /**
     * Bulk-cancel all non-completed, non-cancelled tasks for an event being archived.
     * Story 10.18: Event Archival Task &amp; Notification Cleanup (AC1).
     *
     * @param eventId the event UUID
     * @return number of tasks updated
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE EventTask t SET t.status = 'cancelled', t.cancelledReason = 'Event archived', "
            + "t.cancelledAt = CURRENT_TIMESTAMP WHERE t.eventId = :eventId "
            + "AND t.status NOT IN ('completed', 'cancelled')")
    int cancelOpenTasksForEvent(@Param("eventId") UUID eventId);
}
