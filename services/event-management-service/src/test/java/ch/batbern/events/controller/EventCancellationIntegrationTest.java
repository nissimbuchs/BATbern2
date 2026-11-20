package ch.batbern.events.controller;

import ch.batbern.events.config.CumsContractHarness;
import ch.batbern.events.config.TestAwsConfig;
import ch.batbern.events.config.TestSecurityConfig;
import ch.batbern.events.core.dto.generated.EventType;
import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.Registration;
import ch.batbern.events.domain.Session;
import ch.batbern.events.domain.SessionUser;
import ch.batbern.events.domain.SpeakerPool;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.repository.RegistrationRepository;
import ch.batbern.events.repository.SessionRepository;
import ch.batbern.events.repository.SessionUserRepository;
import ch.batbern.events.repository.SpeakerPoolRepository;
import ch.batbern.events.service.SpeakerReminderService;
import ch.batbern.shared.test.AbstractIntegrationTest;
import ch.batbern.shared.test.mail.SesRecorder;
import ch.batbern.shared.types.EventWorkflowState;
import ch.batbern.shared.types.SpeakerWorkflowState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Cancelling an event (#824 item 2): the organizer moves the event to CANCELLED, every registration
 * that still holds a place or waits for one is cancelled, and each of those people gets the
 * cancellation mail in their language, after the commit. A cancelled event leaves the public site and
 * takes no registrations, the schedulers leave it alone, and the state is terminal.
 *
 * <p>Commits like production (no test transaction): the mail goes out after the commit. Mail runs
 * through the real {@code EmailService} into the {@link SesRecorder}; CUMS answers through the
 * {@link CumsContractHarness}. Synthetic SES simulator addresses only.
 */
@Import({TestSecurityConfig.class, TestAwsConfig.class})
@DisplayName("Event cancellation (#824 item 2)")
class EventCancellationIntegrationTest extends AbstractIntegrationTest {

    private static final String CODE = "BATbern9760";
    private static final String OTHER = "BATbern9761";
    private static final String SIM = "@simulator.amazonses.com";
    private static final String ORGANIZER = "olga.organizer";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private EventRepository eventRepository;
    @Autowired
    private RegistrationRepository registrationRepository;
    @Autowired
    private SessionRepository sessionRepository;
    @Autowired
    private SessionUserRepository sessionUserRepository;
    @Autowired
    private SpeakerPoolRepository speakerPoolRepository;
    @Autowired
    private SpeakerReminderService speakerReminderService;
    @Autowired
    private SesRecorder sesRecorder;
    @Autowired
    private CumsContractHarness cums;
    @Autowired
    private JdbcTemplate jdbc;

    private Event event;

    @BeforeEach
    void setUp() {
        cleanUp();
        sesRecorder.reset();
        event = eventRepository.saveAndFlush(event(CODE, EventWorkflowState.AGENDA_PUBLISHED));
        registrant("dora.registered", "registered", "de");
        registrant("evan.confirmed", "confirmed", "en");
        registrant("wim.waiting", "waitlist", "de");
        registrant("carl.cancelled", "cancelled", "de");
        Registration enrolled = registrant("otto.organizer", "confirmed", "de");
        Map<String, Object> metadata = new HashMap<>();
        metadata.put(Registration.AUTO_REGISTERED_FROM_KEY, Registration.TRIGGER_STAKEHOLDER_ENROLLMENT);
        enrolled.setMetadata(metadata);
        registrationRepository.saveAndFlush(enrolled);
    }

    @AfterEach
    void cleanUp() {
        sesRecorder.awaitAsyncSends();
        cums.reset();
        for (String code : List.of(CODE, OTHER)) {
            jdbc.update("DELETE FROM mail_message WHERE event_code = ?", code);
            jdbc.update("DELETE FROM speaker_reminder_log WHERE event_id IN "
                    + "(SELECT id FROM events WHERE event_code = ?)", code);
            jdbc.update("DELETE FROM events WHERE event_code = ?", code);
        }
    }

    @Test
    @DisplayName("cancel: state CANCELLED, active registrations cancelled, each person mailed once in their language")
    void cancel_cancelsRegistrations_andMailsEachActiveRegistrant() throws Exception {
        transition(CODE, "CANCELLED")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workflowState", is("CANCELLED")));

        assertThat(eventRepository.findByEventCode(CODE).orElseThrow().getWorkflowState())
                .isEqualTo(EventWorkflowState.CANCELLED);
        assertThat(registrationRepository.findByEventId(event.getId()))
                .extracting(Registration::getStatus)
                .containsOnly("cancelled")
                .hasSize(5);

        sesRecorder.awaitAsyncSends();
        assertThat(sesRecorder.mails()).hasSize(4);
        assertThat(sesRecorder.mailsTo(sim("dora.registered"))).singleElement().satisfies(mail -> {
            assertThat(mail.subject()).contains("abgesagt").contains("Cancel Probe " + CODE);
            assertThat(mail.html()).contains("Dora");
        });
        assertThat(sesRecorder.mailsTo(sim("evan.confirmed"))).singleElement()
                .satisfies(mail -> assertThat(mail.subject()).contains("cancelled"));
        assertThat(sesRecorder.mailsTo(sim("wim.waiting"))).hasSize(1);
        assertThat(sesRecorder.mailsTo(sim("otto.organizer"))).hasSize(1);
        assertThat(sesRecorder.mailsTo(sim("carl.cancelled")))
                .as("a registration cancelled before gets no mail").isEmpty();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM mail_message WHERE event_code = ?",
                Long.class, CODE)).as("one recorded send for the Mail Center").isEqualTo(1L);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM mail_recipient r JOIN mail_message m "
                + "ON m.id = r.mail_message_id WHERE m.event_code = ? AND r.kind = 'TO'", Long.class, CODE))
                .as("with each registrant as a recipient").isEqualTo(4L);

        // Idempotent: cancelling again mails nobody
        sesRecorder.reset();
        transition(CODE, "CANCELLED").andExpect(status().isOk());
        sesRecorder.awaitAsyncSends();
        assertThat(sesRecorder.mails()).isEmpty();
    }

    @Test
    @DisplayName("a completed event cannot be cancelled: 422, nothing changes, no mail")
    void cancel_completedEvent_isRefused() throws Exception {
        event.setWorkflowState(EventWorkflowState.EVENT_COMPLETED);
        eventRepository.saveAndFlush(event);

        transition(CODE, "CANCELLED").andExpect(status().isUnprocessableEntity());

        assertThat(registrationRepository.findByEventIdAndAttendeeUsername(event.getId(), "dora.registered")
                .orElseThrow().getStatus()).isEqualTo("registered");
        sesRecorder.awaitAsyncSends();
        assertThat(sesRecorder.mails()).isEmpty();
    }

    @Test
    @DisplayName("CANCELLED is terminal without override")
    void cancelled_isTerminal() throws Exception {
        transition(CODE, "CANCELLED").andExpect(status().isOk());

        transition(CODE, "AGENDA_PUBLISHED").andExpect(status().isUnprocessableEntity());
        transition(CODE, "ARCHIVED").andExpect(status().isUnprocessableEntity());
    }

    @Test
    @DisplayName("a cancelled event leaves the public site and takes no registrations")
    void cancelledEvent_isNotPublic_andRefusesRegistrations() throws Exception {
        // Positive control: published and open before the cancellation
        mockMvc.perform(get("/api/v1/public/events/{code}", CODE)).andExpect(status().isOk());
        assertThat(upcomingPublicCodes()).contains(CODE);

        transition(CODE, "CANCELLED").andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/public/events/{code}", CODE)).andExpect(status().isNotFound());
        assertThat(upcomingPublicCodes()).doesNotContain(CODE);
        mockMvc.perform(post("/api/v1/events/{code}/registrations", CODE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"Late\",\"lastName\":\"Comer\",\"email\":\"success+late"
                                + SIM + "\",\"termsAccepted\":true}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.details.code", is("EVENT_CANCELLED")));
        assertThat(registrationRepository.findByEventId(event.getId())).hasSize(5);
    }

    @Test
    @DisplayName("speakers of a cancelled event get no automatic reminders")
    void speakerReminders_skipCancelledEvent() throws Exception {
        Event other = eventRepository.saveAndFlush(event(OTHER, EventWorkflowState.SPEAKER_IDENTIFICATION));
        LocalDate deadline = LocalDate.now(ZoneId.of("Europe/Zurich")).plusDays(14); // TIER_1
        invitedSpeaker(event, "sina.cancelled", deadline);
        invitedSpeaker(other, "sven.active", deadline);
        transition(CODE, "CANCELLED").andExpect(status().isOk());
        sesRecorder.awaitAsyncSends();
        sesRecorder.reset();

        speakerReminderService.processReminders();

        sesRecorder.awaitAsyncSends();
        assertThat(sesRecorder.mailsTo(sim("sven.active"))).as("positive control").hasSize(1);
        assertThat(sesRecorder.mailsTo(sim("sina.cancelled"))).isEmpty();
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private ResultActions transition(String code, String targetState) throws Exception {
        return mockMvc.perform(post("/api/v1/events/{code}/transition", code)
                .with(user(ORGANIZER).roles("ORGANIZER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"targetState\":\"" + targetState + "\"}"));
    }

    private List<String> upcomingPublicCodes() throws Exception {
        String body = mockMvc.perform(get("/api/v1/public/events").param("scope", "upcoming").param("limit", "100"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return com.jayway.jsonpath.JsonPath.read(body, "$.data[*].eventCode");
    }

    private Registration registrant(String username, String status, String language) {
        String firstName = Character.toUpperCase(username.charAt(0)) + username.substring(1, username.indexOf('.'));
        cums.userWithSettings(username, sim(username), Map.of("language", language));
        return registrationRepository.saveAndFlush(Registration.builder()
                .registrationCode(CODE + "-" + username)
                .eventId(event.getId())
                .deregistrationToken(UUID.randomUUID())
                .attendeeUsername(username)
                .attendeeFirstName(firstName)
                .attendeeLastName("Probe")
                .attendeeEmail(sim(username))
                .status(status)
                .registrationDate(Instant.now())
                .build());
    }

    private void invitedSpeaker(Event of, String username, LocalDate responseDeadline) {
        Session session = sessionRepository.saveAndFlush(Session.builder()
                .eventId(of.getId()).eventCode(of.getEventCode())
                .sessionSlug("talk-" + username.replace('.', '-'))
                .title("Talk of " + username).sessionType("presentation").build());
        sessionUserRepository.saveAndFlush(SessionUser.builder().session(session).username(username)
                .speakerRole(SessionUser.SpeakerRole.PRIMARY_SPEAKER).isConfirmed(false).build());
        cums.user(username, "Speaker", "Probe", sim(username));
        speakerPoolRepository.saveAndFlush(SpeakerPool.builder().eventId(of.getId())
                .speakerName(username).status(SpeakerWorkflowState.INVITED)
                .responseDeadline(responseDeadline).sessionId(session.getId()).build());
    }

    private static Event event(String code, EventWorkflowState state) {
        Instant date = Instant.now().plus(40, ChronoUnit.DAYS).truncatedTo(ChronoUnit.HOURS);
        return Event.builder()
                .eventCode(code)
                .title("Cancel Probe " + code)
                .eventNumber(Integer.parseInt(code.substring("BATbern".length())))
                .date(date)
                .registrationDeadline(date.minus(7, ChronoUnit.DAYS))
                .venueName("Kornhausforum")
                .venueAddress("Kornhausplatz 18, 3011 Bern")
                .venueCapacity(200)
                .organizerUsername(ORGANIZER)
                .currentAttendeeCount(0)
                .eventType(EventType.EVENING)
                .workflowState(state)
                .currentPublishedPhase("agenda")
                .build();
    }

    /** A class-scoped address of the SES mailbox simulator (nothing is delivered in tests). */
    private static String sim(String username) {
        return "success+cancel9760-" + username.replace('.', '-') + SIM;
    }
}
