package ch.batbern.events.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
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
public class SpeakerPool {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(columnDefinition = "UUID")
    private UUID id;

    @Column(name = "event_id", nullable = false, columnDefinition = "UUID")
    private UUID eventId;

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
     * Valid values (from V14 migration):
     * - 'identified' (default) - potential speaker identified
     * - 'contacted' - speaker has been contacted
     * - 'ready' - speaker is ready to be invited
     * - 'accepted' - speaker accepted invitation
     * - 'declined' - speaker declined
     * - 'content_submitted' - speaker submitted content
     * - 'quality_reviewed' - content has been reviewed
     * - 'slot_assigned' - speaker assigned to time slot
     * - 'confirmed' - speaker confirmed attendance
     * - 'withdrew' - speaker withdrew from event
     * - 'overflow' - speaker added to overflow list
     */
    @Column(name = "status", nullable = false, length = 50)
    private String status = "identified";

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // Constructors

    public SpeakerPool() {
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
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
