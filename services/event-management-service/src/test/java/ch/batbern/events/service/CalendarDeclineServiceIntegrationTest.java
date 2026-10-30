package ch.batbern.events.service;

import ch.batbern.events.client.UserApiClient;
import ch.batbern.events.config.TestAwsConfig;
import ch.batbern.events.config.TestSecurityConfig;
import ch.batbern.events.core.dto.generated.EventType;
import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.Registration;
import ch.batbern.events.forwarding.CalendarReply;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.repository.RegistrationRepository;
import ch.batbern.events.service.CalendarDeclineService.Outcome;
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
 * Story 16.0 Part B (#1161) against real PostgreSQL: a calendar "Declined" from a registrant sends
 * the deregistration-link mail; the registration stays until the link is clicked.
 */
@Transactional
@Import({TestSecurityConfig.class, TestAwsConfig.class})
@DisplayName("Calendar declines (PostgreSQL)")
class CalendarDeclineServiceIntegrationTest extends AbstractIntegrationTest {

    private static final String SENDER = "success+decliner@simulator.amazonses.com";

    @Autowired
    private CalendarDeclineService service;
    @Autowired
    private EventRepository eventRepository;
    @Autowired
    private RegistrationRepository registrationRepository;
    @MockitoBean
    private DeregistrationEmailService deregistrationEmailService;
    @MockitoBean
    private UserApiClient userApiClient;

    private Event event;
    private Instant eventStart;

    @BeforeEach
    void setUp() {
        eventStart = Instant.now().plus(30, ChronoUnit.DAYS).truncatedTo(ChronoUnit.HOURS);
        event = eventRepository.save(event("BATbern912", 912, eventStart));
    }

    @Test
    @DisplayName("should_sendLink_when_uidMatchesActiveRegistration")
    void should_sendLink_when_uidMatchesActiveRegistration() {
        Registration registration = registrationRepository.save(registration(event, "UID1", SENDER, "confirmed"));

        Outcome outcome = service.handleDecline("success+other@simulator.amazonses.com", reply(
                "reg-" + registration.getCalendarUid() + "@batbern.ch", eventStart));

        // Matched by UID even though the reply came from another address (forwarded invite)
        assertThat(outcome).isEqualTo(Outcome.LINK_SENT);
        verify(deregistrationEmailService).sendDeregistrationLinkEmail(
                argThat(r -> r.getRegistrationCode().equals("BATbern912-reg-UID1")), any(), any());
        assertThat(registrationRepository.findById(registration.getId()).orElseThrow().getStatus())
                .isEqualTo("confirmed");
    }

    @Test
    @DisplayName("should_matchBySenderAndEventDate_when_oldInviteHasRandomUid")
    void should_matchBySenderAndEventDate_when_oldInviteHasRandomUid() {
        registrationRepository.save(registration(event, "OLD1", SENDER, "registered"));

        Outcome outcome = service.handleDecline(SENDER.toUpperCase(),
                reply(UUID.randomUUID() + "@batbern.ch", eventStart.plus(18, ChronoUnit.HOURS)));

        assertThat(outcome).isEqualTo(Outcome.LINK_SENT);
        verify(deregistrationEmailService).sendDeregistrationLinkEmail(
                argThat(r -> r.getRegistrationCode().equals("BATbern912-reg-OLD1")), any(), any());
    }

    @Test
    @DisplayName("should_doNothing_when_fallbackIsAmbiguous")
    void should_doNothing_when_fallbackIsAmbiguous() {
        Event sameDay = eventRepository.save(event("BATbern913", 913, eventStart.plus(2, ChronoUnit.HOURS)));
        registrationRepository.save(registration(event, "AMB1", SENDER, "confirmed"));
        registrationRepository.save(registration(sameDay, "AMB2", SENDER, "confirmed"));

        Outcome outcome = service.handleDecline(SENDER, reply(UUID.randomUUID() + "@batbern.ch", eventStart));

        assertThat(outcome).isEqualTo(Outcome.UNMATCHED);
        verify(deregistrationEmailService, never()).sendDeregistrationLinkEmail(any(), any(), any());
    }

    @Test
    @DisplayName("should_doNothing_when_noRegistrationNearTheDate")
    void should_doNothing_when_noRegistrationNearTheDate() {
        registrationRepository.save(registration(event, "FAR1", SENDER, "confirmed"));

        Outcome outcome = service.handleDecline(SENDER,
                reply(UUID.randomUUID() + "@batbern.ch", eventStart.plus(3, ChronoUnit.DAYS)));

        assertThat(outcome).isEqualTo(Outcome.UNMATCHED);
        verify(deregistrationEmailService, never()).sendDeregistrationLinkEmail(any(), any(), any());
    }

    @Test
    @DisplayName("should_doNothing_when_registrationAlreadyCancelled")
    void should_doNothing_when_registrationAlreadyCancelled() {
        Registration cancelled = registrationRepository.save(registration(event, "CXL1", SENDER, "cancelled"));

        Outcome outcome = service.handleDecline(SENDER,
                reply("reg-" + cancelled.getCalendarUid() + "@batbern.ch", eventStart));

        assertThat(outcome).isEqualTo(Outcome.NOT_ACTIVE);
        verify(deregistrationEmailService, never()).sendDeregistrationLinkEmail(any(), any(), any());
    }

    @Test
    @DisplayName("should_sendOneMailOnly_when_declinedTwiceWithinSevenDays")
    void should_sendOneMailOnly_when_declinedTwiceWithinSevenDays() {
        Registration registration = registrationRepository.save(registration(event, "TWO1", SENDER, "confirmed"));
        CalendarReply decline = reply("reg-" + registration.getCalendarUid() + "@batbern.ch", eventStart);

        service.handleDecline(SENDER, decline);
        Outcome second = service.handleDecline(SENDER, decline);

        assertThat(second).isEqualTo(Outcome.ALREADY_SENT);
        verify(deregistrationEmailService, times(1)).sendDeregistrationLinkEmail(any(), any(), any());
    }

    private static CalendarReply reply(String uid, Instant start) {
        return new CalendarReply(uid, "DECLINED", "BATbern Test Event", start);
    }

    private static Event event(String code, int number, Instant date) {
        return Event.builder()
                .eventCode(code)
                .title("BATbern Test Event #" + number)
                .eventNumber(number)
                .date(date)
                .registrationDeadline(date.minus(1, ChronoUnit.DAYS))
                .venueName("Test Venue")
                .venueAddress("Bern")
                .venueCapacity(200)
                .organizerUsername("organizer")
                .currentAttendeeCount(0)
                .eventType(EventType.EVENING)
                .workflowState(EventWorkflowState.AGENDA_PUBLISHED)
                .build();
    }

    private static Registration registration(Event event, String suffix, String email, String status) {
        return Registration.builder()
                .registrationCode(event.getEventCode() + "-reg-" + suffix)
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
