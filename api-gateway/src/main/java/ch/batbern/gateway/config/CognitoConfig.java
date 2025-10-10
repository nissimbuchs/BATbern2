package ch.batbern.gateway.config;

import ch.batbern.gateway.auth.CognitoJWKSProvider;
import ch.batbern.gateway.auth.CognitoJWTValidator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
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

    @Value("${aws.cognito.userPoolClientId:#{null}}")
    private String userPoolClientId;

    @Bean
    public CognitoJWTValidator cognitoJWTValidator(CognitoJWKSProvider jwksProvider) {
        if (userPoolId == null || userPoolClientId == null) {
            throw new IllegalStateException(
                "Cognito configuration is missing. Please set aws.cognito.userPoolId and aws.cognito.userPoolClientId"
            );
        }
        return new CognitoJWTValidator(jwksProvider, userPoolId, region, userPoolClientId);
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

    /**
     * JWT Decoder for Spring Security OAuth2 Resource Server
     * Configured to validate JWTs from AWS Cognito
     */
    @Bean
    public JwtDecoder jwtDecoder() {
        if (userPoolId == null || region == null) {
            throw new IllegalStateException(
                "Cognito configuration is missing. Please set aws.cognito.userPoolId and aws.cognito.region"
            );
        }

        // AWS Cognito JWKS URL format
        String jwksUrl = String.format(
            "https://cognito-idp.%s.amazonaws.com/%s/.well-known/jwks.json",
            region,
            userPoolId
        );

        return NimbusJwtDecoder.withJwkSetUri(jwksUrl).build();
    }
}
