package ch.batbern.events.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Response DTO for the newsletter send-job status endpoint (Story 10.7 robustness).
 *
 * <p>Returned by {@code GET /api/v1/events/{eventCode}/newsletter/sends/{sendId}/status}.
 * Frontend polls this at 3-second intervals while status is PENDING or IN_PROGRESS.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NewsletterSendStatusResponse {

    private UUID id;

    /**
     * PENDING — job created, background thread not yet started.
     * IN_PROGRESS — background thread is actively sending.
     * COMPLETED — all recipients delivered.
     * PARTIAL — send finished but some recipients failed.
     * FAILED — send aborted; no or very few emails delivered.
     */
    private String status;

    private int sentCount;
    private int failedCount;

    /** Total number of subscribers targeted at the time the send was initiated. */
    private int totalCount;

    /** 0–100 percentage, derived server-side from sentCount + failedCount / totalCount. */
    private int percentComplete;

    private Instant startedAt;
    private Instant completedAt;
}
