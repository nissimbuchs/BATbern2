package ch.batbern.migration.model.target;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Company DTO for Target API
 *
 * Data Transfer Object for POST /api/companies (Company Management Service).
 * Matches the API contract from Company Management Service.
 *
 * Story: 3.2.1 - Migration Tool Implementation, AC 17-20
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CompanyDto {

    /**
     * Company unique name (normalized, max 12 chars)
     * Examples: "sbb", "mobiliar", "swisscom"
     * AC 19: Normalized company names (max 12 chars, alphanumeric only)
     */
    @JsonProperty("name")
    private String name;

    /**
     * Full display name
     * Examples: "SBB CFF FFS", "Die Mobiliar"
     */
    @JsonProperty("displayName")
    private String displayName;

    /**
     * Company website URL
     */
    @JsonProperty("website")
    private String website;

    /**
     * Logo S3 key (after upload)
     * Pattern: company-logos/{companyName}/{fileId}.{ext}
     * AC 20: Logo migration to S3
     */
    @JsonProperty("logoS3Key")
    private String logoS3Key;

    /**
     * Logo file ID (UUID)
     * Used for S3 key generation
     */
    @JsonProperty("logoFileId")
    private String logoFileId;

    /**
     * Logo CDN URL (generated after S3 upload)
     * Pattern: https://cdn.batbern.ch/company-logos/{companyName}/{fileId}.{ext}
     */
    @JsonProperty("logoCdnUrl")
    private String logoCdnUrl;

    /**
     * Verification status
     * AC 18: All migrated companies are unverified
     */
    @JsonProperty("isVerified")
    private Boolean isVerified = false;

    /**
     * Description/notes (optional)
     */
    @JsonProperty("description")
    private String description;
}
