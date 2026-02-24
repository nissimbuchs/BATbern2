package ch.batbern.events.controller;

import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.EventTask;
import ch.batbern.events.dto.CompleteTaskRequest;
import ch.batbern.events.dto.CreateEventTaskRequest;
import ch.batbern.events.dto.CreateTasksFromTemplatesRequest;
import ch.batbern.events.dto.EventTaskResponse;
import ch.batbern.events.dto.ReassignTaskRequest;
import ch.batbern.events.dto.UpdateEventTaskRequest;
import ch.batbern.events.dto.UpdateTaskStatusRequest;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.security.SecurityContextHelper;
import ch.batbern.events.service.EventTaskService;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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
 * - PUT  /api/v1/tasks/{taskId}/complete                 - Mark task complete
 * - PUT  /api/v1/tasks/{taskId}/reassign                 - Reassign task to different organizer
 * - PUT  /api/v1/tasks/{taskId}/status                   - Update task status (drag-and-drop)
 *
 * Security: All endpoints require ORGANIZER role
 */
@RestController
@RequiredArgsConstructor
@Slf4j
public class EventTaskController {

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
    @GetMapping("/api/v1/events/{eventCode}/tasks")
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<List<EventTaskResponse>> listEventTasks(@PathVariable String eventCode) {
        log.info("GET /api/v1/events/{}/tasks", eventCode);

        // Find event by code
        Event event = eventRepository.findByEventCode(eventCode)
                .orElseThrow(() -> new EntityNotFoundException("Event not found: " + eventCode));

        List<EventTask> tasks = eventTaskService.getEventTasks(event.getId());
        List<EventTaskResponse> response = tasks.stream()
                .map(task -> EventTaskResponse.fromEntity(task, event.getEventCode()))
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
    @PostMapping("/api/v1/events/{eventCode}/tasks")
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<EventTaskResponse> createAdHocTask(
            @PathVariable String eventCode,
            @Valid @RequestBody CreateEventTaskRequest request) {

        log.info("POST /api/v1/events/{}/tasks - taskName: {}", eventCode, request.getTaskName());

        // Find event by code
        Event event = eventRepository.findByEventCode(eventCode)
                .orElseThrow(() -> new EntityNotFoundException("Event not found: " + eventCode));

        EventTask task = eventTaskService.createAdHocTask(
                event.getId(),
                request.getTaskName(),
                request.getTriggerState(),
                request.getDueDate(),
                request.getAssignedOrganizerUsername(),
                request.getNotes()
        );

        EventTaskResponse response = EventTaskResponse.fromEntity(task, event.getEventCode());
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
    @PostMapping("/api/v1/events/{eventCode}/tasks/from-templates")
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<List<EventTaskResponse>> createTasksFromTemplates(
            @PathVariable String eventCode,
            @Valid @RequestBody CreateTasksFromTemplatesRequest request) {

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
                .map(task -> EventTaskResponse.fromEntity(task, evtCode))
                .collect(Collectors.toList());

        log.info("Created {} tasks from templates for event {}", response.size(), eventCode);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Get tasks assigned to current organizer.
     * Story 5.5 AC24: List tasks for current organizer
     *
     * @param critical if true, return only critical tasks (overdue + due soon)
     * @return list of tasks
     */
    @GetMapping("/api/v1/tasks/my-tasks")
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<List<EventTaskResponse>> getMyTasks(
            @RequestParam(required = false, defaultValue = "false") boolean critical) {

        String username = securityContextHelper.getCurrentUsername();
        log.info("GET /api/v1/tasks/my-tasks - username: {}, critical: {}", username, critical);

        List<EventTask> tasks = critical
                ? eventTaskService.getCriticalTasksForOrganizer(username)
                : eventTaskService.getTasksForOrganizer(username);

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
                .map(task -> EventTaskResponse.fromEntity(task, eventCodeMap.get(task.getEventId())))
                .collect(Collectors.toList());

        return ResponseEntity.ok(response);
    }

    /**
     * Get all tasks (for all organizers).
     * Story 5.5: Task dashboard with "All Tasks" filter
     *
     * @param critical if true, return only critical tasks (overdue + due soon)
     * @return list of all tasks
     */
    @GetMapping("/api/v1/tasks/all-tasks")
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<List<EventTaskResponse>> getAllTasks(
            @RequestParam(required = false, defaultValue = "false") boolean critical) {

        log.info("GET /api/v1/tasks/all-tasks - critical: {}", critical);

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
                .map(task -> EventTaskResponse.fromEntity(task, eventCodeMap.get(task.getEventId())))
                .collect(Collectors.toList());

        return ResponseEntity.ok(response);
    }

    /**
     * Mark a task as complete.
     * Story 5.5 AC25: Task completion tracking
     *
     * @param taskId task ID
     * @param request completion request with optional notes
     * @return updated task
     */
    @PutMapping("/api/v1/tasks/{taskId}/complete")
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<EventTaskResponse> completeTask(
            @PathVariable UUID taskId,
            @Valid @RequestBody CompleteTaskRequest request) {

        String completedByUsername = securityContextHelper.getCurrentUsername();
        log.info("PUT /api/v1/tasks/{}/complete - completedBy: {}", taskId, completedByUsername);

        EventTask task = eventTaskService.completeTask(taskId, completedByUsername, request.getNotes());
        String eventCode = eventRepository.findById(task.getEventId())
                .map(Event::getEventCode)
                .orElse(null);
        EventTaskResponse response = EventTaskResponse.fromEntity(task, eventCode);

        return ResponseEntity.ok(response);
    }

    /**
     * Reassign a task to a different organizer.
     * Story 5.5 AC27: Task assignment flexibility
     *
     * @param taskId task ID
     * @param request reassignment request
     * @return updated task
     */
    @PutMapping("/api/v1/tasks/{taskId}/reassign")
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<EventTaskResponse> reassignTask(
            @PathVariable UUID taskId,
            @Valid @RequestBody ReassignTaskRequest request) {

        log.info("PUT /api/v1/tasks/{}/reassign - newOrganizer: {}",
                taskId, request.getNewOrganizerUsername());

        EventTask task = eventTaskService.reassignTask(taskId, request.getNewOrganizerUsername());
        String eventCode = eventRepository.findById(task.getEventId())
                .map(Event::getEventCode)
                .orElse(null);
        EventTaskResponse response = EventTaskResponse.fromEntity(task, eventCode);

        return ResponseEntity.ok(response);
    }

    /**
     * Update task status (for drag-and-drop).
     * Story 5.5: Manual status transitions via drag-and-drop
     *
     * Allows transitions: pending ↔ todo ↔ completed
     *
     * @param taskId task ID
     * @param request status update request
     * @return updated task
     */
    @PutMapping("/api/v1/tasks/{taskId}/status")
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<EventTaskResponse> updateTaskStatus(
            @PathVariable UUID taskId,
            @Valid @RequestBody UpdateTaskStatusRequest request) {

        log.info("PUT /api/v1/tasks/{}/status - newStatus: {}", taskId, request.getStatus());

        EventTask task = eventTaskService.updateTaskStatus(taskId, request.getStatus());
        String eventCode = eventRepository.findById(task.getEventId())
                .map(Event::getEventCode)
                .orElse(null);
        EventTaskResponse response = EventTaskResponse.fromEntity(task, eventCode);

        return ResponseEntity.ok(response);
    }

    /**
     * Update task details (notes, due date, assigned organizer).
     * Patch semantics: only provided non-null fields are updated.
     *
     * @param taskId task ID
     * @param request update request (all fields optional)
     * @return updated task
     */
    @PatchMapping("/api/v1/tasks/{taskId}")
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<EventTaskResponse> updateTask(
            @PathVariable UUID taskId,
            @RequestBody UpdateEventTaskRequest request) {

        log.info("PATCH /api/v1/tasks/{}", taskId);

        EventTask task = eventTaskService.updateTask(
                taskId,
                request.getNotes(),
                request.getDueDate(),
                request.getAssignedOrganizerUsername()
        );
        String eventCode = eventRepository.findById(task.getEventId())
                .map(Event::getEventCode)
                .orElse(null);
        EventTaskResponse response = EventTaskResponse.fromEntity(task, eventCode);

        return ResponseEntity.ok(response);
    }

    // === Helper Methods ===
}
