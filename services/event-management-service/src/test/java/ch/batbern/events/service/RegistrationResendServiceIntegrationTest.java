package ch.batbern.events.service;

import ch.batbern.events.config.CumsContractHarness;
import ch.batbern.events.config.TestAwsConfig;
import ch.batbern.events.config.TestSecurityConfig;
import ch.batbern.events.core.dto.generated.EventType;
import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.Registration;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.repository.RegistrationRepository;
import ch.batbern.shared.test.AbstractIntegrationTest;
import ch.batbern.shared.test.mail.SesRecorder;
import ch.batbern.shared.types.EventWorkflowState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The daily resend of the confirmation mail ({@link RegistrationResendService}) on the real beans:
 * repository query, {@link RegistrationConfirmationSender}, the real {@code EmailService} into the
 * {@link SesRecorder}, CUMS as the {@link CumsContractHarness} (#1315, replaces the Mockito unit test).
 *
 * <p>Not {@code @Transactional}: the job saves each row in its own transaction, so the test removes its
 * event and registrations in {@code @BeforeEach} and {@code @AfterEach}. The database is shared with
 * other classes, so the assertions look at this class's rows and addresses only.
 */
@Import({TestSecurityConfig.class, TestAwsConfig.class})
@DisplayName("RegistrationResendService on the real beans")
class RegistrationResendServiceIntegrationTest extends AbstractIntegrationTest {

    private static final String EVENT_CODE = "BATbern1315";
    private static final String ANNA_MAIL = "success+anna.resend@simulator.amazonses.com";
    private static final String BEN_MAIL = "success+ben.resend@simulator.amazonses.com";
    private static final Pattern CONFIRM_TOKEN =
            Pattern.compile("confirm-registration\\?token=([A-Za-z0-9._-]+)");

    @Autowired
    private RegistrationResendService resendService;
    @Autowired
    private RegistrationRepository registrationRepository;
    @Autowired
    private EventRepository eventRepository;
    @Autowired
    private RegistrationConfirmationSender confirmationSender;
    @Autowired
    private CumsContractHarness cums;
    @Autowired
    private SesRecorder sesRecorder;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private MockMvc mockMvc;

    private Event event;

    @BeforeEach
    void setUp() {
        cleanUp();
        cums.reset();
        sesRecorder.reset();
        event = eventRepository.save(event());
        cums.user("anna.resend", "Anna", "Resend", ANNA_MAIL);
        cums.user("ben.resend", "Ben", "Resend", BEN_MAIL);
    }

    @AfterEach
    void tearDown() {
        cleanUp();
        cums.reset();
    }

    private void cleanUp() {
        jdbc.update("DELETE FROM registrations WHERE event_id IN (SELECT id FROM events WHERE event_code = ?)",
                EVENT_CODE);
        jdbc.update("DELETE FROM events WHERE event_code = ?", EVENT_CODE);
    }

    @Test
    @DisplayName("an unconfirmed registration past the grace window gets a working link, and the resend is counted")
    void should_resendWorkingLinkAndCount_when_registrationUnconfirmedPastGraceWindow() throws Exception {
        Registration anna = pendingSince("anna.resend", 72);

        runJob();

        sesRecorder.awaitAsyncSends();
        assertThat(sesRecorder.mailsTo(ANNA_MAIL)).hasSize(1);
        String html = sesRecorder.mailsTo(ANNA_MAIL).get(0).html();
        assertThat(html).contains("/deregister?token=" + anna.getDeregistrationToken());
        Registration counted = registrationRepository.findById(anna.getId()).orElseThrow();
        assertThat(counted.getConfirmationResendCount()).isEqualTo(1);
        assertThat(counted.getConfirmationResentAt()).isAfter(Instant.now().minus(5, ChronoUnit.MINUTES));

        // The resent link confirms the registration.
        Matcher token = CONFIRM_TOKEN.matcher(html);
        assertThat(token.find()).as("confirmation link in the mail").isTrue();
        mockMvc.perform(post("/api/v1/events/{eventCode}/registrations/confirm", EVENT_CODE)
                        .param("token", token.group(1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Registration confirmed successfully!"));
    }

    @ParameterizedTest
    @CsvSource({"en,anna.resend@test.com,Please confirm your registration",
                "de,anna.resend@test.com,Bitte bestätigen",
                "'',success+anna.resend@simulator.amazonses.com,Bitte bestätigen"})
    @DisplayName("#1315: the resent mail is in the attendee's language, German by default (was always German)")
    void should_resendInPreferredLanguage(String language, String address, String subject) {
        if (!language.isEmpty()) {
            cums.userWithLanguage("anna.resend", language);
        }
        pendingSince("anna.resend", 72);

        runJob();

        sesRecorder.awaitAsyncSends();
        assertThat(sesRecorder.mailsTo(address)).singleElement()
                .satisfies(mail -> assertThat(mail.subject()).startsWith(subject));
    }

    @Test
    @DisplayName("a registration inside the grace window is not resent")
    void should_notResend_when_registrationInsideGraceWindow() {
        Registration fresh = pendingSince("ben.resend", 1);
        pendingSince("anna.resend", 72); // positive control

        runJob();

        sesRecorder.awaitAsyncSends();
        assertThat(sesRecorder.mailsTo(ANNA_MAIL)).hasSize(1);
        assertThat(sesRecorder.mailsTo(BEN_MAIL)).isEmpty();
        assertThat(registrationRepository.findById(fresh.getId()).orElseThrow().getConfirmationResendCount())
                .isZero();
    }

    @Test
    @DisplayName("disabled, the job sends nothing")
    void should_sendNothing_when_disabled() {
        Registration anna = pendingSince("anna.resend", 72);
        RegistrationResendService disabled = new RegistrationResendService(
                registrationRepository, eventRepository, confirmationSender, false, 48, 2, 120);

        disabled.resendUnconfirmedRegistrations();

        sesRecorder.awaitAsyncSends();
        assertThat(sesRecorder.mailsTo(ANNA_MAIL)).isEmpty();
        assertThat(registrationRepository.findById(anna.getId()).orElseThrow().getConfirmationResendCount())
                .isZero();
        // Positive control: the same registration, the enabled job.
        runJob();
        sesRecorder.awaitAsyncSends();
        assertThat(sesRecorder.mailsTo(ANNA_MAIL)).hasSize(1);
    }

    @Test
    @DisplayName("a registration that fails does not stop the batch, and is not counted")
    void should_continueBatch_when_oneRegistrationFails() {
        cums.unknownUser("ghost.resend");
        Registration ghost = pendingSince("ghost.resend", 72);
        Registration anna = pendingSince("anna.resend", 72);

        runJob();

        sesRecorder.awaitAsyncSends();
        assertThat(sesRecorder.mailsTo(ANNA_MAIL)).hasSize(1);
        assertThat(registrationRepository.findById(anna.getId()).orElseThrow().getConfirmationResendCount())
                .isEqualTo(1);
        assertThat(registrationRepository.findById(ghost.getId()).orElseThrow().getConfirmationResendCount())
                .isZero();
    }

    /**
     * Runs the job through its proxy. ShedLock holds the lock for at least a minute after a run
     * ({@code lockAtLeastFor}), so the lock of the previous run is expired first. Expired, not
     * deleted: the provider remembers the rows it inserted and only updates them afterwards. A fixed
     * past instant, because {@code lock_until} holds UTC without a zone and the session's now() does not.
     */
    private void runJob() {
        jdbc.update("UPDATE shedlock SET lock_until = TIMESTAMP '2000-01-01 00:00:00'"
                + " WHERE name = 'resendUnconfirmedRegistrations'");
        resendService.resendUnconfirmedRegistrations();
    }

    /** A pending registration whose {@code created_at} lies this many hours back. */
    private Registration pendingSince(String username, int hours) {
        Registration saved = registrationRepository.save(Registration.builder()
                .registrationCode(EVENT_CODE + "-reg-"
                        + UUID.randomUUID().toString().substring(0, 6).toUpperCase(Locale.ROOT))
                .eventId(event.getId())
                .attendeeUsername(username)
                .attendeeFirstName("Test")
                .attendeeLastName("Resend")
                .attendeeEmail(username + "@example.com")
                .status("registered")
                .registrationDate(Instant.now())
                .build());
        jdbc.update("UPDATE registrations SET created_at = ? WHERE id = ?",
                Timestamp.from(Instant.now().minus(hours, ChronoUnit.HOURS)), saved.getId());
        return registrationRepository.findById(saved.getId()).orElseThrow();
    }

    private static Event event() {
        Instant date = Instant.now().plus(30, ChronoUnit.DAYS);
        return Event.builder()
                .eventCode(EVENT_CODE)
                .title("Resend Test")
                .eventNumber(1315)
                .date(date)
                .registrationDeadline(date.minus(7, ChronoUnit.DAYS))
                .venueName("Test Venue")
                .venueAddress("Test Address 123, Bern")
                .venueCapacity(100)
                .organizerUsername("test.organizer")
                .currentAttendeeCount(0)
                .eventType(EventType.EVENING)
                .workflowState(EventWorkflowState.CREATED)
                .build();
    }
}
