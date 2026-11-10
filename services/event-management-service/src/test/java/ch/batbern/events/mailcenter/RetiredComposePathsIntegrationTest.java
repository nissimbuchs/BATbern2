package ch.batbern.events.mailcenter;

import ch.batbern.events.core.dto.generated.EventType;
import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.NewsletterSend;
import ch.batbern.events.domain.SpeakerPool;
import ch.batbern.events.dto.venuecoordination.VenueCoordinationConfig;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.repository.NewsletterSendRepository;
import ch.batbern.events.repository.SpeakerPoolRepository;
import ch.batbern.events.service.AdminSettingsService;
import ch.batbern.shared.test.AbstractIntegrationTest;
import ch.batbern.shared.test.mail.SesRecorder;
import ch.batbern.shared.types.EventWorkflowState;
import ch.batbern.shared.types.SpeakerWorkflowState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Story 16.8c: the per-audience compose endpoints that the Mail Center composer replaced are gone.
 * Each answers 404 for an organizer, on an event that exists, with a body that the old endpoint
 * accepted; nothing is sent and no send row is written. Every send goes through
 * {@code /events/{eventCode}/mails}.
 */
@DisplayName("Retired compose paths answer 404 (Story 16.8c)")
class RetiredComposePathsIntegrationTest extends AbstractIntegrationTest {

    private static final String EVENT_CODE = "BATbern924";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private EventRepository eventRepository;
    @Autowired
    private SpeakerPoolRepository speakerPoolRepository;
    @Autowired
    private NewsletterSendRepository sendRepository;
    @Autowired
    private AdminSettingsService adminSettingsService;
    @Autowired
    private SesRecorder sesRecorder;
    @Autowired
    private JdbcTemplate jdbc;

    private Event event;
    private SpeakerPool speaker;
    private NewsletterSend send;

    @BeforeEach
    void setUp() {
        cleanUp();
        sesRecorder.reset();
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        event = eventRepository.saveAndFlush(Event.builder()
                .eventCode(EVENT_CODE).title("BATbern Test Event #924").eventNumber(924)
                .date(now.plus(30, ChronoUnit.DAYS)).registrationDeadline(now.plus(29, ChronoUnit.DAYS))
                .venueName("Test Venue").venueAddress("Bern").venueCapacity(200)
                .organizerUsername("organizer1").currentAttendeeCount(0)
                .eventType(EventType.EVENING).workflowState(EventWorkflowState.AGENDA_PUBLISHED)
                .build());
        speaker = speakerPoolRepository.saveAndFlush(SpeakerPool.builder().eventId(event.getId())
                .speakerName("Ines Probe").status(SpeakerWorkflowState.INVITED).remindersDisabled(false)
                .responseDeadline(LocalDate.now().plusDays(7)).build());
        send = sendRepository.saveAndFlush(NewsletterSend.builder().eventId(event.getId())
                .templateKey("newsletter-event").reminder(false).locale("de").sentAt(now)
                .sentByUsername("organizer1").recipientCount(1).sentCount(0).failedCount(1)
                .status("FAILED").testMode(false).build());
        adminSettingsService.setSetting(VenueCoordinationConfig.SETTING_KEY, """
                {"venue":{"salutation":"Frau","name":"Vera Venue","email":"success+venue924@simulator.amazonses.com"},
                 "catering":{"salutation":"Herr","name":"Carl Catering",
                             "email":"success+catering924@simulator.amazonses.com"},
                 "coordinatorUsername":"olga.organizer"}
                """, "organizer1");
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM newsletter_recipients WHERE send_id IN (SELECT s.id FROM newsletter_sends s"
                + " JOIN events e ON e.id = s.event_id WHERE e.event_code = ?)", EVENT_CODE);
        jdbc.update("DELETE FROM newsletter_sends WHERE event_id IN (SELECT id FROM events WHERE event_code = ?)",
                EVENT_CODE);
        jdbc.update("DELETE FROM speaker_reminder_log WHERE event_id IN (SELECT id FROM events WHERE event_code = ?)",
                EVENT_CODE);
        jdbc.update("DELETE FROM speaker_outreach_history WHERE speaker_pool_id IN (SELECT sp.id FROM speaker_pool sp"
                + " JOIN events e ON e.id = sp.event_id WHERE e.event_code = ?)", EVENT_CODE);
        jdbc.update("DELETE FROM speaker_pool WHERE event_id IN (SELECT id FROM events WHERE event_code = ?)",
                EVENT_CODE);
        jdbc.update("DELETE FROM events WHERE event_code = ?", EVENT_CODE);
        jdbc.update("DELETE FROM app_settings WHERE setting_key = ?", VenueCoordinationConfig.SETTING_KEY);
    }

    @ParameterizedTest(name = "{0} {1} → 404")
    @WithMockUser(username = "organizer1", roles = "ORGANIZER")
    @CsvSource(delimiter = '|', textBlock = """
            POST | /newsletter/send                   | {"isReminder":false,"locale":"de"}
            POST | /newsletter/preview                | {"isReminder":false,"locale":"de"}
            GET  | /newsletter/history                |
            GET  | /newsletter/sends/{send}/status    |
            POST | /newsletter/sends/{send}/retry     |
            POST | /registrant-notices/preview        | {"templateKey":"slides-online","locale":"de"}
            POST | /registrant-notices/send           | {"templateKey":"slides-online"}
            POST | /venue-coordination/preview        | {"templateKey":"venue-timetable","recipients":["VENUE"],"locale":"de"}
            POST | /venue-coordination/send           | {"templateKey":"venue-timetable","recipients":["VENUE"],"locale":"de"}
            POST | /speaker-pool/{speaker}/send-reminder | {"reminderType":"RESPONSE"}
            """)
    void retiredPath_answers404(String method, String path, String body) throws Exception {
        String url = "/api/v1/events/" + EVENT_CODE + path
                .replace("{send}", send.getId().toString())
                .replace("{speaker}", speaker.getId().toString());
        var call = request(HttpMethod.valueOf(method), url);
        if (body != null) {
            call.contentType(MediaType.APPLICATION_JSON).content(body);
        }

        mockMvc.perform(call).andExpect(status().isNotFound());

        sesRecorder.awaitAsyncSends();
        assertThat(sesRecorder.mails()).isEmpty();
        assertThat(sendRepository.findAll()).filteredOn(s -> s.getEventId().equals(event.getId()))
                .singleElement().satisfies(s -> assertThat(s.getStatus()).isEqualTo("FAILED"));
    }

    @Test
    @WithMockUser(username = "organizer1", roles = "ORGANIZER")
    @DisplayName("Positive control: the composer, which replaces them, answers on the same event")
    void composerPreview_answers200() throws Exception {
        mockMvc.perform(request(HttpMethod.POST, "/api/v1/events/" + EVENT_CODE + "/mails/preview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"audience\":\"VENUE\",\"templateKey\":\"venue-timetable\",\"locale\":\"de\","
                                + "\"venueRecipients\":[\"VENUE\"]}"))
                .andExpect(status().isOk());
    }
}
