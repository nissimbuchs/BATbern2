package ch.batbern.migration.model.target;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.UUID;

/**
 * Speaker DTO for POST to Speaker Coordination API
 * Per ADR-004: Bio is NOT in Speaker (it's in User)
 */
@Data
public class SpeakerDto {

    @JsonProperty("userId")
    private UUID userId;  // Foreign key to User entity

    @JsonProperty("expertise")
    private String expertise;  // Speaker's area of expertise (optional for legacy)

    @JsonProperty("isActive")
    private Boolean isActive;  // Default: true for migrated speakers
}
