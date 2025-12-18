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
 * Outreach History entity for tracking organizer contact attempts with potential speakers.
 * Story 5.3: Speaker Outreach Tracking
 *
 * Represents individual contact attempts made by organizers to speakers in the speaker pool.
 * Tracks the method of contact, date, and notes about the conversation.
 *
 * Architecture: Lives in event-management-service (Epic 5 organizer workflow domain)
 * Database: speaker_outreach_history table (V16 migration)
 */
@Entity
@Table(name = "speaker_outreach_history")
public class OutreachHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(columnDefinition = "UUID")
    private UUID id;

    /**
     * Reference to the speaker in the speaker pool.
     * Foreign key to speaker_pool.id
     */
    @Column(name = "speaker_pool_id", nullable = false, columnDefinition = "UUID")
    private UUID speakerPoolId;

    /**
     * Date and time when contact was made.
     * Used for timeline visualization and days-since-contact calculation.
     */
    @Column(name = "contact_date", nullable = false)
    private Instant contactDate;

    /**
     * Method of contact: email, phone, in_person
     * Stored as lowercase string in database (matches V16 CHECK constraint)
     */
    @Column(name = "contact_method", nullable = false, length = 50)
    private String contactMethod;

    /**
     * Free-text notes about the conversation.
     * Organizers record speaker response, next steps, availability, etc.
     */
    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    /**
     * Username of the organizer who made the contact.
     * NOT a UUID - stores the actual username for display.
     */
    @Column(name = "organizer_username", nullable = false, length = 100)
    private String organizerUsername;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // Constructors

    public OutreachHistory() {
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

    public UUID getSpeakerPoolId() {
        return speakerPoolId;
    }

    public void setSpeakerPoolId(UUID speakerPoolId) {
        this.speakerPoolId = speakerPoolId;
    }

    public Instant getContactDate() {
        return contactDate;
    }

    public void setContactDate(Instant contactDate) {
        this.contactDate = contactDate;
    }

    public String getContactMethod() {
        return contactMethod;
    }

    public void setContactMethod(String contactMethod) {
        this.contactMethod = contactMethod;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public String getOrganizerUsername() {
        return organizerUsername;
    }

    public void setOrganizerUsername(String organizerUsername) {
        this.organizerUsername = organizerUsername;
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

    // Lifecycle callbacks

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
