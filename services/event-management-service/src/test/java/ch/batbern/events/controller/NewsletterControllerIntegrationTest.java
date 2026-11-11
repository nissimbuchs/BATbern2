package ch.batbern.events.controller;

import ch.batbern.events.config.CumsContractHarness;
import ch.batbern.shared.test.AbstractIntegrationTest;
import ch.batbern.shared.service.EmailService;
import ch.batbern.shared.test.mail.SesRecorder;
import ch.batbern.shared.utils.EmailAddresses;
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
 * Tests all newsletter endpoints against real PostgreSQL via Testcontainers, on the production
 * chain with CUMS as the {@link CumsContractHarness} and SES as the {@link SesRecorder}. Not
 * {@code @Transactional}: each test starts and ends with an empty subscriber table.
 */
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

    @Autowired
    private SesRecorder sesRecorder;

    @Autowired
    private EmailService emailService;

    @BeforeEach
    void setUp() {
        subscriberRepository.deleteAll();
        cums.reset();
        sesRecorder.reset();
    }

    @AfterEach
    void cleanUp() {
        subscriberRepository.deleteAll();
        cums.reset();
    }

    // ── AC2, 4.10c: POST /newsletter/subscribers (anonymous subscribe) ────────

    @Test
    @DisplayName("POST /newsletter/subscribers — anonymous, new address → 200, stored, no mail (positive control)")
    void should_subscribeWithoutMail_when_anonymousPostsNewAddress() throws Exception {
        String address = "success+newsletter.probe@simulator.amazonses.com";
        // Positive control: a mail to this address reaches the recorder.
        emailService.sendHtmlEmail(address, "Control", "<p>control</p>");

        mockMvc.perform(post("/api/v1/newsletter/subscribers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("email", "Success+Newsletter.Probe@simulator.amazonses.com", "language", "en"))))
                .andExpect(status().isOk());

        assertThat(subscriberRepository.findByEmailIgnoreCase(address)).get().satisfies(sub -> {
            assertThat(sub.getEmail()).isEqualTo(address); // stored lower-cased (#1099)
            assertThat(sub.getLanguage()).isEqualTo("en");
            assertThat(sub.getSource()).isEqualTo("explicit");
            assertThat(sub.getUsername()).isNull();
            assertThat(sub.getUnsubscribedAt()).isNull();
        });
        sesRecorder.awaitAsyncSends();
        assertThat(sesRecorder.mailsTo(address)).singleElement()
                .satisfies(mail -> assertThat(mail.subject()).isEqualTo("Control"));
    }

    @Test
    @DisplayName("POST /newsletter/subscribers — duplicate active email → 409 Conflict")
    void subscribe_duplicateActiveEmail_returns409() throws Exception {
        subscriberRepository.save(NewsletterSubscriber.builder()
                .email("dup@batbern-test.ch")
                .language("de")
                .source("explicit")
                .unsubscribeToken("tok-dup-1")
                .build());

        mockMvc.perform(post("/api/v1/newsletter/subscribers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", "DUP@batbern-test.ch"))))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("POST /newsletter/subscribers — unsubscribed address → 200, reactivated")
    void should_reactivate_when_unsubscribedAddressSubscribesAgain() throws Exception {
        subscriberRepository.save(NewsletterSubscriber.builder()
                .email("back@batbern-test.ch").language("de").source("explicit")
                .unsubscribeToken("tok-back-1").unsubscribedAt(Instant.now()).build());

        mockMvc.perform(post("/api/v1/newsletter/subscribers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", "back@batbern-test.ch"))))
                .andExpect(status().isOk());

        assertThat(subscriberRepository.findByEmailIgnoreCase("back@batbern-test.ch"))
                .get().satisfies(sub -> {
                    assertThat(sub.getUnsubscribedAt()).isNull();
                    assertThat(sub.getUnsubscribeToken()).isEqualTo("tok-back-1");
                });
    }

    @Test
    @DisplayName("POST /newsletter/subscribers — invalid email → 400")
    void subscribe_invalidEmail_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/newsletter/subscribers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", "not-an-email"))))
                .andExpect(status().isBadRequest());
        assertThat(subscriberRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("POST /newsletter/subscribers — RFC 2606 reserved domain → 400, nothing stored")
    void subscribe_reservedDomain_returns400() throws Exception {
        for (String email : new String[]{
            "zaproxy@example.com",
            "user@example.org",
            "user@example.net",
            "test-e2e@e2e.batbern.invalid",
            "user@something.test",
            "user@localhost"
        }) {
            mockMvc.perform(post("/api/v1/newsletter/subscribers")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of("email", email))))
                    .andExpect(status().isBadRequest());
        }
        assertThat(subscriberRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("4.10c: anonymous calls stay closed on the rest of the collection and on my-subscriptions → 401")
    void should_return401_when_anonymousUsesAnythingButTheSubscribe() throws Exception {
        NewsletterSubscriber sub = subscriberRepository.save(NewsletterSubscriber.builder()
                .email("kept@batbern-test.ch").language("de").source("explicit")
                .unsubscribeToken("tok-kept-1").build());

        mockMvc.perform(get("/api/v1/newsletter/subscribers")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/newsletter/subscribers/" + sub.getId() + "/unsubscribe"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/newsletter/my-subscriptions")).andExpect(status().isUnauthorized());
        mockMvc.perform(patch("/api/v1/newsletter/my-subscriptions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"kept@batbern-test.ch\",\"subscribed\":false}"))
                .andExpect(status().isUnauthorized());

        assertThat(subscriberRepository.findById(sub.getId())).get()
                .extracting(NewsletterSubscriber::getUnsubscribedAt).isNull();
    }

    @Test
    @DisplayName("4.10c: the old paths are gone: /newsletter/subscribe and /newsletter/my-subscription → 404/405")
    @WithMockUser(username = DORA, roles = "USER")
    void should_notServe_oldNewsletterPaths() throws Exception {
        List<Integer> codes = new ArrayList<>();
        codes.add(mockMvc.perform(post("/api/v1/newsletter/subscribe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"old.path@batbern-test.ch\"}"))
                .andReturn().getResponse().getStatus());
        codes.add(mockMvc.perform(get("/api/v1/newsletter/my-subscription")).andReturn().getResponse().getStatus());
        codes.add(mockMvc.perform(patch("/api/v1/newsletter/my-subscription")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"subscribed\":true}"))
                .andReturn().getResponse().getStatus());
        codes.add(mockMvc.perform(put("/api/v1/newsletter/my-subscriptions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"dora@batbern-test.ch\",\"subscribed\":true}"))
                .andReturn().getResponse().getStatus());
        assertThat(codes).allMatch(code -> code == 404 || code == 405);
        assertThat(subscriberRepository.findAll()).isEmpty();
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

    // ── #1244, 4.10c: newsletter per address (/newsletter/my-subscriptions GET + PATCH) ──

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

    private void saveDoraRow(String email, String language, boolean active) {
        subscriberRepository.save(NewsletterSubscriber.builder()
                .email(email).language(language).source("account").username(DORA)
                .unsubscribeToken("tok-" + email).unsubscribedAt(active ? null : Instant.now()).build());
    }

    @Test
    @DisplayName("GET /newsletter/my-subscriptions — primary first, then confirmed addresses, each with state and language")
    @WithMockUser(username = DORA, roles = "USER")
    void should_listPrimaryAndConfirmedAddresses_when_gettingMySubscriptions() throws Exception {
        doraHasAddresses();
        saveDoraRow("dora.private@batbern-test.ch", "en", true);

        mockMvc.perform(get("/api/v1/newsletter/my-subscriptions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.addresses.length()").value(2))
                .andExpect(jsonPath("$.addresses[0].email").value("dora@batbern-test.ch"))
                .andExpect(jsonPath("$.addresses[0].primary").value(true))
                .andExpect(jsonPath("$.addresses[0].subscribed").value(false))
                .andExpect(jsonPath("$.addresses[0].language").isEmpty())
                .andExpect(jsonPath("$.addresses[1].email").value("dora.private@batbern-test.ch"))
                .andExpect(jsonPath("$.addresses[1].primary").value(false))
                .andExpect(jsonPath("$.addresses[1].subscribed").value(true))
                .andExpect(jsonPath("$.addresses[1].language").value("en"));
    }

    @Test
    @DisplayName("PATCH /newsletter/my-subscriptions — subscribe and unsubscribe one address, the others stay; no mail")
    @WithMockUser(username = DORA, roles = "USER")
    void should_changeOnlyThatAddress_when_patchingOneSubscription() throws Exception {
        doraHasAddresses();
        saveDoraRow("dora@batbern-test.ch", "de", true);
        // Positive control: a mail to Dora's address reaches the recorder.
        emailService.sendHtmlEmail("dora.private@batbern-test.ch", "Control", "<p>control</p>");

        mockMvc.perform(patch("/api/v1/newsletter/my-subscriptions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"Dora.Private@batbern-test.ch\",\"subscribed\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.addresses[0].subscribed").value(true))
                .andExpect(jsonPath("$.addresses[1].subscribed").value(true))
                .andExpect(jsonPath("$.addresses[1].language").value("de"));

        assertThat(subscriberRepository.findByEmailIgnoreCase("dora.private@batbern-test.ch"))
                .get().satisfies(sub -> {
                    assertThat(sub.getUsername()).isEqualTo(DORA);
                    assertThat(sub.getSource()).isEqualTo("account");
                    assertThat(sub.getUnsubscribedAt()).isNull();
                });

        mockMvc.perform(patch("/api/v1/newsletter/my-subscriptions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"dora@batbern-test.ch\",\"subscribed\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.addresses[0].subscribed").value(false))
                .andExpect(jsonPath("$.addresses[1].subscribed").value(true));

        sesRecorder.awaitAsyncSends();
        assertThat(sesRecorder.mails()).singleElement()
                .satisfies(mail -> assertThat(mail.subject()).isEqualTo("Control"));
    }

    @Test
    @DisplayName("PATCH /newsletter/my-subscriptions — partial: language only changes the language, absent subscribed stays")
    @WithMockUser(username = DORA, roles = "USER")
    void should_changeOnlyLanguage_when_subscribedIsAbsent() throws Exception {
        doraHasAddresses();
        saveDoraRow("dora@batbern-test.ch", "de", true);
        saveDoraRow("dora.private@batbern-test.ch", "de", false);

        mockMvc.perform(patch("/api/v1/newsletter/my-subscriptions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"dora@batbern-test.ch\",\"language\":\"en\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.addresses[0].subscribed").value(true))
                .andExpect(jsonPath("$.addresses[0].language").value("en"));

        mockMvc.perform(patch("/api/v1/newsletter/my-subscriptions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"dora.private@batbern-test.ch\",\"language\":\"en\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.addresses[1].subscribed").value(false))
                .andExpect(jsonPath("$.addresses[1].language").value("en"));

        assertThat(subscriberRepository.findByEmailIgnoreCase("dora@batbern-test.ch"))
                .get().extracting(NewsletterSubscriber::getUnsubscribedAt).isNull();
        assertThat(subscriberRepository.findByEmailIgnoreCase("dora.private@batbern-test.ch"))
                .get().extracting(NewsletterSubscriber::getUnsubscribedAt).isNotNull();
    }

    /** #1143: a row still subscribed under the username for an address that left the account. */
    @Test
    @DisplayName("Stale address: listed while subscribed, may be unsubscribed, never subscribed again")
    @WithMockUser(username = DORA, roles = "USER")
    void should_listAndOnlyUnsubscribe_when_addressIsStale() throws Exception {
        doraHasAddresses();
        saveDoraRow("dora.old@batbern-test.ch", "de", true);

        mockMvc.perform(get("/api/v1/newsletter/my-subscriptions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.addresses.length()").value(3))
                .andExpect(jsonPath("$.addresses[2].email").value("dora.old@batbern-test.ch"))
                .andExpect(jsonPath("$.addresses[2].primary").value(false))
                .andExpect(jsonPath("$.addresses[2].subscribed").value(true));

        mockMvc.perform(patch("/api/v1/newsletter/my-subscriptions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"dora.old@batbern-test.ch\",\"language\":\"en\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(patch("/api/v1/newsletter/my-subscriptions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"dora.old@batbern-test.ch\",\"subscribed\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.addresses.length()").value(2));

        assertThat(subscriberRepository.findByEmailIgnoreCase("dora.old@batbern-test.ch"))
                .get().extracting(NewsletterSubscriber::getUnsubscribedAt).isNotNull();

        mockMvc.perform(patch("/api/v1/newsletter/my-subscriptions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"dora.old@batbern-test.ch\",\"subscribed\":true}"))
                .andExpect(status().isForbidden());
        assertThat(subscriberRepository.findByEmailIgnoreCase("dora.old@batbern-test.ch"))
                .get().extracting(NewsletterSubscriber::getUnsubscribedAt).isNotNull();
    }

    @Test
    @DisplayName("PATCH /newsletter/my-subscriptions — an unconfirmed or foreign address → 403, nothing stored")
    @WithMockUser(username = DORA, roles = "USER")
    void should_return403_when_addressIsNotOneOfMine() throws Exception {
        doraHasAddresses();
        subscriberRepository.save(NewsletterSubscriber.builder()
                .email("someone@batbern-test.ch").language("de").source("explicit")
                .unsubscribeToken("tok-someone").build());

        for (String body : new String[] {
            "{\"email\":\"dora.pending@batbern-test.ch\",\"subscribed\":true}",
            "{\"email\":\"someone@batbern-test.ch\",\"subscribed\":false}",
            "{\"email\":\"someone@batbern-test.ch\",\"language\":\"en\"}"}) {
            mockMvc.perform(patch("/api/v1/newsletter/my-subscriptions")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isForbidden());
        }
        assertThat(subscriberRepository.findAll()).singleElement().satisfies(sub -> {
            assertThat(sub.getEmail()).isEqualTo("someone@batbern-test.ch");
            assertThat(sub.getUnsubscribedAt()).isNull();
            assertThat(sub.getLanguage()).isEqualTo("de");
        });
    }

    @Test
    @DisplayName("4.9b PATCH /newsletter/my-subscriptions — an address over 254 characters or a bad language → 400")
    @WithMockUser(username = DORA, roles = "USER")
    void should_return400_when_addressIsTooLong() throws Exception {
        doraHasAddresses();
        // 64 + 1 + 185 + 5 = 255 characters; the pattern alone accepts it (idn-email has no @Email)
        String tooLong = "a".repeat(64) + "@" + "b".repeat(185) + ".test";
        assertThat(tooLong).hasSize(255).matches(EmailAddresses.OPENAPI_PATTERN);

        mockMvc.perform(patch("/api/v1/newsletter/my-subscriptions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + tooLong + "\",\"subscribed\":true}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(patch("/api/v1/newsletter/my-subscriptions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"dora@batbern-test.ch\",\"language\":\"fr\"}"))
                .andExpect(status().isBadRequest());
        assertThat(subscriberRepository.findAll()).isEmpty();
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
