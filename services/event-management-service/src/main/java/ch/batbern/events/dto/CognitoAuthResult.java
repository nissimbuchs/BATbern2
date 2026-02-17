package ch.batbern.events.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Story 9.3 Task 3.4: DTO for Cognito authentication result received from company-user-management-service.
 * Mirrors ch.batbern.companyuser.dto.CognitoAuthResult.
 */
@Data
@NoArgsConstructor
public class CognitoAuthResult {
    private String accessToken;
    private String idToken;
    private String refreshToken;
}
