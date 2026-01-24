package ch.batbern.events.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Event Response DTO
 * Story 1.16.2: Eliminate UUIDs from API
 * Story BAT-109: Archive browsing with resource expansion
 * Exposes eventCode (String) as API identifier instead of UUID
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL) // Omit null fields from JSON response
public class EventResponse {
    // Core event fields
    private String eventCode;
    private String title;
    private Integer eventNumber;
    private Instant date;
    private Instant registrationDeadline;
    private String venueName;
    private String venueAddress;
    private Integer venueCapacity;
    private String organizerUsername;
    private Integer currentAttendeeCount;
    private Instant publishedAt;
    private String metadata;
    private String description;
    private Instant createdAt;
    private Instant updatedAt;
    private String createdBy;
    private String updatedBy;
    private String themeImageUrl;
    private String themeImageUploadId;
    private String eventType;
    private String topicCode;
    private String workflowState;
    private String currentPublishedPhase;

    // Optional expanded resources (Story BAT-109: Archive browsing)
    // These fields are populated only when requested via ?include parameter
    private Map<String, Object> topic;        // Populated with include=topics
    private Map<String, Object> venue;        // Populated with include=venue
    private List<Map<String, Object>> sessions; // Populated with include=sessions (includes speakers)

    // Optional metrics fields (populated with include=metrics)
    private Integer confirmedSpeakersCount;
    private Integer speakersWithCompleteInfoCount;
    private Integer pendingMaterialsCount;
    private Integer maxSpeakerSlots;
    private Integer sessionsWithMaterialsCount;
    private Integer totalSessionsCount;

    // Note: Static fromEntity() methods removed in Phase 3 (BAT-91).
    // Use EventMapper.toDto() instead.
}
