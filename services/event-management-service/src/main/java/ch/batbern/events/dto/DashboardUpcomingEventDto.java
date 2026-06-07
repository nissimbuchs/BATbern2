package ch.batbern.events.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;

/**
 * DTO for an upcoming event in the speaker dashboard.
 * Story 6.4: Speaker Dashboard (View-Only) - AC2, AC4, AC5
 */
// 2026-05-20 (Q#D) — contentStatus / contentStatusLabel dropped end-to-end. The
// workflow state + per-field has* flags + reviewerFeedback cover the same info
// without the parallel-status confusion.
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
        String contentUrl
) {}
