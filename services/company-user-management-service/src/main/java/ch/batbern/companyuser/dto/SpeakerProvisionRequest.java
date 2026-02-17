package ch.batbern.companyuser.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Story 9.2: Request DTO for provisioning a speaker Cognito account.
 * Called by event-management-service via UserApiClient when a speaker accepts an invitation.
 */
@Data
public class SpeakerProvisionRequest {

    @NotBlank
    @Email
    private String email;

    @NotBlank
    private String firstName;

    @NotBlank
    private String lastName;
}
