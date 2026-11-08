package ch.batbern.events.config;

import ch.batbern.shared.service.EmailService;
import org.mockito.Mockito;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;
import software.amazon.awssdk.services.s3.model.CopyObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.net.MalformedURLException;
import java.net.URI;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Test configuration for AWS beans
 * Provides mock AWS clients for integration tests
 */
@TestConfiguration
@Profile("test")
public class TestAwsConfig {

    /**
     * Mock S3Client for testing GenericLogoService
     * Story 2.5.3a: Event Theme Image Upload
     */
    // #1186: not named s3Client. The test profile allows bean overriding, so a test bean with the
    // production bean's name was silently replaced by AwsConfig's real client and @Primary never applied.
    @Bean
    @Primary
    public S3Client testS3Client() {
        S3Client mockS3Client = Mockito.mock(S3Client.class);

        // Configure mock to return successful response for copyObject
        CopyObjectResponse successResponse = CopyObjectResponse.builder().build();
        when(mockS3Client.copyObject(any(CopyObjectRequest.class)))
                .thenReturn(successResponse);

        // Configure mock for putObject (used by EventPhotoService, TeaserImageService, etc.)
        PutObjectResponse putResponse = PutObjectResponse.builder().build();
        when(mockS3Client.putObject(
                any(PutObjectRequest.class), any(software.amazon.awssdk.core.sync.RequestBody.class)))
                .thenReturn(putResponse);

        return mockS3Client;
    }

    /**
     * Mock S3Presigner for testing MaterialsUploadService
     * Story 5.9: Session Materials Upload
     */
    // #1186: see testS3Client, same reason.
    @Bean
    @Primary
    public S3Presigner testS3Presigner() {
        S3Presigner mockS3Presigner = Mockito.mock(S3Presigner.class);

        // Configure mock to return presigned URL
        PresignedPutObjectRequest mockPresignedRequest = Mockito.mock(PresignedPutObjectRequest.class);
        try {
            when(mockPresignedRequest.url()).thenReturn(URI.create("https://minio.local:8450/test-upload-url").toURL());
        } catch (MalformedURLException e) {
            throw new RuntimeException("Failed to create test URL", e);
        }

        when(mockS3Presigner.presignPutObject(any(PutObjectPresignRequest.class)))
                .thenReturn(mockPresignedRequest);

        // Configure mock for presignGetObject (used by services that issue download-side presigned URLs)
        PresignedGetObjectRequest mockGetPresignedRequest = Mockito.mock(PresignedGetObjectRequest.class);
        try {
            when(mockGetPresignedRequest.url()).thenReturn(URI.create("https://presigned.s3.amazonaws.com/test-asset").toURL());
        } catch (MalformedURLException e) {
            throw new RuntimeException("Failed to create test presigned GET URL", e);
        }
        when(mockS3Presigner.presignGetObject(any(GetObjectPresignRequest.class)))
                .thenReturn(mockGetPresignedRequest);

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

    /**
     * Mock SqsAsyncClient for testing (Story 10.17).
     * spring-cloud-aws-starter-sqs auto-configures SqsAsyncClient which requires
     * real AWS credentials. This mock prevents integration test startup failures
     * when no AWS SQS is configured in the test environment.
     */
    @Bean
    @Primary
    public SqsAsyncClient sqsAsyncClient() {
        return Mockito.mock(SqsAsyncClient.class);
    }
}
