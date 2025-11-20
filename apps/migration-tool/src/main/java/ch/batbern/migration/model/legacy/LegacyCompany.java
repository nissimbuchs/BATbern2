package ch.batbern.migration.model.legacy;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Legacy Company Data
 *
 * Represents company data from docs/migration/companies.json (70 companies).
 * Structure: { metadata, companies: [ {id, displayName, ...} ] }
 *
 * Story: 3.2.1 - Migration Tool Implementation, AC 17-20
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LegacyCompany {

    /**
     * Company identifier (becomes Company.name after normalization)
     * Examples: "sbb", "mobiliar", "swisscom"
     * Max 12 chars alphanumeric
     */
    @JsonProperty("id")
    private String id;

    /**
     * Full official company name
     * Examples: "SBB CFF FFS", "Die Mobiliar"
     */
    @JsonProperty("displayName")
    private String displayName;

    /**
     * Company website URL
     * Examples: "https://www.sbb.ch"
     */
    @JsonProperty("url")
    private String url;

    /**
     * Logo filename (local file)
     * Examples: "sbb.jpg", "mobiliar.jpg"
     */
    @JsonProperty("logo")
    private String logo;

    /**
     * Logo URL from company website (optional)
     * If present, logo should be downloaded
     */
    @JsonProperty("logoUrl")
    private String logoUrl;

    /**
     * Absolute path to local logo file
     * Used for file migration
     */
    @JsonProperty("logoFilePath")
    private String logoFilePath;

    /**
     * Number of speaker mentions (for prioritization)
     */
    @JsonProperty("speakerCount")
    private Integer speakerCount;

    /**
     * Indicates if logo file exists locally
     */
    @JsonProperty("has_logo")
    private Boolean hasLogo;

    /**
     * Status: "complete", "needs_logo", "pending_url", "duplicate"
     * AC 17: Skip duplicates (status="duplicate")
     */
    @JsonProperty("status")
    private String status;

    /**
     * Additional notes (e.g., acquisitions, mergers)
     */
    @JsonProperty("note")
    private String note;
}
