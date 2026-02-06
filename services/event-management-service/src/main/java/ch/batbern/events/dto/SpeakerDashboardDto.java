package ch.batbern.events.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;

import java.util.List;

/**
 * DTO for the speaker dashboard summary.
 * Story 6.4: Speaker Dashboard (View-Only) - AC1-AC8
 *
 * Aggregates all speaker-event associations for a single speaker
 * into upcoming and past event lists.
 */
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SpeakerDashboardDto(
        String speakerName,
        String profilePictureUrl,
        int profileCompleteness,
        List<DashboardUpcomingEventDto> upcomingEvents,
        List<DashboardPastEventDto> pastEvents
) {}
