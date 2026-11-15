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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The registration module's mails go out after the commit, never for a change that rolls back: each
 * path runs once in a transaction marked rollback-only (no mail, state unchanged) and then committed
 * (exactly one mail, the positive control).
 *
 * <p>Not {@code @Transactional}: the test's own {@link TransactionTemplate} is the transaction under
 * test, and data is removed in {@link #tearDown}.
 */
@Import({TestSecurityConfig.class, TestAwsConfig.class})
@DisplayName("Registration mails are sent after commit")
class RegistrationMailAfterCommitIntegrationTest extends AbstractIntegrationTest {

    private static final String CODE = "BATbern9760";
    private static final String SIM = "@simulator.amazonses.com";
    private static final String WANDA = "success+wanda-waiting" + SIM;
    private static final String RITA = "success+rita-registered" + SIM;

    @Autowired
    private EventRepository eventRepository;
    @Autowired
    private RegistrationRepository registrationRepository;
    @Autowired
    private RegistrationService registrationService;
    @Autowired
    private WaitlistPromotionService waitlistPromotionService;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private SesRecorder sesRecorder;
    @Autowired
    private CumsContractHarness cums;

    private Event event;

    @BeforeEach
    void setUp() {
        // ASCII-only, minimal fixture (17.0d exception): the test is about when a mail is sent, one
        // event with one place holder and one waiter suffices, and no name is searched or sorted.
        eventRepository.findByEventCode(CODE).ifPresent(eventRepository::delete);
        sesRecorder.reset();
        cums.user("wanda.waiting", "{\"email\":\"" + WANDA + "\"}");
        cums.user("rita.registered", "{\"email\":\"" + RITA + "\"}");
        event = eventRepository.save(event());
        registration("rita.registered", RITA, "registered", null);
        registration("wanda.waiting", WANDA, "waitlist", 1);
    }

    @AfterEach
    void tearDown() {
        sesRecorder.awaitAsyncSends();
        eventRepository.findByEventCode(CODE).ifPresent(eventRepository::delete);
        cums.reset();
    }

    @Test
    @DisplayName("should_mailPromotionOnlyAfterCommit_when_manuallyPromoted")
    void should_mailPromotionOnlyAfterCommit_when_manuallyPromoted() {
        assertMailOnlyAfterCommit(WANDA, "wanda.waiting", "registered",
                status -> waitlistPromotionService.manuallyPromote(code("wanda.waiting")));
    }

    @Test
    @DisplayName("should_mailPromotionOnlyAfterCommit_when_organizerCancelsAPlaceHolder")
    void should_mailPromotionOnlyAfterCommit_when_organizerCancelsAPlaceHolder() {
        assertMailOnlyAfterCommit(WANDA, "wanda.waiting", "registered",
                status -> registrationService.changeStatus(reloadEvent(), code("rita.registered"), "cancelled"));
    }

    @Test
    @DisplayName("should_mailPromotionOnlyAfterCommit_when_organizerDeletesAPlaceHolder")
    void should_mailPromotionOnlyAfterCommit_when_organizerDeletesAPlaceHolder() {
        assertMailOnlyAfterCommit(WANDA, "wanda.waiting", "registered",
                status -> registrationService.deleteByOrganizer(reloadEvent(), code("rita.registered")));
    }

    @Test
    @DisplayName("should_mailPromotionOnlyAfterCommit_when_attendeeCancels")
    void should_mailPromotionOnlyAfterCommit_when_attendeeCancels() {
        assertMailOnlyAfterCommit(WANDA, "wanda.waiting", "registered",
                status -> registrationService.cancelRegistration(
                        registrationRepository.findByRegistrationCode(code("rita.registered")).orElseThrow()));
    }

    @Test
    @DisplayName("should_mailConfirmedOnlyAfterCommit_when_confirmedByEmailReply")
    void should_mailConfirmedOnlyAfterCommit_when_confirmedByEmailReply() {
        assertMailOnlyAfterCommit(RITA, "rita.registered", "confirmed",
                status -> registrationService.confirmByEmail(RITA, CODE));
    }

    /**
     * Runs {@code change} rolled back (no mail, the registration unchanged), then committed (one mail
     * to {@code recipient}, the registration in {@code committedStatus}).
     */
    private void assertMailOnlyAfterCommit(String recipient, String username, String committedStatus,
                                           Consumer<TransactionStatus> change) {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        String before = status(username);

        tx.executeWithoutResult(status -> {
            change.accept(status);
            status.setRollbackOnly();
        });
        sesRecorder.awaitAsyncSends();
        assertThat(status(username)).as("rolled back").isEqualTo(before);
        assertThat(sesRecorder.mailsTo(recipient)).as("no mail for a rolled-back change").isEmpty();

        tx.executeWithoutResult(change);
        sesRecorder.awaitAsyncSends();
        assertThat(status(username)).as("committed").isEqualTo(committedStatus);
        assertThat(sesRecorder.mailsTo(recipient)).as("one mail after the commit").hasSize(1);
    }

    private Event reloadEvent() {
        return eventRepository.findByEventCode(CODE).orElseThrow();
    }

    private String status(String username) {
        return registrationRepository.findByEventIdAndAttendeeUsername(event.getId(), username)
                .map(Registration::getStatus).orElse("deleted");
    }

    private static String code(String username) {
        return CODE + "-" + username;
    }

    private static Event event() {
        Instant date = Instant.now().plus(40, ChronoUnit.DAYS).truncatedTo(ChronoUnit.HOURS);
        return Event.builder()
                .eventCode(CODE)
                .title("After commit " + CODE)
                .eventNumber(9760)
                .date(date)
                .registrationDeadline(date.minus(7, ChronoUnit.DAYS))
                .venueName("Kornhausforum")
                .venueAddress("Kornhausplatz 18, 3011 Bern")
                .venueCapacity(200)
                .organizerUsername("olga.organizer")
                .currentAttendeeCount(0)
                .eventType(EventType.EVENING)
                .workflowState(EventWorkflowState.CREATED)
                .registrationCapacity(1)
                .build();
    }

    private void registration(String username, String email, String status, Integer waitlistPosition) {
        registrationRepository.save(Registration.builder()
                .registrationCode(code(username))
                .eventId(event.getId())
                .deregistrationToken(UUID.randomUUID())
                .attendeeUsername(username)
                .attendeeFirstName("Capture")
                .attendeeLastName("AfterCommit")
                .attendeeEmail(email)
                .status(status)
                .waitlistPosition(waitlistPosition)
                .registrationDate(Instant.now().minus(1, ChronoUnit.DAYS))
                .build());
    }
}
