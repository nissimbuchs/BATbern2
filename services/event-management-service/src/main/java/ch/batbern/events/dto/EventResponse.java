package ch.batbern.events.dto;

import ch.batbern.events.domain.Event;
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

    /**
     * Convert Event entity to EventResponse DTO
     * @deprecated Use {@link ch.batbern.events.mapper.EventMapper#toDto(Event)} instead.
     *             Will be removed in Phase 3 of BAT-90.
     */
    @Deprecated(forRemoval = true)
    public static EventResponse fromEntity(Event event) {
        return EventResponse.builder()
                .eventCode(event.getEventCode())
                .title(event.getTitle())
                .eventNumber(event.getEventNumber())
                .date(event.getDate())
                .registrationDeadline(event.getRegistrationDeadline())
                .venueName(event.getVenueName())
                .venueAddress(event.getVenueAddress())
                .venueCapacity(event.getVenueCapacity())
                .organizerUsername(event.getOrganizerUsername())
                .currentAttendeeCount(event.getCurrentAttendeeCount())
                .publishedAt(event.getPublishedAt())
                .metadata(event.getMetadata())
                .description(event.getDescription())
                .createdAt(event.getCreatedAt())
                .updatedAt(event.getUpdatedAt())
                .createdBy(event.getCreatedBy())
                .updatedBy(event.getUpdatedBy())
                .themeImageUrl(event.getThemeImageUrl())
                .themeImageUploadId(event.getThemeImageUploadId())
                .eventType(event.getEventType() != null ? event.getEventType().getValue() : null)
                .topicCode(event.getTopicCode())
                .workflowState(event.getWorkflowState() != null ? event.getWorkflowState().name() : null)
                .currentPublishedPhase(event.getCurrentPublishedPhase() != null
                        ? event.getCurrentPublishedPhase().toUpperCase() : null)
                .build();
    }

    /**
     * Convert Event entity to EventResponse DTO with actual registration count
     * Used when displaying event lists and details to show accurate registration numbers
     *
     * @param event The event entity
     * @param actualRegistrationCount The actual count from registrations table
     * @return EventResponse with accurate registration count
     * @deprecated Use {@link ch.batbern.events.mapper.EventMapper#toDto(Event, long)} instead.
     *             Will be removed in Phase 3 of BAT-90.
     */
    @Deprecated(forRemoval = true)
    public static EventResponse fromEntity(Event event, long actualRegistrationCount) {
        return EventResponse.builder()
                .eventCode(event.getEventCode())
                .title(event.getTitle())
                .eventNumber(event.getEventNumber())
                .date(event.getDate())
                .registrationDeadline(event.getRegistrationDeadline())
                .venueName(event.getVenueName())
                .venueAddress(event.getVenueAddress())
                .venueCapacity(event.getVenueCapacity())
                .organizerUsername(event.getOrganizerUsername())
                .currentAttendeeCount((int) actualRegistrationCount)
                .publishedAt(event.getPublishedAt())
                .metadata(event.getMetadata())
                .description(event.getDescription())
                .createdAt(event.getCreatedAt())
                .updatedAt(event.getUpdatedAt())
                .createdBy(event.getCreatedBy())
                .updatedBy(event.getUpdatedBy())
                .themeImageUrl(event.getThemeImageUrl())
                .themeImageUploadId(event.getThemeImageUploadId())
                .eventType(event.getEventType() != null ? event.getEventType().getValue() : null)
                .topicCode(event.getTopicCode())
                .workflowState(event.getWorkflowState() != null ? event.getWorkflowState().name() : null)
                .build();
    }
}
