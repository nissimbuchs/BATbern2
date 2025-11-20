package ch.batbern.migration.model.target;

import java.util.UUID;

/**
 * Target DTO for SessionUser junction entity
 * Maps to Event Management Service POST /api/session-users
 *
 * Story: 3.2.1 - AC12: SessionUser Junction Creation
 */
public class SessionUserDto {
    private UUID sessionId;
    private UUID userId;
    private String role = "SPEAKER";  // Default role for migrated sessions

    public UUID getSessionId() {
        return sessionId;
    }

    public void setSessionId(UUID sessionId) {
        this.sessionId = sessionId;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }
}
