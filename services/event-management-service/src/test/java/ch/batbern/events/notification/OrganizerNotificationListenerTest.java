package ch.batbern.events.notification;

import ch.batbern.events.client.UserApiClient;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.shared.events.EventWorkflowTransitionEvent;
import ch.batbern.shared.events.SpeakerAcceptedEvent;
import ch.batbern.shared.events.SpeakerWorkflowStateChangeEvent;
import ch.batbern.shared.types.EventWorkflowState;
import ch.batbern.shared.types.SpeakerWorkflowState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * #1068: every organizer notification names who caused it, and a speaker state change carries
 * its event code (so deleting the event also deletes the notification).
 */
@ExtendWith(MockitoExtension.class)
class OrganizerNotificationListenerTest {

    @Mock
    private NotificationRepository notificationRepository;
    @Mock
    private UserApiClient userApiClient;
    @Mock
    private NotificationService notificationService;
    @Mock
    private EventRepository eventRepository;

    @InjectMocks
    private OrganizerNotificationListener listener;

    @BeforeEach
    void setUp() {
        lenient().when(userApiClient.getOrganizerUsernames()).thenReturn(List.of("anna.organizer"));
        lenient().when(notificationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(eventRepository.existsByEventCode("BATbern60")).thenReturn(true);
    }

    // The listener is @Async: a test that creates and at once deletes an event would otherwise
    // get notifications written after the delete removed the event's notifications.
    @Test
    void should_skipNotification_when_eventNoLongerExists() {
        when(eventRepository.existsByEventCode("BATbern99999")).thenReturn(false);

        listener.onAnyDomainEvent(new EventWorkflowTransitionEvent(
                "BATbern99999", EventWorkflowState.CREATED, EventWorkflowState.TOPIC_SELECTION,
                "batbern.organizer", Instant.now(), false, null));

        verify(notificationRepository, never()).save(any());
    }

    private Notification saved() {
        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());
        return captor.getValue();
    }

    @Test
    void should_nameInitiatorAndTargetState_when_eventWorkflowTransitions() {
        listener.onAnyDomainEvent(new EventWorkflowTransitionEvent(
                "BATbern60", EventWorkflowState.SLOT_ASSIGNMENT, EventWorkflowState.AGENDA_PUBLISHED,
                "nissim.buchs", Instant.now(), false, null));

        assertThat(saved().getBody()).isEqualTo("nissim.buchs moved BATbern60 to Agenda Published");
    }

    @Test
    void should_carryEventCodeAndNameInitiator_when_speakerWorkflowStateChanges() {
        SpeakerWorkflowStateChangeEvent event = new SpeakerWorkflowStateChangeEvent(
                UUID.randomUUID(), UUID.randomUUID(),
                SpeakerWorkflowState.CONTACTED, SpeakerWorkflowState.READY, "nissim.buchs");
        event.getMetadata().put("eventCode", "BATbern60");

        listener.onAnyDomainEvent(event);

        Notification notification = saved();
        assertThat(notification.getEventCode()).isEqualTo("BATbern60");
        assertThat(notification.getBody())
                .isEqualTo("nissim.buchs moved a speaker of BATbern60 from Contacted to Ready");
    }

    @Test
    void should_nameInitiator_when_speakerEventWithoutStates() {
        SpeakerAcceptedEvent event = new SpeakerAcceptedEvent(
                UUID.randomUUID(), "BATbern60", UUID.randomUUID(),
                "Jane Speaker", "Acme", "Architecture", "nissim.buchs");

        listener.onAnyDomainEvent(event);

        assertThat(saved().getBody()).startsWith("nissim.buchs - Speaker Accepted");
    }
}
