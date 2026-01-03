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
