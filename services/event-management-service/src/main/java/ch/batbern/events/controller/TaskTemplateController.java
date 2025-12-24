package ch.batbern.events.controller;

import ch.batbern.events.domain.TaskTemplate;
import ch.batbern.events.dto.CreateTaskTemplateRequest;
import ch.batbern.events.dto.TaskTemplateResponse;
import ch.batbern.events.dto.UpdateTaskTemplateRequest;
import ch.batbern.events.service.TaskTemplateService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * REST Controller for Task Template Management
 * Story 5.5: Configurable Task System (AC19, AC22, AC26)
 *
 * Endpoints:
 * - GET    /api/v1/tasks/templates          - List all task templates
 * - POST   /api/v1/tasks/templates          - Create custom template
 * - PUT    /api/v1/tasks/templates/{id}     - Update custom template
 * - DELETE /api/v1/tasks/templates/{id}     - Delete custom template
 *
 * Security: All endpoints require ORGANIZER role
 */
@RestController
@RequestMapping("/api/v1/tasks/templates")
@RequiredArgsConstructor
@Slf4j
public class TaskTemplateController {

    private final TaskTemplateService taskTemplateService;

    /**
     * List all task templates (default + custom).
     * Story 5.5 AC26: Task templates library
     *
     * @return list of all templates
     */
    @GetMapping
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<List<TaskTemplateResponse>> listAllTemplates() {
        log.info("GET /api/v1/tasks/templates");

        List<TaskTemplate> templates = taskTemplateService.listAllTemplates();
        List<TaskTemplateResponse> response = templates.stream()
                .map(TaskTemplateResponse::fromEntity)
                .collect(Collectors.toList());

        return ResponseEntity.ok(response);
    }

    /**
     * Create a new custom task template.
     * Story 5.5 AC22: Custom task creation with "Save as template" option
     *
     * @param request create template request
     * @return created template
     */
    @PostMapping
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<TaskTemplateResponse> createTemplate(
            @Valid @RequestBody CreateTaskTemplateRequest request) {

        log.info("POST /api/v1/tasks/templates - name: {}", request.getName());

        // Extract organizer username from security context
        String createdByUsername = getCurrentUsername();

        TaskTemplate template = taskTemplateService.createTemplate(
                request.getName(),
                request.getTriggerState(),
                request.getDueDateType(),
                request.getDueDateOffsetDays(),
                createdByUsername
        );

        TaskTemplateResponse response = TaskTemplateResponse.fromEntity(template);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Update an existing custom task template.
     * Story 5.5 AC26: Custom templates can be edited (default templates cannot)
     *
     * @param templateId the template ID
     * @param request update template request
     * @return updated template
     */
    @PutMapping("/{templateId}")
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<TaskTemplateResponse> updateTemplate(
            @PathVariable UUID templateId,
            @Valid @RequestBody UpdateTaskTemplateRequest request) {

        log.info("PUT /api/v1/tasks/templates/{} - name: {}", templateId, request.getName());

        try {
            TaskTemplate template = taskTemplateService.updateTemplate(
                    templateId,
                    request.getName(),
                    request.getTriggerState(),
                    request.getDueDateType(),
                    request.getDueDateOffsetDays()
            );

            TaskTemplateResponse response = TaskTemplateResponse.fromEntity(template);
            return ResponseEntity.ok(response);

        } catch (IllegalStateException e) {
            // Cannot modify default template (AC26)
            log.warn("Attempt to update default template {}: {}", templateId, e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
    }

    /**
     * Delete a custom task template.
     * Story 5.5 AC26: Custom templates can be deleted (default templates cannot)
     *
     * @param templateId the template ID
     * @return no content on success
     */
    @DeleteMapping("/{templateId}")
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<Void> deleteTemplate(@PathVariable UUID templateId) {
        log.info("DELETE /api/v1/tasks/templates/{}", templateId);

        try {
            taskTemplateService.deleteTemplate(templateId);
            return ResponseEntity.noContent().build();

        } catch (IllegalStateException e) {
            // Cannot delete default template (AC26)
            log.warn("Attempt to delete default template {}: {}", templateId, e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
    }

    // === Helper Methods ===

    /**
     * Get current authenticated username from security context.
     */
    private String getCurrentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null ? authentication.getName() : "system";
    }
}
