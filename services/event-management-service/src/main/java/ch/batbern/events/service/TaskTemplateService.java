package ch.batbern.events.service;

import ch.batbern.events.domain.TaskTemplate;
import ch.batbern.events.repository.TaskTemplateRepository;
import ch.batbern.shared.types.EventWorkflowState;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service for task template management (Story 5.5 AC19-27).
 *
 * Handles:
 * - CRUD operations for task templates
 * - Default template management (read-only, system-provided)
 * - Custom template management (created by organizers)
 * - Template library for event planning
 *
 * Default Templates (AC19):
 * 1. Venue Booking (trigger: topic_selection, due: -90 days)
 * 2. Partner Meeting (trigger: topic_selection, due: 0 days)
 * 3. Moderator Assignment (trigger: topic_selection, due: -14 days)
 * 4. Newsletter: Topic (trigger: topic_selection, due: immediate)
 * 5. Newsletter: Speakers (trigger: agenda_published, due: -30 days)
 * 6. Newsletter: Final (trigger: agenda_published, due: -14 days)
 * 7. Catering (trigger: agenda_published, due: -30 days)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TaskTemplateService {

    private final TaskTemplateRepository taskTemplateRepository;

    // Valid event workflow states for trigger (lowercase_snake_case per coding-standards.md)
    private static final Set<String> VALID_TRIGGER_STATES = Arrays.stream(EventWorkflowState.values())
            .map(state -> state.name().toLowerCase())
            .collect(Collectors.toSet());

    // Valid due date types
    private static final Set<String> VALID_DUE_DATE_TYPES = Set.of(
            "immediate",
            "relative_to_event",
            "absolute"
    );

    /**
     * Get all task templates (default + custom).
     *
     * @return list of all templates (AC26)
     */
    public List<TaskTemplate> listAllTemplates() {
        log.debug("Fetching all task templates");
        return taskTemplateRepository.findAll();
    }

    /**
     * Get all default (system-provided) task templates.
     *
     * These templates are read-only and cannot be edited or deleted.
     *
     * @return list of default templates (AC26)
     */
    public List<TaskTemplate> listDefaultTemplates() {
        log.debug("Fetching default task templates");
        return taskTemplateRepository.findByIsDefaultTrue();
    }

    /**
     * Get all custom (organizer-created) task templates.
     *
     * @return list of custom templates (AC26)
     */
    public List<TaskTemplate> getCustomTemplates() {
        log.debug("Fetching custom task templates");
        return taskTemplateRepository.findByIsDefaultFalse();
    }

    /**
     * Create a new custom task template.
     *
     * Custom templates can be saved for reuse across multiple events (AC22).
     *
     * @param name the template name
     * @param triggerState the workflow state that triggers task creation
     * @param dueDateType the due date calculation type ('immediate', 'relative_to_event', 'absolute')
     * @param dueDateOffsetDays the offset in days for relative due dates (negative = before, positive = after)
     * @param createdByUsername the organizer creating the template
     * @return the created template
     * @throws IllegalArgumentException if template name is missing or invalid
     */
    @Transactional
    public TaskTemplate createTemplate(
            String name,
            String triggerState,
            String dueDateType,
            Integer dueDateOffsetDays,
            String createdByUsername
    ) {
        log.info("Creating custom task template: {} by user: {}", name, createdByUsername);

        // Validation
        validateTemplateName(name);
        validateTriggerState(triggerState);
        validateDueDateType(dueDateType);
        validateOffsetDays(dueDateType, dueDateOffsetDays);

        // Create template
        TaskTemplate template = new TaskTemplate();
        template.setName(name);
        template.setTriggerState(triggerState);
        template.setDueDateType(dueDateType);
        template.setDueDateOffsetDays(dueDateOffsetDays);
        template.setIsDefault(false);
        template.setCreatedByUsername(createdByUsername);

        TaskTemplate saved = taskTemplateRepository.save(template);
        log.info("Created custom task template with ID: {}", saved.getId());

        return saved;
    }

    /**
     * Update an existing custom task template.
     *
     * Default templates cannot be updated (AC26).
     *
     * @param templateId the template ID
     * @param name the updated template name
     * @param triggerState the updated trigger state
     * @param dueDateType the updated due date type
     * @param dueDateOffsetDays the updated offset days
     * @return the updated template
     * @throws IllegalArgumentException if template is a default template
     * @throws jakarta.persistence.EntityNotFoundException if template not found
     */
    @Transactional
    public TaskTemplate updateTemplate(
            UUID templateId,
            String name,
            String triggerState,
            String dueDateType,
            Integer dueDateOffsetDays
    ) {
        log.info("Updating custom task template: {}", templateId);

        // Find template
        TaskTemplate template = taskTemplateRepository.findById(templateId)
                .orElseThrow(() -> new EntityNotFoundException("Template not found: " + templateId));

        // Prevent modification of default templates
        if (template.getIsDefault()) {
            throw new IllegalStateException("Cannot modify default template: " + templateId);
        }

        // Validation
        validateTemplateName(name);
        validateTriggerState(triggerState);
        validateDueDateType(dueDateType);
        validateOffsetDays(dueDateType, dueDateOffsetDays);

        // Update fields
        template.setName(name);
        template.setTriggerState(triggerState);
        template.setDueDateType(dueDateType);
        template.setDueDateOffsetDays(dueDateOffsetDays);

        TaskTemplate updated = taskTemplateRepository.save(template);
        log.info("Updated custom task template: {}", templateId);

        return updated;
    }

    /**
     * Delete a custom task template.
     *
     * Default templates cannot be deleted (AC26).
     *
     * @param templateId the template ID
     * @throws IllegalArgumentException if template is a default template
     * @throws jakarta.persistence.EntityNotFoundException if template not found
     */
    @Transactional
    public void deleteTemplate(UUID templateId) {
        log.info("Deleting custom task template: {}", templateId);

        // Find template
        TaskTemplate template = taskTemplateRepository.findById(templateId)
                .orElseThrow(() -> new EntityNotFoundException("Template not found: " + templateId));

        // Prevent deletion of default templates
        if (template.getIsDefault()) {
            throw new IllegalStateException("Cannot delete default template: " + templateId);
        }

        taskTemplateRepository.delete(template);
        log.info("Deleted custom task template: {}", templateId);
    }

    /**
     * Get task template by ID.
     *
     * @param templateId the template ID
     * @return the template
     * @throws jakarta.persistence.EntityNotFoundException if template not found
     */
    public TaskTemplate getTemplateById(UUID templateId) {
        log.debug("Fetching task template: {}", templateId);
        return taskTemplateRepository.findById(templateId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Template not found: " + templateId));
    }

    // === Validation Helper Methods ===

    private void validateTemplateName(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Template name is required");
        }
    }

    private void validateTriggerState(String triggerState) {
        if (triggerState == null || !VALID_TRIGGER_STATES.contains(triggerState)) {
            throw new IllegalArgumentException(
                    "Invalid trigger state: " + triggerState + ". Must be one of: " + VALID_TRIGGER_STATES
            );
        }
    }

    private void validateDueDateType(String dueDateType) {
        if (dueDateType == null || !VALID_DUE_DATE_TYPES.contains(dueDateType)) {
            throw new IllegalArgumentException(
                    "Invalid due date type: " + dueDateType + ". Must be one of: " + VALID_DUE_DATE_TYPES
            );
        }
    }

    private void validateOffsetDays(String dueDateType, Integer offsetDays) {
        if ("relative_to_event".equals(dueDateType) && offsetDays == null) {
            throw new IllegalArgumentException("Offset days required for relative_to_event type");
        }
    }
}
