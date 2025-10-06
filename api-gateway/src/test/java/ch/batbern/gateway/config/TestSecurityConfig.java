package ch.batbern.gateway.config;

import ch.batbern.gateway.auth.service.EmailService;
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
import software.amazon.awssdk.services.cognitoidentityprovider.model.*;
import software.amazon.awssdk.services.cloudwatchlogs.CloudWatchLogsClient;
import software.amazon.awssdk.services.cloudwatchlogs.model.*;

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
     * Uses Mockito to prevent AWS network calls and control responses
     * Fixes QA issue TEST-001: Backend test coverage at 40% due to AWS SDK mocking issues
     */
    @Bean
    @Primary
    public CognitoIdentityProviderClient mockCognitoClient() {
        CognitoIdentityProviderClient mockClient = org.mockito.Mockito.mock(CognitoIdentityProviderClient.class);

        // Mock successful forgotPassword response
        ForgotPasswordResponse successResponse = ForgotPasswordResponse.builder()
                .codeDeliveryDetails(CodeDeliveryDetailsType.builder()
                        .destination("t***@example.com")
                        .deliveryMedium(DeliveryMediumType.EMAIL)
                        .attributeName("email")
                        .build())
                .build();

        org.mockito.Mockito.when(mockClient.forgotPassword(org.mockito.ArgumentMatchers.any(ForgotPasswordRequest.class)))
                .thenAnswer(invocation -> {
                    ForgotPasswordRequest request = invocation.getArgument(0);
                    if (request.username().contains("nonexistent")) {
                        throw UserNotFoundException.builder()
                                .message("User not found")
                                .build();
                    }
                    return successResponse;
                });

        return mockClient;
    }

    /**
     * Mock Email Service for tests
     * Prevents actual email sending during tests
     */
    @Bean
    @Primary
    public EmailService mockEmailService() {
        EmailService mockService = org.mockito.Mockito.mock(EmailService.class);
        org.mockito.Mockito.doNothing().when(mockService).sendPasswordResetEmail(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString()
        );
        return mockService;
    }

    /**
     * Mock CloudWatch Logs Client for tests
     * Prevents AWS SDK from trying to connect to real CloudWatch
     */
    @Bean
    @Primary
    public CloudWatchLogsClient mockCloudWatchLogsClient() {
        CloudWatchLogsClient mockClient = org.mockito.Mockito.mock(CloudWatchLogsClient.class);

        org.mockito.Mockito.when(mockClient.createLogGroup(org.mockito.ArgumentMatchers.any(CreateLogGroupRequest.class)))
                .thenReturn(CreateLogGroupResponse.builder().build());

        org.mockito.Mockito.when(mockClient.createLogStream(org.mockito.ArgumentMatchers.any(CreateLogStreamRequest.class)))
                .thenReturn(CreateLogStreamResponse.builder().build());

        org.mockito.Mockito.when(mockClient.putLogEvents(org.mockito.ArgumentMatchers.any(PutLogEventsRequest.class)))
                .thenReturn(PutLogEventsResponse.builder().nextSequenceToken("test-sequence-token").build());

        return mockClient;
    }
}
