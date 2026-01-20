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
    private String username; // Story 6.3: Linked user account username (ADR-003)
    private String speakerName;
    private String company;
    private String expertise;
    private String assignedOrganizerId;
    private String status;
    private UUID sessionId;
    private String notes;
    private String email;
    private String phone;
    private String proposedPresentationTitle;
    private String commentsForOrganizer;
    private Instant createdAt;
    private Instant updatedAt;

    // Constructors

    public SpeakerPoolResponse() {
    }

    public SpeakerPoolResponse(UUID id, UUID eventId, String username, String speakerName, String company,
                               String expertise, String assignedOrganizerId, String status,
                               UUID sessionId, String notes, String email, String phone,
                               String proposedPresentationTitle, String commentsForOrganizer,
                               Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.eventId = eventId;
        this.username = username;
        this.speakerName = speakerName;
        this.company = company;
        this.expertise = expertise;
        this.assignedOrganizerId = assignedOrganizerId;
        this.status = status;
        this.sessionId = sessionId;
        this.notes = notes;
        this.email = email;
        this.phone = phone;
        this.proposedPresentationTitle = proposedPresentationTitle;
        this.commentsForOrganizer = commentsForOrganizer;
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
                speakerPool.getUsername(), // Story 6.3: Include linked username
                speakerPool.getSpeakerName(),
                speakerPool.getCompany(),
                speakerPool.getExpertise(),
                speakerPool.getAssignedOrganizerId(),
                speakerPool.getStatus() != null ? speakerPool.getStatus().name() : null,
                speakerPool.getSessionId(),
                speakerPool.getNotes(),
                speakerPool.getEmail(),
                speakerPool.getPhone(),
                speakerPool.getProposedPresentationTitle(),
                speakerPool.getCommentsForOrganizer(),
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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
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

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getProposedPresentationTitle() {
        return proposedPresentationTitle;
    }

    public void setProposedPresentationTitle(String proposedPresentationTitle) {
        this.proposedPresentationTitle = proposedPresentationTitle;
    }

    public String getCommentsForOrganizer() {
        return commentsForOrganizer;
    }

    public void setCommentsForOrganizer(String commentsForOrganizer) {
        this.commentsForOrganizer = commentsForOrganizer;
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
