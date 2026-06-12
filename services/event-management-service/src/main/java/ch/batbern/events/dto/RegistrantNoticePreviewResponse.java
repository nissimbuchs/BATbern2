package ch.batbern.events.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Preview of a registrant-notice mail (Story 7.3 hardening — Registrant Notices tab).
 *
 * <p>Rendered subject + HTML for the organizer's chosen preview language, plus the count of
 * active registrants the send would reach. The actual send still resolves each registrant's own
 * language (AC4) — this preview locale only controls what the organizer sees.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegistrantNoticePreviewResponse {

    /** Rendered, variable-substituted subject line. */
    private String subject;

    /** Rendered, layout-merged HTML body for the preview iframe. */
    private String htmlPreview;

    /** Number of active registrants (registered/confirmed) who would receive the mail. */
    private int recipientCount;
}
