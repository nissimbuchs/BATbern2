package ch.batbern.partners.controller;

import ch.batbern.partners.config.TestAwsConfig;
import ch.batbern.partners.config.TestSecurityConfig;
import ch.batbern.partners.domain.Partner;
import ch.batbern.partners.domain.PartnershipLevel;
import ch.batbern.partners.repository.PartnerNoteRepository;
import ch.batbern.partners.repository.PartnerRepository;
import ch.batbern.shared.test.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import ch.batbern.partners.client.EventManagementClient;
import ch.batbern.partners.client.UserServiceClient;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for Partner Notes API — Story 8.4.
 * Tests cover AC1–5 (role-based access, list, create, update, delete).
 *
 * Uses AbstractIntegrationTest (PostgreSQL Testcontainer).
 * Integration test profile permits all requests (testFilterChain).
 * @PreAuthorize + SecurityConfig URL rules are tested via security-specific tests.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import({TestSecurityConfig.class, TestAwsConfig.class})
class PartnerNoteControllerIntegrationTest extends AbstractIntegrationTest {

    static final String BASE = "/api/v1/partners/%s/notes";

    @Autowired
    MockMvc mockMvc;
    @Autowired
    ObjectMapper objectMapper;
    @Autowired
    PartnerNoteRepository noteRepository;
    @Autowired
    PartnerRepository partnerRepository;

    @MockitoBean
    EventManagementClient eventManagementClient;
    @MockitoBean
    UserServiceClient userServiceClient;

    private String testCompanyName;

    @BeforeEach
    void setUp() {
        noteRepository.deleteAll();
        partnerRepository.deleteAll();

        testCompanyName = "AlphaCo";
        partnerRepository.save(Partner.builder()
                .companyName(testCompanyName)
                .partnershipLevel(PartnershipLevel.GOLD)
                .partnershipStartDate(LocalDate.now().minusYears(1))
                .partnershipEndDate(LocalDate.now().plusYears(1))
                .build());
    }

    // ─── AC2: List notes ─────────────────────────────────────────────────────

    @Test
    void should_listNotes_when_organizerRequests() throws Exception {
        mockMvc.perform(get(notesBase(testCompanyName))
                        .with(user("organizer").roles("ORGANIZER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void should_returnNotesSortedByCreatedAtDesc_when_multipleNotesExist() throws Exception {
        // Create two notes
        createNote(testCompanyName, "First Note", "Content 1");
        createNote(testCompanyName, "Second Note", "Content 2");

        mockMvc.perform(get(notesBase(testCompanyName))
                        .with(user("organizer").roles("ORGANIZER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                // Most recent first
                .andExpect(jsonPath("$[0].title", is("Second Note")))
                .andExpect(jsonPath("$[1].title", is("First Note")));
    }

    @Test
    void should_return404_when_companyNameUnknown() throws Exception {
        mockMvc.perform(get(notesBase("UnknownCo"))
                        .with(user("organizer").roles("ORGANIZER")))
                .andExpect(status().isNotFound());
    }

    // ─── AC3: Create note ────────────────────────────────────────────────────

    @Test
    void should_createNote_when_validRequest() throws Exception {
        Map<String, String> req = new HashMap<>();
        req.put("title", "Q1 Discussion");
        req.put("content", "<p>Discussed partnership goals for Q1.</p>");

        mockMvc.perform(post(notesBase(testCompanyName))
                        .with(user("organizer").roles("ORGANIZER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.title", is("Q1 Discussion")))
                .andExpect(jsonPath("$.content", is("<p>Discussed partnership goals for Q1.</p>")))
                .andExpect(jsonPath("$.authorUsername", notNullValue()))
                .andExpect(jsonPath("$.createdAt", notNullValue()))
                .andExpect(jsonPath("$.updatedAt", notNullValue()));
    }

    @Test
    void should_return400_when_titleMissing() throws Exception {
        Map<String, String> req = new HashMap<>();
        req.put("content", "Content without title");

        mockMvc.perform(post(notesBase(testCompanyName))
                        .with(user("organizer").roles("ORGANIZER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void should_return400_when_contentMissing() throws Exception {
        Map<String, String> req = new HashMap<>();
        req.put("title", "Title without content");

        mockMvc.perform(post(notesBase(testCompanyName))
                        .with(user("organizer").roles("ORGANIZER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    // ─── AC4: Update note ────────────────────────────────────────────────────

    @Test
    void should_updateNote_when_patchRequest() throws Exception {
        String noteId = createNote(testCompanyName, "Original Title", "Original Content");

        Map<String, String> update = new HashMap<>();
        update.put("title", "Updated Title");

        mockMvc.perform(patch(notesBase(testCompanyName) + "/" + noteId)
                        .with(user("organizer").roles("ORGANIZER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title", is("Updated Title")))
                // content unchanged (partial update)
                .andExpect(jsonPath("$.content", is("Original Content")));
    }

    @Test
    void should_return404_when_noteIdUnknownOnUpdate() throws Exception {
        Map<String, String> update = new HashMap<>();
        update.put("content", "Updated content");

        mockMvc.perform(patch(notesBase(testCompanyName) + "/" + UUID.randomUUID())
                        .with(user("organizer").roles("ORGANIZER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isNotFound());
    }

    // ─── AC5: Delete note ────────────────────────────────────────────────────

    @Test
    void should_deleteNote_when_deleteRequest() throws Exception {
        String noteId = createNote(testCompanyName, "To Delete", "Content");

        mockMvc.perform(delete(notesBase(testCompanyName) + "/" + noteId)
                        .with(user("organizer").roles("ORGANIZER")))
                .andExpect(status().isNoContent());

        // Verify note no longer returned on GET
        mockMvc.perform(get(notesBase(testCompanyName))
                        .with(user("organizer").roles("ORGANIZER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void should_return404_when_noteIdUnknownOnDelete() throws Exception {
        mockMvc.perform(delete(notesBase(testCompanyName) + "/" + UUID.randomUUID())
                        .with(user("organizer").roles("ORGANIZER")))
                .andExpect(status().isNotFound());
    }

    // ─── AC1: Role-based access (PARTNER → 403) ──────────────────────────────

    @Test
    void should_return403_when_partnerTriesToListNotes() throws Exception {
        mockMvc.perform(get(notesBase(testCompanyName))
                        .with(user("alice").roles("PARTNER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void should_return403_when_partnerTriesToCreateNote() throws Exception {
        Map<String, String> req = new HashMap<>();
        req.put("title", "Attempt");
        req.put("content", "Partner trying to create note");

        mockMvc.perform(post(notesBase(testCompanyName))
                        .with(user("alice").roles("PARTNER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    @Test
    void should_return403_when_unauthenticatedTriesToListNotes() throws Exception {
        mockMvc.perform(get(notesBase(testCompanyName)))
                .andExpect(status().isForbidden());
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private String notesBase(String companyName) {
        return String.format(BASE, companyName);
    }

    private String createNote(String companyName, String title, String content) throws Exception {
        Map<String, String> req = new HashMap<>();
        req.put("title", title);
        req.put("content", content);

        MvcResult result = mockMvc.perform(post(notesBase(companyName))
                        .with(user("organizer").roles("ORGANIZER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        return json.get("id").asText();
    }
}
