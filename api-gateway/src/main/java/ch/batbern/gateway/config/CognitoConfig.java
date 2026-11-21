package ch.batbern.gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;

/**
 * AWS Cognito configuration: the Cognito Identity Provider client. Tokens are verified by the shared
 * {@code BatbernJwtDecoder} ({@link SecurityConfig#jwtDecoder}), not here.
 */
@Configuration
public class CognitoConfig {

    @Value("${aws.cognito.region:${AWS_REGION:eu-central-1}}")
    private String region;

    /**
     * Cognito Identity Provider Client for password reset operations
     */
    @Bean
    public CognitoIdentityProviderClient cognitoIdentityProviderClient() {
        return CognitoIdentityProviderClient.builder()
            .region(Region.of(region))
            .credentialsProvider(DefaultCredentialsProvider.builder().build())
            .build();
    }
}
