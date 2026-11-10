package ch.batbern.events.controller;

import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.EventTask;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.security.SecurityContextHelper;
import ch.batbern.events.service.EventTaskService;
import ch.batbern.events.tasks.api.generated.EventTasksApi;
import ch.batbern.events.tasks.dto.generated.CreateEventTaskRequest;
import ch.batbern.events.tasks.dto.generated.CreateTasksFromTemplatesRequest;
import ch.batbern.events.tasks.dto.generated.EventTaskResponse;
import ch.batbern.events.tasks.dto.generated.UpdateEventTaskRequest;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * REST Controller for Event Task Management
 * Story 5.5: Configurable Task System (AC21-27)
 *
 * Endpoints:
 * - GET  /api/v1/events/{eventCode}/tasks                - List tasks for event
 * - POST /api/v1/events/{eventCode}/tasks                - Create ad-hoc task
 * - POST /api/v1/events/{eventCode}/tasks/from-templates - Create tasks from templates
 * - GET  /api/v1/tasks/my-tasks                          - Get tasks assigned to current organizer
 * - PATCH /api/v1/tasks/{taskId}                        - Complete, reopen, move, reassign, edit
 * - DELETE /api/v1/tasks/{taskId}                       - Delete task
 *
 * Security: All endpoints require ORGANIZER role
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Slf4j
public class EventTaskController implements EventTasksApi {

    private final EventTaskService eventTaskService;
    private final EventRepository eventRepository;
    private final SecurityContextHelper securityContextHelper;

    /**
     * List all tasks for an event.
     * Story 5.5 AC24: Task dashboard showing all event tasks
     *
     * @param eventCode event code
     * @return list of tasks
     */
    @Override
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<List<EventTaskResponse>> listEventTasks(String eventCode) {
        log.info("GET /api/v1/events/{}/tasks", eventCode);

        // Find event by code
        Event event = eventRepository.findByEventCode(eventCode)
                .orElseThrow(() -> new EntityNotFoundException("Event not found: " + eventCode));

        List<EventTask> tasks = eventTaskService.getEventTasks(event.getId());
        List<EventTaskResponse> response = tasks.stream()
                .map(task -> toResponse(task, event.getEventCode()))
                .collect(Collectors.toList());

        return ResponseEntity.ok(response);
    }

    /**
     * Create an ad-hoc task for an event.
     * Story 5.5 AC22: Custom task creation
     *
     * @param eventCode event code
     * @param request create task request
     * @return created task
     */
    @Override
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<EventTaskResponse> createAdHocTask(
            String eventCode,
            CreateEventTaskRequest request) {

        log.info("POST /api/v1/events/{}/tasks - taskName: {}", eventCode, request.getTaskName());

        // Find event by code
        Event event = eventRepository.findByEventCode(eventCode)
                .orElseThrow(() -> new EntityNotFoundException("Event not found: " + eventCode));

        EventTask task = eventTaskService.createAdHocTask(
                event.getId(),
                request.getTaskName(),
                request.getTriggerState(),
                toInstant(request.getDueDate()),
                request.getAssignedOrganizerUsername(),
                request.getNotes()
        );

        EventTaskResponse response = toResponse(task, event.getEventCode());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Create tasks from templates for an event.
     * Story 5.5 AC21: Task template instantiation with assignees
     *
     * @param eventCode event code
     * @param request request containing template IDs and assignees
     * @return list of created tasks
     */
    @Override
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<List<EventTaskResponse>> createTasksFromTemplates(
            String eventCode,
            CreateTasksFromTemplatesRequest request) {

        log.info("POST /api/v1/events/{}/tasks/from-templates - {} templates",
                eventCode, request.getTemplates().size());

        // Find event by code
        Event event = eventRepository.findByEventCode(eventCode)
                .orElseThrow(() -> new EntityNotFoundException("Event not found: " + eventCode));

        // Convert DTO configs to service records
        List<EventTaskService.TemplateAssignmentConfig> configs = request.getTemplates().stream()
                .map(t -> new EventTaskService.TemplateAssignmentConfig(
                        t.getTemplateId(),
                        t.getAssignedOrganizerUsername()
                ))
                .collect(Collectors.toList());

        // Create tasks
        List<EventTask> tasks = eventTaskService.createTasksFromTemplatesWithAssignees(
                event.getId(),
                configs
        );

        String evtCode = event.getEventCode();
        List<EventTaskResponse> response = tasks.stream()
                .map(task -> toResponse(task, evtCode))
                .collect(Collectors.toList());

        log.info("Created {} tasks from templates for event {}", response.size(), eventCode);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Get tasks assigned to current organizer.
     * Story 5.5 AC24: List tasks for current organizer
     *
     * @return list of tasks
     */
    @Override
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<List<EventTaskResponse>> getMyTasks() {

        String username = securityContextHelper.getCurrentUsername();
        log.info("GET /api/v1/tasks/my-tasks - username: {}", username);

        List<EventTask> tasks = eventTaskService.getTasksForOrganizer(username);

        // Fetch event codes for all tasks
        java.util.Map<UUID, String> eventCodeMap = tasks.stream()
                .map(EventTask::getEventId)
                .distinct()
                .collect(Collectors.toMap(
                        eventId -> eventId,
                        eventId -> eventRepository.findById(eventId)
                                .map(ch.batbern.events.domain.Event::getEventCode)
                                .orElse(null)
                ));

        List<EventTaskResponse> response = tasks.stream()
                .map(task -> toResponse(task, eventCodeMap.get(task.getEventId())))
                .collect(Collectors.toList());

        return ResponseEntity.ok(response);
    }

    /**
     * Get all tasks (for all organizers).
     * Story 5.5: Task dashboard with "All Tasks" filter
     *
     * @return list of all tasks
     */
    @Override
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<List<EventTaskResponse>> getAllTasks() {

        log.info("GET /api/v1/tasks/all-tasks");

        // Get all tasks from the service
        List<EventTask> tasks = eventTaskService.getAllTasks();

        // Fetch event codes for all tasks
        java.util.Map<UUID, String> eventCodeMap = tasks.stream()
                .map(EventTask::getEventId)
                .distinct()
                .collect(Collectors.toMap(
                        eventId -> eventId,
                        eventId -> eventRepository.findById(eventId)
                                .map(Event::getEventCode)
                                .orElse(null)
                ));

        List<EventTaskResponse> response = tasks.stream()
                .map(task -> toResponse(task, eventCodeMap.get(task.getEventId())))
                .collect(Collectors.toList());

        return ResponseEntity.ok(response);
    }

    /**
     * Update a task: status (complete, reopen, drag-and-drop), notes, due date, assignee.
     * Patch semantics: only provided fields are updated. Completing records the caller.
     *
     * @param taskId task ID
     * @param request update request (all fields optional)
     * @return updated task
     */
    @Override
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<EventTaskResponse> updateTask(
            UUID taskId,
            UpdateEventTaskRequest request) {

        log.info("PATCH /api/v1/tasks/{} - status: {}", taskId, request.getStatus());

        EventTask task = eventTaskService.updateTask(
                taskId,
                new EventTaskService.TaskChanges(
                        request.getStatus() == null ? null : request.getStatus().getValue(),
                        request.getNotes(),
                        toInstant(request.getDueDate()),
                        request.getAssignedOrganizerUsername()),
                securityContextHelper.getCurrentUsername()
        );
        String eventCode = eventRepository.findById(task.getEventId())
                .map(Event::getEventCode)
                .orElse(null);
        EventTaskResponse response = toResponse(task, eventCode);

        return ResponseEntity.ok(response);
    }

    @Override
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<Void> deleteTask(UUID taskId) {
        log.info("DELETE /api/v1/tasks/{}", taskId);
        eventTaskService.deleteTask(taskId);
        return ResponseEntity.noContent().build();
    }

    // === Helper Methods ===

    /**
     * Map an EventTask entity to the generated response DTO.
     * Instant timestamps are surfaced as UTC OffsetDateTime (wire stays {@code …Z}).
     *
     * @param task      the entity
     * @param eventCode resolved event code (may be null for tasks without an event)
     */
    private static EventTaskResponse toResponse(EventTask task, String eventCode) {
        return EventTaskResponse.builder()
                .id(task.getId())
                .eventId(task.getEventId())
                .eventCode(eventCode)
                .templateId(task.getTemplateId())
                .taskName(task.getTaskName())
                .triggerState(task.getTriggerState())
                .dueDate(toOffset(task.getDueDate()))
                .assignedOrganizerUsername(task.getAssignedOrganizerUsername())
                .status(task.getStatus())
                .notes(task.getNotes())
                .completedDate(toOffset(task.getCompletedDate()))
                .completedByUsername(task.getCompletedByUsername())
                .createdAt(toOffset(task.getCreatedAt()))
                .updatedAt(toOffset(task.getUpdatedAt()))
                .build();
    }

    private static OffsetDateTime toOffset(Instant instant) {
        return instant != null ? instant.atOffset(ZoneOffset.UTC) : null;
    }

    private static Instant toInstant(OffsetDateTime offsetDateTime) {
        return offsetDateTime != null ? offsetDateTime.toInstant() : null;
    }
}
