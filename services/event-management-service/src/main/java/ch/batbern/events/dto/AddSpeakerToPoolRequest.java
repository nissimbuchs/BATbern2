package ch.batbern.events.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Request DTO for adding a speaker to the event speaker pool (Story 5.2 AC9-12).
 *
 * <p>Story 11.D.1 (AR23): {@code @JsonIgnoreProperties(ignoreUnknown = false)} —
 * speakers added via this endpoint stay in {@code IDENTIFIED}/{@code CONTACTED} per
 * ADR-009 §0.2. An {@code email} field (or any other unknown field) is rejected with
 * HTTP 400 rather than silently accepted; promotion + email capture happens via
 * {@code POST /speakers/{speakerId}/promote}.
 */
@JsonIgnoreProperties(ignoreUnknown = false)
public class AddSpeakerToPoolRequest {

    private String speakerName;
    private String company;
    private String expertise;
    private String assignedOrganizerId;
    private String notes;

    // Constructors

    public AddSpeakerToPoolRequest() {
    }

    public AddSpeakerToPoolRequest(String speakerName, String company, String expertise,
                                   String assignedOrganizerId, String notes) {
        this.speakerName = speakerName;
        this.company = company;
        this.expertise = expertise;
        this.assignedOrganizerId = assignedOrganizerId;
        this.notes = notes;
    }

    // Getters and Setters

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

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }
}
