package ch.batbern.events.dto;

/**
 * Request DTO for adding a speaker to the event speaker pool (Story 5.2 AC9-12).
 */
public class AddSpeakerToPoolRequest {

    private String speakerName;
    private String company;
    private String expertise;
    private String assignedOrganizerId;
    private String notes;
    private String email;
    private String phone;

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
