package ch.batbern.migration.model.target;

import java.util.List;
import java.util.UUID;

/**
 * Target DTO for Session entity
 * Maps to Event Management Service POST /api/sessions
 *
 * Story: 3.2.1 - AC6: Session Migration
 */
public class SessionDto {
    private UUID eventId;
    private String title;
    private String description;
    private Integer orderInProgram;
    private String presentationFileS3Key;

    // Transient fields for SessionUser creation (not sent to API)
    private List<String> speakerNames;  // For lookup in entity_id_mapping

    public UUID getEventId() {
        return eventId;
    }

    public void setEventId(UUID eventId) {
        this.eventId = eventId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Integer getOrderInProgram() {
        return orderInProgram;
    }

    public void setOrderInProgram(Integer orderInProgram) {
        this.orderInProgram = orderInProgram;
    }

    public String getPresentationFileS3Key() {
        return presentationFileS3Key;
    }

    public void setPresentationFileS3Key(String presentationFileS3Key) {
        this.presentationFileS3Key = presentationFileS3Key;
    }

    public List<String> getSpeakerNames() {
        return speakerNames;
    }

    public void setSpeakerNames(List<String> speakerNames) {
        this.speakerNames = speakerNames;
    }
}
