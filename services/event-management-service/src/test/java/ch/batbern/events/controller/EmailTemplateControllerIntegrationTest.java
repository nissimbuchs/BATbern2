package ch.batbern.events.controller;

import ch.batbern.shared.test.AbstractIntegrationTest;
import ch.batbern.events.config.TestAwsConfig;
import ch.batbern.events.config.TestSecurityConfig;
import ch.batbern.events.repository.EmailTemplateRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for Email Template API (Story 10.2 — Task 2g).
 *
 * Tests verify:
 * - GET /api/v1/email-templates - List all templates (seeded + any created)
 * - GET /api/v1/email-templates/{key}/{locale} - Get single template
 * - POST /api/v1/email-templates - Create custom template
 * - PUT /api/v1/email-templates/{key}/{locale} - Update template
 * - DELETE /api/v1/email-templates/{key}/{locale} - Delete (400 for system/layout)
 *
 * Uses PostgreSQL via Testcontainers (AbstractIntegrationTest).
 */
@Transactional
@Import({TestSecurityConfig.class, TestAwsConfig.class})
@DisplayName("EmailTemplateController Integration Tests")
class EmailTemplateControllerIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private EmailTemplateRepository emailTemplateRepository;

    // ── GET list ─────────────────────────────────────────────────────────────

    @Test
    @WithMockUser(username = "organizer", roles = {"ORGANIZER"})
    @DisplayName("GET /api/v1/email-templates returns seeded templates (at least 20: 18 content + 2 layout)")
    void list_returnsSeededTemplates() throws Exception {
        mockMvc.perform(get("/api/v1/email-templates"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(20))));
    }

    @Test
    @WithMockUser(username = "organizer", roles = {"ORGANIZER"})
    @DisplayName("GET /api/v1/email-templates?isLayout=true returns 2 layout templates")
    void list_filteredByIsLayout_returnsLayouts() throws Exception {
        mockMvc.perform(get("/api/v1/email-templates")
                .param("isLayout", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    @WithMockUser(username = "organizer", roles = {"ORGANIZER"})
    @DisplayName("GET /api/v1/email-templates?category=SPEAKER returns only SPEAKER templates")
    void list_filteredByCategory_returnsSpeakerTemplates() throws Exception {
        mockMvc.perform(get("/api/v1/email-templates")
                .param("category", "SPEAKER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].category").value(
                        org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.is("SPEAKER"))));
    }

    // ── GET single ───────────────────────────────────────────────────────────

    @Test
    @WithMockUser(username = "organizer", roles = {"ORGANIZER"})
    @DisplayName("GET /api/v1/email-templates/speaker-invitation/de returns seeded template")
    void get_existingTemplate_returns200() throws Exception {
        mockMvc.perform(get("/api/v1/email-templates/speaker-invitation/de"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.templateKey").value("speaker-invitation"))
                .andExpect(jsonPath("$.locale").value("de"))
                .andExpect(jsonPath("$.isSystemTemplate").value(true));
    }

    @Test
    @WithMockUser(username = "organizer", roles = {"ORGANIZER"})
    @DisplayName("GET /api/v1/email-templates/batbern-default/de returns layout template")
    void get_layoutTemplate_returnsLayoutFields() throws Exception {
        mockMvc.perform(get("/api/v1/email-templates/batbern-default/de"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.templateKey").value("batbern-default"))
                .andExpect(jsonPath("$.isLayout").value(true))
                .andExpect(jsonPath("$.category").value("LAYOUT"));
    }

    @Test
    @WithMockUser(username = "organizer", roles = {"ORGANIZER"})
    @DisplayName("GET /api/v1/email-templates/nonexistent/de returns 404")
    void get_notFound_returns404() throws Exception {
        mockMvc.perform(get("/api/v1/email-templates/nonexistent/de"))
                .andExpect(status().isNotFound());
    }

    // ── POST create ──────────────────────────────────────────────────────────

    @Test
    @WithMockUser(username = "organizer", roles = {"ORGANIZER"})
    @DisplayName("POST /api/v1/email-templates creates custom template with layoutKey")
    void create_validContentTemplate_returns201() throws Exception {
        Map<String, Object> request = Map.of(
                "templateKey", "my-custom-template",
                "locale", "de",
                "category", "SPEAKER",
                "subject", "Mein Betreff",
                "htmlBody", "<p>Inhalt</p>",
                "isLayout", false,
                "layoutKey", "batbern-default"
        );

        mockMvc.perform(post("/api/v1/email-templates")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.templateKey").value("my-custom-template"))
                .andExpect(jsonPath("$.layoutKey").value("batbern-default"))
                .andExpect(jsonPath("$.isSystemTemplate").value(false));
    }

    @Test
    @WithMockUser(username = "organizer", roles = {"ORGANIZER"})
    @DisplayName("POST /api/v1/email-templates without subject returns 400")
    void create_contentTemplateWithoutSubject_returns400() throws Exception {
        Map<String, Object> request = Map.of(
                "templateKey", "bad-template",
                "locale", "de",
                "category", "SPEAKER",
                "htmlBody", "<p>body</p>",
                "isLayout", false
        );

        mockMvc.perform(post("/api/v1/email-templates")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    // ── PUT update ───────────────────────────────────────────────────────────

    @Test
    @WithMockUser(username = "organizer", roles = {"ORGANIZER"})
    @DisplayName("PUT /api/v1/email-templates/speaker-invitation/de updates subject and htmlBody")
    void update_existingTemplate_returns200() throws Exception {
        Map<String, Object> request = Map.of(
                "subject", "Neuer Betreff",
                "htmlBody", "<html>Neuer Inhalt</html>"
        );

        mockMvc.perform(put("/api/v1/email-templates/speaker-invitation/de")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subject").value("Neuer Betreff"));
    }

    // ── DELETE ───────────────────────────────────────────────────────────────

    @Test
    @WithMockUser(username = "organizer", roles = {"ORGANIZER"})
    @DisplayName("DELETE system template returns 400")
    void delete_systemTemplate_returns400() throws Exception {
        mockMvc.perform(delete("/api/v1/email-templates/speaker-invitation/de"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "organizer", roles = {"ORGANIZER"})
    @DisplayName("DELETE layout template returns 400")
    void delete_layoutTemplate_returns400() throws Exception {
        mockMvc.perform(delete("/api/v1/email-templates/batbern-default/de"))
                .andExpect(status().isBadRequest());
    }
}
