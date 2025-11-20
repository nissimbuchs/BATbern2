package ch.batbern.migration.model.target;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.UUID;

/**
 * User DTO for POST to Company Management API
 * Per ADR-004: Bio goes in User, NOT in Speaker
 */
@Data
public class UserDto {

    @JsonProperty("username")
    private String username;  // Generated from name (e.g., "thomas.goetz")

    @JsonProperty("email")
    private String email;  // Optional - may not exist in legacy data

    @JsonProperty("firstName")
    private String firstName;

    @JsonProperty("lastName")
    private String lastName;

    @JsonProperty("bio")
    private String bio;  // ADR-004: Bio in User entity

    @JsonProperty("companyId")
    private UUID companyId;  // Foreign key to Company

    @JsonProperty("profilePictureS3Key")
    private String profilePictureS3Key;  // S3 key for portrait photo

    @JsonProperty("role")
    private String role;  // Default: "SPEAKER" for migrated users
}
