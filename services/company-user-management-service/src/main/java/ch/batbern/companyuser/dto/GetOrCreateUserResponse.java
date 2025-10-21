package ch.batbern.companyuser.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Get-or-create user response DTO
 * Story 1.16.2: userId contains username (meaningful ID), not UUID
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GetOrCreateUserResponse {
    private String userId;  // Story 1.16.2: username (e.g., "john.doe"), not UUID
    private boolean created;
    private String cognitoUserId;
    private UserResponse user;
}
