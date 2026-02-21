package ch.batbern.events.service;

import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.SpeakerPool;
import ch.batbern.shared.types.SpeakerResponseType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Default implementation of OrganizerNotificationService.
 * Story 6.2a: Invitation Response Portal - AC6
 *
 * Sends asynchronous notifications to event organizers when speakers respond.
 * Actual notification delivery is handled via domain events and the
 * OrganizerNotificationListener.
 *
 * This implementation logs the notification and relies on the SpeakerResponseReceivedEvent
 * domain event to trigger in-app notifications to organizers via the existing
 * notification infrastructure.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class DefaultOrganizerNotificationService implements OrganizerNotificationService {

    /**
     * Notify organizer(s) that a speaker has responded to their invitation.
     *
     * This is called AFTER the domain event is published, so the
     * OrganizerNotificationListener will handle creating in-app notifications.
     * This method provides additional logging and could be extended to send
     * email notifications in the future.
     *
     * @param speaker the speaker who responded
     * @param event the event for the invitation
     * @param responseType the type of response (ACCEPT, DECLINE, TENTATIVE)
     */
    @Override
    @Async
    public void notifyOrganizerOfResponse(SpeakerPool speaker, Event event, SpeakerResponseType responseType) {
        log.info("Notifying organizer {} of speaker {} response: {} for event {}",
                event.getOrganizerUsername(),
                speaker.getSpeakerName(),
                responseType,
                event.getEventCode());

        // Domain event (SpeakerResponseReceivedEvent) is already published by the service,
        // which triggers OrganizerNotificationListener to create in-app notifications.
        //
        // Future enhancement: Add email notification here if needed.
        // For now, organizers receive in-app notifications via the domain event listener.
    }
}
