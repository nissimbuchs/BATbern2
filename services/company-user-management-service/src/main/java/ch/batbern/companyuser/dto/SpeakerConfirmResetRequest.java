package ch.batbern.companyuser.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Story 9.3 Task 2.3: Request DTO for confirming a Cognito password reset.
 * M4 fix: @Email on email field.
 */
@Data
public class SpeakerConfirmResetRequest {

    @NotBlank
    @Email
    private String email;

    @NotBlank
    private String confirmationCode;

    @NotBlank
    @Size(min = 8)
    private String newPassword;
}
