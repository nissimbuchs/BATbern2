package ch.batbern.events.controller;

import ch.batbern.events.config.CumsContractHarness;
import ch.batbern.shared.test.AbstractIntegrationTest;
import ch.batbern.events.config.TestAwsConfig;
import ch.batbern.events.config.TestSecurityConfig;
import ch.batbern.events.domain.NewsletterSubscriber;
import ch.batbern.events.repository.NewsletterSubscriberRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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

    /** CUMS behind the real UserApiClient (Story 17.0b2). */
    @Autowired
    private CumsContractHarness cums;

    /** A CUMS username (lower-case first.last); the old bare "dora" could never exist there. */
    private static final String DORA = "dora.meier";

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private NewsletterSubscriberRepository subscriberRepository;

    @BeforeEach
    void setUp() {
        subscriberRepository.deleteAll();
        cums.reset();
    }

    @AfterEach
    void resetCums() {
        cums.reset();
    }

    // ── AC2: POST /newsletter/subscribe ──────────────────────────────────────

    @Test
    @DisplayName("POST /newsletter/subscribe — new email → 200 OK")
    void subscribe_newEmail_returns200() throws Exception {
        Map<String, Object> body = Map.of("email", "test@batbern-test.ch", "language", "de");

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
                .email("dup@batbern-test.ch")
                .language("de")
                .source("explicit")
                .unsubscribeToken("tok-dup-1")
                .build());

        Map<String, Object> body = Map.of("email", "dup@batbern-test.ch");

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

    @Test
    @DisplayName("POST /newsletter/subscribe — RFC 2606 reserved domain → 400")
    void subscribe_reservedDomain_returns400() throws Exception {
        for (String email : new String[]{
            "zaproxy@example.com",
            "user@example.org",
            "user@example.net",
            "test-e2e@e2e.batbern.invalid",
            "user@something.test",
            "user@localhost"
        }) {
            mockMvc.perform(post("/api/v1/newsletter/subscribe")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of("email", email))))
                    .andExpect(status().isBadRequest());
        }
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
    @DisplayName("GET /newsletter/my-subscription — unauthenticated → 401")
    void getMySubscription_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/newsletter/my-subscription"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /newsletter/my-subscription — authenticated → 200")
    @WithMockUser(username = "carol", roles = "USER")
    void getMySubscription_authenticated_returns200() throws Exception {
        mockMvc.perform(get("/api/v1/newsletter/my-subscription"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subscribed").value(false));
    }

    /** #1143: username is not unique (one row per address); two rows must not 500. */
    @Test
    @DisplayName("GET /newsletter/my-subscription — two rows for one username → 200, subscribed if any is active")
    @WithMockUser(username = DORA, roles = "USER")
    void should_reportSubscribed_when_userHasTwoSubscriberRows() throws Exception {
        subscriberRepository.save(NewsletterSubscriber.builder()
                .email("dora.old@example.com").language("de").source("account").username(DORA)
                .unsubscribeToken("tok-dora-1").unsubscribedAt(Instant.now()).build());
        subscriberRepository.save(NewsletterSubscriber.builder()
                .email("dora@example.com").language("de").source("account").username(DORA)
                .unsubscribeToken("tok-dora-2").build());

        mockMvc.perform(get("/api/v1/newsletter/my-subscription"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subscribed").value(true))
                .andExpect(jsonPath("$.email").value("dora@example.com"));
    }

    /** #1143: unsubscribing from the account settings stops every address of that user. */
    @Test
    @DisplayName("PATCH /newsletter/my-subscription — unsubscribe with two rows → all rows unsubscribed")
    @WithMockUser(username = DORA, roles = "USER")
    void should_unsubscribeAllRows_when_userHasTwoSubscriberRows() throws Exception {
        subscriberRepository.save(NewsletterSubscriber.builder()
                .email("dora.old@example.com").language("de").source("account").username(DORA)
                .unsubscribeToken("tok-dora-3").build());
        subscriberRepository.save(NewsletterSubscriber.builder()
                .email("dora@example.com").language("de").source("account").username(DORA)
                .unsubscribeToken("tok-dora-4").build());

        mockMvc.perform(patch("/api/v1/newsletter/my-subscription")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"subscribed\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subscribed").value(false));

        assertThat(subscriberRepository.findAll())
                .filteredOn(s -> DORA.equals(s.getUsername()))
                .allSatisfy(s -> assertThat(s.getUnsubscribedAt()).isNotNull());
    }

    // ── #1244: newsletter per address ─────────────────────────────────────────

    private void doraHasAddresses() {
        cums.user(DORA, """
                {"id": "%s", "email": "Dora@Batbern-Test.ch", "firstName": "Dora", "lastName": "Meier",
                 "roles": ["ATTENDEE"], "createdAt": "2025-01-15T10:00:00Z", "updatedAt": "2025-01-15T10:00:00Z",
                 "additionalEmails": [
                   {"email": "dora.private@batbern-test.ch", "createdAt": "2025-01-15T10:00:00Z",
                    "verifiedAt": "2025-01-16T10:00:00Z"},
                   {"email": "dora.pending@batbern-test.ch", "createdAt": "2025-01-15T10:00:00Z"}]}"""
                .formatted(DORA));
    }

    @Test
    @DisplayName("#1244 GET /newsletter/my-subscriptions — primary first, then confirmed addresses, each with its state")
    @WithMockUser(username = DORA, roles = "USER")
    void should_listPrimaryAndConfirmedAddresses_when_gettingMySubscriptions() throws Exception {
        doraHasAddresses();
        subscriberRepository.save(NewsletterSubscriber.builder()
                .email("dora.private@batbern-test.ch").language("de").source("account").username(DORA)
                .unsubscribeToken("tok-dora-5").build());

        mockMvc.perform(get("/api/v1/newsletter/my-subscriptions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.addresses.length()").value(2))
                .andExpect(jsonPath("$.addresses[0].email").value("dora@batbern-test.ch"))
                .andExpect(jsonPath("$.addresses[0].primary").value(true))
                .andExpect(jsonPath("$.addresses[0].subscribed").value(false))
                .andExpect(jsonPath("$.addresses[1].email").value("dora.private@batbern-test.ch"))
                .andExpect(jsonPath("$.addresses[1].primary").value(false))
                .andExpect(jsonPath("$.addresses[1].subscribed").value(true));
    }

    @Test
    @DisplayName("#1244 PUT /newsletter/my-subscriptions — subscribe and unsubscribe one address, the others stay")
    @WithMockUser(username = DORA, roles = "USER")
    void should_changeOnlyThatAddress_when_puttingOneSubscription() throws Exception {
        doraHasAddresses();
        subscriberRepository.save(NewsletterSubscriber.builder()
                .email("dora@batbern-test.ch").language("de").source("account").username(DORA)
                .unsubscribeToken("tok-dora-6").build());

        mockMvc.perform(put("/api/v1/newsletter/my-subscriptions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"Dora.Private@batbern-test.ch\",\"subscribed\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.addresses[0].subscribed").value(true))
                .andExpect(jsonPath("$.addresses[1].subscribed").value(true));

        assertThat(subscriberRepository.findByEmailIgnoreCase("dora.private@batbern-test.ch"))
                .get().satisfies(sub -> {
                    assertThat(sub.getUsername()).isEqualTo(DORA);
                    assertThat(sub.getUnsubscribedAt()).isNull();
                });

        mockMvc.perform(put("/api/v1/newsletter/my-subscriptions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"dora@batbern-test.ch\",\"subscribed\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.addresses[0].subscribed").value(false))
                .andExpect(jsonPath("$.addresses[1].subscribed").value(true));
    }

    @Test
    @DisplayName("#1244 PUT /newsletter/my-subscriptions — an unconfirmed or foreign address → 403, nothing stored")
    @WithMockUser(username = DORA, roles = "USER")
    void should_return403_when_addressIsNotOneOfMine() throws Exception {
        doraHasAddresses();

        for (String address : new String[] {"dora.pending@batbern-test.ch", "someone@batbern-test.ch"}) {
            mockMvc.perform(put("/api/v1/newsletter/my-subscriptions")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"" + address + "\",\"subscribed\":true}"))
                    .andExpect(status().isForbidden());
        }
        assertThat(subscriberRepository.findAll()).isEmpty();
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
    // Duplicate basic tests removed by code review — covered by Story 10.28 tests below:
    // - listSubscribers_nonOrganizer → should_return403_when_nonOrganizerAccesses
    // - listSubscribers_organizer → should_returnPaginatedResponse_when_organizerListsSubscribers

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
    @DisplayName("GET /newsletter/subscribers?sort=email — ascending by email (ADR-013 sort vocab)")
    @WithMockUser(username = "organizer", roles = "ORGANIZER")
    void should_sortAscByEmail_when_sortParamProvided() throws Exception {
        NewsletterSubscriber zulu = NewsletterSubscriber.builder()
                .email("zulu@example.com").language("de")
                .source("explicit").unsubscribeToken("tok-asc1")
                .subscribedAt(Instant.parse("2025-01-01T00:00:00Z"))
                .build();
        subscriberRepository.save(zulu);
        NewsletterSubscriber alpha = NewsletterSubscriber.builder()
                .email("alpha@example.com").language("de")
                .source("explicit").unsubscribeToken("tok-asc2")
                .subscribedAt(Instant.parse("2026-01-01T00:00:00Z"))
                .build();
        subscriberRepository.save(alpha);

        mockMvc.perform(get("/api/v1/newsletter/subscribers").param("sort", "email"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].email").value("alpha@example.com"))
                .andExpect(jsonPath("$.data[1].email").value("zulu@example.com"));
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

    // ── #1131: offset paging needs a total order ─────────────────────────────

    @Test
    @DisplayName("#1131 GET /newsletter/subscribers — 250 rows sharing subscribedAt: every id exactly once across pages")
    @WithMockUser(username = "organizer", roles = "ORGANIZER")
    void should_returnEveryIdExactlyOnce_when_pagingOverTiedSortKey() throws Exception {
        // Production: 1829 of 2087 subscribers share the May 2026 import's subscribed_at.
        Instant importedAt = Instant.parse("2026-05-01T10:00:00Z");
        Set<String> expectedIds = new HashSet<>();
        for (int i = 0; i < 250; i++) {
            expectedIds.add(subscriberRepository.save(NewsletterSubscriber.builder()
                    .email(String.format("tied%03d@example.com", i))
                    .language("de").source("import")
                    .unsubscribeToken("tok-tied-" + i)
                    .subscribedAt(importedAt)
                    .build()).getId().toString());
        }
        // A few later sign-ups with distinct dates sort first under -subscribedAt. They shift the
        // tie across page boundaries; PostgreSQL's bounded (top-N) sort then returns the tied rows
        // in a different order per OFFSET (measured: 12 duplicates in 270 rows without the fix).
        for (int i = 0; i < 20; i++) {
            expectedIds.add(subscriberRepository.save(NewsletterSubscriber.builder()
                    .email(String.format("later%02d@example.com", i))
                    .language("de").source("explicit")
                    .unsubscribeToken("tok-later-" + i)
                    .subscribedAt(importedAt.plus(i + 1, java.time.temporal.ChronoUnit.DAYS))
                    .build()).getId().toString());
        }
        subscriberRepository.flush();

        // No sort parameter: the admin UI's default, -subscribedAt.
        List<String> seen = new ArrayList<>();
        for (int page = 1; page <= 3; page++) {
            String body = mockMvc.perform(get("/api/v1/newsletter/subscribers")
                            .param("status", "all").param("page", String.valueOf(page)).param("limit", "100"))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            for (JsonNode node : objectMapper.readTree(body).get("data")) {
                seen.add(node.get("id").asText());
            }
        }
        assertThat(seen).hasSize(270).doesNotHaveDuplicates();
        assertThat(new HashSet<>(seen)).isEqualTo(expectedIds);
    }

    // ── Subscriber search (e-mail, first name, username; folded, any order) ──────

    private void saveSubscriber(String email, String firstName, String username) {
        subscriberRepository.save(NewsletterSubscriber.builder()
                .email(email).firstName(firstName).username(username).language("de")
                .source("explicit").unsubscribeToken("tok-" + email)
                .build());
    }

    private void saveSearchSubscribers() {
        saveSubscriber("a1@example.com", "Anna", "anna.mueller");
        saveSubscriber("b2@example.com", "Ben", "ben.muller");
        saveSubscriber("c3@example.com", "Jürg", null);
        saveSubscriber("hpm@example.com", "Hans Peter", "hanspeter.muster");
    }

    @ParameterizedTest
    @CsvSource({
        "Müller,2", "Mueller,2", "muller,2", "muster hans,1", "peter,1", "jurg,1", "Juerg,1",
        "b2@example,1", "anna muller,1", "anna ben,0"
    })
    @WithMockUser(username = "organizer", roles = "ORGANIZER")
    @DisplayName("Subscriber search folds and matches the last name via the username")
    void should_findSubscribers_bySearch(String input, int expected) throws Exception {
        saveSearchSubscribers();

        mockMvc.perform(get("/api/v1/newsletter/subscribers").param("search", input))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pagination.totalItems").value(expected));
    }

    @Test
    @WithMockUser(username = "organizer", roles = "ORGANIZER")
    @DisplayName("A subscriber beyond page 1 is returned, totalItems counts all, status combines")
    void should_pageSubscriberSearch_andCombineWithStatus() throws Exception {
        saveSearchSubscribers();

        mockMvc.perform(get("/api/v1/newsletter/subscribers").param("search", "example")
                        .param("sort", "email").param("page", "2").param("limit", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].email").value("hpm@example.com"))
                .andExpect(jsonPath("$.pagination.totalItems").value(4));
        mockMvc.perform(get("/api/v1/newsletter/subscribers").param("search", "mueller")
                        .param("status", "unsubscribed"))
                .andExpect(jsonPath("$.pagination.totalItems").value(0));
    }

    @ParameterizedTest
    @ValueSource(strings = {"(test", "a  b", "100%", "'", "C++", "", "   "})
    @WithMockUser(username = "organizer", roles = "ORGANIZER")
    @DisplayName("Hostile or blank subscriber search answers 2xx; blank is no search")
    void should_answer2xx_when_subscriberSearchIsHostileOrBlank(String input) throws Exception {
        saveSearchSubscribers();

        var result = mockMvc.perform(get("/api/v1/newsletter/subscribers").param("search", input))
                .andExpect(status().isOk());
        if (input.isBlank()) {
            result.andExpect(jsonPath("$.pagination.totalItems").value(4));
        }
        if ("100%".equals(input)) {
            result.andExpect(jsonPath("$.pagination.totalItems").value(0));
        }
    }
}
