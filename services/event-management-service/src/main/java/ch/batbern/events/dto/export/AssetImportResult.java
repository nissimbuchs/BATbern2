package ch.batbern.events.dto.export;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;

/**
 * Result of a ZIP asset import operation.
 * Story 10.20: AC4
 */
@Data
@Builder
public class AssetImportResult {

    private Instant importedAt;
    private int importedCount;
    private String s3Prefix;
    private List<String> errors;
}
