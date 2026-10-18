package ch.batbern.events.service;

import ch.batbern.events.client.UserApiClient;
import ch.batbern.events.config.TestAwsConfig;
import ch.batbern.events.config.TestSecurityConfig;
import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.NewsletterSubscriber;
import ch.batbern.events.domain.Registration;
import ch.batbern.events.exception.DuplicateSubscriberException;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.repository.NewsletterRecipientRepository;
import ch.batbern.events.repository.NewsletterSendRepository;
import ch.batbern.events.repository.NewsletterSubscriberRepository;
import ch.batbern.events.repository.RegistrationRepository;
import ch.batbern.shared.service.EmailService;
import ch.batbern.shared.test.AbstractIntegrationTest;
import ch.batbern.shared.types.EventWorkflowState;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Email addresses are matched case-insensitively (issue #1099). Production had a person with two
 * active newsletter rows ("x@ace.ch" and "X@ace.ch"), and lookups by email silently missed
 * registrations and subscribers whose stored address differed only in case.
 */
@Transactional
@Import({TestSecurityConfig.class, TestAwsConfig.class})
@DisplayName("Case-insensitive email matching (#1099)")
class EmailCaseInsensitiveMatchingIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private NewsletterSubscriberService newsletterSubscriberService;
    @Autowired
    private RegistrationService registrationService;
    @Autowired
    private NewsletterSubscriberRepository subscriberRepository;
    @Autowired
    private NewsletterSendRepository sendRepository;
    @Autowired
    private NewsletterRecipientRepository recipientRepository;
    @Autowired
    private RegistrationRepository registrationRepository;
    @Autowired
    private EventRepository eventRepository;

    @MockitoBean
    private UserApiClient userApiClient;
    @MockitoBean
    private EmailService emailService;

    @BeforeEach
    void setUp() {
        recipientRepository.deleteAll();
        sendRepository.deleteAll();
        subscriberRepository.deleteAll();
    }

    // ── Newsletter ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("subscribe stores the address lower-cased")
    void should_storeLowerCasedEmail_when_subscribing() {
        NewsletterSubscriber sub = newsletterSubscriberService.subscribe(
                " Case.Test@BATbern.ch ", "Case", "de", "explicit", null);

        assertThat(sub.getEmail()).isEqualTo("case.test@batbern.ch");
    }

    @Test
    @DisplayName("subscribing again with different capitalisation is a duplicate, not a second row")
    void should_rejectDuplicate_when_sameEmailDiffersOnlyInCase() {
        newsletterSubscriberService.subscribe("case.test@batbern.ch", "Case", "de", "explicit", null);

        assertThatThrownBy(() -> newsletterSubscriberService.subscribe(
                "Case.Test@batbern.ch", "Case", "de", "explicit", null))
                .isInstanceOf(DuplicateSubscriberException.class);
        assertThat(subscriberRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("database refuses two subscriber rows that differ only in case")
    void should_rejectAtDatabaseLevel_when_emailsDifferOnlyInCase() {
        subscriberRepository.saveAndFlush(subscriber("db.case@batbern.ch"));

        assertThatThrownBy(() -> subscriberRepository.saveAndFlush(subscriber("DB.Case@batbern.ch")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("a hard bounce reported with different capitalisation still suppresses the subscriber")
    void should_suppressSubscriber_when_bounceAddressDiffersInCase() {
        subscriberRepository.saveAndFlush(subscriber("bounce.case@batbern.ch"));
        // Conditional bean (aws.ses.bounce.enabled), so build it over the real repositories
        BounceProcessingService bounceProcessingService = new BounceProcessingService(
                subscriberRepository, recipientRepository, new ObjectMapper(), 3);

        bounceProcessingService.handleHardBounce("Bounce.Case@BATbern.ch", UUID.randomUUID());

        NewsletterSubscriber sub = subscriberRepository.findByEmailIgnoreCase("bounce.case@batbern.ch")
                .orElseThrow();
        assertThat(sub.getSuppressedAt()).isNotNull();
        assertThat(sub.getBounceType()).isEqualTo("hard");
    }

    @Test
    @DisplayName("an unsubscribe reply from a differently capitalised address unsubscribes")
    void should_unsubscribe_when_replyAddressDiffersInCase() {
        subscriberRepository.saveAndFlush(subscriber("unsub.case@batbern.ch"));

        newsletterSubscriberService.unsubscribeByEmail("Unsub.Case@BATbern.ch");

        assertThat(subscriberRepository.findByEmailIgnoreCase("unsub.case@batbern.ch").orElseThrow()
                .getUnsubscribedAt()).isNotNull();
    }

    // ── Registrations by email (deregistration by email, ACCEPT reply) ──────

    @Test
    @DisplayName("registration lookup by email ignores capitalisation")
    void should_findRegistration_when_emailDiffersInCase() {
        Event event = saveEvent("BATbern761");
        saveRegistration(event.getId(), "case.person", "Case.Person@example.com", "registered");

        assertThat(registrationRepository.findByAttendeeEmailAndEventCode(
                "case.person@EXAMPLE.com", "BATbern761")).isPresent();
    }

    @Test
    @DisplayName("ACCEPT reply from a differently capitalised address confirms the registration")
    void should_confirmRegistration_when_replyAddressDiffersInCase() {
        Event event = saveEvent("BATbern762");
        saveRegistration(event.getId(), "reply.person", "reply.person@example.com", "registered");

        registrationService.confirmByEmail("Reply.Person@Example.com", "BATbern762");

        assertThat(registrationRepository.findByAttendeeEmailAndEventCode(
                "reply.person@example.com", "BATbern762").orElseThrow().getStatus())
                .isEqualTo("confirmed");
    }

    // ── Fixtures ────────────────────────────────────────────────────────────

    private NewsletterSubscriber subscriber(String email) {
        return NewsletterSubscriber.builder()
                .email(email)
                .firstName("Case")
                .language("de")
                .source("explicit")
                .unsubscribeToken(UUID.randomUUID().toString())
                .build();
    }

    private Event saveEvent(String code) {
        Instant date = Instant.now().plus(30, ChronoUnit.DAYS);
        return eventRepository.save(Event.builder()
                .eventCode(code)
                .eventNumber(Integer.parseInt(code.substring("BATbern".length())))
                .title("Case Matching Event")
                .date(date)
                .registrationDeadline(date.minus(10, ChronoUnit.DAYS))
                .venueName("Test Venue")
                .venueAddress("Test Address")
                .venueCapacity(100)
                .eventType(ch.batbern.events.core.dto.generated.EventType.EVENING)
                .workflowState(EventWorkflowState.CREATED)
                .organizerUsername("org.user")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build());
    }

    private void saveRegistration(UUID eventId, String username, String email, String status) {
        registrationRepository.save(Registration.builder()
                .registrationCode("REG-" + UUID.randomUUID())
                .eventId(eventId)
                .attendeeUsername(username)
                .attendeeEmail(email)
                .status(status)
                .registrationDate(Instant.now())
                .build());
    }
}
