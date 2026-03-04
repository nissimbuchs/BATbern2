package ch.batbern.events.dto.export;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;

/**
 * Asset manifest response — presigned-URL list for all binary assets.
 * Story 10.20: AC2
 *
 * Each URL is valid for 1 hour.
 */
@Data
@Builder
public class AssetManifestResponse {

    private Instant exportedAt;
    private int assetCount;
    private List<AssetEntry> assets;
}
