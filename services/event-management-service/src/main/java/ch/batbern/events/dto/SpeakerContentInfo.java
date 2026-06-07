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
// 2026-05-20 (Q#E) — `contentStatus` field dropped. It was only read internally by
// ContentSubmissionService to compute `needsRevision`; no UI surface consumes the
// raw string. The portal reads `needsRevision` + `reviewerFeedback` directly. The
// speaker_pool workflow status is the single canonical status everywhere.
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

        // Current draft
        boolean hasDraft,
        String draftTitle,
        String draftAbstract,
        Integer draftVersion,
        Instant lastSavedAt,

        // Revision support (AC8)
        boolean needsRevision,
        String reviewerFeedback,
        Instant reviewedAt,
        String reviewedBy,

        // Material upload (AC7)
        boolean hasMaterial,
        String materialUrl,
        String materialFileName
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
                .hasDraft(false)
                .needsRevision(false)
                .hasMaterial(false)
                .materialUrl(null)
                .materialFileName(null)
                .build();
    }
}
