package ch.batbern.companyuser.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Story 9.3 Task 2.1: Request DTO for speaker email+password authentication.
 * Called by event-management-service via UserApiClient.
 * M4 fix: @Email prevents injection into Cognito auth calls.
 */
@Data
public class SpeakerAuthRequest {

    @NotBlank
    @Email
    private String email;

    @NotBlank
    private String password;
}
