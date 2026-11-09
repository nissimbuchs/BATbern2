package ch.batbern.events.contract;

import ch.batbern.events.config.TestAwsConfig;
import ch.batbern.events.config.TestSecurityConfig;
import ch.batbern.events.core.dto.generated.EventType;
import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.Registration;
import ch.batbern.events.domain.Session;
import ch.batbern.events.entity.EventAgendaConfig;
import ch.batbern.events.mailcenter.RequestConversations;
import ch.batbern.events.repository.EventAgendaConfigRepository;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.repository.RegistrationRepository;
import ch.batbern.events.repository.SessionRepository;
import ch.batbern.shared.test.AbstractIntegrationTest;
import ch.batbern.shared.types.EventWorkflowState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static ch.batbern.shared.test.contract.ProviderContract.assertExamplesMatchSchema;
import static ch.batbern.shared.test.contract.ProviderContract.assertResponseMatchesSpec;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Provider half of the S2S contract (Story 17.0b1): the EMS operation PCS calls on the public API
 * ({@code EventManagementClientImpl.getAttendanceSummary}) runs through the real controller and must
 * match the spec PCS's contract test answers from; the spec examples must match the same schema.
 * PCS reads the event schedule from {@code /internal/events/{eventCode}/schedule} (17.0 deferred
 * work), no longer from the public {@code GET /api/v1/events/{eventCode}?include=sessions}.
 *
 * <p>Story 17.0b2: the same for every {@code /internal} operation in
 * {@code docs/api/internal/ems-internal.openapi.yml} (callers: CUMS merge and Mail Center clients,
 * PCS {@code HttpOutboundMailRecorder}).
 */
@Import({TestSecurityConfig.class, TestAwsConfig.class})
@DisplayName("EMS honours the S2S contract PCS is tested against")
class S2sProviderContractIntegrationTest extends AbstractIntegrationTest {

    private static final String ANALYTICS = "docs/api/event-analytics-api.openapi.yml";
    private static final String EMS_INTERNAL = "docs/api/internal/ems-internal.openapi.yml";
    private static final String MERGE_SOURCE = "contract.source";
    private static final String MERGE_TARGET = "contract.target";
    private static final UUID MERGE_REQUEST = UUID.fromString("7a1c0000-0000-4000-8000-0000000b2001");
    private static final UUID LOGO_REQUEST = UUID.fromString("7a1c0000-0000-4000-8000-0000000b2002");
    private static final String MAIL_GROUP = "contract-0b2_BATbern9017_partner-meeting-invite";
    private static final String CODE = "BATbern9017";
    private static final Instant DATE = Instant.parse("2026-06-11T16:00:00Z");

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private EventRepository eventRepository;
    @Autowired
    private SessionRepository sessionRepository;
    @Autowired
    private RegistrationRepository registrationRepository;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private EventAgendaConfigRepository agendaConfigRepository;

    @BeforeEach
    void seed() {
        cleanUp();
        Event event = eventRepository.save(Event.builder()
                .eventCode(CODE).title("BATbern 9017 - Contracts").eventNumber(9017)
                .date(DATE).registrationDeadline(DATE.minus(7, ChronoUnit.DAYS))
                .venueName("Kursaal Bern").venueAddress("Kornhausstrasse 3, 3013 Bern").venueCapacity(200)
                .organizerUsername("organizer.alice").currentAttendeeCount(0)
                .eventType(EventType.EVENING).workflowState(EventWorkflowState.AGENDA_PUBLISHED)
                .build());
        sessionRepository.save(Session.builder()
                .eventId(event.getId()).eventCode(CODE).sessionSlug("contract-keynote")
                .title("Contract keynote").sessionType("presentation")
                .startTime(DATE).endTime(DATE.plus(45, ChronoUnit.MINUTES))
                .build());
        registrationRepository.save(Registration.builder()
                .registrationCode(CODE + "-reg-contract").eventId(event.getId())
                .attendeeUsername("contract.attendee").attendeeCompanyId("ContractCo")
                .status("confirmed").registrationDate(DATE.minus(30, ChronoUnit.DAYS))
                .build());
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM user_merge_log WHERE source_username = ?", MERGE_SOURCE);
        jdbc.update("DELETE FROM session_users WHERE username IN (?, ?)", MERGE_SOURCE, MERGE_TARGET);
        jdbc.update("DELETE FROM mail_message WHERE send_group_id = ?", MAIL_GROUP);
        jdbc.update("DELETE FROM mail_conversation WHERE merge_request_id = ? OR logo_request_id = ?",
                MERGE_REQUEST, LOGO_REQUEST);
        jdbc.update("DELETE FROM mail_message WHERE send_group_id IN (?, ?)",
                RequestConversations.Kind.MERGE.groupId(MERGE_REQUEST),
                RequestConversations.Kind.LOGO.groupId(LOGO_REQUEST));
        eventRepository.findByEventCode(CODE).ifPresent(e -> {
            agendaConfigRepository.findByEventId(e.getId()).ifPresent(agendaConfigRepository::delete);
            registrationRepository.deleteAll(registrationRepository.findByEventId(e.getId()));
            sessionRepository.deleteAll(sessionRepository.findByEventId(e.getId()));
            eventRepository.delete(e);
        });
    }

    @Test
    void should_haveValidExamples_forConsumedOperations() {
        assertExamplesMatchSchema(ANALYTICS, "GET", "/api/v1/events/attendance-summary");
    }

    @Test
    @WithMockUser(username = "partner.contract", roles = "PARTNER")
    void attendanceSummary_matchesSpec() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/events/attendance-summary")
                        .param("companyName", "ContractCo").param("fromYear", "2026"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.eventCode == '" + CODE + "')].companyAttendees").value(1))
                .andReturn();

        assertResponseMatchesSpec(ANALYTICS, result);
    }

    // ------------------------------------------------------------------ /internal (Story 17.0b2)

    @ParameterizedTest(name = "{0} {1}")
    @CsvSource({
        "GET,  /internal/users/merge-preview",
        "GET,  /internal/events/{eventCode}/schedule",
        "POST, /internal/users/merge",
        "POST, /internal/users/primary-email",
        "POST, /internal/mails",
        "POST, /internal/mail-center/merge-requests",
        "POST, /internal/mail-center/merge-requests/{requestId}/done",
        "POST, /internal/mail-center/logo-requests",
        "POST, /internal/mail-center/logo-requests/{requestId}/done",
    })
    @DisplayName("internal spec examples match their schema")
    void should_haveValidExamples_forInternalOperations(String method, String path) {
        assertExamplesMatchSchema(EMS_INTERNAL, method, path);
    }

    private MvcResult internal(MockHttpServletRequestBuilder request, int expectedStatus) throws Exception {
        MvcResult result = mockMvc.perform(request).andExpect(status().is(expectedStatus)).andReturn();
        assertResponseMatchesSpec(EMS_INTERNAL, result);
        return result;
    }

    private static MockHttpServletRequestBuilder postJson(String path, String json, Object... vars) {
        return post(path, vars).contentType(MediaType.APPLICATION_JSON).content(json);
    }

    @Test
    @DisplayName("schedule: the event's own times (EventTimeResolver), without a JWT")
    void eventSchedule_hasTheEventsTimes_withoutCaller() throws Exception {
        // Agenda published: the earliest session start and latest session end (seeded 16:00Z-16:45Z).
        internal(get("/internal/events/{eventCode}/schedule", CODE), 200);
        mockMvc.perform(get("/internal/events/{eventCode}/schedule", CODE))
                .andExpect(jsonPath("$.eventCode").value(CODE))
                .andExpect(jsonPath("$.date").value("2026-06-11T16:00:00Z"))
                .andExpect(jsonPath("$.venueName").value("Kursaal Bern"))
                .andExpect(jsonPath("$.startTime").value("2026-06-11T16:00:00Z"))
                .andExpect(jsonPath("$.endTime").value("2026-06-11T16:45:00Z"));
        internal(get("/internal/events/{eventCode}/schedule", "BATbern9999"), 404);
    }

    @Test
    @DisplayName("schedule before the agenda is published: the evening times EMS configures, not a copy in PCS")
    void eventSchedule_usesConfiguredTypeTimes_beforeAgendaPublished() throws Exception {
        Event event = eventRepository.findByEventCode(CODE).orElseThrow();
        event.setWorkflowState(EventWorkflowState.SPEAKER_IDENTIFICATION);
        eventRepository.save(event);
        java.time.LocalTime start = jdbc.queryForObject(
                "SELECT typical_start_time FROM event_types WHERE type = 'evening'", java.time.LocalTime.class);
        java.time.LocalTime end = jdbc.queryForObject(
                "SELECT typical_end_time FROM event_types WHERE type = 'evening'", java.time.LocalTime.class);

        mockMvc.perform(get("/internal/events/{eventCode}/schedule", CODE))
                .andExpect(jsonPath("$.startTime").value(zurich(start)))
                .andExpect(jsonPath("$.endTime").value(zurich(end)));
    }

    @Test
    @DisplayName("schedule with a per-event agenda override: the override's times (Story 15.2)")
    void eventSchedule_usesPerEventOverride_beforeAgendaPublished() throws Exception {
        Event event = eventRepository.findByEventCode(CODE).orElseThrow();
        event.setWorkflowState(EventWorkflowState.SPEAKER_IDENTIFICATION);
        eventRepository.save(event);
        agendaConfigRepository.save(EventAgendaConfig.builder()
                .eventId(event.getId()).minSlots(1).maxSlots(4).slotDuration(45).defaultCapacity(200)
                .typicalStartTime(java.time.LocalTime.of(17, 15)).typicalEndTime(java.time.LocalTime.of(21, 30))
                .build());

        internal(get("/internal/events/{eventCode}/schedule", CODE), 200);
        mockMvc.perform(get("/internal/events/{eventCode}/schedule", CODE))
                .andExpect(jsonPath("$.startTime").value("2026-06-11T15:15:00Z"))
                .andExpect(jsonPath("$.endTime").value("2026-06-11T19:30:00Z"));
    }

    /** The event day (2026-06-11) at a Zurich local time, as the instant EMS serializes. */
    private static String zurich(java.time.LocalTime time) {
        return java.time.LocalDate.of(2026, 6, 11).atTime(time).atZone(java.time.ZoneId.of("Europe/Zurich"))
                .toInstant().toString();
    }

    @Test
    void mergePreview_andMerge_andPrimaryEmail_matchSpec() throws Exception {
        internal(get("/internal/users/merge-preview").param("source", MERGE_SOURCE).param("target", MERGE_TARGET),
                200);
        internal(get("/internal/users/merge-preview").param("source", MERGE_TARGET).param("target", MERGE_TARGET),
                400);
        internal(get("/internal/users/merge-preview").param("source", MERGE_SOURCE), 400);
        internal(postJson("/internal/users/merge", """
                {"sourceUsername": "%s", "targetUsername": "%s", "performedBy": "organizer.alice",
                 "trigger": "ORGANIZER", "sourceProfile": {"email": "contract.source@example.com"}}"""
                .formatted(MERGE_SOURCE, MERGE_TARGET)), 200);
        internal(postJson("/internal/users/primary-email", """
                {"username": "%s", "oldEmail": "old@example.com", "newEmail": "new@example.com"}"""
                .formatted(MERGE_TARGET)), 200);
    }

    @Test
    @DisplayName("merge with both profiles leading a talk at one event: 409 MERGE_BLOCKED, as the spec says")
    void mergeBlocked_matchesSpec() throws Exception {
        Event event = eventRepository.findByEventCode(CODE).orElseThrow();
        UUID own = sessionRepository.findByEventId(event.getId()).get(0).getId();
        UUID other = sessionRepository.save(Session.builder()
                .eventId(event.getId()).eventCode(CODE).sessionSlug("contract-second")
                .title("Second talk").sessionType("presentation")
                .startTime(DATE.plus(1, ChronoUnit.HOURS)).endTime(DATE.plus(2, ChronoUnit.HOURS))
                .build()).getId();
        String slot = "INSERT INTO session_users (id, session_id, username, speaker_role) "
                + "VALUES (?, ?, ?, 'primary_speaker')";
        jdbc.update(slot, UUID.randomUUID(), own, MERGE_SOURCE);
        jdbc.update(slot, UUID.randomUUID(), other, MERGE_TARGET);

        MvcResult result = internal(postJson("/internal/users/merge", """
                {"sourceUsername": "%s", "targetUsername": "%s", "performedBy": "organizer.alice",
                 "trigger": "ORGANIZER"}""".formatted(MERGE_SOURCE, MERGE_TARGET)), 409);

        assertThat(result.getResponse().getContentAsString()).contains("MERGE_BLOCKED").contains(CODE);
    }

    @Test
    void recordMail_matchesSpec() throws Exception {
        internal(postJson("/internal/mails", """
                {"sendGroupId": "%s", "eventCode": "%s", "audience": "PARTNERS",
                 "templateKey": "partner-meeting-invite", "contentSource": "TEMPLATE", "senderUsername": "organizer1",
                 "test": false, "reference": "partner-meeting:0b2", "to": "success+p1@simulator.amazonses.com",
                 "cc": [], "subject": "Invite", "htmlBody": "<p>i</p>", "sesMessageId": "ses-0b2",
                 "failureReason": null}""".formatted(MAIL_GROUP, CODE)), 202);
        internal(postJson("/internal/mails", """
                {"sendGroupId": "%s", "audience": "SPEAKERS", "to": "a@example.com"}""".formatted(MAIL_GROUP)), 400);
    }

    @Test
    void requestConversations_openAndClose_matchSpec() throws Exception {
        String open = """
                {"requestId": "%s", "requesterName": "Anna Meier", "requesterEmail": "anna.meier@example.com",
                 "subject": "Contract request", "note": null}""";
        internal(postJson("/internal/mail-center/merge-requests", open.formatted(MERGE_REQUEST)), 200);
        internal(postJson("/internal/mail-center/merge-requests/{id}/done", "{\"decidedBy\": \"organizer.alice\"}",
                MERGE_REQUEST), 204);
        internal(postJson("/internal/mail-center/logo-requests", open.formatted(LOGO_REQUEST)), 200);
        internal(postJson("/internal/mail-center/logo-requests/{id}/done", "{\"decidedBy\": \"organizer.alice\"}",
                LOGO_REQUEST), 204);
        internal(postJson("/internal/mail-center/merge-requests", "{\"requestId\": \"%s\"}".formatted(MERGE_REQUEST)),
                400);
    }
}
