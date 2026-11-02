package ch.batbern.events.mailcenter;

import ch.batbern.events.client.UserApiClient;
import ch.batbern.events.config.TestAwsConfig;
import ch.batbern.events.config.TestSecurityConfig;
import ch.batbern.events.core.dto.generated.EventType;
import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.EventTask;
import ch.batbern.events.domain.Registration;
import ch.batbern.events.domain.Session;
import ch.batbern.events.domain.SessionQnaWindow;
import ch.batbern.events.domain.SpeakerPool;
import ch.batbern.events.dto.generated.users.InvitationCredentialsResponse;
import ch.batbern.events.dto.generated.users.UserResponse;
import ch.batbern.events.dto.venuecoordination.VenueCoordinationConfig;
import ch.batbern.events.dto.venuecoordination.VenueCoordinationConfig.Role;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.service.AdminSettingsService;
import ch.batbern.events.service.DeregistrationEmailService;
import ch.batbern.events.service.InboundEmailConfirmationEmailService;
import ch.batbern.events.service.NewsletterEmailService;
import ch.batbern.events.service.PrimarySpeakerResolver;
import ch.batbern.events.service.QnaNotificationService;
import ch.batbern.events.service.RegistrationEmailService;
import ch.batbern.events.service.SlidesOnlineEmailService;
import ch.batbern.events.service.SpeakerAcceptanceEmailService;
import ch.batbern.events.service.SpeakerInvitationEmailService;
import ch.batbern.events.service.SpeakerReminderEmailService;
import ch.batbern.events.service.TaskReminderEmailService;
import ch.batbern.events.service.VenueCoordinationService;
import ch.batbern.events.service.WaitlistPromotionEmailService;
import ch.batbern.shared.service.CapturedEmail;
import ch.batbern.shared.service.EmailService;
import ch.batbern.shared.service.LocalEmailCapture;
import ch.batbern.shared.test.AbstractIntegrationTest;
import ch.batbern.shared.types.EventWorkflowState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Year;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;

/**
 * Story 16.11 AC6: pins the subject and HTML of every template-based mail as today's code renders
 * it, so the move to one pipeline and one variable catalog can be proven byte-identical (except
 * the owner-decided changes, which update the goldens in one visible commit).
 *
 * <p>Renders through the real {@link EmailService} (no SES client: the local capture branch, nothing
 * is sent) and the real templates seeded from the classpath. Fixed fixtures; {@code currentYear} is
 * normalised. A missing golden file is created under {@code src/test/resources/mail-golden}; a
 * mismatch fails and writes the actual output to {@code build/mail-golden-actual} for the diff.
 * Synthetic SES simulator addresses only.
 */
@Import({TestSecurityConfig.class, TestAwsConfig.class})
@DisplayName("Mail golden files (Story 16.11)")
class MailGoldenTest extends AbstractIntegrationTest {

    private static final String EVENT_CODE = "BATbern931";
    private static final Instant EVENT_DATE = Instant.parse("2027-03-05T17:00:00Z");
    private static final String SIM = "@simulator.amazonses.com";
    private static final Path GOLDEN = Path.of("src/test/resources/mail-golden");
    private static final Path ACTUAL = Path.of("build/mail-golden-actual");

    @MockitoBean(answers = Answers.CALLS_REAL_METHODS)
    private EmailService emailService;
    @MockitoBean
    private UserApiClient userApiClient;
    @MockitoBean
    private PrimarySpeakerResolver primarySpeakerResolver;

    @Autowired
    private EventRepository eventRepository;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private AdminSettingsService adminSettingsService;
    @Autowired
    private RegistrationEmailService registrationEmailService;
    @Autowired
    private DeregistrationEmailService deregistrationEmailService;
    @Autowired
    private WaitlistPromotionEmailService waitlistPromotionEmailService;
    @Autowired
    private InboundEmailConfirmationEmailService inboundEmailConfirmationEmailService;
    @Autowired
    private TaskReminderEmailService taskReminderEmailService;
    @Autowired
    private QnaNotificationService qnaNotificationService;
    @Autowired
    private SpeakerInvitationEmailService speakerInvitationEmailService;
    @Autowired
    private SpeakerAcceptanceEmailService speakerAcceptanceEmailService;
    @Autowired
    private SpeakerReminderEmailService speakerReminderEmailService;
    @Autowired
    private NewsletterEmailService newsletterEmailService;
    @Autowired
    private SlidesOnlineEmailService slidesOnlineEmailService;
    @Autowired
    private VenueCoordinationService venueCoordinationService;

    private final LocalEmailCapture capture = new LocalEmailCapture();
    private final List<String> mismatches = new ArrayList<>();
    private Event event;

    @BeforeEach
    void setUp() {
        cleanUp();
        ReflectionTestUtils.setField(emailService, "localEmailCapture", capture);
        ReflectionTestUtils.setField(emailService, "replyToEmail", "replies@batbern.ch");
        event = eventRepository.saveAndFlush(Event.builder()
                .eventCode(EVENT_CODE).title("Architektur im Wandel").eventNumber(931)
                .date(EVENT_DATE).registrationDeadline(EVENT_DATE.minusSeconds(86_400))
                .venueName("Kornhausforum").venueAddress("Kornhausplatz 18, 3011 Bern").venueCapacity(200)
                .organizerUsername("organizer1").currentAttendeeCount(0)
                .eventType(EventType.EVENING).workflowState(EventWorkflowState.AGENDA_PUBLISHED)
                .build());
        UserResponse anna = user("anna.attendee", "success+anna931" + SIM, "Anna", "Muster");
        lenient().when(userApiClient.getUserByUsername("anna.attendee")).thenReturn(anna);
        lenient().when(userApiClient.getUserByUsername("organizer1"))
                .thenReturn(user("organizer1", "success+org931" + SIM, "Olga", "Org"));
        lenient().when(userApiClient.getUserByUsername("sara.speaker"))
                .thenReturn(user("sara.speaker", "success+sara931" + SIM, "Sara", "Speaker"));
        lenient().when(userApiClient.getPreferredLanguage(anyString())).thenReturn("de");
        lenient().when(userApiClient.getPreferredLanguage("anna.en")).thenReturn("en");
        lenient().when(userApiClient.getQnaNotificationFrequency(anyString())).thenReturn("live");
        lenient().when(primarySpeakerResolver.resolve(any())).thenReturn(Optional.of(
                new PrimarySpeakerResolver.PrimarySpeakerProfile("sara.speaker", "success+sara931" + SIM, "Sara",
                        "Speaker", "AcmeAG", List.of())));
        adminSettingsService.setSetting(VenueCoordinationConfig.SETTING_KEY, """
                {"venue":{"salutation":"Frau","name":"Vera Venue","email":"success+venue931@simulator.amazonses.com"},
                 "catering":{"salutation":"Herr","name":"Kurt Koch","email":"success+catering931@simulator.amazonses.com"},
                 "coordinatorUsername":"organizer1"}
                """, "test");
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM mail_message WHERE event_code = ?", EVENT_CODE);
        jdbc.update("DELETE FROM events WHERE event_code = ?", EVENT_CODE);
        jdbc.update("DELETE FROM app_settings WHERE setting_key = ?", VenueCoordinationConfig.SETTING_KEY);
    }

    @Test
    @DisplayName("every template-based mail renders as pinned")
    void everyMail_matchesGolden() throws Exception {
        for (Locale locale : List.of(Locale.GERMAN, Locale.ENGLISH)) {
            String l = locale.getLanguage();
            Registration reg = registration(l);
            UserResponse profile = user(reg.getAttendeeUsername(), reg.getAttendeeEmail(), "Anna", "Muster");

            check("registration-confirmation-" + l, () -> registrationEmailService.sendRegistrationConfirmation(
                    reg, profile, event, "confirm-token-931", "https://batbern.ch/deregister?token=dereg-931",
                    locale));
            check("registration-organizer-added-" + l,
                    () -> registrationEmailService.sendOrganizerAddedConfirmation(reg, profile, event, locale));
            check("registration-confirmed-" + l,
                    () -> registrationEmailService.sendRegistrationConfirmed(reg, profile, event, locale));
            check("unsubscribe-confirmation-" + l,
                    () -> inboundEmailConfirmationEmailService.sendUnsubscribeConfirmation("success+u931" + SIM, l));
            check("speaker-invitation-" + l, () -> speakerInvitationEmailService.sendInvitationEmail(
                    speaker(), event, "https://batbern.ch/login", credentials(), locale));
            check("speaker-acceptance-" + l,
                    () -> speakerAcceptanceEmailService.sendAcceptanceConfirmationEmail(speaker(), event, locale));
            for (String type : List.of("RESPONSE", "CONTENT")) {
                for (String tier : List.of("TIER_1", "TIER_2", "TIER_3")) {
                    SpeakerReminderEmailService.RenderedReminder r = speakerReminderEmailService.render(speaker(),
                            event, type, tier, LocalDate.now().plusDays(5), locale).orElseThrow();
                    compare(SpeakerReminderEmailService.templateKey(type, tier) + "-" + l, r.subject(), r.html());
                }
            }
            NewsletterEmailService.NewsletterDraft draft = newsletterEmailService.prepare(event, l, false,
                    newsletterEmailService.effectiveTemplateKey(null));
            compare("newsletter-event-" + l, draft.subject(),
                    newsletterEmailService.renderBody(draft, "https://batbern.ch/unsubscribe?token=unsub-931"));
            for (String key : List.of("slides-online", "registrant-notice-deregistration-call",
                    "registrant-notice-deregistration-call-selfservice")) {
                SlidesOnlineEmailService.RenderedMail mail = slidesOnlineEmailService.personalise(
                        slidesOnlineEmailService.renderBase(event, key, l), reg, false);
                compare(key + "-" + l, mail.subject(), mail.html());
            }
            for (String key : List.of("venue-timetable", "catering-offerte-request")) {
                VenueCoordinationService.VenueMail mail = venueCoordinationService.renderFor(EVENT_CODE, key,
                        List.of(Role.VENUE, Role.CATERING), l, "Bitte 20 Stühle");
                compare(key + "-" + l, mail.subject(), mail.body());
            }
            compareQna(l);
        }
        // Single-locale paths (locale from the user profile)
        Registration de = registration("de");
        check("deregistration-link-de", () -> deregistrationEmailService.sendDeregistrationLinkEmail(de, event,
                "https://batbern.ch/deregister?token=dereg-931"));
        de.setEventId(event.getId());
        de.setStatus("waitlist");
        de.setWaitlistPosition(3);
        check("registration-waitlist-confirmation-de", () -> waitlistPromotionEmailService.sendWaitlistConfirmationEmail(de));
        check("waitlist-promotion-de", () -> waitlistPromotionEmailService.sendPromotionEmail(de));
        check("task-reminder-de", () -> taskReminderEmailService.sendTaskDeadlineReminder(task(), Locale.GERMAN));

        assertThat(mismatches).as("mails that differ from their golden file (actual output in %s)",
                ACTUAL.toAbsolutePath()).isEmpty();
    }

    @Test
    @DisplayName("AC8: a newsletter send renders every recipient from the content resolved at its start")
    void newsletter_contentFixedPerSend() {
        NewsletterEmailService.NewsletterDraft draft = newsletterEmailService.prepare(event, "de", false,
                "newsletter-event");
        String before = newsletterEmailService.renderBody(draft, "https://batbern.ch/unsubscribe?token=a");
        String original = jdbc.queryForObject(
                "SELECT html_body FROM email_templates WHERE template_key = 'newsletter-event' AND locale = 'de'",
                String.class);
        try {
            jdbc.update("UPDATE email_templates SET html_body = '<p>EDITED DURING SEND</p>'"
                    + " WHERE template_key = 'newsletter-event' AND locale = 'de'");

            String after = newsletterEmailService.renderBody(draft, "https://batbern.ch/unsubscribe?token=a");

            assertThat(after).isEqualTo(before).doesNotContain("EDITED DURING SEND");
        } finally {
            jdbc.update("UPDATE email_templates SET html_body = ? WHERE template_key = 'newsletter-event'"
                    + " AND locale = 'de'", original);
        }
    }

    private void compareQna(String locale) {
        Session session = Session.builder().id(UUID.randomUUID()).eventId(event.getId()).eventCode(EVENT_CODE)
                .title("Fitness Functions in der Praxis").sessionSlug("fitness-functions").build();
        SessionQnaWindow window = SessionQnaWindow.builder().id(UUID.randomUUID()).sessionId(session.getId())
                .eventCode(EVENT_CODE).build();
        lenient().when(userApiClient.getUserByUsername("sara.speaker"))
                .thenReturn(user("sara.speaker", "success+sara931" + SIM, "Sara", "Speaker"));
        Object mail = ReflectionTestUtils.invokeMethod(qnaNotificationService, "renderMail", window, session, event,
                "sara.speaker", 3, locale);
        compare("qna-new-questions-" + locale, ReflectionTestUtils.invokeMethod(mail, "subject"),
                ReflectionTestUtils.invokeMethod(mail, "html"));
    }

    /** Runs one send and pins what reached the capture (sends may be asynchronous). */
    private void check(String name, Runnable send) throws InterruptedException {
        capture.clear();
        send.run();
        long deadline = System.nanoTime() + 10_000_000_000L;
        while (capture.getAll().isEmpty() && System.nanoTime() < deadline) {
            Thread.sleep(50);
        }
        assertThat(capture.getAll()).as("no mail captured for %s", name).isNotEmpty();
        CapturedEmail email = capture.getAll().get(0);
        compare(name, email.subject(), email.htmlBody());
    }

    private void compare(String name, String subject, String html) {
        String actual = normalise("Subject: " + subject + "\n\n" + html);
        Path golden = GOLDEN.resolve(name + ".html");
        try {
            if (!Files.exists(golden)) {
                Files.createDirectories(GOLDEN);
                Files.writeString(golden, actual, StandardCharsets.UTF_8);
                return;
            }
            if (!Files.readString(golden, StandardCharsets.UTF_8).equals(actual)) {
                Files.createDirectories(ACTUAL);
                Files.writeString(ACTUAL.resolve(name + ".html"), actual, StandardCharsets.UTF_8);
                mismatches.add(name);
            }
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String normalise(String text) {
        // Speaker reminders are rendered for a deadline five days from today.
        String reminderDeadline = LocalDate.now().plusDays(5).format(DateTimeFormatter.ofPattern("dd.MM.yyyy"));
        // Signed links (registration confirmation) carry issue and expiry times.
        return text.replace(reminderDeadline, "{DEADLINE}")
                .replace(String.valueOf(Year.now().getValue()), "{YEAR}")
                .replaceAll("eyJ[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+", "{JWT}");
    }

    private Registration registration(String locale) {
        String username = "en".equals(locale) ? "anna.en" : "anna.attendee";
        return Registration.builder().id(UUID.fromString("00000000-0000-4000-8000-000000000931"))
                .registrationCode("BAT931-REG-0001").eventId(event.getId()).attendeeUsername(username)
                .attendeeFirstName("Anna").attendeeLastName("Muster").attendeeEmail("success+anna931" + SIM)
                .status("confirmed").deregistrationToken(UUID.fromString("00000000-0000-4000-8000-000000000932"))
                .calendarUid(UUID.fromString("00000000-0000-4000-8000-000000000933")).build();
    }

    private SpeakerPool speaker() {
        return SpeakerPool.builder().id(UUID.fromString("00000000-0000-4000-8000-000000000934"))
                .eventId(event.getId()).speakerName("Sara Speaker").company("AcmeAG")
                .responseDeadline(LocalDate.of(2027, 1, 15)).contentDeadline(LocalDate.of(2027, 2, 15)).build();
    }

    private static InvitationCredentialsResponse credentials() {
        return new InvitationCredentialsResponse().temporaryPassword("placeholder")
                .action(InvitationCredentialsResponse.ActionEnum.FRESH_TEMP_PASSWORD);
    }

    private EventTask task() {
        EventTask task = new EventTask();
        task.setId(UUID.fromString("00000000-0000-4000-8000-000000000935"));
        task.setEventId(event.getId());
        task.setTaskName("Catering bestätigen");
        task.setDueDate(Instant.parse("2027-02-20T09:00:00Z"));
        task.setAssignedOrganizerUsername("organizer1");
        task.setNotes("Vegetarisch für 40");
        return task;
    }

    private static UserResponse user(String username, String email, String first, String last) {
        return new UserResponse().id(username).email(email).firstName(first).lastName(last);
    }
}
