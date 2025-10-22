package ch.batbern.companyuser.dto;

import ch.batbern.companyuser.domain.Role;
import ch.batbern.companyuser.domain.UserPreferences;
import ch.batbern.companyuser.domain.UserSettings;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Set;

/**
 * User response DTO
 * Story 1.16.2: id field contains username (meaningful ID), not UUID
 * AC14: Resource expansion support (?include=company,preferences,settings)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)  // Omit null expanded resources
public class UserResponse {
    private String id;  // Story 1.16.2: username (e.g., "john.doe"), not UUID
    private String email;
    private String firstName;
    private String lastName;
    private String bio;
    private String companyId;  // Story 1.16.2: company name (e.g., "GoogleZH"), not UUID
    private Set<Role> roles;
    private String profilePictureUrl;
    private boolean active;  // Serialized as "active" (frontend adapted to use this name)
    private Instant createdAt;
    private Instant updatedAt;
    private Instant lastLoginAt;

    // Expanded resources (populated when ?include= parameter is used)
    private CompanyDTO company;      // Expanded when include=company
    private UserPreferences preferences;  // Expanded when include=preferences
    private UserSettings settings;    // Expanded when include=settings

    /**
     * Simplified Company DTO for expansion
     * Full company details come from Company Management Service
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CompanyDTO {
        private String id;  // Company name (e.g., "GoogleZH")
        private String name;  // Display name
        // Additional fields would be fetched from Company Management Service
    }
}
