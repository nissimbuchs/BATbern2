package ch.batbern.companyuser.dto;

import lombok.Builder;
import lombok.Data;

/**
 * Story 9.3: Result of a successful Cognito user authentication.
 * Contains Cognito tokens returned by AdminInitiateAuth.
 */
@Data
@Builder
public class CognitoAuthResult {

    /** Cognito access token (short-lived, for API calls) */
    private String accessToken;

    /** Cognito ID token (contains user claims including custom:role) */
    private String idToken;

    /** Cognito refresh token (long-lived, for token refresh) */
    private String refreshToken;
}
