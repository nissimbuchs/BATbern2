package ch.batbern.events.mailcenter;

import ch.batbern.events.config.CumsContractHarness;
import ch.batbern.events.config.TestAwsConfig;
import ch.batbern.events.config.TestSecurityConfig;
import ch.batbern.events.core.dto.generated.EventType;
import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.NewsletterSubscriber;
import ch.batbern.events.domain.Registration;
import ch.batbern.events.domain.Session;
import ch.batbern.events.domain.SessionUser;
import ch.batbern.events.domain.SpeakerPool;
import ch.batbern.events.dto.venuecoordination.VenueCoordinationConfig;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.repository.NewsletterSubscriberRepository;
import ch.batbern.events.repository.RegistrationRepository;
import ch.batbern.events.repository.SessionRepository;
import ch.batbern.events.repository.SessionUserRepository;
import ch.batbern.events.repository.SpeakerPoolRepository;
import ch.batbern.events.service.AdminSettingsService;
import ch.batbern.shared.test.AbstractIntegrationTest;
import ch.batbern.shared.test.mail.SesRecorder;
import ch.batbern.shared.test.security.RoleStubTables;
import ch.batbern.shared.test.security.TestTokens;
import ch.batbern.shared.types.EventWorkflowState;
import ch.batbern.shared.types.SpeakerWorkflowState;
import ch.batbern.shared.utils.DateTimeUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.web.servlet.MockMvc;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Agentic interaction story 7 (AD-15): audience sends refuse agent tokens. An organizer's agent token
 * (real, from {@link TestTokens}, through the production chain and decoder) gets 403 on every composer
 * audience, on a test send and on a newsletter retry, and nothing is recorded or sent. The same request
 * with the organizer's Cognito-shaped token (real {@link JwtAuthenticationConverter}) goes out: the
 * positive control in each test.
 *
 * <p>CUMS answers at the wire ({@link CumsContractHarness}); speakers resolve through a real session and
 * its PRIMARY_SPEAKER. The EMS test schema has no {@code role_assignments}: this test creates it
 * ({@link RoleStubTables}), so the agent's roles resolve by its {@code sub} as in production. Not {@code @Transactional}: the sends run on
 * {@code @Async} threads; rows are removed in {@code @AfterEach}. Synthetic simulator addresses only.
 */
@Import({TestSecurityConfig.class, TestAwsConfig.class})
@DisplayName("EMS: audience sends refuse agent tokens (AD-15)")
class AudienceGuardIntegrationTest extends AbstractIntegrationTest {

    private static final String EVENT_CODE = "BATbern9707";
    private static final String BASE = "/api/v1/events/" + EVENT_CODE + "/mails";
    private static final String SIM = "@simulator.amazonses.com";
    private static final String ORGANIZER = "olga.guard";
    private static final String ORGANIZER_SUB = "c0000000-0000-4000-8000-000000000707";
    private static final String OTHER_ORGANIZER = "otto.guard";
    private static final String ORG1 = "success+guard-org1" + SIM;
    private static final String ORG2 = "success+guard-org2" + SIM;
    private static final String SUBSCRIBER = "success+guard-sub" + SIM;
    private static final String REGISTRANT = "success+guard-reg" + SIM;
    private static final String SPEAKER = "anna.guard";
    private static final String SPEAKER_MAIL = "success+guard-spk" + SIM;
    private static final String VENUE = "success+guard-venue" + SIM;
    private static final String CATERING = "success+guard-catering" + SIM;
    private static final String PARTNER = "pia.guard";
    private static final String PARTNER_MAIL = "success+guard-partner" + SIM;
    private static final String RUN = "run-guard-7";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private EventRepository eventRepository;
    @Autowired
    private NewsletterSubscriberRepository subscriberRepository;
    @Autowired
    private RegistrationRepository registrationRepository;
    @Autowired
    private SpeakerPoolRepository speakerPoolRepository;
    @Autowired
    private SessionRepository sessionRepository;
    @Autowired
    private SessionUserRepository sessionUserRepository;
    @Autowired
    private AdminSettingsService adminSettingsService;
    @Autowired
    private JwtAuthenticationConverter jwtAuthenticationConverter;
    @Autowired
    private SesRecorder sesRecorder;
    @Autowired
    private CumsContractHarness cums;

    private Event event;

    @BeforeEach
    void setUp() {
        RoleStubTables.create(jdbc);
        cleanUp();
        sesRecorder.reset();
        RoleStubTables.grant(jdbc, ORGANIZER, ORGANIZER_SUB, "ORGANIZER");

        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        event = eventRepository.saveAndFlush(Event.builder()
                .eventCode(EVENT_CODE).title("BATbern Guard Probe #9707").eventNumber(9707)
                .date(now.plus(30, ChronoUnit.DAYS)).registrationDeadline(now.plus(29, ChronoUnit.DAYS))
                .venueName("Kornhausforum").venueAddress("Kornhausplatz 18, 3011 Bern").venueCapacity(200)
                .organizerUsername(ORGANIZER).currentAttendeeCount(0)
                .eventType(EventType.EVENING).workflowState(EventWorkflowState.AGENDA_PUBLISHED)
                .build());

        cums.usersWithRole("ORGANIZER", ORGANIZER, OTHER_ORGANIZER);
        cums.user(ORGANIZER, "Olga", "Guard", ORG1);
        cums.user(OTHER_ORGANIZER, "Otto", "Guard", ORG2);
    }

    @AfterEach
    void cleanUp() {
        sesRecorder.awaitAsyncSends();
        cums.reset();
        String eventIds = "(SELECT id FROM events WHERE event_code = ?)";
        jdbc.update("DELETE FROM mail_message WHERE event_code = ?", EVENT_CODE);
        jdbc.update("DELETE FROM newsletter_recipients WHERE send_id IN (SELECT id FROM newsletter_sends"
                + " WHERE event_id IN " + eventIds + ")", EVENT_CODE);
        jdbc.update("DELETE FROM newsletter_sends WHERE event_id IN " + eventIds, EVENT_CODE);
        jdbc.update("DELETE FROM newsletter_subscribers WHERE email = ?", SUBSCRIBER);
        jdbc.update("DELETE FROM speaker_reminder_log WHERE event_id IN " + eventIds, EVENT_CODE);
        jdbc.update("DELETE FROM speaker_outreach_history WHERE speaker_pool_id IN (SELECT id FROM speaker_pool"
                + " WHERE event_id IN " + eventIds + ")", EVENT_CODE);
        jdbc.update("DELETE FROM speaker_pool WHERE event_id IN " + eventIds, EVENT_CODE);
        jdbc.update("DELETE FROM session_users WHERE session_id IN (SELECT id FROM sessions WHERE event_id IN "
                + eventIds + ")", EVENT_CODE);
        jdbc.update("DELETE FROM sessions WHERE event_id IN " + eventIds, EVENT_CODE);
        jdbc.update("DELETE FROM registrations WHERE event_id IN " + eventIds, EVENT_CODE);
        jdbc.update("DELETE FROM events WHERE event_code = ?", EVENT_CODE);
        jdbc.update("DELETE FROM app_settings WHERE setting_key = ?", VenueCoordinationConfig.SETTING_KEY);
        RoleStubTables.remove(jdbc, ORGANIZER_SUB);
    }

    // ── composer send, every audience ─────────────────────────────────────────

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"NEWSLETTER", "REGISTRANTS", "SPEAKERS", "VENUE", "PARTNERS"})
    @DisplayName("an agent's audience send answers 403 and sends nothing; the person's goes out")
    void should_refuseAgent_andSendForPerson_when_audienceSend(String audience) throws Exception {
        String request = fixture(audience, "RECIPIENTS");
        String recipient = recipientOf(audience);

        mockMvc.perform(post(BASE).header(HttpHeaders.AUTHORIZATION, agentBearer())
                        .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Audience sends refuse agent tokens (AD-15): mail-center send"));

        sesRecorder.awaitAsyncSends();
        assertThat(mailMessages()).as("no mail recorded for the event").isZero();
        assertThat(newsletterSends()).as("no send row").isZero();
        assertThat(sesRecorder.mails()).as("nothing reached SES").isEmpty();

        mockMvc.perform(post(BASE).with(person())
                        .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isAccepted());

        sesRecorder.awaitAsyncSends();
        assertThat(sesRecorder.mailsTo(recipient)).isNotEmpty();
        assertThat(mailMessages()).isPositive();
    }

    @Test
    @DisplayName("an agent's test send to the organizers answers 403 and sends nothing; the person's goes out")
    void should_refuseAgent_andSendForPerson_when_testSend() throws Exception {
        String request = fixture("NEWSLETTER", "ORGANIZERS");

        mockMvc.perform(post(BASE).header(HttpHeaders.AUTHORIZATION, agentBearer())
                        .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Audience sends refuse agent tokens (AD-15): mail-center send"));

        sesRecorder.awaitAsyncSends();
        assertThat(mailMessages()).isZero();
        assertThat(sesRecorder.mails()).isEmpty();

        mockMvc.perform(post(BASE).with(person())
                        .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isAccepted());

        assertThat(sesRecorder.mailsTo(ORG1)).isNotEmpty();
        assertThat(sesRecorder.mailsTo(ORG2)).isNotEmpty();
        assertThat(sesRecorder.mailsTo(SUBSCRIBER)).isEmpty();
    }

    // ── retry ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("an agent's newsletter retry answers 403 and leaves the send row; the person's retry goes out")
    void should_refuseAgent_andRetryForPerson_when_newsletterRetry() throws Exception {
        subscriber();
        UUID sendId = UUID.randomUUID();
        jdbc.update("INSERT INTO newsletter_sends (id, event_id, template_key, is_reminder, locale, sent_at,"
                + " sent_by_username, recipient_count, status, sent_count, failed_count, test_mode, created_at)"
                + " VALUES (?, ?, 'newsletter-event', false, 'de', now(), ?, 1, 'FAILED', 0, 1, false, now())",
                sendId, event.getId(), ORGANIZER);
        jdbc.update("INSERT INTO newsletter_recipients (send_id, email, delivery_status) VALUES (?, ?, 'failed')",
                sendId, SUBSCRIBER);
        String mailKey = EVENT_CODE + "-1";
        jdbc.update("INSERT INTO mail_message (id, mail_key, send_group_id, event_code, audience, template_key,"
                        + " content_source, subject, body_html, sender_username, newsletter_send_id, created_at)"
                        + " VALUES (?, ?, ?, ?, 'NEWSLETTER', 'newsletter-event', 'TEMPLATE', 'News', '<p>News</p>',"
                        + " ?, ?, ?)",
                UUID.randomUUID(), mailKey, "grp-" + mailKey, EVENT_CODE, ORGANIZER, sendId,
                Timestamp.from(Instant.now()));

        mockMvc.perform(post(BASE + "/" + mailKey + "/retry").header(HttpHeaders.AUTHORIZATION, agentBearer()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Audience sends refuse agent tokens (AD-15): mail-center retry"));

        sesRecorder.awaitAsyncSends();
        assertThat(sendStatus(sendId)).isEqualTo("FAILED");
        assertThat(jdbc.queryForObject("SELECT delivery_status FROM newsletter_recipients WHERE send_id = ?",
                String.class, sendId)).isEqualTo("failed");
        assertThat(sesRecorder.mails()).isEmpty();

        mockMvc.perform(post(BASE + "/" + mailKey + "/retry").with(person()))
                .andExpect(status().isAccepted());

        sesRecorder.awaitAsyncSends();
        assertThat(sendStatus(sendId)).isEqualTo("COMPLETED");
        assertThat(sesRecorder.mailsTo(SUBSCRIBER)).hasSize(1);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private static String agentBearer() {
        return "Bearer " + TestTokens.agentToken(ORGANIZER_SUB, ORGANIZER, RUN, "organizer");
    }

    /** The organizer signed in as a person: Cognito-shaped claims through the real role mapping. */
    private JwtRequestPostProcessor person() {
        Converter<Jwt, Collection<GrantedAuthority>> realConversion =
                token -> jwtAuthenticationConverter.convert(token).getAuthorities();
        return jwt()
                .jwt(token -> token.subject(ORGANIZER_SUB)
                        .claim("custom:role", "ORGANIZER")
                        .claim("custom:username", ORGANIZER))
                .authorities(realConversion);
    }

    /** Seeds the audience and returns the send request for {@code target}. */
    private String fixture(String audience, String target) {
        String body = switch (audience) {
            case "NEWSLETTER" -> {
                subscriber();
                yield "\"audience\":\"NEWSLETTER\",\"locale\":\"de\"";
            }
            case "REGISTRANTS" -> {
                registrationRepository.saveAndFlush(Registration.builder()
                        .registrationCode(EVENT_CODE + "-reg-guard").eventId(event.getId())
                        .attendeeUsername("dora.guard").attendeeEmail(REGISTRANT).attendeeFirstName("Dora")
                        .attendeeLastName("Brünnimann").status("confirmed").registrationDate(Instant.now()).build());
                yield "\"audience\":\"REGISTRANTS\",\"templateKey\":\"slides-online\"";
            }
            case "SPEAKERS" -> {
                speaker();
                yield "\"audience\":\"SPEAKERS\",\"templateKey\":\"speaker-reminder-response-tier2\"";
            }
            case "VENUE" -> {
                adminSettingsService.setSetting(VenueCoordinationConfig.SETTING_KEY, """
                        {"venue":{"salutation":"Frau","name":"Vera Venue","email":"%s"},
                         "catering":{"salutation":"Herr","name":"Kurt Koch","email":"%s"},
                         "coordinatorUsername":"%s"}
                        """.formatted(VENUE, CATERING, ORGANIZER), "test");
                yield "\"audience\":\"VENUE\",\"templateKey\":\"venue-timetable\",\"locale\":\"de\","
                        + "\"venueRecipients\":[\"VENUE\",\"CATERING\"]";
            }
            case "PARTNERS" -> {
                cums.usersWithRole("PARTNER", PARTNER);
                cums.user(PARTNER, CumsContractHarness.userJson(PARTNER, "Pia", "Guard", PARTNER_MAIL, "AlphaAG"));
                yield "\"audience\":\"PARTNERS\",\"templateKey\":\"partner-info\",\"locale\":\"de\"";
            }
            default -> throw new IllegalArgumentException(audience);
        };
        return "{" + body + ",\"target\":\"" + target + "\"}";
    }

    private static String recipientOf(String audience) {
        return switch (audience) {
            case "NEWSLETTER" -> SUBSCRIBER;
            case "REGISTRANTS" -> REGISTRANT;
            case "SPEAKERS" -> SPEAKER_MAIL;
            case "VENUE" -> VENUE;
            case "PARTNERS" -> PARTNER_MAIL;
            default -> throw new IllegalArgumentException(audience);
        };
    }

    private void subscriber() {
        subscriberRepository.saveAndFlush(NewsletterSubscriber.builder().email(SUBSCRIBER).firstName("Ada")
                .language("de").source("explicit").unsubscribeToken("tok-guard-" + UUID.randomUUID())
                .confirmedAt(Instant.now()).build());
    }

    /** An INVITED speaker whose session's PRIMARY_SPEAKER resolves through CUMS. */
    private void speaker() {
        Session session = sessionRepository.save(Session.builder()
                .eventId(event.getId()).eventCode(EVENT_CODE).title("Guard Talk")
                .sessionSlug("guard-talk-" + System.nanoTime()).sessionType("presentation").build());
        sessionUserRepository.save(SessionUser.builder().session(session).username(SPEAKER)
                .speakerRole(SessionUser.SpeakerRole.PRIMARY_SPEAKER).isConfirmed(false).build());
        speakerPoolRepository.saveAndFlush(SpeakerPool.builder().eventId(event.getId()).sessionId(session.getId())
                .speakerName("Anna Guard").status(SpeakerWorkflowState.INVITED).remindersDisabled(false)
                .responseDeadline(DateTimeUtils.todaySwiss().plusDays(7)).build());
        cums.user(SPEAKER, "Anna", "Guard", SPEAKER_MAIL);
    }

    private int mailMessages() {
        return jdbc.queryForObject("SELECT count(*) FROM mail_message WHERE event_code = ?", Integer.class,
                EVENT_CODE);
    }

    private int newsletterSends() {
        return jdbc.queryForObject("SELECT count(*) FROM newsletter_sends WHERE event_id = ?", Integer.class,
                event.getId());
    }

    private String sendStatus(UUID sendId) {
        return jdbc.queryForObject("SELECT status FROM newsletter_sends WHERE id = ?", String.class, sendId);
    }
}
