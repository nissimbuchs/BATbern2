package ch.batbern.migration.model.legacy;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * Legacy speaker data model from sessions.json referenten array
 */
@Data
public class LegacySpeaker {

    @JsonProperty("name")
    private String name;  // Format: "FirstName LastName, Company"

    @JsonProperty("bio")
    private String bio;

    @JsonProperty("company")
    private String company;  // Company identifier (normalized)

    @JsonProperty("portrait")
    private String portrait;  // Photo filename
}
