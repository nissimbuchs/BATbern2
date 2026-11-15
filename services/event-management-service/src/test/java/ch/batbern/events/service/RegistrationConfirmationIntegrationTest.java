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
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Double opt-in completion on the real beans: once a pending registration is confirmed (link or
 * ACCEPT reply), the attendee gets the "registration confirmed" mail with the calendar invite, in
 * their language. An ACCEPT reply follows the rule of the confirmation link (#1315): only a pending
 * ({@code registered}) registration is confirmed; a confirmed one is left alone; any other status is
 * refused silently (no state change, no mail), like a reply that matches no registration.
 *
 * <p>Replaces the Mockito unit test {@code RegistrationServiceConfirmationTest}: CUMS is the
 * {@link CumsContractHarness}, SES the {@link SesRecorder}, no own-code mocks. Not
 * {@code @Transactional}: the service commits, so the test removes its event and registrations.
 */
@Import({TestSecurityConfig.class, TestAwsConfig.class})
@DisplayName("Registration confirmed mail and ACCEPT reply on the real beans")
class RegistrationConfirmationIntegrationTest extends AbstractIntegrationTest {

    private static final String EVENT_CODE = "BATbern13151";
    private static final String JOHN_MAIL = "success+john.accept@simulator.amazonses.com";

    @Autowired
    private RegistrationService registrationService;
    @Autowired
    private RegistrationRepository registrationRepository;
    @Autowired
    private EventRepository eventRepository;
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
        cums.reset();
        sesRecorder.reset();
        event = eventRepository.save(event());
        cums.user("john.accept", "John", "Accept", JOHN_MAIL);
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

    // ── notifyRegistrationConfirmed ─────────────────────────────────────────

    @ParameterizedTest
    @CsvSource({"en,Registration confirmed", "de,Anmeldung bestätigt", "'',Anmeldung bestätigt"})
    @DisplayName("the confirmed mail is in the attendee's language, German by default")
    void should_sendConfirmedMailInPreferredLanguage(String language, String subject) {
        String address = "john.lang@test.com"; // userWithLanguage answers <username>@test.com
        if (language.isEmpty()) {
            cums.user("john.lang", "John", "Lang", address);
        } else {
            cums.userWithLanguage("john.lang", language);
        }
        Registration registration = save("john.lang", address, "confirmed");

        registrationService.notifyRegistrationConfirmed(registration, event);

        sesRecorder.awaitAsyncSends();
        assertThat(sesRecorder.mailsTo(address)).singleElement()
                .satisfies(mail -> assertThat(mail.subject()).startsWith(subject));
    }

    @Test
    @DisplayName("an attendee CUMS does not know gets no mail, and the confirmation does not fail")
    void should_notFail_when_attendeeUnknownToCums() {
        cums.unknownUser("gone.user");
        Registration registration = save("gone.user", "gone.user@example.com", "confirmed");

        assertThatCode(() -> registrationService.notifyRegistrationConfirmed(registration, event))
                .doesNotThrowAnyException();
        // Positive control: a known attendee does get the mail.
        registrationService.notifyRegistrationConfirmed(save("john.accept", JOHN_MAIL, "confirmed"), event);

        sesRecorder.awaitAsyncSends();
        assertThat(sesRecorder.mails()).singleElement()
                .satisfies(mail -> assertThat(mail.to()).containsExactly(JOHN_MAIL));
    }

    // ── confirmByEmail (ACCEPT reply) ───────────────────────────────────────

    @Test
    @DisplayName("an ACCEPT reply confirms a pending registration and sends the confirmed mail once")
    void should_confirmAndMail_when_acceptReplyForPendingRegistration() {
        Registration registration = save("john.accept", JOHN_MAIL, "registered");

        registrationService.confirmByEmail(JOHN_MAIL, EVENT_CODE);

        assertThat(statusOf(registration)).isEqualTo("confirmed");
        sesRecorder.awaitAsyncSends();
        assertThat(sesRecorder.mailsTo(JOHN_MAIL)).singleElement()
                .satisfies(mail -> assertThat(mail.subject()).startsWith("Anmeldung bestätigt"));
    }

    @Test
    @DisplayName("an ACCEPT reply for a confirmed registration changes nothing and mails nobody")
    void should_doNothing_when_acceptReplyForConfirmedRegistration() {
        Registration registration = save("john.accept", JOHN_MAIL, "confirmed");

        registrationService.confirmByEmail(JOHN_MAIL, EVENT_CODE);

        assertThat(statusOf(registration)).isEqualTo("confirmed");
        sesRecorder.awaitAsyncSends();
        assertThat(sesRecorder.mails()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"waitlist", "attended", "no_show", "cancelled"})
    @DisplayName("#1315: an ACCEPT reply does not confirm a registration that is no longer pending")
    void should_refuseSilently_when_acceptReplyForProcessedRegistration(String processed) {
        Registration registration = save("john.accept", JOHN_MAIL, processed);
        if ("waitlist".equals(processed)) {
            registration.setWaitlistPosition(1);
            registration = registrationRepository.save(registration);
        }

        assertThatCode(() -> registrationService.confirmByEmail(JOHN_MAIL, EVENT_CODE))
                .doesNotThrowAnyException();

        Registration unchanged = registrationRepository.findById(registration.getId()).orElseThrow();
        assertThat(unchanged.getStatus()).isEqualTo(processed);
        if ("waitlist".equals(processed)) {
            assertThat(unchanged.getWaitlistPosition()).isEqualTo(1);
        }
        sesRecorder.awaitAsyncSends();
        assertThat(sesRecorder.mails()).isEmpty();
    }

    @Test
    @DisplayName("an ACCEPT reply from a differently capitalised address confirms the registration")
    void should_confirm_when_replyAddressDiffersInCase() {
        Registration registration = save("john.accept", JOHN_MAIL, "registered");

        registrationService.confirmByEmail(JOHN_MAIL.toUpperCase(Locale.ROOT), EVENT_CODE);

        assertThat(statusOf(registration)).isEqualTo("confirmed");
    }

    // ── Fixtures ────────────────────────────────────────────────────────────

    private String statusOf(Registration registration) {
        return registrationRepository.findById(registration.getId()).orElseThrow().getStatus();
    }

    private Registration save(String username, String email, String status) {
        return registrationRepository.save(Registration.builder()
                .registrationCode(EVENT_CODE + "-reg-T" + UUID.randomUUID().toString().substring(0, 8)
                        .toUpperCase(Locale.ROOT))
                .eventId(event.getId())
                .attendeeUsername(username)
                .attendeeFirstName("John")
                .attendeeLastName("Accept")
                .attendeeEmail(email)
                .status(status)
                .deregistrationToken(UUID.randomUUID())
                .registrationDate(Instant.now())
                .build());
    }

    private static Event event() {
        Instant date = Instant.now().plus(60, ChronoUnit.DAYS);
        return Event.builder()
                .eventCode(EVENT_CODE)
                .title("Accept Reply Test")
                .eventNumber(13151)
                .date(date)
                .registrationDeadline(date.minus(7, ChronoUnit.DAYS))
                .venueName("Test Venue")
                .venueAddress("Test Address 123, Bern")
                .venueCapacity(100)
                .organizerUsername("test.organizer")
                .currentAttendeeCount(0)
                .description("Test event for the ACCEPT reply")
                .eventType(EventType.EVENING)
                .workflowState(EventWorkflowState.CREATED)
                .build();
    }
}
