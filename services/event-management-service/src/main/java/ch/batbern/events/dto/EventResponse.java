package ch.batbern.events.dto;

import ch.batbern.events.domain.Event;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.Instant;

/**
 * Event Response DTO
 * Story 1.16.2: Eliminate UUIDs from API
 * Exposes eventCode (String) as API identifier instead of UUID
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EventResponse {
    private String eventCode;
    private String title;
    private Integer eventNumber;
    private Instant date;
    private Instant registrationDeadline;
    private String venueName;
    private String venueAddress;
    private Integer venueCapacity;
    private String status;
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

    /**
     * Convert Event entity to EventResponse DTO
     */
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
                .status(event.getStatus())
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
                .build();
    }
}
