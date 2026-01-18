package ch.batbern.events.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Response DTO for Session entity with speakers
 * Story 1.15a.1b: Session-User Many-to-Many Relationship
 * Story 1.16.2: Uses sessionSlug as public identifier
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionResponse {

    // Public identifiers (Story 1.16.2)
    private String sessionSlug;
    private String eventCode; // Transient field set from path parameter

    // Session details
    private String title;
    private String description;
    private String sessionType; // keynote, presentation, workshop, panel_discussion, networking, break, lunch
    private String startTime; // ISO-8601 format
    private String endTime; // ISO-8601 format
    private String room;
    private Integer capacity;
    private String language;

    // Timestamps
    private String createdAt; // ISO-8601 format
    private String updatedAt; // ISO-8601 format

    // Story 1.15a.1b: Speakers array (enriched with User data)
    private List<SessionSpeakerResponse> speakers;

    // Story 5.9: Session materials
    private List<SessionMaterialResponse> materials;

    // Story 5.9: Materials count (for overview displays)
    private Integer materialsCount;

    // Story 5.9: Materials status (NONE, PARTIAL, COMPLETE)
    private String materialsStatus;
}
