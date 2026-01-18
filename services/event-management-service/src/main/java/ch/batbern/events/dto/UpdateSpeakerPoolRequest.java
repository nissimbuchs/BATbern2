package ch.batbern.events.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Request DTO for updating a speaker in the event speaker pool.
 *
 * Editable fields: speakerName, company, expertise, assignedOrganizerId, notes, email, phone
 * Non-editable fields: status, sessionId (managed by workflow)
 */
public class UpdateSpeakerPoolRequest {

    @NotBlank(message = "Speaker name is required")
    private String speakerName;
    private String company;
    private String expertise;
    private String assignedOrganizerId;
    private String notes;

    @NotBlank(message = "Email is required")
    @Email(message = "Email must be valid")
    private String email;

    @NotBlank(message = "Phone is required")
    private String phone;

    // Constructors

    public UpdateSpeakerPoolRequest() {
    }

    public UpdateSpeakerPoolRequest(String speakerName, String company, String expertise,
                                    String assignedOrganizerId, String notes, String email, String phone) {
        this.speakerName = speakerName;
        this.company = company;
        this.expertise = expertise;
        this.assignedOrganizerId = assignedOrganizerId;
        this.notes = notes;
        this.email = email;
        this.phone = phone;
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
}
