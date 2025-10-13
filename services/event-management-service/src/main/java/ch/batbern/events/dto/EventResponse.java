package ch.batbern.events.dto;

import ch.batbern.events.domain.Event;
import lombok.*;
import java.time.Instant;

/**
 * Event Response DTO
 * Story 1.15a.1: Events API Consolidation
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EventResponse {
    private String id;
    private String title;
    private Instant date;
    private String status;
    private String description;
    private String venueId;
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
                .date(event.getDate())
                .status(event.getStatus())
                .description(event.getDescription())
                .venueId(event.getVenueId())
                .createdAt(event.getCreatedAt())
                .updatedAt(event.getUpdatedAt())
                .createdBy(event.getCreatedBy())
                .updatedBy(event.getUpdatedBy())
                .build();
    }
}
