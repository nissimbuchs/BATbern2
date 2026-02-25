package ch.batbern.events.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Request DTO for newsletter subscription (Story 10.7 — AC2).
 */
@Data
public class NewsletterSubscribeRequest {

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email address")
    private String email;

    @Size(max = 100)
    private String firstName;

    /** Language preference: "de" or "en". Defaults to "de". */
    private String language = "de";
}
