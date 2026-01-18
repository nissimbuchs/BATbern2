package ch.batbern.events.listener;

import ch.batbern.events.domain.SpeakerInvitation;
import ch.batbern.events.event.InvitationRespondedEvent;
import ch.batbern.events.repository.SpeakerInvitationRepository;
import ch.batbern.events.service.InvitationEmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Listener for InvitationRespondedEvent to notify organizers when speakers respond.
 *
 * Story 6.2 - AC6: Response triggers organizer notification
 *
 * This listener:
 * - Listens for speaker response events
 * - Sends email notification to the organizer who sent the invitation
 * - Includes response type, decline reason (if declined), and preferences (if accepted)
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class InvitationResponseNotificationListener {

    private final SpeakerInvitationRepository invitationRepository;
    private final InvitationEmailService invitationEmailService;

    /**
     * Handle invitation responded events.
     *
     * Sends email notification to the organizer when a speaker responds to an invitation.
     *
     * This method is:
     * - Asynchronous: Runs in a separate thread to avoid blocking the response API
     * - Non-blocking: Exceptions are logged but don't break the response flow
     * - Idempotent: Safe to call multiple times (email sending is idempotent)
     * - Transactional: Runs in a new transaction to ensure fresh data read
     *
     * @param event the invitation responded event
     */
    @Async
    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public void handleInvitationResponded(InvitationRespondedEvent event) {
        try {
            log.info("Received InvitationRespondedEvent for invitation {} (speaker: {}, response: {})",
                    event.getAggregateId(), event.getUsername(), event.getResponseType());

            // Fetch the invitation to get organizer email and full details
            UUID invitationId = event.getAggregateId();
            SpeakerInvitation invitation = invitationRepository.findById(invitationId)
                    .orElse(null);

            if (invitation == null) {
                log.warn("Invitation not found with ID: {} - skipping organizer notification", invitationId);
                return;
            }

            // Get organizer email (createdBy is the organizer username)
            String organizerUsername = invitation.getCreatedBy();
            String organizerEmail = getOrganizerEmail(organizerUsername);

            if (organizerEmail == null || organizerEmail.isBlank()) {
                log.warn("Cannot determine email for organizer {} - skipping notification", organizerUsername);
                return;
            }

            // Send notification email
            invitationEmailService.sendOrganizerNotificationEmail(
                    invitation,
                    event.getResponseType(),
                    organizerEmail
            );

            log.info("Organizer notification sent for invitation {} response", invitationId);

        } catch (Exception e) {
            // Log error but don't break the response flow
            log.error("Failed to send organizer notification for invitation {}: {}",
                    event.getAggregateId(), e.getMessage(), e);
        }
    }

    /**
     * Get organizer email from username.
     *
     * TODO: Integrate with User Service API to fetch actual email.
     * For now, assumes username might be email format.
     */
    private String getOrganizerEmail(String username) {
        // If username looks like an email, use it directly
        if (username != null && username.contains("@")) {
            return username;
        }
        // TODO: Call User Service API to get email by username
        log.debug("Cannot determine email for username: {} - need User Service integration", username);
        return null;
    }
}
