package ch.batbern.companyuser.service;

import ch.batbern.companyuser.domain.User;
import ch.batbern.companyuser.domain.UserAdditionalEmail;
import ch.batbern.companyuser.dto.generated.AdditionalEmail;
import ch.batbern.companyuser.dto.generated.UserResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;

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
                log.warn("Invalid profile picture URL for user {}: {}",
                        user.getUsername(), user.getProfilePictureUrl());
            }
        }

        return new UserResponse()
                .id(user.getUsername())  // Story 1.16.2: username as id
                .hasCognitoAccount(user.getCognitoUserId() != null)
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
                .createdAt(user.getCreatedAt() != null
                        ? user.getCreatedAt().atOffset(java.time.ZoneOffset.UTC) : null)
                .updatedAt(user.getUpdatedAt() != null
                        ? user.getUpdatedAt().atOffset(java.time.ZoneOffset.UTC) : null)
                .lastLoginAt(user.getLastLoginAt() != null
                        ? user.getLastLoginAt().atOffset(java.time.ZoneOffset.UTC) : null)
                // Story 10.32: flatten additional emails. Always present; may be
                // empty. Consumed by the SES forwarder Lambda (sender-auth +
                // address-resolver) and by the user-settings UI.
                .additionalEmails(mapAdditionalEmails(user));
    }

    private List<AdditionalEmail> mapAdditionalEmails(User user) {
        if (user.getAdditionalEmails() == null || user.getAdditionalEmails().isEmpty()) {
            return Collections.emptyList();
        }
        return user.getAdditionalEmails().stream()
                .map(UserResponseMapper::mapAdditionalEmailToDto)
                .toList();
    }

    /**
     * Story 10.32 — map a {@link UserAdditionalEmail} entity to its OpenAPI
     * DTO. Lives on this mapper (not on {@code UserService}) so the DTO
     * layout is a mapper-only concern; the service stays focused on business
     * logic. Static so it can be used both from list streams and from
     * {@code UserService.addAdditionalEmail} without instantiating the
     * mapper bean.
     */
    public static AdditionalEmail mapAdditionalEmailToDto(UserAdditionalEmail row) {
        AdditionalEmail dto = new AdditionalEmail();
        dto.setEmail(row.getEmail());
        dto.setLabel(row.getLabel());
        dto.setCreatedAt(row.getCreatedAt() != null
                ? OffsetDateTime.ofInstant(row.getCreatedAt(), ZoneOffset.UTC)
                : null);
        dto.setVerifiedAt(row.getVerifiedAt() != null
                ? OffsetDateTime.ofInstant(row.getVerifiedAt(), ZoneOffset.UTC)
                : null);
        return dto;
    }
}
