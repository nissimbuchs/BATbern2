package ch.batbern.companyuser.config;

import ch.batbern.companyuser.service.CognitoIntegrationService;
import ch.batbern.companyuser.service.UserSearchService;
import ch.batbern.shared.service.EmailService;
import ch.batbern.shared.service.SlugGenerationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.mockito.Mockito;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.eventbridge.EventBridgeAsyncClient;
import software.amazon.awssdk.services.eventbridge.model.PutEventsRequest;
import software.amazon.awssdk.services.eventbridge.model.PutEventsResponse;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Test configuration for AWS beans
 * Provides mock AWS clients for integration tests
 */
@TestConfiguration
@Profile("test")
public class TestAwsConfig {

    @Bean
    @Primary
    public EventBridgeAsyncClient eventBridgeAsyncClient() {
        EventBridgeAsyncClient mockClient = Mockito.mock(EventBridgeAsyncClient.class);

        // Configure mock to return successful response for putEvents
        PutEventsResponse successResponse = PutEventsResponse.builder()
                .failedEntryCount(0)
                .build();

        when(mockClient.putEvents(any(PutEventsRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(successResponse));

        return mockClient;
    }

    /**
     * Mock S3Client for tests
     * Story 1.16.3: Generic File Upload Service
     * Required by GenericLogoService and LogoCleanupService
     */
    @Bean
    @Primary
    public S3Client s3Client() {
        // Return mock S3Client - individual tests will configure specific behavior
        return Mockito.mock(S3Client.class);
    }

    @Bean
    @Primary
    public S3Presigner s3Presigner() {
        S3Presigner mockPresigner = Mockito.mock(S3Presigner.class);

        // Configure mock to return a presigned URL for profile picture uploads
        try {
            URL mockPresignedUrl = new URL("https://s3.amazonaws.com/test-bucket/presigned-url");
            PresignedPutObjectRequest mockPresignedRequest = Mockito.mock(PresignedPutObjectRequest.class);
            when(mockPresignedRequest.url()).thenReturn(mockPresignedUrl);
            when(mockPresigner.presignPutObject(any(PutObjectPresignRequest.class)))
                .thenReturn(mockPresignedRequest);
        } catch (MalformedURLException e) {
            throw new RuntimeException("Failed to create mock presigned URL", e);
        }

        return mockPresigner;
    }

    @Bean
    @Primary
    public CognitoIdentityProviderClient cognitoIdentityProviderClient() {
        // Mock Cognito client for tests (Story 1.14-2 Task 12)
        return Mockito.mock(CognitoIdentityProviderClient.class);
    }

    @Bean
    @Primary
    public ObjectMapper eventBridgeObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
        return mapper;
    }

    @Bean
    @Primary
    public CognitoIntegrationService cognitoIntegrationService() {
        CognitoIntegrationService mockService = Mockito.mock(CognitoIntegrationService.class);

        // Mock createCognitoUser to return a test Cognito user ID
        when(mockService.createCognitoUser(any()))
                .thenReturn("cognito-test-user-id");

        // syncUserAttributes is void, no need to configure

        return mockService;
    }

    // Note: UserSearchService should NOT be mocked - it needs to perform actual searches
    // Removed mock bean to allow real implementation to be used

    /**
     * Mock EmailService for testing email functionality
     * Provides mock shared-kernel EmailService for integration tests
     */
    @Bean
    @Primary
    public EmailService emailService() {
        EmailService mockEmailService = Mockito.mock(EmailService.class);
        // sendEmail is void, no need to configure
        return mockEmailService;
    }

    /**
     * Mock SlugGenerationService for testing username generation
     * Story 1.16.2: Username generation from first/last name
     */
    @Bean
    @Primary
    public SlugGenerationService slugGenerationService() {
        SlugGenerationService mockService = Mockito.mock(SlugGenerationService.class);

        // Mock generateUsername to return a predictable username format
        when(mockService.generateUsername(anyString(), anyString()))
                .thenAnswer(invocation -> {
                    String firstName = invocation.getArgument(0);
                    String lastName = invocation.getArgument(1);
                    return firstName.toLowerCase() + "." + lastName.toLowerCase();
                });

        // Mock ensureUniqueUsername to return the base username (assume unique in tests)
        when(mockService.ensureUniqueUsername(anyString(), any(Function.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        return mockService;
    }
}
