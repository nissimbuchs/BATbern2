package ch.batbern.migration.model.target;

import java.util.UUID;

/**
 * Response DTO from Event Management Service after session creation
 * Returned from POST /api/sessions
 *
 * Story: 3.2.1 - AC6: Session Migration
 */
public class SessionResponse {
    private UUID id;
    private UUID eventId;
    private String title;
    private Integer orderInProgram;

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

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public Integer getOrderInProgram() {
        return orderInProgram;
    }

    public void setOrderInProgram(Integer orderInProgram) {
        this.orderInProgram = orderInProgram;
    }
}
