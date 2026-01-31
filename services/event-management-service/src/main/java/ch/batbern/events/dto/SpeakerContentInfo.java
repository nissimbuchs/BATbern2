package ch.batbern.events.dto;

import lombok.Builder;

import java.time.Instant;

/**
 * DTO containing speaker content information for the portal.
 * Story 6.3: Speaker Content Self-Submission Portal
 *
 * Returned by GET /speaker-portal/content to display:
 * - Session assignment status (AC1)
 * - Current draft/submission (AC4)
 * - Revision feedback (AC8)
 */
@Builder
public record SpeakerContentInfo(
        // Speaker info
        String speakerName,
        String eventCode,
        String eventTitle,

        // Session assignment (AC1)
        boolean hasSessionAssigned,
        String sessionTitle,
        boolean canSubmitContent,

        // Current content status
        String contentStatus,
        boolean hasDraft,
        String draftTitle,
        String draftAbstract,
        Integer draftVersion,
        Instant lastSavedAt,

        // Revision support (AC8)
        boolean needsRevision,
        String reviewerFeedback,
        Instant reviewedAt,
        String reviewedBy
) {
    /**
     * Static factory for when no session is assigned.
     */
    public static SpeakerContentInfo noSession(String speakerName, String eventCode, String eventTitle) {
        return SpeakerContentInfo.builder()
                .speakerName(speakerName)
                .eventCode(eventCode)
                .eventTitle(eventTitle)
                .hasSessionAssigned(false)
                .canSubmitContent(false)
                .contentStatus("PENDING")
                .hasDraft(false)
                .needsRevision(false)
                .build();
    }
}
