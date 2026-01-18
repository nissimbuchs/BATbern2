package ch.batbern.events.domain;

import ch.batbern.events.converter.InvitationStatusConverter;
import ch.batbern.events.converter.ResponseTypeConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Speaker Invitation entity - Story 6.1.
 *
 * Represents an invitation sent to a speaker for a specific event.
 * Follows ADR-003/ADR-004 compliance:
 * - References Speaker via username (meaningful ID, NOT userId UUID)
 * - References Event via event_code (meaningful ID, NOT eventId UUID)
 * - NO foreign key constraints (cross-service references)
 *
 * The response_token enables passwordless speaker responses.
 *
 * @see ch.batbern.events.domain.Speaker for speaker profiles
 */
@Entity
@Table(name = "speaker_invitations")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SpeakerInvitation {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(columnDefinition = "UUID")
    private UUID id;

    /**
     * Speaker username - meaningful identifier (ADR-003).
     * References Speaker in Event Management Service.
     * NO foreign key constraint (cross-service per ADR-004).
     */
    @Column(name = "username", nullable = false, length = 100)
    private String username;

    /**
     * Event code - meaningful identifier (ADR-003).
     * References Event in Event Management Service.
     * NO foreign key constraint for flexibility.
     */
    @Column(name = "event_code", nullable = false, length = 50)
    private String eventCode;

    /**
     * Unique 64-character cryptographic token for passwordless response.
     * Speaker uses this token to respond without authentication.
     */
    @Column(name = "response_token", nullable = false, unique = true, length = 64)
    private String responseToken;

    /**
     * Invitation status tracking.
     * Workflow: PENDING → SENT → OPENED → RESPONDED/EXPIRED
     * Stored as lowercase in database (e.g., 'pending').
     */
    @Column(name = "invitation_status", nullable = false, length = 50)
    @Convert(converter = InvitationStatusConverter.class)
    @Builder.Default
    private InvitationStatus invitationStatus = InvitationStatus.PENDING;

    /**
     * When the invitation email was sent.
     */
    @Column(name = "sent_at")
    private Instant sentAt;

    /**
     * When the speaker responded to the invitation.
     */
    @Column(name = "responded_at")
    private Instant respondedAt;

    /**
     * Type of response from speaker.
     * Stored as lowercase in database (e.g., 'accepted').
     */
    @Column(name = "response_type", length = 50)
    @Convert(converter = ResponseTypeConverter.class)
    private ResponseType responseType;

    /**
     * Reason provided when speaker declines.
     */
    @Column(name = "decline_reason", columnDefinition = "TEXT")
    private String declineReason;

    /**
     * When the invitation expires.
     */
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    /**
     * Number of reminder emails sent.
     */
    @Column(name = "reminder_count")
    @Builder.Default
    private Integer reminderCount = 0;

    /**
     * When the last reminder was sent.
     */
    @Column(name = "last_reminder_at")
    private Instant lastReminderAt;

    /**
     * AWS SES message ID for tracking email delivery.
     */
    @Column(name = "email_message_id", length = 255)
    private String emailMessageId;

    /**
     * When the email was opened (via SES tracking).
     */
    @Column(name = "email_opened_at")
    private Instant emailOpenedAt;

    /**
     * Record creation timestamp.
     */
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /**
     * Record last update timestamp.
     */
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * Organizer username who sent the invitation.
     */
    @Column(name = "created_by", nullable = false, length = 100)
    private String createdBy;

    // ========== Story 6.2: Speaker Response Portal Fields ==========

    /**
     * Personalized message from organizer explaining why this speaker was chosen.
     * Displayed as "Why we chose you" on the response portal.
     */
    @Column(name = "personal_message", columnDefinition = "TEXT")
    private String personalMessage;

    /**
     * Speaker's preferred time slot for presenting.
     * Values: MORNING, AFTERNOON, NO_PREFERENCE (stored as lowercase in DB).
     */
    @Column(name = "preferred_time_slot", length = 50)
    private String preferredTimeSlot;

    /**
     * Speaker's travel/accommodation requirements.
     * Values: LOCAL, ACCOMMODATION, VIRTUAL (stored as lowercase in DB).
     */
    @Column(name = "travel_requirements", length = 50)
    private String travelRequirements;

    /**
     * Technical requirements requested by speaker.
     * Comma-separated list: mac_adapter, remote_option, special_av
     */
    @Column(name = "technical_requirements", columnDefinition = "TEXT")
    private String technicalRequirements;

    /**
     * Initial presentation title proposed by speaker when accepting.
     */
    @Column(name = "initial_presentation_title", length = 200)
    private String initialPresentationTitle;

    /**
     * Additional comments from speaker to organizer.
     */
    @Column(name = "comments_for_organizer", columnDefinition = "TEXT")
    private String commentsForOrganizer;

    /**
     * General notes from speaker response.
     */
    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    /**
     * Check if invitation has expired.
     */
    public boolean isExpired() {
        return expiresAt != null && Instant.now().isAfter(expiresAt);
    }

    /**
     * Check if invitation can still be responded to.
     */
    public boolean canRespond() {
        return !isExpired()
                && invitationStatus != InvitationStatus.RESPONDED
                && invitationStatus != InvitationStatus.EXPIRED;
    }
}
