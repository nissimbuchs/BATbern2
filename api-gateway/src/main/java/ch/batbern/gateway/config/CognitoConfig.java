package ch.batbern.gateway.config;

import ch.batbern.gateway.auth.CognitoJWKSProvider;
import ch.batbern.gateway.auth.CognitoJWTValidator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.ses.SesClient;

/**
 * AWS Services Configuration
 *
 * Provides beans for Cognito JWT validation, Cognito Identity Provider, and SES
 */
@Configuration
public class CognitoConfig {

    @Value("${aws.cognito.userPoolId:#{null}}")
    private String userPoolId;

    @Value("${aws.cognito.region:${AWS_REGION:eu-central-1}}")
    private String region;

    @Value("${aws.cognito.appClientId:#{null}}")
    private String appClientId;

    @Bean
    public CognitoJWTValidator cognitoJWTValidator(CognitoJWKSProvider jwksProvider) {
        if (userPoolId == null || appClientId == null) {
            throw new IllegalStateException(
                "Cognito configuration is missing. Please set aws.cognito.userPoolId and aws.cognito.appClientId"
            );
        }
        return new CognitoJWTValidator(jwksProvider, userPoolId, region, appClientId);
    }

    /**
     * Cognito Identity Provider Client for password reset operations
     */
    @Bean
    public CognitoIdentityProviderClient cognitoIdentityProviderClient() {
        return CognitoIdentityProviderClient.builder()
            .region(Region.of(region))
            .credentialsProvider(DefaultCredentialsProvider.create())
            .build();
    }

    /**
     * SES Client for sending templated emails
     */
    @Bean
    public SesClient sesClient() {
        return SesClient.builder()
            .region(Region.of(region))
            .credentialsProvider(DefaultCredentialsProvider.create())
            .build();
    }
}
