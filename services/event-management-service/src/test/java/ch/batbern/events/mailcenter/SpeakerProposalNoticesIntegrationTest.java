package ch.batbern.events.mailcenter;

import ch.batbern.events.config.CumsContractHarness;
import ch.batbern.events.domain.Event;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.repository.SessionProposalRepository;
import ch.batbern.events.repository.SpeakerPoolRepository;
import ch.batbern.shared.test.AbstractIntegrationTest;
import ch.batbern.shared.test.mail.SesRecorder;
import ch.batbern.shared.types.EventWorkflowState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A speaker proposal ("I could speak on that") is confirmed to the person who sent it and lands in
 * the Mail Center: an open conversation on the event with the proposed talk, and the new-mail notice
 * to every organizer. Before, nobody got a mail and the proposal waited unseen in the speaker pool.
 * Runs committed (no class-level transaction): the notices follow the commit of the proposal.
 */
@DisplayName("Speaker proposal: confirmation and Mail Center")
class SpeakerProposalNoticesIntegrationTest extends AbstractIntegrationTest {

    private static final String EVENT_CODE = "BATbern951";
    private static final String PROPOSER = "anna.bruennimann";
    private static final String PROPOSER_ADDRESS = "success+anna.proposal@simulator.amazonses.com";
    private static final String ORGANIZER = "olga.organizer";
    private static final String ORGANIZER_ADDRESS = "success+olga.proposal@simulator.amazonses.com";
    private static final String TITLE = "FinOps zwischen Cloud und KI";
    private static final String BODY = """
            { "sessionTitle": "%s",
              "abstract": "Wie Kosten sichtbar werden, bevor die Rechnung kommt." }
            """.formatted(TITLE);

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private CumsContractHarness cums;
    @Autowired
    private SesRecorder sesRecorder;
    @Autowired
    private EventRepository eventRepository;
    @Autowired
    private SpeakerPoolRepository speakerPoolRepository;
    @Autowired
    private SessionProposalRepository sessionProposalRepository;
    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        sesRecorder.reset();
        cleanUp();
        saveEvent();
        cums.usersWithRole("ORGANIZER", ORGANIZER);
        cums.userWithSettings(ORGANIZER, ORGANIZER_ADDRESS, Map.of("language", "de"));
    }

    @AfterEach
    void tearDown() {
        sesRecorder.awaitAsyncSends();
        cums.reset();
        cleanUp();
    }

    private void proposerSpeaks(String language) {
        cums.user(PROPOSER, """
                {"firstName": "Anna", "lastName": "Brünnimann", "email": "%s",
                 "settings": {"theme": "auto", "language": "%s", "timezone": "Europe/Zurich",
                   "emailNotifications": true, "qnaNotificationFrequency": "live", "quietHoursStart": null,
                   "quietHoursEnd": null, "profileVisibility": "public", "showEmail": false,
                   "showCompany": true, "showActivityHistory": true, "allowMessaging": true,
                   "allowCalendarSync": false, "twoFactorEnabled": false}}
                """.formatted(PROPOSER_ADDRESS, language));
    }

    @Test
    void should_confirmToProposer_and_openConversation_and_noticeOrganizers_when_proposalSubmitted()
            throws Exception {
        proposerSpeaks("de");

        propose().andExpect(status().isCreated());

        awaitMailTo(PROPOSER_ADDRESS);
        var confirmation = sesRecorder.mailsTo(PROPOSER_ADDRESS);
        assertThat(confirmation).singleElement().satisfies(mail -> {
            assertThat(mail.subject()).contains("BATbern951").contains(TITLE);
            assertThat(mail.html()).contains("Anna").contains(TITLE).contains("Vorschlag")
                    .doesNotContain("{{");
        });

        Map<String, Object> conversation = conversationOfEvent();
        assertThat(conversation.get("status")).isEqualTo("OPEN");
        assertThat(conversation.get("counterpart_address")).isEqualTo(PROPOSER_ADDRESS);
        assertThat(conversation.get("counterpart_name")).isEqualTo("Anna Brünnimann");
        assertThat((String) conversation.get("subject")).contains(TITLE);
        String body = jdbc.queryForObject("SELECT body_html FROM mail_message WHERE conversation_id = :id"
                        + " AND direction = 'IN'", new MapSqlParameterSource("id", conversation.get("id")),
                String.class);
        assertThat(body).contains(TITLE).contains("Wie Kosten sichtbar werden");

        String token = jdbc.queryForObject("SELECT reply_token FROM mail_conversation WHERE id = :id",
                new MapSqlParameterSource("id", conversation.get("id")), String.class);
        assertThat(confirmation.get(0).replyTo()).singleElement().asString().startsWith("replies+" + token + "@");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM mail_message WHERE conversation_id = :id"
                        + " AND direction = 'OUT' AND template_key = 'mailcenter-speaker-proposal'",
                new MapSqlParameterSource("id", conversation.get("id")), Integer.class))
                .as("the confirmation is filed in the conversation").isEqualTo(1);

        awaitMailTo(ORGANIZER_ADDRESS);
        assertThat(sesRecorder.mailsTo(ORGANIZER_ADDRESS)).singleElement().satisfies(mail -> {
            assertThat(mail.subject()).contains("von Anna Brünnimann: ").contains(TITLE).doesNotContain("?");
            assertThat(mail.html()).contains((String) conversation.get("conversation_key"));
        });
    }

    @Test
    void should_confirmInEnglish_when_proposerPrefersEnglish() throws Exception {
        proposerSpeaks("en");

        propose().andExpect(status().isCreated());

        awaitMailTo(PROPOSER_ADDRESS);
        assertThat(sesRecorder.mailsTo(PROPOSER_ADDRESS)).singleElement().satisfies(mail -> {
            assertThat(mail.subject()).contains(TITLE);
            assertThat(mail.html()).contains("proposal").doesNotContain("Vorschlag");
        });
    }

    @Test
    void should_sendNothingMore_when_theSameProposerProposesAgain() throws Exception {
        proposerSpeaks("de");
        propose().andExpect(status().isCreated());
        awaitMailTo(PROPOSER_ADDRESS);
        awaitMailTo(ORGANIZER_ADDRESS);
        // positive control: the first proposal was confirmed and announced once
        assertThat(sesRecorder.mailsTo(PROPOSER_ADDRESS)).hasSize(1);
        assertThat(sesRecorder.mailsTo(ORGANIZER_ADDRESS)).hasSize(1);

        propose().andExpect(status().isConflict());

        sesRecorder.awaitAsyncSends();
        assertThat(sesRecorder.mailsTo(PROPOSER_ADDRESS)).hasSize(1);
        assertThat(sesRecorder.mailsTo(ORGANIZER_ADDRESS)).hasSize(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM mail_conversation WHERE event_code = :e",
                new MapSqlParameterSource("e", EVENT_CODE), Integer.class)).isEqualTo(1);
    }

    private org.springframework.test.web.servlet.ResultActions propose() throws Exception {
        return mockMvc.perform(post("/api/v1/events/{eventCode}/speaker-nominations", EVENT_CODE)
                .contentType(MediaType.APPLICATION_JSON).content(BODY)
                .with(user(PROPOSER).roles("ATTENDEE")));
    }

    private void awaitMailTo(String address) {
        sesRecorder.awaitAsyncSends();
        assertThat(sesRecorder.mailsTo(address)).as("a mail to %s", address).isNotEmpty();
    }

    private Map<String, Object> conversationOfEvent() {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT id, conversation_key, status, subject,"
                + " counterpart_address, counterpart_name FROM mail_conversation WHERE event_code = :e",
                new MapSqlParameterSource("e", EVENT_CODE));
        assertThat(rows).hasSize(1);
        return rows.get(0);
    }

    private void cleanUp() {
        MapSqlParameterSource e = new MapSqlParameterSource("e", EVENT_CODE);
        jdbc.update("DELETE FROM mail_message WHERE event_code = :e"
                + " OR conversation_id IN (SELECT id FROM mail_conversation WHERE event_code = :e)", e);
        jdbc.update("DELETE FROM mail_conversation WHERE event_code = :e", e);
        eventRepository.findByEventCode(EVENT_CODE).ifPresent(event -> {
            sessionProposalRepository.deleteAll(sessionProposalRepository.findAll().stream()
                    .filter(p -> p.getEventId().equals(event.getId())).toList());
            speakerPoolRepository.deleteAll(speakerPoolRepository.findAll().stream()
                    .filter(p -> p.getEventId().equals(event.getId())).toList());
            eventRepository.delete(event);
        });
    }

    private void saveEvent() {
        Event event = new Event();
        event.setEventCode(EVENT_CODE);
        event.setEventNumber(951);
        event.setTitle("FinOps im Spannungsfeld");
        event.setDate(Instant.now().plus(60, ChronoUnit.DAYS));
        event.setRegistrationDeadline(Instant.now().plus(50, ChronoUnit.DAYS));
        event.setVenueName("Test Venue");
        event.setVenueAddress("Test Address");
        event.setVenueCapacity(150);
        event.setOrganizerUsername(ORGANIZER);
        event.setEventType(ch.batbern.events.core.dto.generated.EventType.EVENING);
        event.setWorkflowState(EventWorkflowState.SPEAKER_IDENTIFICATION);
        event.setTopicCode("finops");
        event.setCurrentPublishedPhase("topic");
        event.setPublishedAt(Instant.now().minus(1, ChronoUnit.DAYS));
        event.setCreatedAt(Instant.now());
        event.setUpdatedAt(Instant.now());
        event.setCreatedBy(ORGANIZER);
        event.setUpdatedBy(ORGANIZER);
        eventRepository.save(event);
    }
}
