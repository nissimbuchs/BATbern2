package ch.batbern.events.service;

import ch.batbern.events.config.CumsContractHarness;
import ch.batbern.events.config.TestAwsConfig;
import ch.batbern.events.config.TestSecurityConfig;
import ch.batbern.events.core.dto.generated.EventType;
import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.Session;
import ch.batbern.events.domain.SessionUser;
import ch.batbern.events.domain.SpeakerPool;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.repository.SessionRepository;
import ch.batbern.events.repository.SessionUserRepository;
import ch.batbern.events.repository.SpeakerPoolRepository;
import ch.batbern.shared.mail.MailTrigger;
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
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Manual speaker reminders (Mail Center composer, speakers audience) against real PostgreSQL, the
 * real {@code PrimarySpeakerResolver} over {@code session_users}, CUMS behind the
 * {@link CumsContractHarness} and SES as the {@link SesRecorder}. Replaces the former mock-verify unit
 * tests of {@code SpeakerReminderService.sendManualReminders} (Story 16.8c touch rule): the speaker
 * receives the mail, the reminder log and the outreach history name the organizer, and a speaker
 * with reminders turned off is not mailed. Not {@code @Transactional}: the class removes its rows.
 */
@Import({TestSecurityConfig.class, TestAwsConfig.class})
@DisplayName("Manual speaker reminders (Story 6.5, 16.8)")
class ManualSpeakerReminderIntegrationTest extends AbstractIntegrationTest {

    private static final String EVENT_CODE = "BATbern762";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private SpeakerReminderService speakerReminderService;
    @Autowired
    private EventRepository eventRepository;
    @Autowired
    private SessionRepository sessionRepository;
    @Autowired
    private SessionUserRepository sessionUserRepository;
    @Autowired
    private SpeakerPoolRepository speakerPoolRepository;
    @Autowired
    private CumsContractHarness cums;
    @Autowired
    private SesRecorder sesRecorder;
    @Autowired
    private JdbcTemplate jdbc;

    private Event event;

    @BeforeEach
    void setUp() {
        cleanUp();
        sesRecorder.reset();
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        event = eventRepository.saveAndFlush(Event.builder()
                .eventCode(EVENT_CODE).title("Reminder Test Event").eventNumber(762)
                .date(now.plus(40, ChronoUnit.DAYS)).registrationDeadline(now.plus(39, ChronoUnit.DAYS))
                .venueName("Test Venue").venueAddress("Bern").venueCapacity(100)
                .organizerUsername("org.user").currentAttendeeCount(0)
                .eventType(EventType.EVENING).workflowState(EventWorkflowState.SPEAKER_IDENTIFICATION)
                .build());
    }

    @AfterEach
    void cleanUp() {
        sesRecorder.awaitAsyncSends();
        cums.reset();
        String ofEvent = "(SELECT id FROM events WHERE event_code = ?)";
        jdbc.update("DELETE FROM mail_message WHERE event_code = ?", EVENT_CODE);
        jdbc.update("DELETE FROM speaker_reminder_log WHERE event_id IN " + ofEvent, EVENT_CODE);
        jdbc.update("DELETE FROM speaker_outreach_history WHERE speaker_pool_id IN (SELECT id FROM speaker_pool"
                + " WHERE event_id IN " + ofEvent + ")", EVENT_CODE);
        jdbc.update("DELETE FROM speaker_pool WHERE event_id IN " + ofEvent, EVENT_CODE);
        jdbc.update("DELETE FROM session_users WHERE session_id IN (SELECT id FROM sessions WHERE event_id IN "
                + ofEvent + ")", EVENT_CODE);
        jdbc.update("DELETE FROM sessions WHERE event_id IN " + ofEvent, EVENT_CODE);
        jdbc.update("DELETE FROM events WHERE event_code = ?", EVENT_CODE);
    }

    @Test
    @WithMockUser(username = "org.user", roles = "ORGANIZER")
    @DisplayName("a chosen tier template reaches the speaker; log and outreach name the organizer (manual_email)")
    void composerTemplate_mailsSpeaker_andLogsTierAndOutreach() throws Exception {
        SpeakerPool speaker = invitedSpeaker("ines.probe", "Ines", false, LocalDate.now().plusDays(14));

        mockMvc.perform(post("/api/v1/events/{eventCode}/mails", EVENT_CODE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"audience\":\"SPEAKERS\",\"templateKey\":\"speaker-reminder-response-tier2\","
                                + "\"target\":\"RECIPIENTS\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.recipientCount").value(1));

        sesRecorder.awaitAsyncSends();
        assertThat(sesRecorder.mailsTo(sim("ines.probe"))).hasSize(1);
        assertThat(reminderLog(speaker)).singleElement().satisfies(row -> {
            assertThat(row.get("tier")).isEqualTo("TIER_2");
            assertThat(row.get("reminder_type")).isEqualTo("RESPONSE");
            assertThat(row.get("triggered_by")).isEqualTo("org.user");
            assertThat(row.get("email_address")).isEqualTo(sim("ines.probe"));
        });
        assertThat(jdbc.queryForList("SELECT contact_method, organizer_username FROM speaker_outreach_history"
                + " WHERE speaker_pool_id = ?", speaker.getId())).singleElement().satisfies(row -> {
                    assertThat(row.get("contact_method")).isEqualTo("manual_email");
                    assertThat(row.get("organizer_username")).isEqualTo("org.user");
                });
    }

    @Test
    @DisplayName("automatic tier: each speaker gets the tier of their own deadline")
    void automaticTier_followsEachSpeakersDeadline() {
        SpeakerPool near = invitedSpeaker("nora.near", "Nora", false, LocalDate.now().plusDays(2));
        SpeakerPool far = invitedSpeaker("fritz.far", "Fritz", false, LocalDate.now().plusDays(13));

        List<String> keys = speakerReminderService.sendManualReminders(event, "RESPONSE", null,
                List.of(near, far), MailTrigger.organizer("org.user"), "org.user");

        sesRecorder.awaitAsyncSends();
        assertThat(keys).containsExactlyInAnyOrder("speaker-reminder-response-tier3",
                "speaker-reminder-response-tier1");
        assertThat(reminderLog(near)).extracting(row -> row.get("tier")).containsExactly("TIER_3");
        assertThat(reminderLog(far)).extracting(row -> row.get("tier")).containsExactly("TIER_1");
        assertThat(sesRecorder.mailsTo(sim("nora.near"))).hasSize(1);
        assertThat(sesRecorder.mailsTo(sim("fritz.far"))).hasSize(1);
    }

    @Test
    @DisplayName("a speaker with reminders turned off is not mailed nor logged; positive control: the other one is")
    void remindersDisabled_notMailed() {
        SpeakerPool muted = invitedSpeaker("mia.muted", "Mia", true, LocalDate.now().plusDays(14));
        SpeakerPool active = invitedSpeaker("anna.active", "Anna", false, LocalDate.now().plusDays(14));

        speakerReminderService.sendManualReminders(event, "RESPONSE", "TIER_1", List.of(muted, active),
                MailTrigger.organizer("org.user"), "org.user");

        sesRecorder.awaitAsyncSends();
        assertThat(sesRecorder.mailsTo(sim("anna.active"))).hasSize(1);
        assertThat(reminderLog(active)).hasSize(1);
        assertThat(sesRecorder.mailsTo(sim("mia.muted"))).isEmpty();
        assertThat(reminderLog(muted)).isEmpty();
    }

    /** An INVITED speaker with a session whose PRIMARY_SPEAKER is {@code username}, known to CUMS. */
    private SpeakerPool invitedSpeaker(String username, String firstName, boolean remindersDisabled,
            LocalDate responseDeadline) {
        Session session = sessionRepository.saveAndFlush(Session.builder()
                .eventId(event.getId()).eventCode(EVENT_CODE).sessionSlug("talk-" + username.replace('.', '-'))
                .title("Talk of " + firstName).sessionType("presentation").build());
        sessionUserRepository.saveAndFlush(SessionUser.builder().session(session).username(username)
                .speakerRole(SessionUser.SpeakerRole.PRIMARY_SPEAKER).isConfirmed(false).build());
        cums.user(username, firstName, "Probe", sim(username));
        return speakerPoolRepository.saveAndFlush(SpeakerPool.builder().eventId(event.getId())
                .speakerName(firstName + " Probe").status(SpeakerWorkflowState.INVITED)
                .responseDeadline(responseDeadline).remindersDisabled(remindersDisabled)
                .sessionId(session.getId()).build());
    }

    private List<Map<String, Object>> reminderLog(SpeakerPool speaker) {
        return jdbc.queryForList("SELECT tier, reminder_type, triggered_by, email_address FROM speaker_reminder_log"
                + " WHERE speaker_pool_id = ?", speaker.getId());
    }

    /** A class-scoped address of the SES mailbox simulator (nothing is delivered in tests). */
    private static String sim(String username) {
        return "success+rem762-" + username.replace('.', '-') + "@simulator.amazonses.com";
    }
}
