package ch.batbern.gateway.config;

import ch.batbern.gateway.auth.service.EmailService;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.annotation.Order;
import org.springframework.core.Ordered;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.*;
import software.amazon.awssdk.services.cloudwatchlogs.CloudWatchLogsClient;
import software.amazon.awssdk.services.cloudwatchlogs.model.*;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * AWS SDK Mock Configuration for Tests
 *
 * Provides mock AWS SDK clients (Cognito, SES) to prevent network calls during tests.
 * Fixes QA issue TEST-001: Backend test coverage at 40% due to AWS SDK mocking issues.
 *
 * Uses @Order(HIGHEST_PRECEDENCE) to override TestSecurityConfig beans when both are imported.
 *
 * Story 1.2.2 - Password Reset Integration Tests
 */
@TestConfiguration
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TestAwsConfig {

    /**
     * Mock JwtDecoder for authenticated endpoints
     * Returns a test JWT with standard claims
     */
    @Bean
    @Primary
    public JwtDecoder mockJwtDecoder() {
        return new JwtDecoder() {
            @Override
            public Jwt decode(String token) throws JwtException {
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
     * Mock Cognito Identity Provider Client
     * Returns successful responses for forgotPassword calls
     */
    @Bean
    @Primary
    public CognitoIdentityProviderClient mockCognitoClient() {
        CognitoIdentityProviderClient mockClient = mock(CognitoIdentityProviderClient.class);

        // Mock successful forgotPassword response
        ForgotPasswordResponse successResponse = ForgotPasswordResponse.builder()
                .codeDeliveryDetails(CodeDeliveryDetailsType.builder()
                        .destination("t***@example.com")
                        .deliveryMedium(DeliveryMediumType.EMAIL)
                        .attributeName("email")
                        .build())
                .build();

        when(mockClient.forgotPassword(any(ForgotPasswordRequest.class)))
                .thenReturn(successResponse);

        // Mock UserNotFoundException for non-existent users
        when(mockClient.forgotPassword(any(ForgotPasswordRequest.class)))
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
     * Mock EmailService
     * Prevents actual email sending during tests
     */
    @Bean
    @Primary
    public EmailService mockEmailService() {
        EmailService mockService = mock(EmailService.class);

        // Mock email sending to do nothing (successful)
        doNothing().when(mockService).sendPasswordResetEmail(anyString(), anyString(), anyString());

        return mockService;
    }

    /**
     * Mock CloudWatch Logs Client
     * Prevents actual CloudWatch API calls during tests
     */
    @Bean
    @Primary
    public CloudWatchLogsClient mockCloudWatchLogsClient() {
        CloudWatchLogsClient mockClient = mock(CloudWatchLogsClient.class);

        // Mock successful CreateLogGroup response
        CreateLogGroupResponse logGroupResponse = CreateLogGroupResponse.builder().build();
        when(mockClient.createLogGroup(any(CreateLogGroupRequest.class)))
                .thenReturn(logGroupResponse);

        // Mock successful CreateLogStream response
        CreateLogStreamResponse logStreamResponse = CreateLogStreamResponse.builder().build();
        when(mockClient.createLogStream(any(CreateLogStreamRequest.class)))
                .thenReturn(logStreamResponse);

        // Mock successful PutLogEvents response
        PutLogEventsResponse putResponse = PutLogEventsResponse.builder()
                .nextSequenceToken("test-sequence-token")
                .build();
        when(mockClient.putLogEvents(any(PutLogEventsRequest.class)))
                .thenReturn(putResponse);

        return mockClient;
    }
}
