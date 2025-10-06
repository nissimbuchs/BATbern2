package ch.batbern.gateway.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for resend reset link endpoint
 *
 * Story 1.2.2 - AC7: Resend functionality
 */
public class ResendResetLinkRequest {

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    @Size(max = 255, message = "Email must not exceed 255 characters")
    private String email;

    public ResendResetLinkRequest() {
    }

    public ResendResetLinkRequest(String email) {
        this.email = email;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    @Override
    public String toString() {
        return "ResendResetLinkRequest{email='***'}";
    }
}
