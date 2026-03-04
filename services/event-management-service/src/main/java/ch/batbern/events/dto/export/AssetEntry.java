package ch.batbern.events.dto.export;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

/**
 * Single asset entry in the asset manifest.
 * Story 10.20: AC2
 */
@Data
@Builder
public class AssetEntry {

    /** Asset type: portrait, logo, material */
    private String type;

    private UUID entityId;
    private String filename;
    private String presignedUrl;
}
