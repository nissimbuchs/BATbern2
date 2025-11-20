package ch.batbern.migration.model.legacy;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * Legacy session data model from sessions.json
 */
@Data
public class LegacySession {

    @JsonProperty("bat")
    private Integer bat;  // Event number

    @JsonProperty("pdf")
    private String pdf;  // PDF filename

    @JsonProperty("title")
    private String title;

    @JsonProperty("abstract")
    private String sessionAbstract;  // "abstract" is a Java keyword

    @JsonProperty("authoren")
    private String authoren;  // Legacy field (German for "authors")

    @JsonProperty("referenten")
    private List<LegacySpeaker> referenten;  // Speakers list
}
