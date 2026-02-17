package ch.batbern.events.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Story 9.4: Aggregate migration report returned by Epic9MigrationService.
 * Contains per-speaker outcomes and aggregate counts.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MigrationReport {

    private int total;
    private int provisionedNew;
    private int extended;
    private int emailsSent;
    private int errors;
    private List<SpeakerMigrationResult> results;
}
