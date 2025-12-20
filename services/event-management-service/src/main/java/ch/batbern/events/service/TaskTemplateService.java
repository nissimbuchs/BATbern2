package ch.batbern.events.service;

import ch.batbern.events.domain.TaskTemplate;
import ch.batbern.events.repository.TaskTemplateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

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
 * 6. Newsletter: Final (trigger: agenda_finalized, due: -14 days)
 * 7. Catering (trigger: agenda_finalized, due: -30 days)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TaskTemplateService {

    private final TaskTemplateRepository taskTemplateRepository;

    /**
     * Get all task templates (default + custom).
     *
     * @return list of all templates (AC26)
     */
    public List<TaskTemplate> getAllTemplates() {
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
    public List<TaskTemplate> getDefaultTemplates() {
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

        // TODO: Implement template creation (Phase 5)
        // 1. Validate name is provided
        // 2. Validate trigger state is valid event workflow state
        // 3. Validate due date type is 'immediate', 'relative_to_event', or 'absolute'
        // 4. Create template with is_default=false
        // 5. Save and return

        throw new UnsupportedOperationException("Template creation not yet implemented");
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

        // TODO: Implement template update (Phase 5)
        // 1. Find template by ID
        // 2. Check if is_default=true → throw exception (read-only)
        // 3. Update fields
        // 4. Save and return

        throw new UnsupportedOperationException("Template update not yet implemented");
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

        // TODO: Implement template deletion (Phase 5)
        // 1. Find template by ID
        // 2. Check if is_default=true → throw exception (read-only)
        // 3. Delete template

        throw new UnsupportedOperationException("Template deletion not yet implemented");
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
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException(
                        "Template not found: " + templateId));
    }
}
