package ch.batbern.events.service;

import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.EventTask;
import ch.batbern.events.domain.TaskTemplate;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.repository.EventTaskRepository;
import ch.batbern.events.repository.TaskTemplateRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service for event task management and auto-creation (Story 5.5 AC19-27).
 *
 * Handles:
 * - Task auto-creation on workflow state transitions (ApplicationListener)
 * - Task completion tracking
 * - Task assignment to organizers
 * - Idempotency for task creation (AC36)
 * - Due date calculation based on template configuration
 *
 * Auto-Creation Workflow (AC23):
 * 1. Event transitions to workflow state (e.g., TOPIC_SELECTION)
 * 2. EventWorkflowTransitionEvent published
 * 3. EventTaskService listens for event
 * 4. Finds all templates with matching trigger_state
 * 5. Creates event_tasks for each template
 * 6. Calculates due_date based on event_date + offset_days
 * 7. Sets status='todo', assigned_organizer if specified
 *
 * Idempotency (AC36):
 * - Check existsByEventIdAndTemplateId() before creating task
 * - Unique index on (event_id, template_id) prevents duplicates at DB level
 * - Duplicate events are logged and skipped (no error thrown)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EventTaskService {

    private final EventTaskRepository eventTaskRepository;
    private final TaskTemplateRepository taskTemplateRepository;
    private final EventRepository eventRepository;

    /**
     * Create tasks for an event from a list of templates (AC21, AC23).
     *
     * Used during event creation to generate tasks with status="pending".
     * Idempotent - safe to call multiple times (AC36).
     *
     * @param eventId the event ID
     * @param templates list of task templates to create tasks from
     * @return list of created tasks
     */
    @Transactional
    public List<EventTask> createTasksForEvent(UUID eventId, List<TaskTemplate> templates) {
        log.info("Creating {} tasks for event {}", templates.size(), eventId);

        // Get event to access date for due date calculation
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new EntityNotFoundException("Event not found: " + eventId));

        List<EventTask> createdTasks = new ArrayList<>();

        for (TaskTemplate template : templates) {
            // Idempotency check (AC36)
            if (eventTaskRepository.existsByEventIdAndTemplateId(eventId, template.getId())) {
                log.debug("Task already exists for event {} and template {}, skipping",
                        eventId, template.getId());
                continue;
            }

            // Create task
            EventTask task = new EventTask();
            task.setEventId(eventId);
            task.setTemplateId(template.getId());
            task.setTaskName(template.getName());
            task.setTriggerState(template.getTriggerState());
            task.setDueDate(calculateDueDate(template, event.getDate()));
            task.setStatus("pending"); // AC21 - tasks start as pending
            task.setAssignedOrganizerUsername(null); // No default assignment

            EventTask saved = eventTaskRepository.save(task);
            createdTasks.add(saved);
            log.debug("Created task {} from template {}", saved.getId(), template.getId());
        }

        log.info("Created {} tasks for event {}", createdTasks.size(), eventId);
        return createdTasks;
    }

    /**
     * Auto-create tasks for an event when it transitions to a workflow state.
     *
     * Called by EventWorkflowTransitionEvent listener (AC23).
     * Idempotent - safe to call multiple times for same event/state (AC36).
     *
     * @param eventId the event ID
     * @param triggeredState the workflow state that triggered task creation
     * @param eventDate the event date (used for due date calculation)
     * @return list of created tasks
     */
    @Transactional
    public List<EventTask> autoCreateTasksForState(
            UUID eventId,
            String triggeredState,
            LocalDateTime eventDate
    ) {
        log.info("Auto-creating tasks for event {} at state: {}", eventId, triggeredState);

        // TODO: Implement task auto-creation (Phase 5)
        // 1. Check idempotency: existsByEventIdAndTriggerState()
        // 2. If already processed, log and return empty list (AC36)
        // 3. Find all templates with trigger_state = triggeredState
        // 4. For each template:
        //    a. Check if task already exists (existsByEventIdAndTemplateId)
        //    b. If not, create task with calculated due date
        //    c. Set status='todo', assigned_organizer from template
        // 5. Return list of created tasks

        throw new UnsupportedOperationException("Task auto-creation not yet implemented");
    }

    /**
     * Calculate due date for a task based on template configuration.
     *
     * @param template the task template
     * @param eventDate the event date (Instant)
     * @return the calculated due date
     */
    private Instant calculateDueDate(TaskTemplate template, Instant eventDate) {
        return switch (template.getDueDateType()) {
            case "immediate" -> Instant.now();
            case "relative_to_event" -> {
                int offsetDays = template.getDueDateOffsetDays() != null ? template.getDueDateOffsetDays() : 0;
                yield eventDate.plus(offsetDays, ChronoUnit.DAYS);
            }
            case "absolute" -> {
                // For absolute dates, would need absolute_due_date field in template
                log.warn("Absolute due date type not yet supported, using immediate");
                yield Instant.now();
            }
            default -> {
                log.warn("Unknown due date type: {}, using immediate", template.getDueDateType());
                yield Instant.now();
            }
        };
    }

    /**
     * Get all tasks for an event.
     *
     * @param eventId the event ID
     * @return list of tasks (AC24)
     */
    public List<EventTask> getEventTasks(UUID eventId) {
        log.debug("Fetching all tasks for event: {}", eventId);
        return eventTaskRepository.findByEventId(eventId);
    }

    /**
     * Get all tasks assigned to an organizer.
     *
     * @param username the organizer's username
     * @return list of tasks (AC24)
     */
    public List<EventTask> getMyTasks(String username) {
        log.debug("Fetching tasks for organizer: {}", username);
        return eventTaskRepository.findByAssignedOrganizerUsername(username);
    }

    /**
     * Mark a task as complete (AC25).
     *
     * @param taskId the task ID
     * @param completedByUsername the username of the organizer completing the task
     * @param notes optional completion notes
     * @return the updated task
     * @throws jakarta.persistence.EntityNotFoundException if task not found
     */
    @Transactional
    public EventTask completeTask(UUID taskId, String completedByUsername, String notes) {
        log.info("Completing task: {} by user: {}", taskId, completedByUsername);

        // Find task
        EventTask task = eventTaskRepository.findById(taskId)
                .orElseThrow(() -> new EntityNotFoundException("Task not found: " + taskId));

        // Update completion details
        task.setStatus("completed");
        task.setCompletedDate(Instant.now());
        task.setCompletedByUsername(completedByUsername);
        if (notes != null && !notes.trim().isEmpty()) {
            task.setNotes(notes);
        }

        EventTask updated = eventTaskRepository.save(task);
        log.info("Task {} marked as completed by {}", taskId, completedByUsername);

        return updated;
    }

    /**
     * Get all tasks assigned to an organizer (AC24).
     *
     * @param username the organizer's username
     * @return list of tasks assigned to this organizer
     */
    public List<EventTask> getTasksForOrganizer(String username) {
        log.debug("Fetching tasks for organizer: {}", username);
        return eventTaskRepository.findByAssignedOrganizerUsername(username);
    }

    /**
     * Get critical tasks for an organizer (overdue + due soon < 3 days) (AC24).
     *
     * @param username the organizer's username
     * @return list of critical tasks
     */
    public List<EventTask> getCriticalTasksForOrganizer(String username) {
        log.debug("Fetching critical tasks for organizer: {}", username);

        // Get all active tasks for organizer
        List<EventTask> allTasks = eventTaskRepository.findByAssignedOrganizerUsernameAndStatus(username, "todo");

        // Filter for overdue or due soon (< 3 days)
        Instant now = Instant.now();
        Instant dueSoonThreshold = now.plus(3, ChronoUnit.DAYS);

        return allTasks.stream()
                .filter(task -> task.getDueDate() != null)
                .filter(task -> task.getDueDate().isBefore(dueSoonThreshold))
                .collect(Collectors.toList());
    }

    /**
     * Reassign a task to a different organizer (AC27).
     *
     * @param taskId the task ID
     * @param newOrganizerUsername the new organizer's username
     * @return the updated task
     * @throws jakarta.persistence.EntityNotFoundException if task not found
     */
    @Transactional
    public EventTask reassignTask(UUID taskId, String newOrganizerUsername) {
        log.info("Reassigning task {} to organizer: {}", taskId, newOrganizerUsername);

        // Find task
        EventTask task = eventTaskRepository.findById(taskId)
                .orElseThrow(() -> new EntityNotFoundException("Task not found: " + taskId));

        // Update assignee
        task.setAssignedOrganizerUsername(newOrganizerUsername);

        EventTask updated = eventTaskRepository.save(task);
        log.info("Task {} reassigned to {}", taskId, newOrganizerUsername);

        return updated;
    }

    /**
     * Create an ad-hoc task (not from template).
     *
     * @param eventId the event ID
     * @param taskName the task name
     * @param triggerState the trigger state
     * @param dueDate the due date
     * @param assignedOrganizerUsername the assigned organizer (optional)
     * @return the created task
     */
    @Transactional
    public EventTask createAdHocTask(
            UUID eventId,
            String taskName,
            String triggerState,
            Instant dueDate,
            String assignedOrganizerUsername
    ) {
        log.info("Creating ad-hoc task for event {}: {}", eventId, taskName);

        // TODO: Implement ad-hoc task creation (Phase 5)
        // 1. Create task with template_id=null (ad-hoc marker)
        // 2. Set provided fields
        // 3. Set status='todo'
        // 4. Save and return

        throw new UnsupportedOperationException("Ad-hoc task creation not yet implemented");
    }
}
