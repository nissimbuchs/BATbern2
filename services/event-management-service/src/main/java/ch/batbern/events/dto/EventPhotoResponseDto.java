package ch.batbern.events.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Response DTO for an event photo.
 * Story 10.21: Event Photos Gallery
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventPhotoResponseDto {

    private UUID id;
    private String eventCode;
    private String displayUrl;
    private String filename;
    private String uploadedBy;
    private Instant uploadedAt;
    private int sortOrder;
}
