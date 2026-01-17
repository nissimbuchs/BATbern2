package ch.batbern.events.config;

import ch.batbern.shared.service.EmailService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.mockito.Mockito;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import software.amazon.awssdk.services.eventbridge.EventBridgeAsyncClient;
import software.amazon.awssdk.services.eventbridge.model.PutEventsRequest;
import software.amazon.awssdk.services.eventbridge.model.PutEventsResponse;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;
import software.amazon.awssdk.services.s3.model.CopyObjectResponse;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.any;
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

    @Bean("eventBridgeObjectMapper")
    @Primary
    public ObjectMapper eventBridgeObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
        return mapper;
    }

    /**
     * Mock S3Client for testing GenericLogoService
     * Story 2.5.3a: Event Theme Image Upload
     */
    @Bean
    @Primary
    public S3Client s3Client() {
        S3Client mockS3Client = Mockito.mock(S3Client.class);

        // Configure mock to return successful response for copyObject
        CopyObjectResponse successResponse = CopyObjectResponse.builder().build();
        when(mockS3Client.copyObject(any(CopyObjectRequest.class)))
                .thenReturn(successResponse);

        return mockS3Client;
    }

    /**
     * Mock S3Presigner for testing MaterialsUploadService
     * Story 5.9: Session Materials Upload
     */
    @Bean
    @Primary
    public S3Presigner s3Presigner() {
        S3Presigner mockS3Presigner = Mockito.mock(S3Presigner.class);

        // Configure mock to return presigned URL
        PresignedPutObjectRequest mockPresignedRequest = Mockito.mock(PresignedPutObjectRequest.class);
        try {
            when(mockPresignedRequest.url()).thenReturn(new URL("https://minio.local:8450/test-upload-url"));
        } catch (MalformedURLException e) {
            throw new RuntimeException("Failed to create test URL", e);
        }

        when(mockS3Presigner.presignPutObject(any(PutObjectPresignRequest.class)))
                .thenReturn(mockPresignedRequest);

        return mockS3Presigner;
    }

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
}
