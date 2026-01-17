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
 * Response DTO for speaker invitation details - Story 6.1.
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
}
