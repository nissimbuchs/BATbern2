package ch.batbern.events.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Response DTO for batch session import operation
 *
 * Contains summary statistics and detailed results for each session
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchImportSessionResult {

    /**
     * Total number of sessions processed (attempted to import)
     */
    private int totalProcessed;

    /**
     * Number of sessions successfully created
     */
    private int successfullyCreated;

    /**
     * Number of existing sessions updated (materials added)
     */
    private int updated;

    /**
     * Number of sessions skipped (e.g., duplicates without changes)
     */
    private int skipped;

    /**
     * Number of sessions that failed to import
     */
    private int failed;

    /**
     * Detailed results for each session import attempt
     */
    private List<SessionImportDetail> details;
}
