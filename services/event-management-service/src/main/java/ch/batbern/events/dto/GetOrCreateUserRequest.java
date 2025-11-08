package ch.batbern.events.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for getting or creating a user profile via User Management Service API.
 * <p>
 * Story 2.2a: Anonymous Event Registration (ADR-005)
 * <p>
 * Used for anonymous event registration where users register without creating a Cognito account.
 * If cognitoSync=false, creates user with cognito_id=NULL (anonymous user).
 * If cognitoSync=true, expects Cognito authentication and links to Cognito account.
 * <p>
 * If user already exists (by email), returns existing user profile.
 * If user doesn't exist, creates new user profile.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GetOrCreateUserRequest {

    @NotBlank(message = "First name is required")
    private String firstName;

    @NotBlank(message = "Last name is required")
    private String lastName;

    @NotBlank(message = "Email is required")
    @Email(message = "Valid email is required")
    private String email;

    @NotNull(message = "Cognito sync flag is required")
    private Boolean cognitoSync; // false = anonymous user, true = Cognito-linked user
}
