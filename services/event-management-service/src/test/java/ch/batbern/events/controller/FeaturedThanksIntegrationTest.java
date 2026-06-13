package ch.batbern.events.controller;

import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.OrganizerThanks;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.repository.OrganizerThanksRepository;
import ch.batbern.shared.test.AbstractIntegrationTest;
import ch.batbern.shared.types.EventWorkflowState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for Story 7.7 — curated featured thank-you notes:
 * {@code PATCH /api/v1/events/{eventCode}/thanks/{id}} (organizer toggle) and
 * {@code GET /api/v1/thanks/featured} (public marquee). PostgreSQL via Testcontainers.
 *
 * <p>Covers AC1 (feature/un-feature + anonymous-rejection), AC2 (public featured pool, random
 * limit cap), AC3 (enrichment of name + company logo, settings_show_company opt-out, deleted-author
 * omission). The author-enrichment join reads the CUMS-owned {@code user_profiles}/{@code companies}
 * tables stubbed in the EMS test container — seeded here via {@link #seedAuthor}.
 */
@Transactional
class FeaturedThanksIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private OrganizerThanksRepository thanksRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static final String ORGANIZER = "org.user";

    @BeforeEach
    void setUp() {
        thanksRepository.deleteAll();
        eventRepository.deleteAll();
        jdbcTemplate.update("DELETE FROM user_profiles");
        jdbcTemplate.update("DELETE FROM companies");
    }

    // ==================== AC1: organizer feature toggle ====================

    @Test
    @WithMockUser(username = ORGANIZER, roles = {"ORGANIZER"})
    @DisplayName("AC1: organizer features a logged-in note → featured true + featured_at set; un-feature clears it")
    void should_featureAndUnfeature_loggedInNote() throws Exception {
        Event event = saveEvent("BATbern950", EventWorkflowState.EVENT_COMPLETED);
        seedAuthor("alice", "Alice", "Aebi", "elca", true, "ELCA", "https://cdn/elca.png");
        OrganizerThanks note = saveThanks(event.getId(), "alice", "Great event!", null);

        mockMvc.perform(patch("/api/v1/events/{eventCode}/thanks/{id}", "BATbern950", note.getId())
                        .contentType(MediaType.APPLICATION_JSON).content("{ \"featured\": true }"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.featured", is(true)))
                .andExpect(jsonPath("$.thankedByFirstName", is("Alice")));
        assertThat(thanksRepository.findById(note.getId()).orElseThrow().getFeaturedAt()).isNotNull();

        mockMvc.perform(patch("/api/v1/events/{eventCode}/thanks/{id}", "BATbern950", note.getId())
                        .contentType(MediaType.APPLICATION_JSON).content("{ \"featured\": false }"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.featured", is(false)));
        assertThat(thanksRepository.findById(note.getId()).orElseThrow().getFeaturedAt()).isNull();
    }

    @Test
    @WithMockUser(username = ORGANIZER, roles = {"ORGANIZER"})
    @DisplayName("AC1: featuring an ANONYMOUS note → 409 THANKS_NOT_FEATURABLE")
    void should_reject_when_featuringAnonymousNote() throws Exception {
        Event event = saveEvent("BATbern951", EventWorkflowState.EVENT_COMPLETED);
        OrganizerThanks anon = saveThanks(event.getId(), null, "anon clap", null);

        mockMvc.perform(patch("/api/v1/events/{eventCode}/thanks/{id}", "BATbern951", anon.getId())
                        .contentType(MediaType.APPLICATION_JSON).content("{ \"featured\": true }"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.details.code", is("THANKS_NOT_FEATURABLE")));
        assertThat(thanksRepository.findById(anon.getId()).orElseThrow().getFeaturedAt()).isNull();
    }

    @Test
    @WithMockUser(username = ORGANIZER, roles = {"ORGANIZER"})
    @DisplayName("AC1: unknown note id for the event → 404 THANKS_NOT_FOUND")
    void should_return404_when_noteNotFoundForEvent() throws Exception {
        saveEvent("BATbern952", EventWorkflowState.EVENT_COMPLETED);

        mockMvc.perform(patch("/api/v1/events/{eventCode}/thanks/{id}", "BATbern952", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON).content("{ \"featured\": true }"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.details.code", is("THANKS_NOT_FOUND")));
    }

    // ==================== AC2/AC3: public featured pool + enrichment ====================

    @Test
    @DisplayName("AC2/AC3: public GET returns only featured+logged-in notes, enriched with name + company logo")
    void should_returnOnlyFeaturedLoggedInNotes_enriched() throws Exception {
        Event event = saveEvent("BATbern953", EventWorkflowState.EVENT_COMPLETED);
        seedAuthor("alice", "Alice", "Aebi", "elca", true, "ELCA", "https://cdn/elca.png");
        seedAuthor("carol", "Carol", "Camenzind", "sbb", true, "SBB", "https://cdn/sbb.png");
        // featured + logged-in → returned
        saveThanks(event.getId(), "alice", "20 Jahre BATbern!", Instant.now());
        // featured but ANONYMOUS → excluded by the query (no username)
        saveThanks(event.getId(), null, "anon featured", Instant.now());
        // logged-in but NOT featured → excluded (distinct author: one row per (event,user))
        saveThanks(event.getId(), "carol", "not featured", null);

        mockMvc.perform(get("/api/v1/thanks/featured"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].note", is("20 Jahre BATbern!")))
                .andExpect(jsonPath("$[0].eventCode", is("BATbern953")))
                .andExpect(jsonPath("$[0].thankedByFirstName", is("Alice")))
                .andExpect(jsonPath("$[0].thankedByCompanyName", is("ELCA")))
                .andExpect(jsonPath("$[0].thankedByCompanyLogoUrl", is("https://cdn/elca.png")));
    }

    @Test
    @DisplayName("AC3: author who opted out of show-company → company name + logo null on the public card")
    void should_suppressCompany_when_showCompanyFalse() throws Exception {
        Event event = saveEvent("BATbern954", EventWorkflowState.EVENT_COMPLETED);
        seedAuthor("bob", "Bob", "Berger", "postfin", false, "PostFinance", "https://cdn/pf.png");
        saveThanks(event.getId(), "bob", "Danke!", Instant.now());

        mockMvc.perform(get("/api/v1/thanks/featured"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].thankedByFirstName", is("Bob")))
                .andExpect(jsonPath("$[0].thankedByCompanyName", nullValue()))
                .andExpect(jsonPath("$[0].thankedByCompanyLogoUrl", nullValue()));
    }

    @Test
    @DisplayName("AC3: a featured note whose author has no profile (deleted) is silently omitted")
    void should_omitFeaturedNote_when_authorDeleted() throws Exception {
        Event event = saveEvent("BATbern955", EventWorkflowState.EVENT_COMPLETED);
        // No seedAuthor for "ghost" → INNER JOIN user_profiles drops the row.
        saveThanks(event.getId(), "ghost", "I left BATbern", Instant.now());

        mockMvc.perform(get("/api/v1/thanks/featured"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @DisplayName("AC2: limit is hard-capped at 9 even when more featured notes exist")
    void should_capLimitAt9() throws Exception {
        Event event = saveEvent("BATbern956", EventWorkflowState.EVENT_COMPLETED);
        for (int i = 0; i < 12; i++) {
            String username = "user" + i;
            seedAuthor(username, "First" + i, "Last" + i, "co" + i, true, "Co" + i, "https://cdn/" + i + ".png");
            saveThanks(event.getId(), username, "note " + i, Instant.now());
        }

        mockMvc.perform(get("/api/v1/thanks/featured").param("limit", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(9)));
    }

    @Test
    @DisplayName("AC2: featured pool is GLOBAL — notes from different events are mixed")
    void should_drawFeaturedAcrossEvents() throws Exception {
        Event e1 = saveEvent("BATbern957", EventWorkflowState.EVENT_COMPLETED);
        Event e2 = saveEvent("BATbern958", EventWorkflowState.EVENT_COMPLETED);
        seedAuthor("alice", "Alice", "Aebi", "elca", true, "ELCA", "https://cdn/elca.png");
        seedAuthor("bob", "Bob", "Berger", "postfin", true, "PostFinance", "https://cdn/pf.png");
        saveThanks(e1.getId(), "alice", "event one", Instant.now());
        saveThanks(e2.getId(), "bob", "event two", Instant.now());

        mockMvc.perform(get("/api/v1/thanks/featured"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[*].eventCode", containsInAnyOrder("BATbern957", "BATbern958")));
    }

    @Test
    @DisplayName("AC2: per-company cap — two featured authors from the SAME company yield one card")
    void should_capOneCardPerCompany() throws Exception {
        Event event = saveEvent("BATbern959", EventWorkflowState.EVENT_COMPLETED);
        seedAuthor("dave", "Dave", "Durrer", "acme", true, "ACME", "https://cdn/acme.png");
        seedAuthor("erin", "Erin", "Egli", "acme", true, "ACME", "https://cdn/acme.png");
        saveThanks(event.getId(), "dave", "from dave", Instant.now());
        saveThanks(event.getId(), "erin", "from erin", Instant.now());

        mockMvc.perform(get("/api/v1/thanks/featured"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].thankedByCompanyName", is("ACME")));
    }

    // ==================== Helpers ====================

    private OrganizerThanks saveThanks(UUID eventId, String username, String note, Instant featuredAt) {
        return thanksRepository.save(OrganizerThanks.builder()
                .eventId(eventId)
                .thankedByUsername(username)
                .note(note)
                .featuredAt(featuredAt)
                .build());
    }

    /** Seed the CUMS-owned rows the enrichment join reads (mirrors the Q&A test). */
    private void seedAuthor(String username, String firstName, String lastName, String companyKey,
                            boolean showCompany, String companyDisplayName, String logoUrl) {
        jdbcTemplate.update("INSERT INTO companies (name, display_name, logo_url) VALUES (?, ?, ?) "
                        + "ON CONFLICT (name) DO NOTHING",
                companyKey, companyDisplayName, logoUrl);
        jdbcTemplate.update(
                "INSERT INTO user_profiles (username, company_id, first_name, last_name, settings_show_company) "
                        + "VALUES (?, ?, ?, ?, ?)",
                username, companyKey, firstName, lastName, showCompany);
    }

    private Event saveEvent(String eventCode, EventWorkflowState workflowState) {
        Event event = new Event();
        event.setEventCode(eventCode);
        event.setEventNumber(Integer.parseInt(eventCode.replaceAll("\\D", "")));
        event.setTitle("Featured Thanks Test Event");
        event.setDate(Instant.now().minus(1, ChronoUnit.DAYS));
        event.setRegistrationDeadline(Instant.now().minus(8, ChronoUnit.DAYS));
        event.setVenueName("Test Venue");
        event.setVenueAddress("Test Address");
        event.setVenueCapacity(150);
        event.setOrganizerUsername(ORGANIZER);
        event.setEventType(ch.batbern.events.dto.generated.EventType.EVENING);
        event.setWorkflowState(workflowState);
        event.setCreatedAt(Instant.now());
        event.setUpdatedAt(Instant.now());
        event.setCreatedBy(ORGANIZER);
        event.setUpdatedBy(ORGANIZER);
        return eventRepository.save(event);
    }
}
