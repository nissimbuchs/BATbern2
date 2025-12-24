package ch.batbern.events.controller;

import ch.batbern.events.AbstractIntegrationTest;
import ch.batbern.events.domain.TaskTemplate;
import ch.batbern.events.repository.TaskTemplateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for TaskTemplateController (Story 5.5 Phase 5).
 *
 * Tests REST API endpoints for task template management (AC19, AC22, AC26).
 *
 * Endpoints tested:
 * - GET  /api/v1/tasks/templates - List all templates
 * - POST /api/v1/tasks/templates - Create custom template
 * - PUT  /api/v1/tasks/templates/{id} - Update custom template
 * - DELETE /api/v1/tasks/templates/{id} - Delete custom template
 */
@Transactional
class TaskTemplateControllerIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TaskTemplateRepository taskTemplateRepository;

    @BeforeEach
    void setUp() {
        // Delete only custom templates (preserve default templates from V22 migration)
        taskTemplateRepository.findByIsDefaultFalse().forEach(taskTemplateRepository::delete);
    }

    /**
     * Test: GET /api/v1/tasks/templates returns all templates (AC26)
     */
    @Test
    @WithMockUser(roles = "ORGANIZER")
    void should_listAllTemplates_when_requestedByOrganizer() throws Exception {
        // Given: Default templates exist in database (seeded by V22 migration)
        // Note: Default templates are seeded by migration, so we expect 7 templates

        // When: GET /api/v1/tasks/templates
        mockMvc.perform(get("/api/v1/tasks/templates"))
                // Then: Returns 200 OK
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                // 7 default templates from V22 migration
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(7))))
                .andExpect(jsonPath("$[0].id").exists())
                .andExpect(jsonPath("$[0].name").exists())
                .andExpect(jsonPath("$[0].triggerState").exists())
                .andExpect(jsonPath("$[0].dueDateType").exists())
                .andExpect(jsonPath("$[0].isDefault").exists());
    }

    /**
     * Test: POST /api/v1/tasks/templates creates custom template (AC22)
     */
    @Test
    @WithMockUser(username = "alice.organizer", roles = "ORGANIZER")
    void should_createCustomTemplate_when_validRequestProvided() throws Exception {
        // Given: Valid create request
        String requestBody = """
                {
                    "name": "Custom Task: Budget Approval",
                    "triggerState": "topic_selection",
                    "dueDateType": "relative_to_event",
                    "dueDateOffsetDays": -60
                }
                """;

        // When: POST /api/v1/tasks/templates
        mockMvc.perform(post("/api/v1/tasks/templates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                // Then: Returns 201 Created
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.name").value("Custom Task: Budget Approval"))
                .andExpect(jsonPath("$.triggerState").value("topic_selection"))
                .andExpect(jsonPath("$.dueDateType").value("relative_to_event"))
                .andExpect(jsonPath("$.dueDateOffsetDays").value(-60))
                .andExpect(jsonPath("$.isDefault").value(false))
                .andExpect(jsonPath("$.createdByUsername").value("alice.organizer"));
    }

    /**
     * Test: POST /api/v1/tasks/templates validates required fields
     */
    @Test
    @WithMockUser(roles = "ORGANIZER")
    void should_return400_when_createTemplateWithMissingName() throws Exception {
        // Given: Request missing name field
        String requestBody = """
                {
                    "triggerState": "topic_selection",
                    "dueDateType": "immediate"
                }
                """;

        // When: POST /api/v1/tasks/templates
        mockMvc.perform(post("/api/v1/tasks/templates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                // Then: Returns 400 Bad Request
                .andExpect(status().isBadRequest());
    }

    /**
     * Test: PUT /api/v1/tasks/templates/{id} updates custom template (AC26)
     */
    @Test
    @WithMockUser(username = "alice.organizer", roles = "ORGANIZER")
    void should_updateCustomTemplate_when_validRequestProvided() throws Exception {
        // Given: Existing custom template
        TaskTemplate template = new TaskTemplate();
        template.setName("Original Name");
        template.setTriggerState("topic_selection");
        template.setDueDateType("immediate");
        template.setIsDefault(false);
        template.setCreatedByUsername("alice.organizer");
        template = taskTemplateRepository.save(template);

        // And: Update request
        String requestBody = """
                {
                    "name": "Updated Name",
                    "triggerState": "agenda_published",
                    "dueDateType": "relative_to_event",
                    "dueDateOffsetDays": -30
                }
                """;

        // When: PUT /api/v1/tasks/templates/{id}
        mockMvc.perform(put("/api/v1/tasks/templates/" + template.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                // Then: Returns 200 OK
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(template.getId().toString()))
                .andExpect(jsonPath("$.name").value("Updated Name"))
                .andExpect(jsonPath("$.triggerState").value("agenda_published"))
                .andExpect(jsonPath("$.dueDateType").value("relative_to_event"))
                .andExpect(jsonPath("$.dueDateOffsetDays").value(-30));
    }

    /**
     * Test: PUT /api/v1/tasks/templates/{id} prevents updating default templates (AC26)
     */
    @Test
    @WithMockUser(roles = "ORGANIZER")
    void should_return403_when_attemptToUpdateDefaultTemplate() throws Exception {
        // Given: Default template (from V22 migration)
        TaskTemplate defaultTemplate = taskTemplateRepository.findByIsDefaultTrue().get(0);

        // And: Update request
        String requestBody = """
                {
                    "name": "Attempt to Change Default",
                    "triggerState": "topic_selection",
                    "dueDateType": "immediate"
                }
                """;

        // When: PUT /api/v1/tasks/templates/{id}
        mockMvc.perform(put("/api/v1/tasks/templates/" + defaultTemplate.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                // Then: Returns 403 Forbidden or 400 Bad Request
                .andExpect(status().is(anyOf(is(403), is(400))));
    }

    /**
     * Test: DELETE /api/v1/tasks/templates/{id} deletes custom template (AC26)
     */
    @Test
    @WithMockUser(roles = "ORGANIZER")
    void should_deleteCustomTemplate_when_requestedByOrganizer() throws Exception {
        // Given: Existing custom template
        TaskTemplate template = new TaskTemplate();
        template.setName("Template to Delete");
        template.setTriggerState("topic_selection");
        template.setDueDateType("immediate");
        template.setIsDefault(false);
        template.setCreatedByUsername("alice.organizer");
        template = taskTemplateRepository.save(template);
        UUID templateId = template.getId();

        // When: DELETE /api/v1/tasks/templates/{id}
        mockMvc.perform(delete("/api/v1/tasks/templates/" + templateId))
                // Then: Returns 204 No Content
                .andExpect(status().isNoContent());

        // And: Template is deleted
        assert taskTemplateRepository.findById(templateId).isEmpty();
    }

    /**
     * Test: DELETE /api/v1/tasks/templates/{id} prevents deleting default templates (AC26)
     */
    @Test
    @WithMockUser(roles = "ORGANIZER")
    void should_return403_when_attemptToDeleteDefaultTemplate() throws Exception {
        // Given: Default template (from V22 migration)
        TaskTemplate defaultTemplate = taskTemplateRepository.findByIsDefaultTrue().get(0);

        // When: DELETE /api/v1/tasks/templates/{id}
        mockMvc.perform(delete("/api/v1/tasks/templates/" + defaultTemplate.getId()))
                // Then: Returns 403 Forbidden or 400 Bad Request
                .andExpect(status().is(anyOf(is(403), is(400))));
    }

    /**
     * Test: GET /api/v1/tasks/templates returns default and custom templates
     */
    @Test
    @WithMockUser(roles = "ORGANIZER")
    void should_returnDefaultAndCustomTemplates_when_listingAllTemplates() throws Exception {
        // Given: Custom template exists
        TaskTemplate customTemplate = new TaskTemplate();
        customTemplate.setName("My Custom Template");
        customTemplate.setTriggerState("agenda_finalized");
        customTemplate.setDueDateType("relative_to_event");
        customTemplate.setDueDateOffsetDays(-14);
        customTemplate.setIsDefault(false);
        customTemplate.setCreatedByUsername("bob.organizer");
        taskTemplateRepository.save(customTemplate);

        // When: GET /api/v1/tasks/templates
        mockMvc.perform(get("/api/v1/tasks/templates"))
                // Then: Returns both default and custom templates
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                // 7 default + 1 custom = 8 templates
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(8))))
                .andExpect(jsonPath("$[?(@.name == 'My Custom Template')]").exists())
                .andExpect(jsonPath("$[?(@.isDefault == true)]", hasSize(greaterThanOrEqualTo(7))));
    }
}
