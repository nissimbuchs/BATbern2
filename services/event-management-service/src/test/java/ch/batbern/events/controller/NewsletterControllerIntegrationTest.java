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

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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

    // ── AC10: GET /newsletter/subscribers/count (ORGANIZER only) ─────────────

    @Test
    @DisplayName("GET /newsletter/subscribers/count — non-organizer → 403")
    @WithMockUser(username = "user", roles = "USER")
    void getSubscriberCount_nonOrganizer_returns403() throws Exception {
        mockMvc.perform(get("/api/v1/newsletter/subscribers/count"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /newsletter/subscribers/count — organizer, no subscribers → totalActive=0")
    @WithMockUser(username = "organizer", roles = "ORGANIZER")
    void getSubscriberCount_organizer_returnsCount() throws Exception {
        subscriberRepository.save(NewsletterSubscriber.builder()
                .email("a@example.com").language("de").source("explicit").unsubscribeToken("tok-cnt-1")
                .build());
        subscriberRepository.save(NewsletterSubscriber.builder()
                .email("b@example.com").language("de").source("explicit").unsubscribeToken("tok-cnt-2")
                .build());

        mockMvc.perform(get("/api/v1/newsletter/subscribers/count"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalActive").value(2));
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
    @DisplayName("GET /newsletter/subscribers — organizer → 200 with paginated response")
    @WithMockUser(username = "organizer", roles = "ORGANIZER")
    void listSubscribers_organizer_returns200() throws Exception {
        mockMvc.perform(get("/api/v1/newsletter/subscribers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.pagination").exists());
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
    @DisplayName("POST /events/{code}/newsletter/preview — with templateKey → 404 not 400 (field is accepted)")
    @WithMockUser(username = "organizer", roles = "ORGANIZER")
    void previewNewsletter_withTemplateKey_fieldAccepted_returns404() throws Exception {
        Map<String, Object> body = Map.of("isReminder", false, "locale", "de", "templateKey", "custom-newsletter");

        mockMvc.perform(post("/api/v1/events/NONEXISTENT-EVENT/newsletter/preview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isNotFound()); // 404 = event not found, NOT 400 = field rejected
    }

    @Test
    @DisplayName("POST /events/{code}/newsletter/send — with templateKey → 404 not 400 (field is accepted)")
    @WithMockUser(username = "organizer", roles = "ORGANIZER")
    void sendNewsletter_withTemplateKey_fieldAccepted_returns404() throws Exception {
        Map<String, Object> body = Map.of("isReminder", false, "locale", "de", "templateKey", "custom-newsletter");

        mockMvc.perform(post("/api/v1/events/NONEXISTENT-EVENT/newsletter/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isNotFound()); // 404 = event not found, NOT 400 = field rejected
    }

    @Test
    @DisplayName("POST /events/{code}/newsletter/preview — null templateKey accepted (backward compat)")
    @WithMockUser(username = "organizer", roles = "ORGANIZER")
    void previewNewsletter_withNullTemplateKey_backwardCompatible_returns404() throws Exception {
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("isReminder", false);
        body.put("locale", "de");
        body.put("templateKey", null);

        mockMvc.perform(post("/api/v1/events/NONEXISTENT-EVENT/newsletter/preview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isNotFound()); // null templateKey accepted → uses default
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

    // ── GET /events/{code}/newsletter/sends/{sendId}/status ──────────────────

    @Test
    @DisplayName("GET /events/{code}/newsletter/sends/{sendId}/status — non-organizer → 403")
    @WithMockUser(username = "user", roles = "USER")
    void getSendStatus_nonOrganizer_returns403() throws Exception {
        mockMvc.perform(get("/api/v1/events/BATbern-test/newsletter/sends/00000000-0000-0000-0000-000000000001/status"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /events/{code}/newsletter/sends/{sendId}/status — organizer, unknown event → 404")
    @WithMockUser(username = "organizer", roles = "ORGANIZER")
    void getSendStatus_unknownEvent_returns404() throws Exception {
        mockMvc.perform(get("/api/v1/events/NONEXISTENT-EVENT/newsletter/sends/00000000-0000-0000-0000-000000000001/status"))
                .andExpect(status().isNotFound());
    }

    // ── POST /events/{code}/newsletter/sends/{sendId}/retry ──────────────────

    @Test
    @DisplayName("POST /events/{code}/newsletter/sends/{sendId}/retry — non-organizer → 403")
    @WithMockUser(username = "user", roles = "USER")
    void retryFailedRecipients_nonOrganizer_returns403() throws Exception {
        mockMvc.perform(post("/api/v1/events/BATbern-test/newsletter/sends/00000000-0000-0000-0000-000000000001/retry"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /events/{code}/newsletter/sends/{sendId}/retry — organizer, unknown event → 404")
    @WithMockUser(username = "organizer", roles = "ORGANIZER")
    void retryFailedRecipients_unknownEvent_returns404() throws Exception {
        mockMvc.perform(post("/api/v1/events/NONEXISTENT-EVENT/newsletter/sends/00000000-0000-0000-0000-000000000001/retry"))
                .andExpect(status().isNotFound());
    }

    // ── Story 10.28: Paginated subscriber list + management actions ─────────

    @Test
    @DisplayName("GET /newsletter/subscribers — organizer → 200 with paginated response")
    @WithMockUser(username = "organizer", roles = "ORGANIZER")
    void should_returnPaginatedResponse_when_organizerListsSubscribers() throws Exception {
        subscriberRepository.save(NewsletterSubscriber.builder()
                .email("sub1@example.com").firstName("Alice").language("en")
                .source("explicit").unsubscribeToken("tok-p1")
                .build());
        subscriberRepository.save(NewsletterSubscriber.builder()
                .email("sub2@example.com").firstName("Bob").language("de")
                .source("registration").unsubscribeToken("tok-p2")
                .build());

        mockMvc.perform(get("/api/v1/newsletter/subscribers")
                        .param("page", "1").param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.pagination.page").value(1))
                .andExpect(jsonPath("$.pagination.totalItems").value(2))
                .andExpect(jsonPath("$.pagination.hasNext").value(false));
    }

    @Test
    @DisplayName("GET /newsletter/subscribers?search=alice — filters by search")
    @WithMockUser(username = "organizer", roles = "ORGANIZER")
    void should_filterBySearch_when_searchParamProvided() throws Exception {
        subscriberRepository.save(NewsletterSubscriber.builder()
                .email("alice@example.com").firstName("Alice").language("en")
                .source("explicit").unsubscribeToken("tok-s1")
                .build());
        subscriberRepository.save(NewsletterSubscriber.builder()
                .email("bob@example.com").firstName("Bob").language("de")
                .source("explicit").unsubscribeToken("tok-s2")
                .build());

        mockMvc.perform(get("/api/v1/newsletter/subscribers")
                        .param("search", "alice"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].email").value("alice@example.com"));
    }

    @Test
    @DisplayName("GET /newsletter/subscribers?status=active — filters active only")
    @WithMockUser(username = "organizer", roles = "ORGANIZER")
    void should_filterByStatus_when_statusActive() throws Exception {
        subscriberRepository.save(NewsletterSubscriber.builder()
                .email("active@example.com").language("de")
                .source("explicit").unsubscribeToken("tok-st1")
                .build());
        NewsletterSubscriber unsub = NewsletterSubscriber.builder()
                .email("unsub@example.com").language("de")
                .source("explicit").unsubscribeToken("tok-st2")
                .build();
        unsub.setUnsubscribedAt(Instant.now());
        subscriberRepository.save(unsub);

        mockMvc.perform(get("/api/v1/newsletter/subscribers")
                        .param("status", "active"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].email").value("active@example.com"));
    }

    @Test
    @DisplayName("GET /newsletter/subscribers — default sort desc by subscribedAt")
    @WithMockUser(username = "organizer", roles = "ORGANIZER")
    void should_sortDescBySubscribedAt_when_defaultSort() throws Exception {
        // Create with different subscribedAt times
        NewsletterSubscriber older = NewsletterSubscriber.builder()
                .email("older@example.com").language("de")
                .source("explicit").unsubscribeToken("tok-sort1")
                .subscribedAt(Instant.parse("2025-01-01T00:00:00Z"))
                .build();
        subscriberRepository.save(older);
        NewsletterSubscriber newer = NewsletterSubscriber.builder()
                .email("newer@example.com").language("de")
                .source("explicit").unsubscribeToken("tok-sort2")
                .subscribedAt(Instant.parse("2026-01-01T00:00:00Z"))
                .build();
        subscriberRepository.save(newer);

        mockMvc.perform(get("/api/v1/newsletter/subscribers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].email").value("newer@example.com"))
                .andExpect(jsonPath("$.data[1].email").value("older@example.com"));
    }

    @Test
    @DisplayName("POST /newsletter/subscribers/{id}/unsubscribe — organizer → 200")
    @WithMockUser(username = "organizer", roles = "ORGANIZER")
    void should_unsubscribeSubscriber_when_organizerRequests() throws Exception {
        NewsletterSubscriber sub = subscriberRepository.save(NewsletterSubscriber.builder()
                .email("tosub@example.com").language("de")
                .source("explicit").unsubscribeToken("tok-unsub1")
                .build());

        mockMvc.perform(post("/api/v1/newsletter/subscribers/" + sub.getId() + "/unsubscribe"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unsubscribedAt").isNotEmpty());
    }

    @Test
    @DisplayName("POST /newsletter/subscribers/{id}/unsubscribe — already unsubscribed → 409")
    @WithMockUser(username = "organizer", roles = "ORGANIZER")
    void should_return409_when_alreadyUnsubscribed() throws Exception {
        NewsletterSubscriber sub = NewsletterSubscriber.builder()
                .email("already@example.com").language("de")
                .source("explicit").unsubscribeToken("tok-409a")
                .build();
        sub.setUnsubscribedAt(Instant.now());
        sub = subscriberRepository.save(sub);

        mockMvc.perform(post("/api/v1/newsletter/subscribers/" + sub.getId() + "/unsubscribe"))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("POST /newsletter/subscribers/{id}/resubscribe — organizer → 200")
    @WithMockUser(username = "organizer", roles = "ORGANIZER")
    void should_resubscribeSubscriber_when_organizerRequests() throws Exception {
        NewsletterSubscriber sub = NewsletterSubscriber.builder()
                .email("resub@example.com").language("de")
                .source("explicit").unsubscribeToken("tok-resub1")
                .build();
        sub.setUnsubscribedAt(Instant.now());
        sub = subscriberRepository.save(sub);

        mockMvc.perform(post("/api/v1/newsletter/subscribers/" + sub.getId() + "/resubscribe"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unsubscribedAt").isEmpty());
    }

    @Test
    @DisplayName("POST /newsletter/subscribers/{id}/resubscribe — already active → 409")
    @WithMockUser(username = "organizer", roles = "ORGANIZER")
    void should_return409_when_alreadyActive() throws Exception {
        NewsletterSubscriber sub = subscriberRepository.save(NewsletterSubscriber.builder()
                .email("active2@example.com").language("de")
                .source("explicit").unsubscribeToken("tok-409b")
                .build());

        mockMvc.perform(post("/api/v1/newsletter/subscribers/" + sub.getId() + "/resubscribe"))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("DELETE /newsletter/subscribers/{id} — organizer → 204")
    @WithMockUser(username = "organizer", roles = "ORGANIZER")
    void should_deleteSubscriber_when_organizerRequests() throws Exception {
        NewsletterSubscriber sub = subscriberRepository.save(NewsletterSubscriber.builder()
                .email("todelete@example.com").language("de")
                .source("explicit").unsubscribeToken("tok-del1")
                .build());

        mockMvc.perform(delete("/api/v1/newsletter/subscribers/" + sub.getId()))
                .andExpect(status().isNoContent());

        assertThat(subscriberRepository.findById(sub.getId())).isEmpty();
    }

    @Test
    @DisplayName("GET /newsletter/subscribers — non-organizer → 403")
    @WithMockUser(username = "speaker", roles = "SPEAKER")
    void should_return403_when_nonOrganizerAccesses() throws Exception {
        mockMvc.perform(get("/api/v1/newsletter/subscribers"))
                .andExpect(status().isForbidden());
    }
}
