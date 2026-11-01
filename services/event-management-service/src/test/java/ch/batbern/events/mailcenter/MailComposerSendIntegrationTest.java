package ch.batbern.events.mailcenter;

import ch.batbern.events.client.UserApiClient;
import ch.batbern.events.config.TestAwsConfig;
import ch.batbern.events.config.TestSecurityConfig;
import ch.batbern.events.core.dto.generated.EventType;
import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.NewsletterSubscriber;
import ch.batbern.events.domain.Registration;
import ch.batbern.events.domain.SpeakerPool;
import ch.batbern.events.dto.generated.users.UserResponse;
import ch.batbern.events.dto.venuecoordination.VenueCoordinationConfig;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.repository.NewsletterSubscriberRepository;
import ch.batbern.events.repository.RegistrationRepository;
import ch.batbern.events.repository.SpeakerPoolRepository;
import ch.batbern.events.service.AdminSettingsService;
import ch.batbern.events.service.PrimarySpeakerResolver;
import ch.batbern.shared.mail.OutboundMailRecorder;
import ch.batbern.shared.service.EmailService;
import ch.batbern.shared.test.AbstractIntegrationTest;
import ch.batbern.shared.types.EventWorkflowState;
import ch.batbern.shared.types.SpeakerWorkflowState;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Story 16.8a AC3, AC5, AC6 against real PostgreSQL and the real {@link EmailService} (no SES client
 * in tests: every send is recorded, nothing leaves the process, AC17).
 *
 * <p>AC3: for every audience, the preview of recipient i is byte-equal to what was recorded for
 * recipient i after the real send. AC5: a test send reaches every organizer and no recipient.
 *
 * <p>Not {@code @Transactional}: the newsletter, registrant and speaker sends run on {@code @Async}
 * threads and the recorder commits in its own transaction, so this class removes its own rows.
 * Synthetic SES simulator addresses only.
 */
@Import({TestSecurityConfig.class, TestAwsConfig.class})
@DisplayName("Mail composer: preview equals sent, test send (Story 16.8a)")
class MailComposerSendIntegrationTest extends AbstractIntegrationTest {

    private static final String EVENT_CODE = "BATbern922";
    private static final String BASE = "/api/v1/events/" + EVENT_CODE + "/mails";
    private static final String SIM = "@simulator.amazonses.com";
    private static final String ORG1 = "success+org1" + SIM;
    private static final String ORG2 = "success+org2" + SIM;

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private EventRepository eventRepository;
    @Autowired
    private NewsletterSubscriberRepository subscriberRepository;
    @Autowired
    private RegistrationRepository registrationRepository;
    @Autowired
    private SpeakerPoolRepository speakerPoolRepository;
    @Autowired
    private AdminSettingsService adminSettingsService;
    @Autowired
    private JdbcTemplate jdbc;

    @MockitoBean
    private UserApiClient userApiClient;
    @MockitoBean
    private PrimarySpeakerResolver primarySpeakerResolver;
    /**
     * {@code TestAwsConfig} replaces {@link EmailService} with a bare mock. This test needs the real
     * render and record code (no SES client: the local branch records and sends nothing), so the
     * mock calls the real methods and gets the real recorder.
     */
    @MockitoBean(answers = Answers.CALLS_REAL_METHODS)
    private EmailService emailService;
    @Autowired
    private OutboundMailRecorder recorder;

    private Event event;

    @BeforeEach
    void setUp() {
        cleanUp();
        ReflectionTestUtils.setField(emailService, "outboundMailRecorder", recorder);
        ReflectionTestUtils.setField(emailService, "replyToEmail", "replies@batbern.ch");
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        event = eventRepository.saveAndFlush(Event.builder()
                .eventCode(EVENT_CODE).title("BATbern Test Event #922").eventNumber(922)
                .date(now.plus(30, ChronoUnit.DAYS)).registrationDeadline(now.plus(29, ChronoUnit.DAYS))
                .venueName("Test Venue").venueAddress("Bern").venueCapacity(200)
                .organizerUsername("organizer1").currentAttendeeCount(0)
                .eventType(EventType.EVENING).workflowState(EventWorkflowState.AGENDA_PUBLISHED)
                .build());

        lenient().when(userApiClient.getOrganizerUsernames()).thenReturn(List.of("organizer1", "organizer2"));
        lenient().when(userApiClient.getUserByUsername("organizer1"))
                .thenReturn(new UserResponse().email(ORG1).firstName("Olga").lastName("Org"));
        lenient().when(userApiClient.getUserByUsername("organizer2"))
                .thenReturn(new UserResponse().email(ORG2).firstName("Otto").lastName("Org"));
        lenient().when(userApiClient.getPreferredLanguage(anyString())).thenReturn("de");
        lenient().when(userApiClient.getPreferredLanguage("att.en")).thenReturn("en");
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM mail_message WHERE event_code = ?", EVENT_CODE);
        jdbc.update("DELETE FROM newsletter_sends WHERE event_id IN (SELECT id FROM events WHERE event_code = ?)",
                EVENT_CODE);
        jdbc.update("DELETE FROM newsletter_subscribers WHERE email LIKE 'success+sub922%'");
        jdbc.update("DELETE FROM speaker_reminder_log WHERE event_id IN (SELECT id FROM events WHERE event_code = ?)",
                EVENT_CODE);
        jdbc.update("DELETE FROM speaker_outreach_history WHERE speaker_pool_id IN (SELECT sp.id FROM speaker_pool sp"
                + " JOIN events e ON e.id = sp.event_id WHERE e.event_code = ?)", EVENT_CODE);
        jdbc.update("DELETE FROM speaker_pool WHERE event_id IN (SELECT id FROM events WHERE event_code = ?)",
                EVENT_CODE);
        jdbc.update("DELETE FROM registrations WHERE event_id IN (SELECT id FROM events WHERE event_code = ?)",
                EVENT_CODE);
        jdbc.update("DELETE FROM events WHERE event_code = ?", EVENT_CODE);
        jdbc.update("DELETE FROM app_settings WHERE setting_key = ?", VenueCoordinationConfig.SETTING_KEY);
    }

    // ── AC3: preview of recipient i == recorded mail of recipient i ──────────

    @Test
    @WithMockUser(username = "organizer1", roles = "ORGANIZER")
    @DisplayName("AC3/AC6 newsletter: preview equals the recorded mail; the send runs as a newsletter job")
    void newsletter_previewEqualsSent() throws Exception {
        subscriber("success+sub922a" + SIM, "Ada");
        subscriber("success+sub922b" + SIM, "Ben");
        String request = "{\"audience\":\"NEWSLETTER\",\"locale\":\"de\",\"recipientIndex\":1}";

        JsonNode preview = preview(request);
        JsonNode sent = send(request, "RECIPIENTS");

        String mailKey = sent.get("mailKeys").get(0).asText();
        awaitUntil(() -> "COMPLETED".equals(newsletterStatus()));
        assertThat(sent.get("recipientCount").asInt()).isEqualTo(2);
        assertRecordedEquals(mailKey, "success+sub922b" + SIM, preview);
    }

    @Test
    @WithMockUser(username = "organizer1", roles = "ORGANIZER")
    @DisplayName("AC3/AC6 registrants: per-recipient language and text; preview equals the recorded mail")
    void registrants_previewEqualsSent() throws Exception {
        registration("att.de", "success+reg922de" + SIM, "Dora", "confirmed");
        registration("att.en", "success+reg922en" + SIM, "Emil", "registered");
        String request = "{\"audience\":\"REGISTRANTS\",\"templateKey\":\"slides-online\",\"recipientIndex\":%d}";

        JsonNode previewDe = preview(request.formatted(0));
        JsonNode previewEn = preview(request.formatted(1));
        JsonNode sent = send(request.formatted(0), "RECIPIENTS");

        String mailKey = sent.get("mailKeys").get(0).asText();
        awaitUntil(() -> "COMPLETED".equals(newsletterStatus()));
        assertThat(previewEn.get("locale").asText()).isEqualTo("en");
        assertRecordedEquals(mailKey, previewDe.at("/recipient/address").asText(), previewDe);
        assertRecordedEquals(mailKey, previewEn.at("/recipient/address").asText(), previewEn, false);
        assertThat(previewEn.get("subject").asText()).startsWith("The slides are online");
    }

    @Test
    @WithMockUser(username = "organizer1", roles = "ORGANIZER")
    @DisplayName("AC3/AC6/AC7 speakers: one request, one recorded mail per template, preview equals sent")
    void speakers_previewEqualsSent() throws Exception {
        speaker("Anna Speaker", "success+spk922a" + SIM, SpeakerWorkflowState.INVITED, false);
        speaker("Bruno Speaker", "success+spk922b" + SIM, SpeakerWorkflowState.INVITED, false);
        speaker("Carl Muted", "success+spk922c" + SIM, SpeakerWorkflowState.INVITED, true);
        String request = "{\"audience\":\"SPEAKERS\",\"templateKey\":\"speaker-reminder-response-tier2\","
                + "\"recipientIndex\":1}";

        JsonNode preview = preview(request);
        JsonNode sent = send(request, "RECIPIENTS");

        assertThat(preview.get("recipientCount").asInt()).isEqualTo(2);
        assertThat(preview.at("/skipped/0/reason").asText()).isEqualTo("REMINDERS_DISABLED");
        assertThat(sent.get("mailKeys")).hasSize(1);
        String mailKey = sent.get("mailKeys").get(0).asText();
        awaitUntil(() -> recipientCount(mailKey) == 2);
        assertRecordedEquals(mailKey, "success+spk922b" + SIM, preview);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM speaker_reminder_log WHERE event_id = ?",
                Integer.class, event.getId())).isEqualTo(2);
    }

    @Test
    @WithMockUser(username = "organizer1", roles = "ORGANIZER")
    @DisplayName("AC3/AC6 venue: one mail, first role To, the rest Cc; preview equals sent")
    void venue_previewEqualsSent() throws Exception {
        venueConfig();
        String request = "{\"audience\":\"VENUE\",\"templateKey\":\"venue-timetable\",\"locale\":\"de\","
                + "\"venueRecipients\":[\"VENUE\",\"CATERING\"],\"venueNotes\":\"Bitte 20 Stühle\"}";

        JsonNode preview = preview(request);
        JsonNode sent = send(request, "RECIPIENTS");

        assertThat(preview.get("personalized").asBoolean()).isFalse();
        assertThat(preview.at("/recipient/cc/0").asText()).isEqualTo("success+catering922" + SIM);
        String mailKey = sent.get("mailKeys").get(0).asText();
        assertRecordedEquals(mailKey, "success+venue922" + SIM, preview);
        assertThat(jdbc.queryForList("SELECT kind FROM mail_recipient r JOIN mail_message m"
                + " ON m.id = r.mail_message_id WHERE m.mail_key = ? ORDER BY kind DESC", String.class, mailKey))
                .containsExactly("TO", "CC");
    }

    // ── AC5: test send ───────────────────────────────────────────────────────

    @Test
    @WithMockUser(username = "organizer1", roles = "ORGANIZER")
    @DisplayName("AC5 newsletter: every organizer, subscribed or not, [TEST] subject, dummy link, no send row")
    void newsletterTestSend_reachesOrganizersOnly() throws Exception {
        subscriber("success+sub922a" + SIM, "Ada");
        String request = "{\"audience\":\"NEWSLETTER\",\"locale\":\"de\"}";

        JsonNode sent = send(request, "ORGANIZERS");

        String mailKey = sent.get("mailKeys").get(0).asText();
        assertThat(sent.get("recipientCount").asInt()).isEqualTo(2);
        Map<String, Object> message = jdbc.queryForMap(
                "SELECT subject, is_test, sender_username, audience FROM mail_message WHERE mail_key = ?", mailKey);
        assertThat((String) message.get("subject")).startsWith("[TEST] ");
        assertThat(message.get("is_test")).isEqualTo(true);
        assertThat(message.get("sender_username")).isEqualTo("organizer1");
        assertThat(message.get("audience")).isEqualTo("NEWSLETTER");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM newsletter_sends WHERE event_id = ?",
                Integer.class, event.getId())).isZero();

        ArgumentCaptor<String> to = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(emailService, atLeastOnce()).sendHtmlEmailSyncWithAttachments(any(), to.capture(), anyString(),
                body.capture(), any(), any());
        assertThat(to.getAllValues()).containsExactlyInAnyOrder(ORG1, ORG2);
        assertThat(body.getAllValues()).allSatisfy(b -> assertThat(b).doesNotContain("tok-922-"));
        verify(emailService, never()).sendHtmlEmailSync(any(), eq("success+sub922a" + SIM), any(), anyString(),
                anyString(), any(), any());
    }

    @Test
    @WithMockUser(username = "organizer1", roles = "ORGANIZER")
    @DisplayName("AC5 registrants: a test send leaves the one-shot guard untouched; the real send still works")
    void registrantTestSend_doesNotConsumeGuard() throws Exception {
        registration("att.de", "success+reg922de" + SIM, "Dora", "confirmed");
        String request = "{\"audience\":\"REGISTRANTS\",\"templateKey\":\"slides-online\"}";

        send(request, "ORGANIZERS");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM newsletter_sends WHERE event_id = ?",
                Integer.class, event.getId())).isZero();

        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(emailService, atLeastOnce()).sendHtmlEmailSync(any(), eq(ORG1), any(), anyString(), body.capture(),
                any(), any());
        assertThat(body.getValue()).doesNotContain("REG-922-");

        send(request, "RECIPIENTS");
        awaitUntil(() -> "COMPLETED".equals(newsletterStatus()));
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private JsonNode preview(String request) throws Exception {
        String body = mockMvc.perform(post(BASE + "/preview").contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body);
    }

    private JsonNode send(String request, String target) throws Exception {
        String withTarget = request.substring(0, request.length() - 1) + ",\"target\":\"" + target + "\"}";
        String body = mockMvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content(withTarget))
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body);
    }

    /** Recipient body when the mail was personalised, otherwise the mail's own body; subject of the mail. */
    private void assertRecordedEquals(String mailKey, String address, JsonNode preview) {
        assertRecordedEquals(mailKey, address, preview, true);
    }

    /**
     * {@code mail_recipient} keeps a body per recipient but no subject, so the subject can only be
     * compared for recipients whose subject is the mail's (the first recipient's language).
     */
    private void assertRecordedEquals(String mailKey, String address, JsonNode preview, boolean compareSubject) {
        Map<String, Object> message = jdbc.queryForMap(
                "SELECT id, subject, body_html FROM mail_message WHERE mail_key = ?", mailKey);
        String recipientBody = jdbc.queryForList(
                "SELECT body_html FROM mail_recipient WHERE mail_message_id = ? AND address = ?", String.class,
                message.get("id"), address).stream().filter(b -> b != null).findFirst().orElse(null);
        String recorded = Optional.ofNullable(recipientBody).orElse((String) message.get("body_html"));

        assertThat(recorded).contains("<").isNotBlank();
        assertThat(preview.get("html").asText()).isEqualTo(recorded);
        if (compareSubject) {
            assertThat(preview.get("subject").asText()).isEqualTo(message.get("subject"));
        }
    }

    private String newsletterStatus() {
        List<String> statuses = jdbc.queryForList("SELECT status FROM newsletter_sends WHERE event_id = ?",
                String.class, event.getId());
        return statuses.isEmpty() ? null : statuses.get(0);
    }

    private int recipientCount(String mailKey) {
        return jdbc.queryForObject("SELECT recipient_count FROM mail_message WHERE mail_key = ?", Integer.class,
                mailKey);
    }

    private static void awaitUntil(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos();
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() > deadline) {
                throw new AssertionError("condition not reached within 20 s");
            }
            Thread.sleep(100);
        }
    }

    private void subscriber(String email, String firstName) {
        subscriberRepository.saveAndFlush(NewsletterSubscriber.builder().email(email).firstName(firstName)
                .language("de").source("explicit").unsubscribeToken("tok-922-" + UUID.randomUUID()).build());
    }

    private void registration(String username, String email, String firstName, String status) {
        registrationRepository.saveAndFlush(Registration.builder()
                .registrationCode("REG-922-" + UUID.randomUUID()).eventId(event.getId())
                .attendeeUsername(username).attendeeEmail(email).attendeeFirstName(firstName)
                .attendeeLastName("Example").status(status).registrationDate(Instant.now()).build());
    }

    private void speaker(String name, String email, SpeakerWorkflowState state, boolean remindersDisabled) {
        SpeakerPool pool = speakerPoolRepository.saveAndFlush(SpeakerPool.builder().eventId(event.getId())
                .speakerName(name).status(state).remindersDisabled(remindersDisabled)
                .responseDeadline(LocalDate.now().plusDays(7)).build());
        String[] parts = name.split(" ");
        PrimarySpeakerResolver.PrimarySpeakerProfile profile = new PrimarySpeakerResolver.PrimarySpeakerProfile(
                parts[0].toLowerCase(), email, parts[0], parts[1], null, List.of());
        lenient().when(primarySpeakerResolver.resolve(org.mockito.ArgumentMatchers.argThat(
                p -> p != null && pool.getId().equals(p.getId())))).thenReturn(Optional.of(profile));
        lenient().when(primarySpeakerResolver.resolveEmail(org.mockito.ArgumentMatchers.argThat(
                p -> p != null && pool.getId().equals(p.getId())))).thenReturn(Optional.of(email));
    }

    private void venueConfig() {
        adminSettingsService.setSetting(VenueCoordinationConfig.SETTING_KEY, """
                {"venue":{"salutation":"Frau","name":"Vera Venue","email":"success+venue922@simulator.amazonses.com"},
                 "catering":{"salutation":"Herr","name":"Kurt Koch","email":"success+catering922@simulator.amazonses.com"},
                 "coordinatorUsername":"organizer1"}
                """, "test");
    }
}
