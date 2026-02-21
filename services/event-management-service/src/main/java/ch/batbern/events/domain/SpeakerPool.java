package ch.batbern.events.domain;

import ch.batbern.events.converter.SpeakerWorkflowStateConverter;
import ch.batbern.shared.types.SpeakerWorkflowState;
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
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Speaker Pool entity for event brainstorming phase (Story 5.2 AC9-13).
 *
 * Represents potential speakers during event planning, before they become confirmed speakers.
 * Organizers brainstorm and track potential speakers here, assigning them for outreach.
 */
@Entity
@Table(name = "speaker_pool")
@Data
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SpeakerPool {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(columnDefinition = "UUID")
    @EqualsAndHashCode.Include
    private UUID id;

    @Column(name = "event_id", nullable = false, columnDefinition = "UUID")
    private UUID eventId;

    @Column(name = "username", length = 255)
    private String username; // Username for authenticated speakers (Story BAT-11)

    @Column(name = "speaker_name", nullable = false, length = 255)
    private String speakerName;

    @Column(name = "company", length = 255)
    private String company;

    @Column(name = "expertise", columnDefinition = "TEXT")
    private String expertise;

    @Column(name = "assigned_organizer_id", length = 255)
    private String assignedOrganizerId; // Username (not UUID)

    /**
     * Status field representing speaker pool workflow state.
     * Uses SpeakerWorkflowState enum with automatic conversion to database format.
     *
     * Database storage: lowercase_with_underscores (e.g., 'identified', 'contacted')
     * Java representation: UPPER_CASE (e.g., IDENTIFIED, CONTACTED)
     *
     * Workflow states:
     * - IDENTIFIED (default) - potential speaker identified
     * - CONTACTED - speaker has been contacted
     * - READY - speaker is ready to be invited
     * - ACCEPTED - speaker accepted invitation
     * - DECLINED - speaker declined
     * - CONTENT_SUBMITTED - speaker submitted content
     * - QUALITY_REVIEWED - content has been reviewed
     * - SLOT_ASSIGNED - speaker assigned to time slot
     * - CONFIRMED - speaker confirmed attendance
     * - WITHDREW - speaker withdrew from event
     * - OVERFLOW - speaker added to overflow list
     *
     * Story 5.3: Updated to use SpeakerWorkflowState enum with converter
     */
    @Column(name = "status", nullable = false, length = 50)
    @Convert(converter = SpeakerWorkflowStateConverter.class)
    private SpeakerWorkflowState status = SpeakerWorkflowState.IDENTIFIED;

    @Column(name = "session_id", columnDefinition = "UUID")
    private UUID sessionId;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    // Story 6.1b: Speaker Invitation System fields
    @Column(name = "email", length = 255)
    private String email;

    @Column(name = "invited_at")
    private Instant invitedAt;

    @Column(name = "response_deadline")
    private LocalDate responseDeadline;

    @Column(name = "content_deadline")
    private LocalDate contentDeadline;

    // Story 6.2a: Speaker Response Portal fields
    @Column(name = "accepted_at")
    private Instant acceptedAt;

    @Column(name = "declined_at")
    private Instant declinedAt;

    @Column(name = "decline_reason", columnDefinition = "TEXT")
    private String declineReason;

    @Column(name = "is_tentative")
    private Boolean isTentative = false;

    @Column(name = "tentative_reason", columnDefinition = "TEXT")
    private String tentativeReason;

    @Column(name = "preferred_time_slot", length = 100)
    private String preferredTimeSlot;

    @Column(name = "travel_requirements", columnDefinition = "TEXT")
    private String travelRequirements;

    @Column(name = "technical_requirements", columnDefinition = "TEXT")
    private String technicalRequirements;

    @Column(name = "initial_presentation_title", length = 500)
    private String initialPresentationTitle;

    @Column(name = "preference_comments", columnDefinition = "TEXT")
    private String preferenceComments;

    // Story 6.5: Automated Deadline Reminders
    @Column(name = "reminders_disabled")
    private Boolean remindersDisabled = false;

    // Story 6.3: Speaker Content Submission Portal fields
    @Column(name = "content_status", length = 50)
    private String contentStatus = "PENDING"; // PENDING, SUBMITTED, APPROVED, REVISION_NEEDED

    @Column(name = "content_submitted_at")
    private Instant contentSubmittedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

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
}
