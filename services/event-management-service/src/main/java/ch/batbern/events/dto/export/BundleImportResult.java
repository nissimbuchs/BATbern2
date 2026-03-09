package ch.batbern.events.dto.export;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Result of a bundle (ZIP) import operation that restores both data and binary assets.
 * Story 10.20: symmetric bundle import — POST /admin/import/bundle
 */
@Data
@Builder
public class BundleImportResult {

    /** Result of the data (JSON) import phase. */
    private LegacyImportResult dataResult;

    /** Number of binary assets successfully uploaded to S3 and linked to entities. */
    private int assetsImported;

    /** Per-asset error messages for assets that could not be restored. */
    private List<String> assetErrors;
}
