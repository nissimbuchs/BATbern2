package ch.batbern.gateway.config;

import ch.batbern.gateway.auth.CognitoJWKSProvider;
import ch.batbern.gateway.auth.CognitoJWTValidator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Cognito Configuration
 *
 * Provides beans for Cognito JWT validation
 */
@Configuration
public class CognitoConfig {

    @Value("${cognito.userPoolId}")
    private String userPoolId;

    @Value("${cognito.region}")
    private String region;

    @Value("${cognito.appClientId}")
    private String appClientId;

    @Bean
    public CognitoJWTValidator cognitoJWTValidator(CognitoJWKSProvider jwksProvider) {
        return new CognitoJWTValidator(jwksProvider, userPoolId, region, appClientId);
    }
}
