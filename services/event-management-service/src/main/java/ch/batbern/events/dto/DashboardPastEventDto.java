package ch.batbern.events.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;

/**
 * DTO for a past event in the speaker dashboard.
 * Story 6.4: Speaker Dashboard (View-Only) - AC3
 */
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DashboardPastEventDto(
        String eventCode,
        String eventTitle,
        String eventDate,
        String sessionTitle,
        boolean hasMaterial,
        String materialFileName
) {}
