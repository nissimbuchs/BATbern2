package ch.batbern.events.dto;

import ch.batbern.events.domain.SpeakerPool;

import java.time.Instant;
import java.util.UUID;

/**
 * Response DTO for speaker pool entries (Story 5.2 AC9-13).
 */
public class SpeakerPoolResponse {

    private UUID id;
    private UUID eventId;
    private String speakerName;
    private String company;
    private String expertise;
    private String assignedOrganizerId;
    private String status;
    private String notes;
    private Instant createdAt;
    private Instant updatedAt;

    // Constructors

    public SpeakerPoolResponse() {
    }

    public SpeakerPoolResponse(UUID id, UUID eventId, String speakerName, String company,
                               String expertise, String assignedOrganizerId, String status,
                               String notes, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.eventId = eventId;
        this.speakerName = speakerName;
        this.company = company;
        this.expertise = expertise;
        this.assignedOrganizerId = assignedOrganizerId;
        this.status = status;
        this.notes = notes;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /**
     * Create response DTO from SpeakerPool entity.
     *
     * @param speakerPool the speaker pool entity
     * @return the response DTO
     */
    public static SpeakerPoolResponse fromEntity(SpeakerPool speakerPool) {
        return new SpeakerPoolResponse(
                speakerPool.getId(),
                speakerPool.getEventId(),
                speakerPool.getSpeakerName(),
                speakerPool.getCompany(),
                speakerPool.getExpertise(),
                speakerPool.getAssignedOrganizerId(),
                speakerPool.getStatus(),
                speakerPool.getNotes(),
                speakerPool.getCreatedAt(),
                speakerPool.getUpdatedAt()
        );
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
}
