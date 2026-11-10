package ch.batbern.events.controller;

import ch.batbern.events.config.CumsContractHarness;
import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.Session;
import ch.batbern.events.domain.SessionUser;
import ch.batbern.events.domain.SpeakerPool;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.repository.SessionRepository;
import ch.batbern.events.repository.SessionUserRepository;
import ch.batbern.events.repository.SpeakerPoolRepository;
import ch.batbern.shared.test.AbstractIntegrationTest;
import ch.batbern.shared.test.contract.ContractServer.Reply;
import ch.batbern.shared.test.mail.SesRecorder;
import ch.batbern.shared.types.EventWorkflowState;
import ch.batbern.shared.types.SpeakerWorkflowState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Story 4.10, ADR-003 amendment: a speaker pool entry is an owned child of its event. Every
 * operation under {@code /events/{eventCode}/speakers/{speakerId}} loads the entry scoped to the
 * event in the path ({@code findByIdAndEventId}), so the id of another event's entry answers 404
 * and nothing changes on that event: not its row, not its history, no mail.
 *
 * <p>Commits like production (no class-level {@code @Transactional}); {@code @AfterEach} removes
 * the two events and everything hanging off them.
 */
@DisplayName("Story 4.10: speaker operations are scoped to the event in the path")
class SpeakerCrossEventScopingIntegrationTest extends AbstractIntegrationTest {

    private static final String THIS_EVENT = "BATbern9601";
    private static final String OTHER_EVENT = "BATbern9602";
    private static final String OTHER_SPEAKER_USERNAME = "hongan.nguyen";
    private static final String OTHER_SPEAKER_EMAIL = "success+hongan@simulator.amazonses.com";

    /** Paths of every speaker operation keyed by {@code {speakerId}}. */
    static final String SPEAKER = "/api/v1/events/{code}/speakers/{id}";
    static final String ENTRY = "/api/v1/events/{code}/speakers/pool/{id}";
    static final String REMINDERS = "/api/v1/events/{code}/speaker-pool/{id}/reminders";
    static final String PROMOTE = SPEAKER + "/promote";
    static final String STATUS = SPEAKER + "/status";
    static final String STATUS_HISTORY = SPEAKER + "/status/history";
    static final String CONTENT = SPEAKER + "/content";
    static final String REVIEW = SPEAKER + "/review";
    static final String OUTREACH = SPEAKER + "/outreach";
    static final String SEND_INVITATION = SPEAKER + "/send-invitation";
    static final String ABSTRACT_ANALYSIS = SPEAKER + "/abstract-analysis";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private SpeakerPoolRepository speakerPoolRepository;

    @Autowired
    private SessionRepository sessionRepository;

    @Autowired
    private SessionUserRepository sessionUserRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private SesRecorder sesRecorder;

    @Autowired
    private CumsContractHarness cums;

    private Event otherEvent;
    private SpeakerPool otherSpeaker;
    private Map<String, Object> otherSpeakerBefore;

    @BeforeEach
    void setUp() {
        cleanUp();
        sesRecorder.reset();
        cums.reset();
        eventRepository.save(event(THIS_EVENT, 9601));
        otherEvent = eventRepository.save(event(OTHER_EVENT, 9602));

        // The other event's speaker is READY with a primary speaker, so every operation would
        // find something to change on it (status, notes, deadlines, reminders, outreach, mail).
        otherSpeaker = speakerPoolRepository.save(SpeakerPool.builder()
                .eventId(otherEvent.getId())
                .speakerName("Hông Ân Nguyễn")
                .company("Brünnimann AG")
                .notes("Original notes")
                .status(SpeakerWorkflowState.READY)
                .build());
        Session session = sessionRepository.save(Session.builder()
                .eventId(otherEvent.getId())
                .eventCode(OTHER_EVENT)
                .title("Event-driven architecture in practice")
                .sessionSlug("cross-event-" + UUID.randomUUID().toString().substring(0, 8))
                .sessionType("presentation")
                .speakerPoolId(otherSpeaker.getId())
                .build());
        sessionUserRepository.save(SessionUser.builder()
                .session(session)
                .username(OTHER_SPEAKER_USERNAME)
                .speakerRole(SessionUser.SpeakerRole.PRIMARY_SPEAKER)
                .isConfirmed(false)
                .build());
        otherSpeaker.setSessionId(session.getId());
        otherSpeaker = speakerPoolRepository.save(otherSpeaker);
        cums.user(OTHER_SPEAKER_USERNAME, "Hông Ân", "Nguyễn", OTHER_SPEAKER_EMAIL);
        cums.answer(HttpMethod.POST, "/internal/users/{username}/issue-invitation-credentials",
                call -> Reply.ok("{\"temporaryPassword\": \"TestTemp123!abcde\", "
                        + "\"action\": \"FRESH_TEMP_PASSWORD\"}"));

        otherSpeakerBefore = snapshot();
    }

    @AfterEach
    void tearDown() {
        cums.reset();
        cleanUp();
    }

    private static Event event(String code, int number) {
        return Event.builder()
                .eventCode(code)
                .eventNumber(number)
                .title("Cross-event scoping " + code)
                .date(Instant.now().plus(60, ChronoUnit.DAYS))
                .registrationDeadline(Instant.now().plus(50, ChronoUnit.DAYS))
                .venueName("Kornhausforum")
                .venueAddress("Kornhausplatz 18, 3011 Bern")
                .venueCapacity(200)
                .eventType(ch.batbern.events.core.dto.generated.EventType.EVENING)
                .workflowState(EventWorkflowState.SPEAKER_IDENTIFICATION)
                .organizerUsername("organizer.test")
                .build();
    }

    private void cleanUp() {
        List<UUID> events = jdbcTemplate.queryForList(
                "SELECT id FROM events WHERE event_code IN (?, ?)", UUID.class, THIS_EVENT, OTHER_EVENT);
        for (UUID eventId : events) {
            jdbcTemplate.update("UPDATE speaker_pool SET session_id = NULL WHERE event_id = ?", eventId);
            jdbcTemplate.update("DELETE FROM speaker_outreach_history WHERE speaker_pool_id IN "
                    + "(SELECT id FROM speaker_pool WHERE event_id = ?)", eventId);
            jdbcTemplate.update("DELETE FROM speaker_status_history WHERE speaker_pool_id IN "
                    + "(SELECT id FROM speaker_pool WHERE event_id = ?)", eventId);
            jdbcTemplate.update("DELETE FROM session_users WHERE session_id IN "
                    + "(SELECT id FROM sessions WHERE event_id = ?)", eventId);
            jdbcTemplate.update("DELETE FROM session_content_history WHERE session_id IN "
                    + "(SELECT id FROM sessions WHERE event_id = ?)", eventId);
            jdbcTemplate.update("DELETE FROM sessions WHERE event_id = ?", eventId);
            jdbcTemplate.update("DELETE FROM speaker_pool WHERE event_id = ?", eventId);
            jdbcTemplate.update("DELETE FROM events WHERE id = ?", eventId);
        }
    }

    /** The other event's speaker as stored, plus its history and outreach row counts. */
    private Map<String, Object> snapshot() {
        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT status, notes, speaker_name, company, reminders_disabled, response_deadline, "
                        + "content_deadline, invited_at, session_id, updated_at FROM speaker_pool WHERE id = ?",
                otherSpeaker.getId());
        row.put("history", jdbcTemplate.queryForObject(
                "SELECT count(*) FROM speaker_status_history WHERE speaker_pool_id = ?",
                Long.class, otherSpeaker.getId()));
        row.put("outreach", jdbcTemplate.queryForObject(
                "SELECT count(*) FROM speaker_outreach_history WHERE speaker_pool_id = ?",
                Long.class, otherSpeaker.getId()));
        row.put("content", jdbcTemplate.queryForObject(
                "SELECT count(*) FROM session_content_history WHERE session_id = ?",
                Long.class, otherSpeaker.getSessionId()));
        return row;
    }

    private static String responseDeadline() {
        return LocalDate.now().plusDays(14).toString();
    }

    /** Every speaker operation keyed by {@code {speakerId}}, with a body it would accept. */
    static Stream<Arguments> speakerOperations() {
        return Stream.of(
                Arguments.of("PATCH entry", HttpMethod.PATCH, ENTRY,
                        "{\"notes\": \"Changed through another event\"}"),
                Arguments.of("reminders", HttpMethod.PATCH, REMINDERS, "{\"remindersDisabled\": true}"),
                Arguments.of("DELETE entry", HttpMethod.DELETE, ENTRY, null),
                Arguments.of("promote", HttpMethod.POST, PROMOTE,
                        "{\"email\": \"success+promoted@simulator.amazonses.com\", "
                                + "\"firstName\": \"Hông Ân\", \"lastName\": \"Nguyễn\"}"),
                Arguments.of("status", HttpMethod.PUT, STATUS,
                        "{\"newStatus\": \"DECLINED\", \"reason\": \"Changed through another event\"}"),
                Arguments.of("status history", HttpMethod.GET, STATUS_HISTORY, null),
                Arguments.of("content GET", HttpMethod.GET, CONTENT, null),
                Arguments.of("content POST", HttpMethod.POST, CONTENT,
                        "{\"presentationTitle\": \"Hijacked title\", "
                                + "\"presentationAbstract\": \"Hijacked abstract\"}"),
                Arguments.of("review", HttpMethod.POST, REVIEW,
                        "{\"action\": \"REJECT\", \"feedback\": \"Changed through another event\"}"),
                Arguments.of("outreach GET", HttpMethod.GET, OUTREACH, null),
                Arguments.of("outreach POST", HttpMethod.POST, OUTREACH,
                        "{\"contactDate\": \"2026-11-10T10:00:00Z\", \"contactMethod\": \"email\", "
                                + "\"notes\": \"Changed through another event\"}"),
                Arguments.of("send-invitation", HttpMethod.POST, SEND_INVITATION,
                        "{\"responseDeadline\": \"" + responseDeadline() + "\", \"locale\": \"de\"}"),
                Arguments.of("abstract-analysis", HttpMethod.POST, ABSTRACT_ANALYSIS, null));
    }

    private MockHttpServletRequestBuilder call(HttpMethod method, String path, String eventCode,
                                               UUID speakerId, String body) {
        MockHttpServletRequestBuilder builder = request(method, path, eventCode, speakerId)
                .with(user("organizer.test").roles("ORGANIZER"));
        return body == null ? builder : builder.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("speakerOperations")
    void should_return404AndChangeNothing_when_poolIdBelongsToAnotherEvent(
            String operation, HttpMethod method, String path, String body) throws Exception {
        mockMvc.perform(call(method, path, THIS_EVENT, otherSpeaker.getId(), body))
                .andExpect(status().isNotFound());

        sesRecorder.awaitAsyncSends();
        assertThat(snapshot()).isEqualTo(otherSpeakerBefore);
        assertThat(sesRecorder.mails()).isEmpty();
    }

    @Test
    void should_actOnTheEntry_when_pathNamesItsOwnEvent() throws Exception {
        // Positive control for the 404s above: the same requests reach the entry through its own
        // event, so the 404 comes from the scoping, not from the request.
        mockMvc.perform(call(HttpMethod.PATCH, ENTRY, OTHER_EVENT, otherSpeaker.getId(),
                        "{\"notes\": \"Changed through its own event\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(call(HttpMethod.PATCH, REMINDERS, OTHER_EVENT, otherSpeaker.getId(),
                        "{\"remindersDisabled\": true}"))
                .andExpect(status().isOk());
        mockMvc.perform(call(HttpMethod.GET, OUTREACH, OTHER_EVENT, otherSpeaker.getId(), null))
                .andExpect(status().isOk());
        mockMvc.perform(call(HttpMethod.POST, SEND_INVITATION, OTHER_EVENT, otherSpeaker.getId(),
                        "{\"responseDeadline\": \"" + responseDeadline() + "\", \"locale\": \"de\"}"))
                .andExpect(status().isOk());

        sesRecorder.awaitAsyncSends();
        Map<String, Object> after = snapshot();
        assertThat(after.get("notes")).isEqualTo("Changed through its own event");
        assertThat(after.get("reminders_disabled")).isEqualTo(true);
        assertThat(after.get("status")).isEqualTo("invited");
        assertThat(sesRecorder.mailsTo(OTHER_SPEAKER_EMAIL)).hasSize(1);
    }
}
