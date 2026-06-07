package ch.batbern.partners.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

/**
 * Response DTO for the PCS test-fixture cleanup endpoint.
 *
 * <p>Reports deletion counts keyed by table. Cascade-deleted rows in dependent tables
 * (partner_meeting_attendance, partner_meeting_rsvps, partner_notes, topic_suggestions
 * → topic_votes) are not counted explicitly — PostgreSQL ON DELETE CASCADE handles them
 * transparently.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Result of a PCS test-fixture cleanup invocation")
public class TestFixtureCleanupResponse {

    @Schema(description = "Number of rows deleted, keyed by table name")
    private Map<String, Integer> deletionCounts;

    @Schema(description = "When the cleanup completed (server time)")
    private Instant executedAt;

    @Schema(description = "Echo of the requested entityType for audit-log correlation")
    private String entityType;

    @Schema(description = "Echo of the requested prefix for audit-log correlation")
    private String prefix;
}
