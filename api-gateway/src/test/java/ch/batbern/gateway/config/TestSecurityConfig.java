package ch.batbern.gateway.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cloudwatchlogs.CloudWatchLogsClient;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Test Security Configuration
 *
 * Provides mock JWT decoder and AWS SDK clients for integration tests
 */
@TestConfiguration
@Profile("test")
public class TestSecurityConfig {

    /**
     * Mock JwtDecoder that accepts any "Bearer mock-jwt-token" and returns a test JWT
     */
    @Bean
    @Primary
    public JwtDecoder mockJwtDecoder() {
        return new JwtDecoder() {
            @Override
            public Jwt decode(String token) throws JwtException {
                // Create a mock JWT with test claims
                Map<String, Object> headers = new HashMap<>();
                headers.put("alg", "HS256");
                headers.put("typ", "JWT");

                Map<String, Object> claims = new HashMap<>();
                claims.put("sub", "test-user-123");
                claims.put("email", "test@example.com");
                claims.put("email_verified", true);
                claims.put("custom:role", "organizer");
                claims.put("custom:companyId", "test-company-123");
                claims.put("iss", "https://cognito-idp.eu-central-1.amazonaws.com/eu-central-1_TestPool");
                claims.put("aud", "test-client-id");
                claims.put("token_use", "access");
                claims.put("auth_time", Instant.now().getEpochSecond());
                claims.put("iat", Instant.now().getEpochSecond());
                claims.put("exp", Instant.now().plusSeconds(3600).getEpochSecond());

                return Jwt.withTokenValue(token)
                        .headers(h -> h.putAll(headers))
                        .claims(c -> c.putAll(claims))
                        .subject("test-user-123")
                        .issuedAt(Instant.now())
                        .expiresAt(Instant.now().plusSeconds(3600))
                        .build();
            }
        };
    }

    /**
     * Mock AWS Credentials Provider for tests
     * Prevents AWS SDK from trying to load credentials from environment
     */
    @Bean
    @Primary
    public AwsCredentialsProvider testAwsCredentialsProvider() {
        return StaticCredentialsProvider.create(
                AwsBasicCredentials.create("test-access-key", "test-secret-key")
        );
    }

    /**
     * Mock Cognito Identity Provider Client for tests
     * Prevents AWS SDK from trying to connect to real Cognito
     */
    @Bean
    @Primary
    public CognitoIdentityProviderClient testCognitoClient(AwsCredentialsProvider credentialsProvider) {
        return CognitoIdentityProviderClient.builder()
                .credentialsProvider(credentialsProvider)
                .region(Region.EU_CENTRAL_1)
                .build();
    }

    /**
     * Mock CloudWatch Logs Client for tests
     * Prevents AWS SDK from trying to connect to real CloudWatch
     */
    @Bean
    @Primary
    public CloudWatchLogsClient testCloudWatchLogsClient(AwsCredentialsProvider credentialsProvider) {
        return CloudWatchLogsClient.builder()
                .credentialsProvider(credentialsProvider)
                .region(Region.EU_CENTRAL_1)
                .build();
    }
}
