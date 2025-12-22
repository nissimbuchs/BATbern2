package ch.batbern.events.controller;

import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.EventTask;
import ch.batbern.events.dto.CompleteTaskRequest;
import ch.batbern.events.dto.CreateEventTaskRequest;
import ch.batbern.events.dto.CreateTasksFromTemplatesRequest;
import ch.batbern.events.dto.EventTaskResponse;
import ch.batbern.events.dto.ReassignTaskRequest;
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
                .map(EventTaskResponse::fromEntity)
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

        EventTaskResponse response = EventTaskResponse.fromEntity(task);
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

        List<EventTaskResponse> response = tasks.stream()
                .map(EventTaskResponse::fromEntity)
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

        List<EventTaskResponse> response = tasks.stream()
                .map(EventTaskResponse::fromEntity)
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
        EventTaskResponse response = EventTaskResponse.fromEntity(task);

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
        EventTaskResponse response = EventTaskResponse.fromEntity(task);

        return ResponseEntity.ok(response);
    }

    // === Helper Methods ===
}
