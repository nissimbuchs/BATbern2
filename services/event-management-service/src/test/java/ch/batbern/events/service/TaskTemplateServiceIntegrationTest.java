/**
 * Integration tests for TaskTemplateService (Story 5.5 Phase 5)
 *
 * Tests cover:
 * - List all task templates (default + custom)
 * - Create custom task template
 * - Update custom task template
 * - Delete custom task template
 * - Prevent modification of default templates
 * - Validation: required fields, trigger state values
 *
 * All tests use PostgreSQL via Testcontainers (not H2) for production parity.
 */
package ch.batbern.events.service;

import ch.batbern.events.AbstractIntegrationTest;
import ch.batbern.events.domain.TaskTemplate;
import ch.batbern.events.repository.TaskTemplateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@Transactional
class TaskTemplateServiceIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private TaskTemplateService taskTemplateService;

    @Autowired
    private TaskTemplateRepository taskTemplateRepository;

    private static final String TEST_USERNAME = "test.organizer";

    @BeforeEach
    void setUp() {
        // Clean up any test data (keep default templates from migration)
        taskTemplateRepository.deleteAll(taskTemplateRepository.findByIsDefault(false));
    }

    /**
     * AC26: List all task templates (default + custom)
     */
    @Test
    void should_listAllTemplates_when_requested() {
        // Given: 7 default templates from V22 migration + 1 custom template
        TaskTemplate customTemplate = createCustomTemplate("Custom Task", "agenda_published");

        // When: List all templates
        List<TaskTemplate> templates = taskTemplateService.listAllTemplates();

        // Then: All templates returned (7 default + 1 custom)
        assertThat(templates).hasSizeGreaterThanOrEqualTo(8);
        assertThat(templates).extracting("name").contains("Custom Task");
        assertThat(templates).extracting("isDefault").contains(true, false);
    }

    /**
     * AC26: List only default templates
     */
    @Test
    void should_listDefaultTemplates_when_requested() {
        // Given: Default templates from migration
        createCustomTemplate("Custom Task", "agenda_published");

        // When: List default templates only
        List<TaskTemplate> defaultTemplates = taskTemplateService.listDefaultTemplates();

        // Then: Only default templates returned
        assertThat(defaultTemplates).hasSize(7);
        assertThat(defaultTemplates).allMatch(TaskTemplate::getIsDefault);
        assertThat(defaultTemplates).extracting("name").containsExactlyInAnyOrder(
            "Venue Booking",
            "Partner Meeting Coordination",
            "Moderator Assignment",
            "Newsletter: Topic Announcement",
            "Newsletter: Speaker Lineup",
            "Newsletter: Final Agenda",
            "Catering Coordination"
        );
    }

    /**
     * AC22: Create custom task template
     */
    @Test
    void should_createCustomTemplate_when_validDataProvided() {
        // Given: Valid template data
        String templateName = "Photography Booking";
        String triggerState = "agenda_finalized";
        String dueDateType = "relative_to_event";
        Integer offsetDays = -21; // 3 weeks before event

        // When: Create custom template
        TaskTemplate created = taskTemplateService.createTemplate(
            templateName,
            triggerState,
            dueDateType,
            offsetDays,
            TEST_USERNAME
        );

        // Then: Template created with correct properties
        assertThat(created.getId()).isNotNull();
        assertThat(created.getName()).isEqualTo(templateName);
        assertThat(created.getTriggerState()).isEqualTo(triggerState);
        assertThat(created.getDueDateType()).isEqualTo(dueDateType);
        assertThat(created.getDueDateOffsetDays()).isEqualTo(offsetDays);
        assertThat(created.getIsDefault()).isFalse();
        assertThat(created.getCreatedByUsername()).isEqualTo(TEST_USERNAME);
        assertThat(created.getCreatedAt()).isNotNull();

        // And: Template persisted to database
        Optional<TaskTemplate> persisted = taskTemplateRepository.findById(created.getId());
        assertThat(persisted).isPresent();
        assertThat(persisted.get().getName()).isEqualTo(templateName);
    }

    /**
     * AC22: Create template with immediate due date
     */
    @Test
    void should_createTemplate_when_dueDateTypeIsImmediate() {
        // Given: Template with immediate due date
        String templateName = "Urgent Task";
        String triggerState = "topic_selection";
        String dueDateType = "immediate";

        // When: Create template
        TaskTemplate created = taskTemplateService.createTemplate(
            templateName,
            triggerState,
            dueDateType,
            null, // No offset for immediate
            TEST_USERNAME
        );

        // Then: Template created successfully
        assertThat(created.getDueDateType()).isEqualTo(dueDateType);
        assertThat(created.getDueDateOffsetDays()).isNull();
    }

    /**
     * AC26: Update custom task template
     */
    @Test
    void should_updateTemplate_when_customTemplateModified() {
        // Given: Existing custom template
        TaskTemplate template = createCustomTemplate("Original Name", "topic_selection");

        // When: Update template properties
        String newName = "Updated Task Name";
        String newTriggerState = "agenda_published";
        Integer newOffsetDays = -14;

        TaskTemplate updated = taskTemplateService.updateTemplate(
            template.getId(),
            newName,
            newTriggerState,
            "relative_to_event",
            newOffsetDays
        );

        // Then: Template updated
        assertThat(updated.getName()).isEqualTo(newName);
        assertThat(updated.getTriggerState()).isEqualTo(newTriggerState);
        assertThat(updated.getDueDateOffsetDays()).isEqualTo(newOffsetDays);
        assertThat(updated.getUpdatedAt()).isAfter(updated.getCreatedAt());

        // And: Changes persisted
        TaskTemplate persisted = taskTemplateRepository.findById(template.getId()).orElseThrow();
        assertThat(persisted.getName()).isEqualTo(newName);
    }

    /**
     * AC26: Prevent updating default templates
     */
    @Test
    void should_throwException_when_attemptingToUpdateDefaultTemplate() {
        // Given: Default template from migration
        TaskTemplate defaultTemplate = taskTemplateRepository.findByIsDefault(true).stream()
            .findFirst()
            .orElseThrow();

        // When/Then: Attempt to update default template throws exception
        assertThatThrownBy(() ->
            taskTemplateService.updateTemplate(
                defaultTemplate.getId(),
                "Modified Name",
                "agenda_published",
                "relative_to_event",
                -30
            )
        ).isInstanceOf(IllegalStateException.class)
         .hasMessageContaining("Cannot modify default template");
    }

    /**
     * AC26: Delete custom task template
     */
    @Test
    void should_deleteTemplate_when_customTemplateDeleted() {
        // Given: Existing custom template
        TaskTemplate template = createCustomTemplate("Task to Delete", "agenda_published");
        UUID templateId = template.getId();

        // When: Delete template
        taskTemplateService.deleteTemplate(templateId);

        // Then: Template deleted from database
        Optional<TaskTemplate> deleted = taskTemplateRepository.findById(templateId);
        assertThat(deleted).isEmpty();
    }

    /**
     * AC26: Prevent deleting default templates
     */
    @Test
    void should_throwException_when_attemptingToDeleteDefaultTemplate() {
        // Given: Default template from migration
        TaskTemplate defaultTemplate = taskTemplateRepository.findByIsDefault(true).stream()
            .findFirst()
            .orElseThrow();

        // When/Then: Attempt to delete default template throws exception
        assertThatThrownBy(() ->
            taskTemplateService.deleteTemplate(defaultTemplate.getId())
        ).isInstanceOf(IllegalStateException.class)
         .hasMessageContaining("Cannot delete default template");
    }

    /**
     * Validation: Required fields
     */
    @Test
    void should_throwException_when_templateNameIsNull() {
        // When/Then: Attempt to create template with null name throws exception
        assertThatThrownBy(() ->
            taskTemplateService.createTemplate(
                null,
                "topic_selection",
                "relative_to_event",
                -90,
                TEST_USERNAME
            )
        ).isInstanceOf(IllegalArgumentException.class)
         .hasMessageContaining("Template name is required");
    }

    /**
     * Validation: Trigger state validation
     */
    @Test
    void should_throwException_when_triggerStateIsInvalid() {
        // When/Then: Attempt to create template with invalid trigger state
        assertThatThrownBy(() ->
            taskTemplateService.createTemplate(
                "Test Task",
                "invalid_state",
                "relative_to_event",
                -90,
                TEST_USERNAME
            )
        ).isInstanceOf(IllegalArgumentException.class)
         .hasMessageContaining("Invalid trigger state");
    }

    /**
     * Validation: Due date type validation
     */
    @Test
    void should_throwException_when_dueDateTypeIsInvalid() {
        // When/Then: Attempt to create template with invalid due date type
        assertThatThrownBy(() ->
            taskTemplateService.createTemplate(
                "Test Task",
                "topic_selection",
                "invalid_type",
                -90,
                TEST_USERNAME
            )
        ).isInstanceOf(IllegalArgumentException.class)
         .hasMessageContaining("Invalid due date type");
    }

    /**
     * Validation: Offset days required for relative_to_event
     */
    @Test
    void should_throwException_when_offsetDaysMissingForRelativeType() {
        // When/Then: Attempt to create relative template without offset
        assertThatThrownBy(() ->
            taskTemplateService.createTemplate(
                "Test Task",
                "topic_selection",
                "relative_to_event",
                null, // Missing offset
                TEST_USERNAME
            )
        ).isInstanceOf(IllegalArgumentException.class)
         .hasMessageContaining("Offset days required for relative_to_event type");
    }

    // === Helper Methods ===

    private TaskTemplate createCustomTemplate(String name, String triggerState) {
        TaskTemplate template = new TaskTemplate();
        template.setName(name);
        template.setTriggerState(triggerState);
        template.setDueDateType("relative_to_event");
        template.setDueDateOffsetDays(-30);
        template.setIsDefault(false);
        template.setCreatedByUsername(TEST_USERNAME);
        return taskTemplateRepository.save(template);
    }
}
