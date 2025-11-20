package ch.batbern.migration.model.target;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.UUID;

/**
 * Response from Speaker Coordination API after creating speaker
 */
@Data
public class SpeakerResponse {

    @JsonProperty("id")
    private UUID id;

    @JsonProperty("userId")
    private UUID userId;

    @JsonProperty("isActive")
    private Boolean isActive;
}
