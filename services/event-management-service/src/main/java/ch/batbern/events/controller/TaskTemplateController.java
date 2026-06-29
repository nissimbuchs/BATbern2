package ch.batbern.events.controller;

import ch.batbern.events.domain.TaskTemplate;
import ch.batbern.events.security.SecurityContextHelper;
import ch.batbern.events.service.TaskTemplateService;
import ch.batbern.events.tasks.api.generated.TaskTemplatesApi;
import ch.batbern.events.tasks.dto.generated.CreateTaskTemplateRequest;
import ch.batbern.events.tasks.dto.generated.TaskTemplateResponse;
import ch.batbern.events.tasks.dto.generated.UpdateTaskTemplateRequest;
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
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Slf4j
public class TaskTemplateController implements TaskTemplatesApi {

    private final TaskTemplateService taskTemplateService;
    private final SecurityContextHelper securityContextHelper;

    /**
     * List all task templates (default + custom).
     * Story 5.5 AC26: Task templates library
     */
    @Override
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<List<TaskTemplateResponse>> listAllTemplates() {
        log.info("GET /api/v1/tasks/templates");

        List<TaskTemplateResponse> response = taskTemplateService.listAllTemplates().stream()
                .map(TaskTemplateController::toResponse)
                .collect(Collectors.toList());

        return ResponseEntity.ok(response);
    }

    /**
     * Create a new custom task template.
     * Story 5.5 AC22: Custom task creation with "Save as template" option
     */
    @Override
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<TaskTemplateResponse> createTemplate(CreateTaskTemplateRequest request) {
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

        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(template));
    }

    /**
     * Update an existing custom task template.
     * Story 5.5 AC26: Custom templates can be edited (default templates cannot)
     */
    @Override
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<TaskTemplateResponse> updateTemplate(
            UUID templateId,
            UpdateTaskTemplateRequest request) {

        log.info("PUT /api/v1/tasks/templates/{} - name: {}", templateId, request.getName());

        try {
            TaskTemplate template = taskTemplateService.updateTemplate(
                    templateId,
                    request.getName(),
                    request.getTriggerState(),
                    request.getDueDateType(),
                    request.getDueDateOffsetDays()
            );

            return ResponseEntity.ok(toResponse(template));

        } catch (IllegalStateException e) {
            // Cannot modify default template (AC26)
            log.warn("Attempt to update default template {}: {}", templateId, e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
    }

    /**
     * Delete a custom task template.
     * Story 5.5 AC26: Custom templates can be deleted (default templates cannot)
     */
    @Override
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<Void> deleteTemplate(UUID templateId) {
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
     * Map a TaskTemplate entity to the generated response DTO.
     * Instant timestamps are surfaced as UTC OffsetDateTime (wire stays {@code …Z}).
     */
    private static TaskTemplateResponse toResponse(TaskTemplate template) {
        return TaskTemplateResponse.builder()
                .id(template.getId())
                .name(template.getName())
                .triggerState(template.getTriggerState())
                .dueDateType(template.getDueDateType())
                .dueDateOffsetDays(template.getDueDateOffsetDays())
                .isDefault(template.getIsDefault())
                .createdByUsername(template.getCreatedByUsername())
                .createdAt(toOffset(template.getCreatedAt()))
                .updatedAt(toOffset(template.getUpdatedAt()))
                .build();
    }

    private static OffsetDateTime toOffset(Instant instant) {
        return instant != null ? instant.atOffset(ZoneOffset.UTC) : null;
    }

    /**
     * Current authenticated CANONICAL username (ADR-003), or {@code "system"} when
     * unauthenticated. Uses {@link SecurityContextHelper#getCurrentUsernameOrNull()}
     * (custom:username claim + Pattern 3b twin DB fallback) instead of
     * {@code authentication.getName()}, which returns the Cognito {@code sub} (a UUID).
     */
    private String getCurrentUsername() {
        String username = securityContextHelper.getCurrentUsernameOrNull();
        return username != null ? username : "system";
    }
}
