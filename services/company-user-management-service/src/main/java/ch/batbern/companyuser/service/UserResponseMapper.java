package ch.batbern.companyuser.service;

import ch.batbern.companyuser.domain.User;
import ch.batbern.companyuser.dto.generated.UserResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Maps User entities to UserResponse DTOs
 * Story 1.16.2: Expose username as id, not UUID
 * Extracted from UserService to break circular dependency with UserSearchService
 */
@Component
@Slf4j
public class UserResponseMapper {

    /**
     * Map User entity to UserResponse DTO (generated from OpenAPI with Lombok builder)
     * Story 1.16.2: Expose username as id, not UUID
     */
    public UserResponse mapToResponse(User user) {
        // Convert String to URI for profilePictureUrl
        java.net.URI profilePictureUri = null;
        if (user.getProfilePictureUrl() != null) {
            try {
                profilePictureUri = java.net.URI.create(user.getProfilePictureUrl());
            } catch (IllegalArgumentException e) {
                log.warn("Invalid profile picture URL for user {}: {}", user.getUsername(), user.getProfilePictureUrl());
            }
        }

        return new UserResponse()
                .id(user.getUsername())  // Story 1.16.2: username as id
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .bio(user.getBio())
                .companyId(user.getCompanyId())  // Story 1.16.2: company name
                .roles(user.getRoles().stream()
                        .map(role -> UserResponse.RolesEnum.valueOf(role.name()))
                        .toList())
                .profilePictureUrl(profilePictureUri)
                .active(user.isActive())
                .createdAt(user.getCreatedAt() != null ?
                        user.getCreatedAt().atOffset(java.time.ZoneOffset.UTC) : null)
                .updatedAt(user.getUpdatedAt() != null ?
                        user.getUpdatedAt().atOffset(java.time.ZoneOffset.UTC) : null)
                .lastLoginAt(user.getLastLoginAt() != null ?
                        user.getLastLoginAt().atOffset(java.time.ZoneOffset.UTC) : null);
    }
}
