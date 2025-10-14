package ch.batbern.events.dto;

import ch.batbern.events.domain.Event;
import lombok.*;
import java.time.Instant;
import java.util.UUID;

/**
 * Event Response DTO
 * Story 1.15a.1: Events API Consolidation
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EventResponse {
    private UUID id;
    private String title;
    private Integer eventNumber;
    private Instant date;
    private Instant registrationDeadline;
    private String venueName;
    private String venueAddress;
    private Integer venueCapacity;
    private String status;
    private UUID organizerId;
    private Integer currentAttendeeCount;
    private Instant publishedAt;
    private String metadata;
    private String description;
    private Instant createdAt;
    private Instant updatedAt;
    private String createdBy;
    private String updatedBy;

    /**
     * Convert Event entity to EventResponse DTO
     */
    public static EventResponse fromEntity(Event event) {
        return EventResponse.builder()
                .id(event.getId())
                .title(event.getTitle())
                .eventNumber(event.getEventNumber())
                .date(event.getDate())
                .registrationDeadline(event.getRegistrationDeadline())
                .venueName(event.getVenueName())
                .venueAddress(event.getVenueAddress())
                .venueCapacity(event.getVenueCapacity())
                .status(event.getStatus())
                .organizerId(event.getOrganizerId())
                .currentAttendeeCount(event.getCurrentAttendeeCount())
                .publishedAt(event.getPublishedAt())
                .metadata(event.getMetadata())
                .description(event.getDescription())
                .createdAt(event.getCreatedAt())
                .updatedAt(event.getUpdatedAt())
                .createdBy(event.getCreatedBy())
                .updatedBy(event.getUpdatedBy())
                .build();
    }
}
