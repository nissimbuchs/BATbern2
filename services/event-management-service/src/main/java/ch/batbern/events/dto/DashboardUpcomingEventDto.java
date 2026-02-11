package ch.batbern.events.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;

/**
 * DTO for an upcoming event in the speaker dashboard.
 * Story 6.4: Speaker Dashboard (View-Only) - AC2, AC4, AC5
 */
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DashboardUpcomingEventDto(
        String eventCode,
        String eventTitle,
        String eventDate,
        String eventLocation,
        String sessionTitle,
        String workflowState,
        String workflowStateLabel,
        String contentStatus,
        String contentStatusLabel,
        boolean hasTitle,
        boolean hasAbstract,
        boolean hasMaterial,
        String materialFileName,
        String responseDeadline,
        String contentDeadline,
        String reviewerFeedback,
        String organizerName,
        String organizerEmail,
        String respondUrl,
        String profileUrl,
        String contentUrl
) {}
