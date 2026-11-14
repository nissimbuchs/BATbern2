package ch.batbern.events.config;

import ch.batbern.shared.test.async.AsyncTaskTracker;
import ch.batbern.shared.test.mail.SesRecorder;
import org.mockito.Mockito;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;
import software.amazon.awssdk.services.s3.model.CopyObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

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
     * Mock S3Client: the boundary of the upload service (Story 17.11, event-theme-image) and the
     * media services
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
     * A real S3Presigner with static test credentials: presigning is local signing, no network. Tests
     * can read what a presigned URL carries, e.g. the {@code response-content-disposition} of a
     * session material download (Story 17.11d). Nothing is ever sent to this endpoint.
     */
    // #1186: see testS3Client, same reason.
    @Bean
    @Primary
    public S3Presigner testS3Presigner() {
        return S3Presigner.builder()
                .region(Region.EU_CENTRAL_1)
                .endpointOverride(URI.create("https://s3.test.invalid"))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create("test-access-key", "test-secret-key")))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .build();
    }

    /**
     * Story 17.0b3: the real {@code EmailService} runs in every integration test, against this
     * recording {@code SesClient} (nothing is sent). Tests autowire {@link SesRecorder}, reset it in
     * {@code @BeforeEach} and assert on what SES received, never on {@code EmailService} calls.
     */
    @Bean
    public SesRecorder sesRecorder(AsyncTaskTracker asyncTasks) {
        return new SesRecorder(asyncTasks::awaitIdle);
    }

    /**
     * Tracks the {@code @Async} executor, so {@link SesRecorder#reset()} first waits for the sends
     * still running from the previous test (17.0 deferred work). Static: a bean post-processor.
     */
    @Bean
    public static AsyncTaskTracker asyncTaskTracker() {
        return new AsyncTaskTracker();
    }

    /** The context's only SES client: the recorder's. */
    @Bean
    @Primary
    public SesClient sesRecorderClient(SesRecorder sesRecorder) {
        return sesRecorder.client();
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
