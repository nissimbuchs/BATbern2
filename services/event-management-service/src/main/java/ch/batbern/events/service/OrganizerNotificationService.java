package ch.batbern.events.service;

import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.SpeakerPool;
import ch.batbern.shared.types.SpeakerResponseType;

/**
 * Service for sending notifications to event organizers.
 * Story 6.2a: Invitation Response Portal - AC6
 *
 * Sends email notifications to organizers when speakers respond to invitations.
 * Notifications are sent asynchronously (non-blocking).
 */
public interface OrganizerNotificationService {

    /**
     * Notify organizer(s) that a speaker has responded to their invitation.
     *
     * @param speaker the speaker who responded
     * @param event the event for the invitation
     * @param responseType the type of response (ACCEPT, DECLINE, TENTATIVE)
     */
    void notifyOrganizerOfResponse(SpeakerPool speaker, Event event, SpeakerResponseType responseType);
}
