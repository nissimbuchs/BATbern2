package ch.batbern.gateway.config.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * AWS Cognito configuration for frontend authentication
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CognitoConfigDTO {

    /**
     * Cognito User Pool ID
     */
    private String userPoolId;

    /**
     * Cognito App Client ID
     */
    private String clientId;

    /**
     * AWS Region for Cognito
     */
    private String region;
}
