package ch.batbern.events.dto;

import ch.batbern.events.domain.InvitationStatus;
import ch.batbern.events.domain.ResponseType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Response DTO for speaker invitation details - Story 6.1, extended in Story 6.2.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InvitationResponse {

    private UUID id;
    private String username;
    private String eventCode;
    private InvitationStatus invitationStatus;
    private Instant sentAt;
    private Instant respondedAt;
    private ResponseType responseType;
    private String declineReason;
    private Instant expiresAt;
    private Integer reminderCount;
    private Instant lastReminderAt;
    private Instant createdAt;
    private String createdBy;

    // Enriched fields (from User Service)
    private String speakerFirstName;
    private String speakerLastName;
    private String speakerEmail;

    // Enriched fields (from Event Service)
    private String eventTitle;
    private Instant eventDate;

    // Story 6.2: Speaker Response Portal fields
    /**
     * Personalized message from organizer explaining why this speaker was chosen.
     * Displayed as "Why we chose you" on the response portal.
     */
    private String personalMessage;

    /**
     * Speaker preferences (populated after ACCEPTED response).
     */
    private SpeakerResponsePreferences preferences;

    /**
     * General notes from speaker response.
     */
    private String notes;
}
