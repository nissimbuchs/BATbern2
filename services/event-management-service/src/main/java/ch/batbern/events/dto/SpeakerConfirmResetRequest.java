package ch.batbern.events.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Story 9.3 Task 3.4: DTO for confirming speaker password reset, sent to company-user-management-service.
 * Mirrors ch.batbern.companyuser.dto.SpeakerConfirmResetRequest.
 *
 * AC6: @Email + @NotBlank prevent email injection attacks (M4).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SpeakerConfirmResetRequest {
    @NotBlank
    @Email
    private String email;
    @NotBlank
    private String confirmationCode;
    @NotBlank
    private String newPassword;
}
