package ch.batbern.events.notification;

import ch.batbern.events.client.UserApiClient;
import ch.batbern.events.config.CumsContractHarness;
import ch.batbern.events.core.dto.generated.EventType;
import ch.batbern.events.domain.Event;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.shared.events.EventWorkflowTransitionEvent;
import ch.batbern.shared.events.SpeakerAcceptedEvent;
import ch.batbern.shared.events.SpeakerWorkflowStateChangeEvent;
import ch.batbern.shared.test.AbstractIntegrationTest;
import ch.batbern.shared.types.EventWorkflowState;
import ch.batbern.shared.types.SpeakerWorkflowState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * #1068: every organizer notification names who caused it, and a speaker state change carries
 * its event code (so deleting the event also deletes the notification). #1222: the notification
 * is an IN_APP row read over REST; nothing is pushed.
 *
 * <p>Real PostgreSQL, the real {@code UserApiClientImpl} with CUMS behind the
 * {@link CumsContractHarness}. The listener is built from the context's beans instead of taken from
 * it, so the call runs on this thread rather than through its {@code @Async} proxy (C6 touch rule:
 * replaces the former Mockito unit test).
 */
class OrganizerNotificationListenerIntegrationTest extends AbstractIntegrationTest {

    private static final String EVENT_CODE = "BATbern764";
    private static final String ORGANIZER = "anna.organizer";

    @Autowired
    private NotificationRepository notificationRepository;
    @Autowired
    private UserApiClient userApiClient;
    @Autowired
    private EventRepository eventRepository;
    @Autowired
    private CumsContractHarness cums;

    private OrganizerNotificationListener listener;

    @BeforeEach
    void setUp() {
        cleanUp();
        cums.usersWithRole("ORGANIZER", ORGANIZER);
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        eventRepository.saveAndFlush(Event.builder()
                .eventCode(EVENT_CODE).title("Notification Listener Event").eventNumber(764)
                .date(now.plus(40, ChronoUnit.DAYS)).registrationDeadline(now.plus(39, ChronoUnit.DAYS))
                .venueName("Test Venue").venueAddress("Bern").venueCapacity(100)
                .organizerUsername("org.user").currentAttendeeCount(0)
                .eventType(EventType.EVENING).workflowState(EventWorkflowState.SLOT_ASSIGNMENT)
                .build());
        listener = new OrganizerNotificationListener(notificationRepository, userApiClient, eventRepository);
    }

    @AfterEach
    void cleanUp() {
        cums.reset();
        notificationRepository.deleteAll(saved());
        eventRepository.findByEventCode(EVENT_CODE).ifPresent(eventRepository::delete);
    }

    private List<Notification> saved() {
        return notificationRepository.findAll().stream()
                .filter(n -> ORGANIZER.equals(n.getRecipientUsername()))
                .toList();
    }

    private Notification single() {
        List<Notification> rows = saved();
        assertThat(rows).hasSize(1);
        return rows.get(0);
    }

    // The listener is @Async: a test that creates and at once deletes an event would otherwise
    // get notifications written after the delete removed the event's notifications.
    @Test
    void should_skipNotification_when_eventNoLongerExists() {
        listener.onAnyDomainEvent(new EventWorkflowTransitionEvent(
                "BATbern99999", EventWorkflowState.CREATED, EventWorkflowState.TOPIC_SELECTION,
                "batbern.organizer", Instant.now(), false, null));

        assertThat(saved()).isEmpty();
    }

    @Test
    void should_writeUnreadInAppRow_when_domainEventArrives() {
        listener.onAnyDomainEvent(new EventWorkflowTransitionEvent(
                EVENT_CODE, EventWorkflowState.SLOT_ASSIGNMENT, EventWorkflowState.AGENDA_PUBLISHED,
                "nissim.buchs", Instant.now(), false, null));

        Notification notification = single();
        assertThat(notification.getChannel()).isEqualTo("IN_APP");
        assertThat(notification.getStatus()).isEqualTo("UNREAD");
        assertThat(notification.getEventCode()).isEqualTo(EVENT_CODE);
    }

    @Test
    void should_nameInitiatorAndTargetState_when_eventWorkflowTransitions() {
        listener.onAnyDomainEvent(new EventWorkflowTransitionEvent(
                EVENT_CODE, EventWorkflowState.SLOT_ASSIGNMENT, EventWorkflowState.AGENDA_PUBLISHED,
                "nissim.buchs", Instant.now(), false, null));

        assertThat(single().getBody()).isEqualTo("nissim.buchs moved " + EVENT_CODE + " to Agenda Published");
    }

    @Test
    void should_carryEventCodeAndNameInitiator_when_speakerWorkflowStateChanges() {
        SpeakerWorkflowStateChangeEvent event = new SpeakerWorkflowStateChangeEvent(
                UUID.randomUUID(), UUID.randomUUID(),
                SpeakerWorkflowState.CONTACTED, SpeakerWorkflowState.READY, "nissim.buchs");
        event.getMetadata().put("eventCode", EVENT_CODE);

        listener.onAnyDomainEvent(event);

        Notification notification = single();
        assertThat(notification.getEventCode()).isEqualTo(EVENT_CODE);
        assertThat(notification.getBody())
                .isEqualTo("nissim.buchs moved a speaker of " + EVENT_CODE + " from Contacted to Ready");
    }

    @Test
    void should_nameInitiator_when_speakerEventWithoutStates() {
        SpeakerAcceptedEvent event = new SpeakerAcceptedEvent(
                UUID.randomUUID(), EVENT_CODE, UUID.randomUUID(),
                "Jane Speaker", "Acme", "Architecture", "nissim.buchs");

        listener.onAnyDomainEvent(event);

        assertThat(single().getBody()).startsWith("nissim.buchs - Speaker Accepted");
    }
}
