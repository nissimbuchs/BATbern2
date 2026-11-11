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
import ch.batbern.shared.test.contract.OpenApiContract;
import ch.batbern.shared.types.EventWorkflowState;
import com.jayway.jsonpath.JsonPath;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static ch.batbern.shared.test.contract.ProviderContract.assertExamplesMatchSchema;
import static ch.batbern.shared.test.contract.ProviderContract.assertResponseMatchesSpec;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Provider half of the S2S contract (Stories 17.0b1, 17.0b2): every {@code /internal} operation in
 * {@code docs/api/internal/ems-internal.openapi.yml} runs through the real controller and must match
 * the spec its consumers' contract tests answer from; the spec examples must match the same schema.
 * Callers: PCS {@code HttpOutboundMailRecorder} and {@code EventManagementClientImpl} (event
 * schedule since the 17.0 deferred work, attendance summary since Epic 17 6b). EMS serves no public
 * operation to another service any more.
 *
 * <p>EMS also implements two contracts CUMS owns, the Mail Center request contract (Epic 17 entry 7)
 * and the user-reference contract (entry 7b): those operations are checked against
 * {@code docs/api/internal/cums-internal.openapi.yml}, the spec the CUMS contract callers are tested
 * against.
 */
@Import({TestSecurityConfig.class, TestAwsConfig.class})
@DisplayName("EMS honours the S2S contract PCS is tested against")
class S2sProviderContractIntegrationTest extends AbstractIntegrationTest {

    private static final String EMS_INTERNAL = "docs/api/internal/ems-internal.openapi.yml";
    private static final String CUMS_INTERNAL = "docs/api/internal/cums-internal.openapi.yml";
    private static final UUID MERGE_REQUEST = UUID.fromString("7a1c0000-0000-4000-8000-0000000b2001");
    private static final UUID LOGO_REQUEST = UUID.fromString("7a1c0000-0000-4000-8000-0000000b2002");
    private static final String MERGE_SOURCE = "contract.source";
    private static final String MERGE_TARGET = "contract.target";
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

    // ------------------------------------------------------------------ /internal (Story 17.0b2)

    @ParameterizedTest(name = "{0} {1}")
    @CsvSource({
        "GET,  /internal/events/{eventCode}/schedule",
        "GET,  /internal/events/attendance-summary",
        "POST, /internal/mails",
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

    @ParameterizedTest(name = "forwarded JWT: {0}")
    @CsvSource({"true", "false"})
    @DisplayName("attendance summary (PCS partner dashboard, 6b), with and without the user's JWT")
    void attendanceSummary_matchesSpec(boolean user) throws Exception {
        MockHttpServletRequestBuilder request = get("/internal/events/attendance-summary")
                .param("companyName", "ContractCo").param("fromYear", "2026");
        if (user) {
            request.with(jwt().jwt(j -> j.claim("custom:username", "partner.contract")
                    .claim("custom:role", "PARTNER")));
        }
        MvcResult result = internal(request, 200);
        List<Number> attendees = JsonPath.read(result.getResponse().getContentAsString(),
                "$[?(@.eventCode == '" + CODE + "')].companyAttendees");
        assertThat(attendees).singleElement().satisfies(n -> assertThat(n.longValue()).isEqualTo(1L));
        internal(get("/internal/events/attendance-summary"), 400);
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

    // ------------------------------------------------------------------ contract owned by CUMS (entry 7)

    /** A contract request CUMS would send: valid against the CUMS spec, answered as that spec says. */
    private MvcResult contractCall(String path, String body, int expectedStatus, Object... vars) throws Exception {
        String concrete = vars.length == 0 ? path : path.replace("{requestId}", vars[0].toString());
        List<String> errors = OpenApiContract.load(CUMS_INTERNAL).validateRequest("POST", concrete, Map.of(),
                Map.of("Content-Type", List.of("application/json")), body);
        if (expectedStatus < 300) {
            assertThat(errors).as("a valid contract request").isEmpty();
        }
        MvcResult result = mockMvc.perform(postJson(path, body, vars)).andExpect(status().is(expectedStatus))
                .andReturn();
        assertResponseMatchesSpec(CUMS_INTERNAL, result);
        return result;
    }

    /** The user-reference preview as CUMS calls it ({@code source} / {@code target} null: left out). */
    private MvcResult contractGet(String source, String target, int expectedStatus) throws Exception {
        Map<String, List<String>> query = new java.util.LinkedHashMap<>();
        MockHttpServletRequestBuilder request = get("/internal/user-references/preview");
        if (source != null) {
            query.put("source", List.of(source));
            request.param("source", source);
        }
        if (target != null) {
            query.put("target", List.of(target));
            request.param("target", target);
        }
        if (expectedStatus < 300) {
            assertThat(OpenApiContract.load(CUMS_INTERNAL).validateRequest("GET",
                    "/internal/user-references/preview", query, Map.of(), null)).as("a valid contract request").isEmpty();
        }
        MvcResult result = mockMvc.perform(request).andExpect(status().is(expectedStatus)).andReturn();
        assertResponseMatchesSpec(CUMS_INTERNAL, result);
        return result;
    }

    @ParameterizedTest(name = "forwarded JWT: {0}")
    @CsvSource({"true", "false"})
    @DisplayName("user-reference contract: preview, merge and primary email, as CUMS calls them, with and without the user's JWT")
    void userReferenceContract_matchesCumsSpec(boolean user) throws Exception {
        if (user) {
            mockMvc.perform(get("/internal/user-references/preview").param("source", MERGE_SOURCE)
                            .param("target", MERGE_TARGET)
                            .with(jwt().jwt(j -> j.claim("custom:username", "organizer.alice")
                                    .claim("custom:role", "ORGANIZER"))))
                    .andExpect(status().isOk());
        }
        contractGet(MERGE_SOURCE, MERGE_TARGET, 200);
        contractGet(MERGE_TARGET, MERGE_TARGET, 400);
        contractGet(MERGE_SOURCE, null, 400);
        contractCall("/internal/user-references/merge", """
                {"sourceUsername": "%s", "targetUsername": "%s", "performedBy": "organizer.alice",
                 "trigger": "ORGANIZER", "sourceProfile": {"email": "contract.source@example.com"}}"""
                .formatted(MERGE_SOURCE, MERGE_TARGET), 200);
        contractCall("/internal/user-references/merge", """
                {"sourceUsername": "%s", "targetUsername": "%s", "performedBy": "organizer.alice",
                 "trigger": "SELF_CLAIM", "sourceProfile": null}""".formatted(MERGE_SOURCE, MERGE_TARGET), 200);
        contractCall("/internal/user-references/merge", "{\"sourceUsername\": \"x\"}", 400);
        contractCall("/internal/user-references/primary-email", """
                {"username": "%s", "oldEmail": "old@example.com", "newEmail": "new@example.com"}"""
                .formatted(MERGE_TARGET), 200);
        contractCall("/internal/user-references/primary-email", "{\"username\": \"x\"}", 400);
    }

    @Test
    @DisplayName("merge with both profiles leading a talk at one event: 409 MERGE_BLOCKED, as the CUMS contract says")
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

        MvcResult preview = contractGet(MERGE_SOURCE, MERGE_TARGET, 200);
        assertThat(preview.getResponse().getContentAsString()).contains(CODE);
        MvcResult result = contractCall("/internal/user-references/merge", """
                {"sourceUsername": "%s", "targetUsername": "%s", "performedBy": "organizer.alice",
                 "trigger": "ORGANIZER"}""".formatted(MERGE_SOURCE, MERGE_TARGET), 409);

        assertThat(result.getResponse().getContentAsString()).contains("MERGE_BLOCKED").contains(CODE);
    }

    @ParameterizedTest(name = "forwarded JWT: {0}")
    @CsvSource({"true", "false"})
    @DisplayName("Mail Center request contract: opened and decided, as CUMS calls them, with and without the user's JWT")
    void mailCenterRequestContract_matchesCumsSpec(boolean user) throws Exception {
        String opened = """
                {"kind": "%s", "requestId": "%s", "requesterName": "Anna Meier",
                 "requesterEmail": "anna.meier@example.com", "subject": "Profil zusammenführen: Anna Meier",
                 "note": null}""";
        String decided = """
                {"kind": "%s", "status": "APPROVED", "decidedBy": "organizer.alice",
                 "decidedAt": "%s"}""";
        if (user) {
            mockMvc.perform(postJson("/internal/mail-center-requests", opened.formatted("MERGE", MERGE_REQUEST))
                            .with(jwt().jwt(j -> j.claim("custom:username", "anna.meier")
                                    .claim("custom:role", "ATTENDEE"))))
                    .andExpect(status().isNoContent());
        }
        contractCall("/internal/mail-center-requests", opened.formatted("MERGE", MERGE_REQUEST), 204);
        contractCall("/internal/mail-center-requests", opened.formatted("LOGO", LOGO_REQUEST), 204);
        contractCall("/internal/mail-center-requests/{requestId}/decision",
                decided.formatted("MERGE", Instant.now()), 204, MERGE_REQUEST);
        contractCall("/internal/mail-center-requests/{requestId}/decision",
                decided.formatted("LOGO", Instant.now()), 204, LOGO_REQUEST);
        contractCall("/internal/mail-center-requests", "{\"requestId\": \"%s\"}".formatted(MERGE_REQUEST), 400);
        contractCall("/internal/mail-center-requests/{requestId}/decision", "{\"kind\": \"MERGE\"}", 400,
                MERGE_REQUEST);

        assertThat(jdbc.queryForList("SELECT status FROM mail_conversation WHERE merge_request_id = ?"
                + " OR logo_request_id = ?", String.class, MERGE_REQUEST, LOGO_REQUEST)).containsExactly("DONE", "DONE");
    }
}
