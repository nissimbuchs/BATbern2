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
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

/**
 * Story 16.5 against real PostgreSQL and the real render and record code (no SES client in tests:
 * nothing leaves the process, AC15). Own text per audience: preview i equals the recorded mail i
 * (AC4), recorded as OWN_TEXT without a template key (AC5), unknown placeholders refused (AC2), the
 * newsletter footer (AC3), the registrant guard (AC9), speakers by status (AC11, AC12), limits (AC13).
 * Not {@code @Transactional}: the newsletter and registrant sends run on {@code @Async} threads.
 */
@Import({TestSecurityConfig.class, TestAwsConfig.class})
@DisplayName("Mail composer: own text (Story 16.5)")
class OwnTextComposerIntegrationTest extends AbstractIntegrationTest {

    private static final String EVENT_CODE = "BATbern923";
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
                .eventCode(EVENT_CODE).title("BATbern Test Event #923").eventNumber(923)
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
        jdbc.update("DELETE FROM newsletter_recipients WHERE send_id IN (SELECT s.id FROM newsletter_sends s"
                + " JOIN events e ON e.id = s.event_id WHERE e.event_code = ?)", EVENT_CODE);
        jdbc.update("DELETE FROM newsletter_sends WHERE event_id IN (SELECT id FROM events WHERE event_code = ?)",
                EVENT_CODE);
        jdbc.update("DELETE FROM newsletter_subscribers WHERE email LIKE 'success+sub923%'");
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

    // ── newsletter ───────────────────────────────────────────────────────────

    @Test
    @WithMockUser(username = "organizer1", roles = "ORGANIZER")
    @DisplayName("AC3/AC4/AC5 newsletter: footer with each subscriber's link, preview equals sent, OWN_TEXT")
    void newsletter_ownText() throws Exception {
        subscriber("success+sub923a" + SIM, "Ada");
        String request = ownText("NEWSLETTER", "News zu {{eventTitle}}", "<p>Hallo zusammen, {{eventTitle}}.</p>",
                "\"locale\":\"de\"");

        JsonNode preview = preview(request);
        assertThat(preview.get("html").asText()).contains("Hallo zusammen, BATbern Test Event #923.")
                .contains("Abmelden");
        assertThat(preview.get("highlightedHtml").asText())
                .contains("<mark data-var=\"eventTitle\">BATbern Test Event #923</mark>");
        assertThat(preview.get("unknownPlaceholders")).isEmpty();
        assertThat(texts(preview.get("offeredVariables"))).contains("eventTitle", "eventDate");
        assertThat(texts(preview.get("allowedVariables"))).contains("unsubscribeLink", "eventTitle")
                .doesNotContain("content");

        JsonNode sent = send(request, "RECIPIENTS");
        String mailKey = sent.get("mailKeys").get(0).asText();
        awaitUntil(() -> "COMPLETED".equals(sendStatus()));
        assertRecordedEquals(mailKey, "success+sub923a" + SIM, preview);
        assertOwnText(mailKey);
        Map<String, Object> row = jdbc.queryForMap("SELECT template_key, content_source, own_subject, own_body_html"
                + " FROM newsletter_sends WHERE event_id = ?", event.getId());
        assertThat(row.get("template_key")).isEqualTo("own-text-newsletter");
        assertThat(row.get("content_source")).isEqualTo("OWN_TEXT");
        assertThat((String) row.get("own_body_html")).isEqualTo("<p>Hallo zusammen, {{eventTitle}}.</p>");

        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(emailService, atLeastOnce()).sendHtmlEmailSync(any(), eq("success+sub923a" + SIM), any(), anyString(),
                body.capture(), any(), any());
        assertThat(body.getValue()).contains("/unsubscribe?token=tok-923-");
    }

    @Test
    @WithMockUser(username = "organizer1", roles = "ORGANIZER")
    @DisplayName("16.11 AC8 + 16.5: a retry renders the own text stored on the send row")
    void newsletter_retryRendersFromRow() throws Exception {
        subscriber("success+sub923a" + SIM, "Ada");
        JsonNode sent = send(ownText("NEWSLETTER", "Info", "<p>Original own text</p>", "\"locale\":\"de\""),
                "RECIPIENTS");
        String mailKey = sent.get("mailKeys").get(0).asText();
        awaitUntil(() -> "COMPLETED".equals(sendStatus()));
        jdbc.update("UPDATE newsletter_recipients SET delivery_status = 'failed' WHERE send_id IN"
                + " (SELECT id FROM newsletter_sends WHERE event_id = ?)", event.getId());
        jdbc.update("UPDATE newsletter_sends SET status = 'PARTIAL', failed_count = 1, sent_count = 0"
                + " WHERE event_id = ?", event.getId());
        org.mockito.Mockito.clearInvocations(emailService);

        mockMvc.perform(post(BASE + "/" + mailKey + "/retry")).andExpect(status().isAccepted());

        awaitUntil(() -> "COMPLETED".equals(sendStatus()));
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(emailService, atLeastOnce()).sendHtmlEmailSync(any(), eq("success+sub923a" + SIM), any(), anyString(),
                body.capture(), any(), any());
        assertThat(body.getValue()).contains("Original own text");
    }

    // ── registrants ──────────────────────────────────────────────────────────

    @Test
    @WithMockUser(username = "organizer1", roles = "ORGANIZER")
    @DisplayName("AC4/AC9 registrants: text as written for all; same subject again needs confirmResend")
    void registrants_ownText_guard() throws Exception {
        registration("att.de", "success+reg923de" + SIM, "Dora", "confirmed");
        registration("att.en", "success+reg923en" + SIM, "Emil", "registered");
        String request = ownText("REGISTRANTS", "Raumwechsel", "<p>Hallo {{recipientName}}, Raum 2.</p>",
                "\"locale\":\"de\",\"recipientIndex\":%d");

        JsonNode previewEn = preview(request.formatted(1));
        assertThat(previewEn.get("locale").asText()).isEqualTo("de");
        assertThat(previewEn.get("alreadySent").isNull()).isTrue();
        JsonNode sent = send(request.formatted(1), "RECIPIENTS");
        String mailKey = sent.get("mailKeys").get(0).asText();
        awaitUntil(() -> "COMPLETED".equals(sendStatus()));
        assertRecordedEquals(mailKey, previewEn.at("/recipient/address").asText(), previewEn);
        assertOwnText(mailKey);

        JsonNode again = preview(request.formatted(0));
        assertThat(again.at("/alreadySent/sentAt").asText()).isNotBlank();
        mockMvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON)
                        .content(withTarget(request.formatted(0), "RECIPIENTS")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.details.code").value("ALREADY_SENT"));
        send(request.formatted(0).replace("\"locale\"", "\"confirmResend\":true,\"locale\""), "RECIPIENTS");
        awaitUntil(() -> jdbc.queryForObject("SELECT count(*) FROM newsletter_sends WHERE event_id = ? AND status"
                + " = 'COMPLETED'", Integer.class, event.getId()) == 2);

        // A different subject is no resend.
        JsonNode other = preview(ownText("REGISTRANTS", "Neuer Raum", "<p>x</p>", "\"locale\":\"de\""));
        assertThat(other.get("alreadySent").isNull()).isTrue();
    }

    @Test
    @WithMockUser(username = "organizer1", roles = "ORGANIZER")
    @DisplayName("registrants by status: own text to the no-shows only; without a choice no-shows are left out")
    void registrants_ownText_byStatus() throws Exception {
        String attended = "success+reg923att" + SIM;
        String noShow = "success+reg923ns" + SIM;
        registration("att.de", attended, "Dora", "attended");
        registration("ns.de", noShow, "Nina", "no_show");

        JsonNode byDefault = preview(ownText("REGISTRANTS", "Folien", "<p>Hallo {{recipientName}}</p>",
                "\"locale\":\"de\""));
        assertThat(byDefault.get("recipientCount").asInt()).isEqualTo(1);
        assertThat(byDefault.at("/recipient/address").asText()).isEqualTo(attended);

        String request = ownText("REGISTRANTS", "Schade, dass Sie fehlten", "<p>Hallo {{recipientName}}</p>",
                "\"locale\":\"de\",\"registrantStatuses\":[\"NO_SHOW\"]");
        JsonNode preview = preview(request);
        assertThat(preview.get("recipientCount").asInt()).isEqualTo(1);
        assertThat(preview.at("/recipient/address").asText()).isEqualTo(noShow);

        JsonNode sent = send(request, "RECIPIENTS");
        String mailKey = sent.get("mailKeys").get(0).asText();
        awaitUntil(() -> "COMPLETED".equals(sendStatus()));
        assertThat(jdbc.queryForList("SELECT r.address FROM mail_recipient r JOIN mail_message m"
                + " ON m.id = r.mail_message_id WHERE m.mail_key = ?", String.class, mailKey))
                .containsExactly(noShow);
    }

    @Test
    @WithMockUser(username = "organizer1", roles = "ORGANIZER")
    @DisplayName("registrant statuses are for own text only")
    void registrants_statusesNeedOwnText() throws Exception {
        registration("att.de", "success+reg923att" + SIM, "Dora", "attended");
        mockMvc.perform(post(BASE + "/preview").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"audience\":\"REGISTRANTS\",\"templateKey\":\"slides-online\","
                                + "\"registrantStatuses\":[\"NO_SHOW\"]}"))
                .andExpect(status().isBadRequest());
    }

    // ── speakers ─────────────────────────────────────────────────────────────

    @Test
    @WithMockUser(username = "organizer1", roles = "ORGANIZER")
    @DisplayName("AC4/AC11/AC12 speakers: by status, own values, preview equals sent, outreach note, no reminder log")
    void speakers_ownText() throws Exception {
        speaker("Anna Speaker", "success+spk923a" + SIM, SpeakerWorkflowState.ACCEPTED, true);
        speaker("Bruno Speaker", "success+spk923b" + SIM, SpeakerWorkflowState.INVITED, false);
        String request = ownText("SPEAKERS", "Hinweis für {{speakerName}}",
                "<p>Hallo {{speakerName}}, Deadline {{deadline}}.</p>",
                "\"locale\":\"de\",\"speakerStatuses\":[\"ACCEPTED\"]");

        JsonNode preview = preview(request);
        assertThat(preview.get("recipientCount").asInt()).isEqualTo(1);
        assertThat(preview.get("subject").asText()).isEqualTo("Hinweis für Anna Speaker");
        assertThat(texts(preview.get("offeredVariables"))).contains("speakerName", "sessionTitle");
        JsonNode sent = send(request, "RECIPIENTS");
        String mailKey = sent.get("mailKeys").get(0).asText();
        assertRecordedEquals(mailKey, "success+spk923a" + SIM, preview);
        assertOwnText(mailKey);
        assertThat(jdbc.queryForList("SELECT h.notes FROM speaker_outreach_history h JOIN speaker_pool sp ON sp.id ="
                + " h.speaker_pool_id WHERE sp.event_id = ?", String.class, event.getId()))
                .containsExactly("Own text: Hinweis für Anna Speaker");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM speaker_reminder_log WHERE event_id = ?",
                Integer.class, event.getId())).isZero();
    }

    // ── venue ────────────────────────────────────────────────────────────────

    @Test
    @WithMockUser(username = "organizer1", roles = "ORGANIZER")
    @DisplayName("AC4/AC5 venue: one mail as today, own text, preview equals sent")
    void venue_ownText() throws Exception {
        venueConfig();
        String request = ownText("VENUE", "Anlass {{eventDate}}", "<p>Guten Tag {{salutation}}</p>{{notes}}",
                "\"locale\":\"de\",\"venueRecipients\":[\"VENUE\"],\"venueNotes\":\"20 Stühle\"");

        JsonNode preview = preview(request);
        assertThat(preview.get("html").asText()).contains("Guten Tag Frau Venue").contains("20 Stühle");
        JsonNode sent = send(request, "RECIPIENTS");
        String mailKey = sent.get("mailKeys").get(0).asText();
        assertRecordedEquals(mailKey, "success+venue923" + SIM, preview);
        assertOwnText(mailKey);
    }

    // ── guards ───────────────────────────────────────────────────────────────

    @Test
    @WithMockUser(username = "organizer1", roles = "ORGANIZER")
    @DisplayName("AC2: an unknown placeholder is marked in the preview and refuses the send (400, nothing recorded)")
    void unknownPlaceholder() throws Exception {
        subscriber("success+sub923a" + SIM, "Ada");
        String request = ownText("NEWSLETTER", "Bis {{deadlin}}", "<p>{{deadlin}} {{eventTitle}}</p>",
                "\"locale\":\"de\"");

        JsonNode preview = preview(request);
        assertThat(texts(preview.get("unknownPlaceholders"))).containsExactly("deadlin");
        assertThat(preview.get("highlightedHtml").asText()).contains("<mark class=\"unknown\">{{deadlin}}</mark>");

        for (String target : List.of("RECIPIENTS", "ORGANIZERS")) {
            mockMvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content(withTarget(request, target)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.details.code").value("UNKNOWN_PLACEHOLDER"))
                    .andExpect(jsonPath("$.details.placeholders[0]").value("deadlin"));
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM mail_message WHERE event_code = ?", Integer.class,
                EVENT_CODE)).isZero();
    }

    @Test
    @WithMockUser(username = "organizer1", roles = "ORGANIZER")
    @DisplayName("AC13: subject with a line break, empty body or empty subject is a 400")
    void limits() throws Exception {
        subscriber("success+sub923a" + SIM, "Ada");
        for (String request : List.of(
                ownText("NEWSLETTER", "Two\nlines", "<p>x</p>", "\"locale\":\"de\""),
                ownText("NEWSLETTER", "Subject", "", "\"locale\":\"de\""),
                ownText("NEWSLETTER", "", "<p>x</p>", "\"locale\":\"de\""))) {
            mockMvc.perform(post(BASE + "/preview").contentType(MediaType.APPLICATION_JSON).content(request))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    @WithMockUser(username = "organizer1", roles = "ORGANIZER")
    @DisplayName("AC10: an own-text test send reaches the organizers, [TEST] subject, recorded as OWN_TEXT test")
    void testSend() throws Exception {
        registration("att.de", "success+reg923de" + SIM, "Dora", "confirmed");
        JsonNode sent = send(ownText("REGISTRANTS", "Info", "<p>Hallo {{recipientName}}</p>", "\"locale\":\"de\""),
                "ORGANIZERS");

        String mailKey = sent.get("mailKeys").get(0).asText();
        Map<String, Object> message = jdbc.queryForMap("SELECT subject, is_test, content_source, template_key"
                + " FROM mail_message WHERE mail_key = ?", mailKey);
        assertThat((String) message.get("subject")).isEqualTo("[TEST] Info");
        assertThat(message.get("is_test")).isEqualTo(true);
        assertThat(message.get("content_source")).isEqualTo("OWN_TEXT");
        assertThat(message.get("template_key")).isNull();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM newsletter_sends WHERE event_id = ?",
                Integer.class, event.getId())).isZero();
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private String ownText(String audience, String subject, String body, String extra) throws Exception {
        return "{\"audience\":\"" + audience + "\",\"contentSource\":\"OWN_TEXT\",\"ownSubject\":"
                + objectMapper.writeValueAsString(subject) + ",\"ownBodyHtml\":"
                + objectMapper.writeValueAsString(body) + "," + extra + "}";
    }

    private static List<String> texts(JsonNode array) {
        List<String> values = new java.util.ArrayList<>();
        array.forEach(node -> values.add(node.asText()));
        return values;
    }

    private void assertOwnText(String mailKey) {
        Map<String, Object> message = jdbc.queryForMap(
                "SELECT content_source, template_key FROM mail_message WHERE mail_key = ?", mailKey);
        assertThat(message.get("content_source")).isEqualTo("OWN_TEXT");
        assertThat(message.get("template_key")).isNull();
    }

    private JsonNode preview(String request) throws Exception {
        String body = mockMvc.perform(post(BASE + "/preview").contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body);
    }

    private static String withTarget(String request, String target) {
        return request.substring(0, request.length() - 1) + ",\"target\":\"" + target + "\"}";
    }

    private JsonNode send(String request, String target) throws Exception {
        String body = mockMvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON)
                        .content(withTarget(request, target)))
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body);
    }

    /** The recipient's recorded body (or the mail's) and the mail's subject equal the preview. */
    private void assertRecordedEquals(String mailKey, String address, JsonNode preview) {
        Map<String, Object> message = jdbc.queryForMap(
                "SELECT id, subject, body_html FROM mail_message WHERE mail_key = ?", mailKey);
        String recipientBody = jdbc.queryForList(
                "SELECT body_html FROM mail_recipient WHERE mail_message_id = ? AND address = ?", String.class,
                message.get("id"), address).stream().filter(b -> b != null).findFirst().orElse(null);
        String recorded = Optional.ofNullable(recipientBody).orElse((String) message.get("body_html"));
        assertThat(preview.get("html").asText()).isEqualTo(recorded);
        assertThat(preview.get("subject").asText()).isEqualTo(message.get("subject"));
    }

    private String sendStatus() {
        List<String> statuses = jdbc.queryForList("SELECT status FROM newsletter_sends WHERE event_id = ?"
                + " ORDER BY sent_at DESC", String.class, event.getId());
        return statuses.isEmpty() ? null : statuses.get(0);
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
                .language("de").source("explicit").unsubscribeToken("tok-923-" + UUID.randomUUID()).build());
    }

    private void registration(String username, String email, String firstName, String status) {
        registrationRepository.saveAndFlush(Registration.builder()
                .registrationCode("REG-923-" + UUID.randomUUID()).eventId(event.getId())
                .attendeeUsername(username).attendeeEmail(email).attendeeFirstName(firstName)
                .attendeeLastName("Example").status(status).registrationDate(Instant.now()).build());
    }

    private void speaker(String name, String email, SpeakerWorkflowState state, boolean remindersDisabled) {
        SpeakerPool pool = speakerPoolRepository.saveAndFlush(SpeakerPool.builder().eventId(event.getId())
                .speakerName(name).status(state).remindersDisabled(remindersDisabled)
                .responseDeadline(LocalDate.now().plusDays(7)).contentDeadline(LocalDate.now().plusDays(14))
                .build());
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
                {"venue":{"salutation":"Frau","name":"Vera Venue","email":"success+venue923@simulator.amazonses.com"},
                 "coordinatorUsername":"organizer1"}
                """, "test");
    }
}
