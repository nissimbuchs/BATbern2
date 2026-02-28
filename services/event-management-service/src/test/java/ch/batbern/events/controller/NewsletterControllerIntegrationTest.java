package ch.batbern.events.controller;

import ch.batbern.shared.test.AbstractIntegrationTest;
import ch.batbern.events.client.UserApiClient;
import ch.batbern.events.config.TestAwsConfig;
import ch.batbern.events.config.TestSecurityConfig;
import ch.batbern.events.domain.NewsletterSubscriber;
import ch.batbern.events.repository.NewsletterSubscriberRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for NewsletterController (Story 10.7 — AC2, AC3, AC7, AC9, AC10, AC12).
 *
 * Tests all newsletter endpoints against real PostgreSQL via Testcontainers.
 */
@Transactional
@Import({TestSecurityConfig.class, TestAwsConfig.class})
@DisplayName("NewsletterController Integration Tests")
public class NewsletterControllerIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserApiClient userApiClient;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private NewsletterSubscriberRepository subscriberRepository;

    @BeforeEach
    void setUp() {
        subscriberRepository.deleteAll();
    }

    // ── AC2: POST /newsletter/subscribe ──────────────────────────────────────

    @Test
    @DisplayName("POST /newsletter/subscribe — new email → 200 OK")
    void subscribe_newEmail_returns200() throws Exception {
        Map<String, Object> body = Map.of("email", "test@example.com", "language", "de");

        mockMvc.perform(post("/api/v1/newsletter/subscribe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /newsletter/subscribe — duplicate active email → 409 Conflict")
    void subscribe_duplicateActiveEmail_returns409() throws Exception {
        // Pre-create an active subscriber
        subscriberRepository.save(NewsletterSubscriber.builder()
                .email("dup@example.com")
                .language("de")
                .source("explicit")
                .unsubscribeToken("tok-dup-1")
                .build());

        Map<String, Object> body = Map.of("email", "dup@example.com");

        mockMvc.perform(post("/api/v1/newsletter/subscribe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("POST /newsletter/subscribe — invalid email → 400")
    void subscribe_invalidEmail_returns400() throws Exception {
        Map<String, Object> body = Map.of("email", "not-an-email");

        mockMvc.perform(post("/api/v1/newsletter/subscribe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    // ── AC3: GET /newsletter/unsubscribe/verify ───────────────────────────────

    @Test
    @DisplayName("GET /newsletter/unsubscribe/verify?token=valid → 200 with email")
    void verifyUnsubscribe_validToken_returns200WithEmail() throws Exception {
        subscriberRepository.save(NewsletterSubscriber.builder()
                .email("alice@example.com")
                .language("de")
                .source("explicit")
                .unsubscribeToken("valid-token-abc")
                .build());

        mockMvc.perform(get("/api/v1/newsletter/unsubscribe/verify")
                        .param("token", "valid-token-abc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("alice@example.com"));
    }

    @Test
    @DisplayName("GET /newsletter/unsubscribe/verify?token=bad → 404")
    void verifyUnsubscribe_invalidToken_returns404() throws Exception {
        mockMvc.perform(get("/api/v1/newsletter/unsubscribe/verify")
                        .param("token", "nonexistent-token"))
                .andExpect(status().isNotFound());
    }

    // ── AC3: POST /newsletter/unsubscribe ─────────────────────────────────────

    @Test
    @DisplayName("POST /newsletter/unsubscribe — valid token → 200")
    void unsubscribe_validToken_returns200() throws Exception {
        subscriberRepository.save(NewsletterSubscriber.builder()
                .email("bob@example.com")
                .language("de")
                .source("explicit")
                .unsubscribeToken("unsubscribe-token-xyz")
                .build());

        Map<String, String> body = Map.of("token", "unsubscribe-token-xyz");

        mockMvc.perform(post("/api/v1/newsletter/unsubscribe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /newsletter/unsubscribe — invalid token → 404")
    void unsubscribe_invalidToken_returns404() throws Exception {
        Map<String, String> body = Map.of("token", "no-such-token");

        mockMvc.perform(post("/api/v1/newsletter/unsubscribe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isNotFound());
    }

    // ── AC7: GET /newsletter/my-subscription ─────────────────────────────────

    @Test
    @DisplayName("GET /newsletter/my-subscription — unauthenticated → 403")
    void getMySubscription_unauthenticated_returns403() throws Exception {
        mockMvc.perform(get("/api/v1/newsletter/my-subscription"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /newsletter/my-subscription — authenticated → 200")
    @WithMockUser(username = "carol", roles = "USER")
    void getMySubscription_authenticated_returns200() throws Exception {
        mockMvc.perform(get("/api/v1/newsletter/my-subscription"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subscribed").value(false));
    }

    // ── AC10: GET /newsletter/subscribers (ORGANIZER only) ───────────────────

    @Test
    @DisplayName("GET /newsletter/subscribers — non-organizer → 403")
    @WithMockUser(username = "user", roles = "USER")
    void listSubscribers_nonOrganizer_returns403() throws Exception {
        mockMvc.perform(get("/api/v1/newsletter/subscribers"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /newsletter/subscribers — organizer → 200 with totalCount")
    @WithMockUser(username = "organizer", roles = "ORGANIZER")
    void listSubscribers_organizer_returns200() throws Exception {
        mockMvc.perform(get("/api/v1/newsletter/subscribers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").exists());
    }

    // ── AC10: Event-scoped endpoints — auth checks ─────────────────────────

    @Test
    @DisplayName("POST /events/{code}/newsletter/send — non-organizer → 403")
    @WithMockUser(username = "user", roles = "USER")
    void sendNewsletter_nonOrganizer_returns403() throws Exception {
        Map<String, Object> body = Map.of("isReminder", false, "locale", "de");

        mockMvc.perform(post("/api/v1/events/BATbern-test/newsletter/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /events/{code}/newsletter/send — organizer, unknown event → 404")
    @WithMockUser(username = "organizer", roles = "ORGANIZER")
    void sendNewsletter_unknownEvent_returns404() throws Exception {
        Map<String, Object> body = Map.of("isReminder", false, "locale", "de");

        mockMvc.perform(post("/api/v1/events/NONEXISTENT-EVENT/newsletter/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("POST /events/{code}/newsletter/preview — non-organizer → 403")
    @WithMockUser(username = "user", roles = "USER")
    void previewNewsletter_nonOrganizer_returns403() throws Exception {
        Map<String, Object> body = Map.of("isReminder", false, "locale", "de");

        mockMvc.perform(post("/api/v1/events/BATbern-test/newsletter/preview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /events/{code}/newsletter/preview — organizer, unknown event → 404")
    @WithMockUser(username = "organizer", roles = "ORGANIZER")
    void previewNewsletter_unknownEvent_returns404() throws Exception {
        Map<String, Object> body = Map.of("isReminder", false, "locale", "de");

        mockMvc.perform(post("/api/v1/events/NONEXISTENT-EVENT/newsletter/preview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /events/{code}/newsletter/history — non-organizer → 403")
    @WithMockUser(username = "user", roles = "USER")
    void getHistory_nonOrganizer_returns403() throws Exception {
        mockMvc.perform(get("/api/v1/events/BATbern-test/newsletter/history"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /events/{code}/newsletter/history — organizer, unknown event → 404")
    @WithMockUser(username = "organizer", roles = "ORGANIZER")
    void getHistory_unknownEvent_returns404() throws Exception {
        mockMvc.perform(get("/api/v1/events/NONEXISTENT-EVENT/newsletter/history"))
                .andExpect(status().isNotFound());
    }
}
