package ch.batbern.events.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for newsletter preview (Story 10.7 — AC10).
 * Contains rendered HTML and subject for preview without sending.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NewsletterPreviewResponse {

    private String subject;
    private String htmlPreview;
    private int recipientCount;
}
