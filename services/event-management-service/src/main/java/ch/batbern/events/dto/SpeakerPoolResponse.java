package ch.batbern.events.dto;

import ch.batbern.events.domain.SpeakerPool;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Response DTO for speaker pool entries (Story 5.2 AC9-13, Story 6.2a).
 */
public class SpeakerPoolResponse {

    private UUID id;
    private UUID eventId;
    private String speakerName;
    private String company;
    private String expertise;
    private String assignedOrganizerId;
    private String status;
    private UUID sessionId;
    private String notes;
    private Instant createdAt;
    private Instant updatedAt;

    // Story 6.1b: Speaker Invitation System fields
    private String username;
    private String email;
    private Instant invitedAt;
    private LocalDate responseDeadline;
    private LocalDate contentDeadline;

    // Story 6.2a: Speaker Response Portal fields
    private Instant acceptedAt;
    private Instant declinedAt;
    private String declineReason;
    private Boolean isTentative;
    private String tentativeReason;
    private String preferredTimeSlot;
    private String travelRequirements;
    private String technicalRequirements;
    private String initialPresentationTitle;
    private String preferenceComments;

    // Constructors

    public SpeakerPoolResponse() {
    }

    /**
     * Create response DTO from SpeakerPool entity.
     *
     * @param speakerPool the speaker pool entity
     * @return the response DTO
     */
    public static SpeakerPoolResponse fromEntity(SpeakerPool speakerPool) {
        SpeakerPoolResponse response = new SpeakerPoolResponse();
        response.id = speakerPool.getId();
        response.eventId = speakerPool.getEventId();
        response.speakerName = speakerPool.getSpeakerName();
        response.company = speakerPool.getCompany();
        response.expertise = speakerPool.getExpertise();
        response.assignedOrganizerId = speakerPool.getAssignedOrganizerId();
        response.status = speakerPool.getStatus() != null ? speakerPool.getStatus().name() : null;
        response.sessionId = speakerPool.getSessionId();
        response.notes = speakerPool.getNotes();
        response.createdAt = speakerPool.getCreatedAt();
        response.updatedAt = speakerPool.getUpdatedAt();

        // Story 6.1b: Speaker Invitation System fields
        response.username = speakerPool.getUsername();
        response.email = speakerPool.getEmail();
        response.invitedAt = speakerPool.getInvitedAt();
        response.responseDeadline = speakerPool.getResponseDeadline();
        response.contentDeadline = speakerPool.getContentDeadline();

        // Story 6.2a: Speaker Response Portal fields
        response.acceptedAt = speakerPool.getAcceptedAt();
        response.declinedAt = speakerPool.getDeclinedAt();
        response.declineReason = speakerPool.getDeclineReason();
        response.isTentative = speakerPool.getIsTentative();
        response.tentativeReason = speakerPool.getTentativeReason();
        response.preferredTimeSlot = speakerPool.getPreferredTimeSlot();
        response.travelRequirements = speakerPool.getTravelRequirements();
        response.technicalRequirements = speakerPool.getTechnicalRequirements();
        response.initialPresentationTitle = speakerPool.getInitialPresentationTitle();
        response.preferenceComments = speakerPool.getPreferenceComments();

        return response;
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

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

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
}
