package ch.batbern.events.controller;

import ch.batbern.events.domain.Event;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.repository.OrganizerThanksRepository;
import ch.batbern.shared.test.AbstractIntegrationTest;
import ch.batbern.shared.types.EventWorkflowState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for "Thank the Organizers" — {@code POST/GET /api/v1/events/{eventCode}/thanks}
 * (Story 7.4). PostgreSQL via Testcontainers for production parity.
 *
 * <p>Covers AC1/AC4/AC5/AC6/AC7: the event-state guard (409), logged-in dedupe + note update,
 * anonymous clap increment, the per-(event,IP) rate-limit rejection (429 without incrementing),
 * and the public-count-only vs organizer-sees-notes GET split.
 *
 * <p>Turnstile token verification itself is an api-gateway concern (the gateway
 * {@code TurnstileVerificationFilter} runs before this service); in EMS isolation an anonymous
 * POST simply inserts a clap row, so the Turnstile leg is asserted at the gateway/E2E layer.
 *
 * <p>Each test creates its OWN event (fresh UUID), so the in-memory {@code ThanksRateLimiter}
 * counters — keyed by {@code eventId:ip} and NOT rolled back with the test transaction — never
 * collide across tests.
 */
@Transactional
class OrganizerThanksIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private OrganizerThanksRepository thanksRepository;

    private static final String ATTENDEE = "jane.attendee";
    private static final String ORGANIZER = "org.user";

    @BeforeEach
    void setUp() {
        thanksRepository.deleteAll();
        eventRepository.deleteAll();
    }

    // ==================== AC4: logged-in dedupe + note update ====================

    @Test
    @WithMockUser(username = ATTENDEE, roles = {"ATTENDEE"})
    @DisplayName("AC4: logged-in thanks once → count 1; repeat → still 1 and the note is updated")
    void should_dedupeLoggedIn_and_updateNote_onRepeat() throws Exception {
        Event event = saveEvent("BATbern940", EventWorkflowState.EVENT_COMPLETED);

        mockMvc.perform(post("/api/v1/events/{eventCode}/thanks", "BATbern940")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"note\": \"Danke!\" }"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count", is(1)))
                .andExpect(jsonPath("$.notes", nullValue()));

        // Repeat: no double-count, note updated to the new value.
        mockMvc.perform(post("/api/v1/events/{eventCode}/thanks", "BATbern940")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"note\": \"Thank you for 20 years!\" }"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count", is(1)));

        assertThat(thanksRepository.countByEventId(event.getId())).isEqualTo(1);
        assertThat(thanksRepository.findByEventIdAndThankedByUsername(event.getId(), ATTENDEE))
                .get()
                .satisfies(t -> assertThat(t.getNote()).isEqualTo("Thank you for 20 years!"));
    }

    // ==================== AC5: anonymous clap increments (not deduped) ====================

    @Test
    @DisplayName("AC5: anonymous thank-yous increment the clap aggregate and are not deduped")
    void should_incrementAnonymousClaps_withoutDedupe() throws Exception {
        Event event = saveEvent("BATbern941", EventWorkflowState.EVENT_LIVE);

        for (int i = 1; i <= 3; i++) {
            mockMvc.perform(post("/api/v1/events/{eventCode}/thanks", "BATbern941")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.count", is(i)));
        }

        assertThat(thanksRepository.countByEventId(event.getId())).isEqualTo(3);
    }

    // ==================== AC3: per-(event,IP) rate-limit rejection (429, no increment) ====================

    @Test
    @DisplayName("AC3: anonymous over the per-(event,IP) cap → 429 THANKS_RATE_LIMITED, no increment")
    void should_reject_when_anonymousRateLimitExceeded() throws Exception {
        Event event = saveEvent("BATbern942", EventWorkflowState.EVENT_COMPLETED);

        // Cap is 5/event/IP/hour. MockMvc remoteAddr defaults to 127.0.0.1.
        for (int i = 1; i <= 5; i++) {
            mockMvc.perform(post("/api/v1/events/{eventCode}/thanks", "BATbern942")
                            .contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isOk());
        }

        // 6th from the same IP → rejected, and the aggregate stays at 5.
        mockMvc.perform(post("/api/v1/events/{eventCode}/thanks", "BATbern942")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.details.code", is("THANKS_RATE_LIMITED")));

        assertThat(thanksRepository.countByEventId(event.getId()))
                .as("rate-limited submission must not increment the aggregate")
                .isEqualTo(5);
    }

    // ==================== AC1/AC7: event-state guard ====================

    @Test
    @WithMockUser(username = ATTENDEE, roles = {"ATTENDEE"})
    @DisplayName("AC1: event not live/completed → 409 THANKS_NOT_ALLOWED, no row created")
    void should_reject_when_eventNotLiveOrCompleted() throws Exception {
        Event event = saveEvent("BATbern943", EventWorkflowState.AGENDA_PUBLISHED);

        mockMvc.perform(post("/api/v1/events/{eventCode}/thanks", "BATbern943")
                        .contentType(MediaType.APPLICATION_JSON).content("{ \"note\": \"too early\" }"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.details.code", is("THANKS_NOT_ALLOWED")));

        assertThat(thanksRepository.countByEventId(event.getId())).isZero();
    }

    @Test
    @WithMockUser(username = ATTENDEE, roles = {"ATTENDEE"})
    @DisplayName("Event not found → 404")
    void should_return404_when_eventMissing() throws Exception {
        mockMvc.perform(post("/api/v1/events/{eventCode}/thanks", "BATbern999")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(username = ATTENDEE, roles = {"ATTENDEE"})
    @DisplayName("Note over 500 chars → 400")
    void should_reject_when_noteTooLong() throws Exception {
        saveEvent("BATbern944", EventWorkflowState.EVENT_COMPLETED);
        String longNote = "x".repeat(501);

        mockMvc.perform(post("/api/v1/events/{eventCode}/thanks", "BATbern944")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"note\": \"" + longNote + "\" }"))
                .andExpect(status().isBadRequest());
    }

    // ============ Bug fix: capture canonical username, NOT the Cognito sub ============

    @Test
    @DisplayName("Logged-in submit stores the canonical username (custom:username), not the Cognito sub")
    void should_storeCanonicalUsername_when_jwtPrincipalNameIsCognitoSub() throws Exception {
        Event event = saveEvent("BATbern946", EventWorkflowState.EVENT_COMPLETED);
        // Reproduces the prod incident: the JWT principal name is the Cognito sub (a UUID), but
        // the meaningful identity lives in the custom:username claim. authentication.getName()
        // returned the sub and stored it as thanked_by_username, which then failed name enrichment.
        String cognitoSub = "c334a852-10c1-70d2-f403-136e0a60acf7";
        String canonicalUsername = "nissim.buchs";

        mockMvc.perform(post("/api/v1/events/{eventCode}/thanks", "BATbern946")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"note\": \"Super Event!\" }")
                        .with(jwt().jwt(j -> j.subject(cognitoSub)
                                .claim("custom:username", canonicalUsername))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count", is(1)));

        assertThat(thanksRepository.findByEventIdAndThankedByUsername(event.getId(), canonicalUsername))
                .as("thank-you must be stored against the canonical username")
                .isPresent();
        assertThat(thanksRepository.findByEventIdAndThankedByUsername(event.getId(), cognitoSub))
                .as("thank-you must NOT be stored against the raw Cognito sub")
                .isEmpty();
    }

    // ==================== AC6: count public, notes organizer-only ====================

    @Test
    @DisplayName("AC6: public GET returns count only (no notes); organizer GET returns notes")
    void should_returnCountPublicly_and_notesOnlyToOrganizer() throws Exception {
        saveEvent("BATbern945", EventWorkflowState.EVENT_COMPLETED);

        // One logged-in thank-you with a note + one anonymous clap.
        mockMvc.perform(post("/api/v1/events/{eventCode}/thanks", "BATbern945")
                        .contentType(MediaType.APPLICATION_JSON).content("{ \"note\": \"Merci!\" }")
                        .with(org.springframework.security.test.web.servlet.request
                                .SecurityMockMvcRequestPostProcessors.user(ATTENDEE).roles("ATTENDEE")))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/events/{eventCode}/thanks", "BATbern945")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk());

        // Public GET (anonymous): count only, no notes.
        mockMvc.perform(get("/api/v1/events/{eventCode}/thanks", "BATbern945"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count", is(2)))
                .andExpect(jsonPath("$.notes", nullValue()));

        // Organizer GET: count + notes (newest first).
        mockMvc.perform(get("/api/v1/events/{eventCode}/thanks", "BATbern945")
                        .with(org.springframework.security.test.web.servlet.request
                                .SecurityMockMvcRequestPostProcessors.user(ORGANIZER).roles("ORGANIZER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count", is(2)))
                .andExpect(jsonPath("$.notes").isArray())
                .andExpect(jsonPath("$.notes.length()", is(2)));
    }

    // ==================== Helper ====================

    private Event saveEvent(String eventCode, EventWorkflowState workflowState) {
        Event event = new Event();
        event.setEventCode(eventCode);
        event.setEventNumber(Integer.parseInt(eventCode.replaceAll("\\D", "")));
        event.setTitle("Thanks Test Event");
        event.setDate(Instant.now().minus(1, ChronoUnit.DAYS));
        event.setRegistrationDeadline(Instant.now().minus(8, ChronoUnit.DAYS));
        event.setVenueName("Test Venue");
        event.setVenueAddress("Test Address");
        event.setVenueCapacity(150);
        event.setOrganizerUsername(ORGANIZER);
        event.setEventType(ch.batbern.events.core.dto.generated.EventType.EVENING);
        event.setWorkflowState(workflowState);
        event.setCreatedAt(Instant.now());
        event.setUpdatedAt(Instant.now());
        event.setCreatedBy(ORGANIZER);
        event.setUpdatedBy(ORGANIZER);
        return eventRepository.save(event);
    }
}
