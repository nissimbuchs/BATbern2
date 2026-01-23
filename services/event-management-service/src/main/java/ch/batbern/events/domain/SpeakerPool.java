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
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SpeakerPool {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(columnDefinition = "UUID")
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

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // Getters and Setters

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getEventId() {
        return eventId;
    }

    public void setEventId(UUID eventId) {
        this.eventId = eventId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getSpeakerName() {
        return speakerName;
    }

    public void setSpeakerName(String speakerName) {
        this.speakerName = speakerName;
    }

    public String getCompany() {
        return company;
    }

    public void setCompany(String company) {
        this.company = company;
    }

    public String getExpertise() {
        return expertise;
    }

    public void setExpertise(String expertise) {
        this.expertise = expertise;
    }

    public String getAssignedOrganizerId() {
        return assignedOrganizerId;
    }

    public void setAssignedOrganizerId(String assignedOrganizerId) {
        this.assignedOrganizerId = assignedOrganizerId;
    }

    public SpeakerWorkflowState getStatus() {
        return status;
    }

    public void setStatus(SpeakerWorkflowState status) {
        this.status = status;
    }

    public UUID getSessionId() {
        return sessionId;
    }

    public void setSessionId(UUID sessionId) {
        this.sessionId = sessionId;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    // Story 6.1b: Speaker Invitation System getters and setters

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public Instant getInvitedAt() {
        return invitedAt;
    }

    public void setInvitedAt(Instant invitedAt) {
        this.invitedAt = invitedAt;
    }

    public LocalDate getResponseDeadline() {
        return responseDeadline;
    }

    public void setResponseDeadline(LocalDate responseDeadline) {
        this.responseDeadline = responseDeadline;
    }

    public LocalDate getContentDeadline() {
        return contentDeadline;
    }

    public void setContentDeadline(LocalDate contentDeadline) {
        this.contentDeadline = contentDeadline;
    }

    // Story 6.2a: Speaker Response Portal getters and setters

    public Instant getAcceptedAt() {
        return acceptedAt;
    }

    public void setAcceptedAt(Instant acceptedAt) {
        this.acceptedAt = acceptedAt;
    }

    public Instant getDeclinedAt() {
        return declinedAt;
    }

    public void setDeclinedAt(Instant declinedAt) {
        this.declinedAt = declinedAt;
    }

    public String getDeclineReason() {
        return declineReason;
    }

    public void setDeclineReason(String declineReason) {
        this.declineReason = declineReason;
    }

    public Boolean getIsTentative() {
        return isTentative;
    }

    public void setIsTentative(Boolean isTentative) {
        this.isTentative = isTentative;
    }

    public String getTentativeReason() {
        return tentativeReason;
    }

    public void setTentativeReason(String tentativeReason) {
        this.tentativeReason = tentativeReason;
    }

    public String getPreferredTimeSlot() {
        return preferredTimeSlot;
    }

    public void setPreferredTimeSlot(String preferredTimeSlot) {
        this.preferredTimeSlot = preferredTimeSlot;
    }

    public String getTravelRequirements() {
        return travelRequirements;
    }

    public void setTravelRequirements(String travelRequirements) {
        this.travelRequirements = travelRequirements;
    }

    public String getTechnicalRequirements() {
        return technicalRequirements;
    }

    public void setTechnicalRequirements(String technicalRequirements) {
        this.technicalRequirements = technicalRequirements;
    }

    public String getInitialPresentationTitle() {
        return initialPresentationTitle;
    }

    public void setInitialPresentationTitle(String initialPresentationTitle) {
        this.initialPresentationTitle = initialPresentationTitle;
    }

    public String getPreferenceComments() {
        return preferenceComments;
    }

    public void setPreferenceComments(String preferenceComments) {
        this.preferenceComments = preferenceComments;
    }

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
