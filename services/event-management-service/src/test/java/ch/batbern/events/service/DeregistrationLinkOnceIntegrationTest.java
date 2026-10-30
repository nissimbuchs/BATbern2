package ch.batbern.events.service;

import ch.batbern.events.client.UserApiClient;
import ch.batbern.events.config.TestAwsConfig;
import ch.batbern.events.config.TestSecurityConfig;
import ch.batbern.events.core.dto.generated.EventType;
import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.Registration;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.repository.RegistrationRepository;
import ch.batbern.events.service.DeregistrationService.DeregistrationLinkResult;
import ch.batbern.shared.test.AbstractIntegrationTest;
import ch.batbern.shared.types.EventWorkflowState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Story 16.0 AC9/AC10/AC11 against real PostgreSQL: a reply "cancel" (and, in Part B, a calendar
 * decline) sends the deregistration-link mail at most once per 7 days, tracked in
 * {@code registrations.decline_link_sent_at}. The registration itself is never cancelled here.
 */
@Transactional
@Import({TestSecurityConfig.class, TestAwsConfig.class})
@DisplayName("Deregistration link at most once per 7 days (PostgreSQL)")
class DeregistrationLinkOnceIntegrationTest extends AbstractIntegrationTest {

    private static final String EVENT_CODE = "BATbern911";
    private static final String EMAIL = "success+alice@simulator.amazonses.com";

    @Autowired
    private DeregistrationService deregistrationService;
    @Autowired
    private EventRepository eventRepository;
    @Autowired
    private RegistrationRepository registrationRepository;
    @MockitoBean
    private DeregistrationEmailService deregistrationEmailService;
    @MockitoBean
    private UserApiClient userApiClient;

    private Registration active;

    @BeforeEach
    void setUp() {
        Instant eventDate = Instant.now().plus(30, ChronoUnit.DAYS);
        Event event = eventRepository.save(Event.builder()
                .eventCode(EVENT_CODE)
                .title("BATbern Test Event #911")
                .eventNumber(911)
                .date(eventDate)
                .registrationDeadline(eventDate.minus(1, ChronoUnit.DAYS))
                .venueName("Test Venue")
                .venueAddress("Bern")
                .venueCapacity(200)
                .organizerUsername("organizer")
                .currentAttendeeCount(0)
                .eventType(EventType.EVENING)
                .workflowState(EventWorkflowState.AGENDA_PUBLISHED)
                .build());
        active = registrationRepository.save(registration(event, "ACTIVE1", EMAIL, "confirmed"));
        registrationRepository.save(registration(event, "CANCEL1", "success+bob@simulator.amazonses.com",
                "cancelled"));
    }

    @Test
    @DisplayName("should_sendLinkAndRecordIt_when_firstCancelReply")
    void should_sendLinkAndRecordIt_when_firstCancelReply() {
        DeregistrationLinkResult result = deregistrationService.requestDeregistrationLinkByReply(EMAIL, EVENT_CODE);

        assertThat(result).isEqualTo(DeregistrationLinkResult.SENT);
        verify(deregistrationEmailService).sendDeregistrationLinkEmail(
                argThat(r -> r.getRegistrationCode().equals(EVENT_CODE + "-reg-ACTIVE1")), any(),
                argThat(link -> link.endsWith("/deregister?token=" + active.getDeregistrationToken())));
        Registration reloaded = registrationRepository.findById(active.getId()).orElseThrow();
        assertThat(reloaded.getDeclineLinkSentAt()).isNotNull();
        assertThat(reloaded.getStatus()).isEqualTo("confirmed");
    }

    @Test
    @DisplayName("should_sendNoSecondMail_when_secondRequestWithinSevenDays")
    void should_sendNoSecondMail_when_secondRequestWithinSevenDays() {
        deregistrationService.requestDeregistrationLinkByReply(EMAIL, EVENT_CODE);

        DeregistrationLinkResult second = deregistrationService.requestDeregistrationLinkByReply(EMAIL, EVENT_CODE);

        assertThat(second).isEqualTo(DeregistrationLinkResult.ALREADY_SENT);
        verify(deregistrationEmailService, times(1)).sendDeregistrationLinkEmail(any(), any(), any());
    }

    @Test
    @DisplayName("should_sendAgain_when_lastLinkOlderThanSevenDays")
    void should_sendAgain_when_lastLinkOlderThanSevenDays() {
        active.setDeclineLinkSentAt(Instant.now().minus(8, ChronoUnit.DAYS));
        registrationRepository.saveAndFlush(active);

        DeregistrationLinkResult result = deregistrationService.requestDeregistrationLinkByReply(EMAIL, EVENT_CODE);

        assertThat(result).isEqualTo(DeregistrationLinkResult.SENT);
        verify(deregistrationEmailService).sendDeregistrationLinkEmail(any(), any(), any());
    }

    @Test
    @DisplayName("should_sendNothing_when_registrationCancelledOrUnknown")
    void should_sendNothing_when_registrationCancelledOrUnknown() {
        assertThat(deregistrationService.requestDeregistrationLinkByReply(
                "success+bob@simulator.amazonses.com", EVENT_CODE)).isEqualTo(DeregistrationLinkResult.NOT_FOUND);
        assertThat(deregistrationService.requestDeregistrationLinkByReply(
                "success+nobody@simulator.amazonses.com", EVENT_CODE)).isEqualTo(DeregistrationLinkResult.NOT_FOUND);
        verify(deregistrationEmailService, never()).sendDeregistrationLinkEmail(any(), any(), any());
    }

    private static Registration registration(Event event, String suffix, String email, String status) {
        return Registration.builder()
                .registrationCode(EVENT_CODE + "-reg-" + suffix)
                .eventId(event.getId())
                .deregistrationToken(UUID.randomUUID())
                .attendeeUsername("user." + suffix.toLowerCase())
                .attendeeFirstName("Test")
                .attendeeLastName(suffix)
                .attendeeEmail(email)
                .status(status)
                .registrationDate(Instant.now())
                .build();
    }
}
