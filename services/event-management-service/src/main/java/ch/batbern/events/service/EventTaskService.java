package ch.batbern.events.service;

import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.EventTask;
import ch.batbern.events.domain.TaskTemplate;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.repository.EventTaskRepository;
import ch.batbern.events.repository.TaskTemplateRepository;
import ch.batbern.shared.types.EventWorkflowState;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

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
     * Trigger value that is not a workflow state: the speakers phase has been published
     * ({@code SpeakersPhasePublishedEvent}). Organizer cockpit plan T5.
     */
    public static final String TRIGGER_SPEAKERS_PUBLISHED = "speakers_published";

    /** Default-template keys (V125) used for newsletter supersession (plan T7). */
    static final String KEY_NEWSLETTER_TOPIC = "newsletter_topic";
    static final String KEY_NEWSLETTER_SPEAKERS = "newsletter_speakers";

    private static final List<String> OPEN_STATUSES = List.of("pending", "todo", "in_progress");
    private static final Set<String> PUBLISHED_SPEAKERS_PHASES = Set.of("speakers", "agenda");

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
            task.setActivationLeadDays(template.getActivationLeadDays());
            // AC21: tasks start as pending — unless the event is already past the trigger,
            // in which case no future transition would ever activate them.
            task.setStatus(initialStatus(template.getTriggerState(), event));
            task.setAssignedOrganizerUsername(null); // No default assignment

            EventTask saved = eventTaskRepository.save(task);
            createdTasks.add(saved);
            log.debug("Created task {} from template {}", saved.getId(), template.getId());
        }

        log.info("Created {} tasks for event {}", createdTasks.size(), eventId);
        supersedeNewsletterTasks(event);
        return createdTasks;
    }

    /**
     * Auto-activate pending tasks when event reaches a trigger state.
     *
     * Called by EventWorkflowTransitionEvent listener (AC23).
     * Activates tasks created with status="pending" at event creation,
     * transitioning them to status="todo" when the event reaches their trigger state.
     * Idempotent - safe to call multiple times for same event/state (AC36).
     *
     * @param eventId the event ID
     * @param triggeredState the workflow state that triggered task activation (lowercase_snake_case)
     * @param eventDate the event date (used for due date recalculation if needed)
     * @return list of activated tasks
     */
    @Transactional
    public List<EventTask> autoCreateTasksForState(
            UUID eventId,
            String triggeredState,
            Instant eventDate
    ) {
        log.info("Activating pending tasks for event {} at state: {}", eventId, triggeredState);

        EventWorkflowState currentState = parseState(triggeredState);
        if (currentState == null) {
            log.warn("Invalid workflow state: {}, cannot activate tasks", triggeredState);
            return List.of();
        }
        Event event = eventRepository.findById(eventId).orElse(null);
        String publishedPhase = event != null ? event.getCurrentPublishedPhase() : null;

        // Trigger states <= current state are activated too (handles state skipping), e.g.
        // CREATED -> SPEAKER_IDENTIFICATION activates CREATED, TOPIC_SELECTION and
        // SPEAKER_IDENTIFICATION tasks.
        List<EventTask> activated = activate(
                eventTaskRepository.findByEventIdAndStatus(eventId, "pending").stream()
                        .filter(task -> isTriggerReached(task.getTriggerState(), currentState, publishedPhase))
                        .toList(),
                "state " + triggeredState);

        if (event != null) {
            supersedeNewsletterTasks(event);
        }
        return activated;
    }

    /**
     * Speakers phase published (plan T5 + T7): activates {@code speakers_published} tasks and
     * cancels the then-obsolete Topic Announcement newsletter task.
     *
     * @param eventCode the event whose speakers phase was just published
     * @return list of activated tasks
     */
    @Transactional
    public List<EventTask> onSpeakersPhasePublished(String eventCode) {
        Event event = eventRepository.findByEventCode(eventCode).orElse(null);
        if (event == null) {
            log.warn("Speakers phase published for unknown event {}", eventCode);
            return List.of();
        }
        List<EventTask> activated = activate(
                eventTaskRepository.findByEventIdAndStatus(event.getId(), "pending").stream()
                        .filter(task -> isTriggerReached(task.getTriggerState(), event))
                        .toList(),
                "speakers phase published");
        supersedeNewsletterTasks(event);
        return activated;
    }

    /**
     * Level-triggered safety net (plan T1 + T4), run daily by {@code TaskActivationScheduler}.
     *
     * <p>Activates every pending task whose trigger has been reached — whether or not a
     * transition event fired — or whose due date is within its activation lead days. Then
     * applies newsletter supersession to every event with open tasks. Idempotent.
     *
     * @param now the reference time (injected for tests)
     * @return number of tasks activated
     */
    @Transactional
    public int reconcileOpenTasks(Instant now) {
        List<EventTask> openTasks = eventTaskRepository.findByStatusIn(OPEN_STATUSES);
        Map<UUID, Event> events = new HashMap<>();
        openTasks.stream().map(EventTask::getEventId).distinct()
                .forEach(id -> eventRepository.findById(id)
                        .filter(e -> e.getWorkflowState() != EventWorkflowState.ARCHIVED)
                        .ifPresent(e -> events.put(id, e)));

        List<EventTask> toActivate = openTasks.stream()
                .filter(task -> "pending".equals(task.getStatus()))
                .filter(task -> events.containsKey(task.getEventId()))
                .filter(task -> isTriggerReached(task.getTriggerState(), events.get(task.getEventId()))
                        || isWithinLeadDays(task, now))
                .toList();
        List<EventTask> activated = activate(toActivate, "reconcile");

        events.values().forEach(this::supersedeNewsletterTasks);
        return activated.size();
    }

    /**
     * Newsletter supersession (plan T7, Nissim 2026-10-24): an announcement is obsolete once a
     * later one has gone out. Topic Announcement once the speakers or the agenda are public;
     * Speaker Lineup once the agenda is public. Identified by template {@code system_key}
     * (V125), not by name, so renaming a template cannot silently disable the rule.
     */
    private void supersedeNewsletterTasks(Event event) {
        boolean agendaPublic = "agenda".equals(event.getCurrentPublishedPhase())
                || isStateAtLeast(event.getWorkflowState(), EventWorkflowState.AGENDA_PUBLISHED);
        boolean speakersPublic = agendaPublic || isSpeakersPhasePublished(event.getCurrentPublishedPhase());
        if (speakersPublic) {
            cancelOpenTasksFromTemplate(event, KEY_NEWSLETTER_TOPIC,
                    "Newsletter superseded: speakers published");
        }
        if (agendaPublic) {
            cancelOpenTasksFromTemplate(event, KEY_NEWSLETTER_SPEAKERS,
                    "Newsletter superseded: agenda published");
        }
    }

    private void cancelOpenTasksFromTemplate(Event event, String systemKey, String reason) {
        taskTemplateRepository.findBySystemKey(systemKey).ifPresent(template -> {
            List<EventTask> open = eventTaskRepository.findByEventIdAndTemplateIdInAndStatusIn(
                    event.getId(), List.of(template.getId()), OPEN_STATUSES);
            Instant now = Instant.now();
            open.forEach(task -> {
                task.setStatus("cancelled");
                task.setCancelledReason(reason);
                task.setCancelledAt(now);
                log.info("Cancelled task {} ({}) for event {}: {}",
                        task.getId(), task.getTaskName(), event.getEventCode(), reason);
            });
            eventTaskRepository.saveAll(open);
        });
    }

    private List<EventTask> activate(List<EventTask> tasks, String cause) {
        if (tasks.isEmpty()) {
            return List.of();
        }
        tasks.forEach(task -> {
            task.setStatus("todo");
            log.info("Activated task {} ({}, trigger {}) via {}",
                    task.getId(), task.getTaskName(), task.getTriggerState(), cause);
        });
        return eventTaskRepository.saveAll(tasks);
    }

    private String initialStatus(String triggerState, Event event) {
        return isTriggerReached(triggerState, event) ? "todo" : "pending";
    }

    private boolean isTriggerReached(String triggerState, Event event) {
        return isTriggerReached(triggerState, event.getWorkflowState(), event.getCurrentPublishedPhase());
    }

    private boolean isTriggerReached(String triggerState, EventWorkflowState currentState, String publishedPhase) {
        if (triggerState == null || currentState == null) {
            return false;
        }
        if (TRIGGER_SPEAKERS_PUBLISHED.equals(triggerState)) {
            return isSpeakersPhasePublished(publishedPhase)
                    || isStateAtLeast(currentState, EventWorkflowState.AGENDA_PUBLISHED);
        }
        EventWorkflowState trigger = parseState(triggerState);
        if (trigger == null) {
            log.warn("Invalid trigger state on task: {}", triggerState);
            return false;
        }
        return isStateAtLeast(currentState, trigger);
    }

    private static boolean isSpeakersPhasePublished(String publishedPhase) {
        // Set.of(...).contains(null) throws — unpublished events carry a null phase
        return publishedPhase != null && PUBLISHED_SPEAKERS_PHASES.contains(publishedPhase);
    }

    private static boolean isWithinLeadDays(EventTask task, Instant now) {
        return task.getActivationLeadDays() != null
                && task.getDueDate() != null
                && !now.isBefore(task.getDueDate().minus(task.getActivationLeadDays(), ChronoUnit.DAYS));
    }

    private static boolean isStateAtLeast(EventWorkflowState state, EventWorkflowState reference) {
        return state != null && state.ordinal() >= reference.ordinal();
    }

    private static EventWorkflowState parseState(String state) {
        try {
            return EventWorkflowState.valueOf(state.toUpperCase());
        } catch (IllegalArgumentException | NullPointerException e) {
            return null;
        }
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
     * Get all tasks (for all organizers).
     * Story 5.5: Task dashboard with "All Tasks" filter
     *
     * @return list of all tasks
     */
    public List<EventTask> getAllTasks() {
        log.debug("Fetching all tasks");
        return eventTaskRepository.findAll();
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
     * Update task status (for drag-and-drop transitions).
     * Story 5.5: Allow manual status transitions via drag-and-drop
     *
     * Supported transitions:
     * - pending → todo (manual activation)
     * - pending → completed (skip todo phase)
     * - todo → completed (normal completion)
     * - todo → pending (revert to pending)
     * - completed → todo (reopen task)
     * - completed → pending (reopen and revert)
     *
     * @param taskId the task ID
     * @param newStatus the new status
     * @return the updated task
     * @throws jakarta.persistence.EntityNotFoundException if task not found
     */
    @Transactional
    public EventTask updateTaskStatus(UUID taskId, String newStatus) {
        log.info("Updating task {} status to: {}", taskId, newStatus);

        // Find task
        EventTask task = eventTaskRepository.findById(taskId)
                .orElseThrow(() -> new EntityNotFoundException("Task not found: " + taskId));

        // Validate status value
        if (!newStatus.matches("pending|todo|in_progress|completed")) {
            throw new IllegalArgumentException(
                    "Invalid status: " + newStatus
                            + ". Must be one of: pending, todo, in_progress, completed");
        }

        String oldStatus = task.getStatus();

        // Update status
        task.setStatus(newStatus);

        // If transitioning to completed, set completion details (if not already set)
        if ("completed".equals(newStatus) && task.getCompletedDate() == null) {
            task.setCompletedDate(Instant.now());
            // Note: completedByUsername should be set separately via completeTask() for proper tracking
            // This is for drag-and-drop quick completion without notes
        }

        // If reopening from completed, clear completion details
        if (!"completed".equals(newStatus) && "completed".equals(oldStatus)) {
            task.setCompletedDate(null);
            task.setCompletedByUsername(null);
        }

        EventTask updated = eventTaskRepository.save(task);
        log.info("Task {} status updated from {} to {}", taskId, oldStatus, newStatus);

        return updated;
    }

    /**
     * Create tasks from templates with assigned organizers (Story 5.5 AC21).
     *
     * Used during event creation/editing to generate tasks from selected templates.
     * Unlike createTasksForEvent(), this method accepts assignee information per template.
     * Idempotent - safe to call multiple times (AC36).
     *
     * @param eventId the event ID
     * @param templateConfigs list of template IDs with optional assignees
     * @return list of created tasks
     */
    @Transactional
    public List<EventTask> createTasksFromTemplatesWithAssignees(
            UUID eventId,
            List<TemplateAssignmentConfig> templateConfigs
    ) {
        log.info("Creating {} tasks from templates for event {}", templateConfigs.size(), eventId);

        // Get event to access date for due date calculation
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new EntityNotFoundException("Event not found: " + eventId));

        List<EventTask> createdTasks = new ArrayList<>();

        for (TemplateAssignmentConfig config : templateConfigs) {
            // Idempotency check (AC36)
            if (eventTaskRepository.existsByEventIdAndTemplateId(eventId, config.templateId())) {
                log.debug("Task already exists for event {} and template {}, skipping",
                        eventId, config.templateId());
                continue;
            }

            // Fetch template
            TaskTemplate template = taskTemplateRepository.findById(config.templateId())
                    .orElseThrow(() -> new EntityNotFoundException("Template not found: " + config.templateId()));

            // Create task with assigned organizer
            EventTask task = new EventTask();
            task.setEventId(eventId);
            task.setTemplateId(template.getId());
            task.setTaskName(template.getName());
            task.setTriggerState(template.getTriggerState());
            task.setDueDate(calculateDueDate(template, event.getDate()));
            task.setActivationLeadDays(template.getActivationLeadDays());
            // AC21: pending until triggered — todo straight away if the event is already past it
            task.setStatus(initialStatus(template.getTriggerState(), event));
            task.setAssignedOrganizerUsername(config.assignedOrganizerUsername()); // Set assignee from config

            EventTask saved = eventTaskRepository.save(task);
            createdTasks.add(saved);
            log.debug("Created task {} from template {} assigned to {}",
                    saved.getId(), template.getId(), config.assignedOrganizerUsername());
        }

        log.info("Created {} tasks for event {}", createdTasks.size(), eventId);
        supersedeNewsletterTasks(event);
        return createdTasks;
    }

    /**
     * Configuration for creating a task from a template with an assignee.
     *
     * @param templateId the template ID
     * @param assignedOrganizerUsername the organizer username to assign (can be null)
     */
    public record TemplateAssignmentConfig(UUID templateId, String assignedOrganizerUsername) {}

    /**
     * Update an existing task (patch semantics — only non-null fields are applied).
     *
     * Allows editing notes, due date, and assigned organizer without changing task status.
     *
     * @param taskId the task ID
     * @param notes updated notes (null = keep existing)
     * @param dueDate updated due date (null = keep existing)
     * @param assignedOrganizerUsername updated assignee (null = keep existing, empty = unassign)
     * @return the updated task
     * @throws jakarta.persistence.EntityNotFoundException if task not found
     */
    @Transactional
    public EventTask updateTask(
            UUID taskId, String notes, java.time.Instant dueDate, String assignedOrganizerUsername) {
        log.info("Updating task {}: notes={}, dueDate={}, assignee={}",
                taskId, notes != null, dueDate, assignedOrganizerUsername);

        EventTask task = eventTaskRepository.findById(taskId)
                .orElseThrow(() -> new EntityNotFoundException("Task not found: " + taskId));

        if (notes != null) {
            task.setNotes(notes.trim().isEmpty() ? null : notes);
        }
        if (dueDate != null) {
            task.setDueDate(dueDate);
        }
        if (assignedOrganizerUsername != null) {
            // Empty string means unassign; non-empty means assign
            task.setAssignedOrganizerUsername(
                    assignedOrganizerUsername.trim().isEmpty() ? null : assignedOrganizerUsername
            );
        }

        EventTask updated = eventTaskRepository.save(task);
        log.info("Task {} updated", taskId);
        return updated;
    }

    /**
     * Delete an event task by ID.
     */
    @Transactional
    public void deleteTask(UUID taskId) {
        EventTask task = eventTaskRepository.findById(taskId)
                .orElseThrow(() -> new EntityNotFoundException("Task not found: " + taskId));
        eventTaskRepository.delete(task);
        log.info("Task {} deleted", taskId);
    }

    /**
     * Create an ad-hoc task (not from template).
     *
     * @param eventId the event ID
     * @param taskName the task name
     * @param triggerState the trigger state
     * @param dueDate the due date
     * @param assignedOrganizerUsername the assigned organizer (optional)
     * @param notes optional task notes
     * @return the created task
     */
    @Transactional
    public EventTask createAdHocTask(
            UUID eventId,
            String taskName,
            String triggerState,
            Instant dueDate,
            String assignedOrganizerUsername,
            String notes
    ) {
        log.info("Creating ad-hoc task for event {}: {}", eventId, taskName);

        // 1. Create task with template_id=null (ad-hoc marker)
        EventTask task = new EventTask();
        task.setEventId(eventId);
        task.setTemplateId(null); // Ad-hoc tasks have no template
        task.setTaskName(taskName);
        task.setTriggerState(triggerState);
        task.setDueDate(dueDate);
        task.setAssignedOrganizerUsername(assignedOrganizerUsername);
        task.setStatus("todo"); // Ad-hoc tasks start as todo
        if (notes != null && !notes.trim().isEmpty()) {
            task.setNotes(notes);
        }

        EventTask saved = eventTaskRepository.save(task);
        log.info("Created ad-hoc task {} for event {}", saved.getId(), eventId);

        return saved;
    }
}
