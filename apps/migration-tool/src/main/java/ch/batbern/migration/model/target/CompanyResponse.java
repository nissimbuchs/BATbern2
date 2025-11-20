package ch.batbern.migration.model.target;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Company Response from Target API
 *
 * Response from POST /api/companies (Company Management Service).
 * Contains the newly created company with assigned UUID.
 *
 * Story: 3.2.1 - Migration Tool Implementation, AC 17-20
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CompanyResponse {

    /**
     * Company UUID assigned by target system
     * Used for entity ID mapping (legacy id → new UUID)
     */
    @JsonProperty("id")
    private UUID id;

    @JsonProperty("name")
    private String name;

    @JsonProperty("displayName")
    private String displayName;

    @JsonProperty("website")
    private String website;

    @JsonProperty("logoS3Key")
    private String logoS3Key;

    @JsonProperty("logoFileId")
    private String logoFileId;

    @JsonProperty("logoCdnUrl")
    private String logoCdnUrl;

    @JsonProperty("isVerified")
    private Boolean isVerified;

    @JsonProperty("description")
    private String description;
}
