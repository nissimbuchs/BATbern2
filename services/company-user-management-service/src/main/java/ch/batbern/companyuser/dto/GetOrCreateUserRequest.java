package ch.batbern.companyuser.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Get-or-create user request DTO
 * Used by domain services to ensure users exist before creating relationships
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GetOrCreateUserRequest {
    @NotBlank(message = "Email is required")
    @Email(message = "Email must be valid")
    private String email;

    @NotBlank(message = "First name is required")
    private String firstName;

    @NotBlank(message = "Last name is required")
    private String lastName;

    private String companyId;  // Story 1.16.2: company name (e.g., "GoogleZH")

    @Builder.Default
    private boolean createIfMissing = true;

    @Builder.Default
    private boolean cognitoSync = true;
}
