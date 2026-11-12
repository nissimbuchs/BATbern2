package ch.batbern.companyuser.config;

import ch.batbern.shared.test.mail.SesRecorder;
import org.mockito.Mockito;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import org.springframework.core.task.SyncTaskExecutor;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.util.concurrent.Executor;

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
     * Mock S3Client for tests
     * Story 1.16.3: Generic File Upload Service
     * Story 17.11: the upload service (company-logo) and its expiry job run on it
     */
    @Bean
    @Primary
    public S3Client s3Client() {
        // Return mock S3Client - individual tests will configure specific behavior
        return Mockito.mock(S3Client.class);
    }

    /**
     * Story 12.12: synchronous executor so the federated avatar import runs inline on the
     * test/request thread — deterministic assertions, and the import shares the
     * {@code @Transactional} test transaction (a real pool thread could not see uncommitted
     * test data). Pre-empts {@code AvatarImportConfig}'s pool via
     * {@code @ConditionalOnMissingBean(name = "avatarImportExecutor")}.
     */
    @Bean(name = "avatarImportExecutor")
    public Executor avatarImportExecutor() {
        return new SyncTaskExecutor();
    }

    /**
     * Story 12.12: mock HTTP client for the Google avatar fetch — individual tests stub
     * success/failure responses. Pre-empts {@code AvatarImportConfig}'s real client via
     * {@code @ConditionalOnMissingBean(name = "avatarFetchHttpClient")}.
     */
    @Bean(name = "avatarFetchHttpClient")
    public HttpClient avatarFetchHttpClient() {
        return Mockito.mock(HttpClient.class);
    }

    @Bean
    @Primary
    public S3Presigner s3Presigner() {
        S3Presigner mockPresigner = Mockito.mock(S3Presigner.class);

        // Configure mock to return a presigned URL for profile picture uploads
        try {
            URL mockPresignedUrl = URI.create("https://s3.amazonaws.com/test-bucket/presigned-url").toURL();
            PresignedPutObjectRequest mockPresignedRequest = Mockito.mock(PresignedPutObjectRequest.class);
            when(mockPresignedRequest.url()).thenReturn(mockPresignedUrl);
            when(mockPresigner.presignPutObject(any(PutObjectPresignRequest.class)))
                .thenReturn(mockPresignedRequest);
        } catch (MalformedURLException e) {
            throw new RuntimeException("Failed to create mock presigned URL", e);
        }

        return mockPresigner;
    }

    /**
     * The context's Cognito user pool: an in-memory one (the boundary). The real
     * {@code CognitoIntegrationServiceImpl} runs on it in every integration test; tests autowire
     * {@link InMemoryCognito}, {@code reset()} it in {@code @BeforeEach} and assert on its logins.
     */
    @Bean
    @Primary
    public InMemoryCognito cognitoIdentityProviderClient() {
        return new InMemoryCognito();
    }

    // Note: UserSearchService should NOT be mocked - it needs to perform actual searches
    // Removed mock bean to allow real implementation to be used

    /**
     * Story 17.0b3: the real {@code EmailService} runs in every integration test, against this
     * recording {@code SesClient} (nothing is sent). Tests autowire {@link SesRecorder}, reset it in
     * {@code @BeforeEach} and assert on what SES received, never on {@code EmailService} calls.
     */
    @Bean
    public SesRecorder sesRecorder() {
        return new SesRecorder();
    }

    /** The context's only SES client: the recorder's. */
    @Bean
    @Primary
    public SesClient sesRecorderClient(SesRecorder sesRecorder) {
        return sesRecorder.client();
    }

    // No SlugGenerationService mock (#1102): it is a pure function, and the mock it replaced
    // (first.toLowerCase() + "." + last.toLowerCase()) hid that real names produced usernames
    // violating chk_username_format. Integration tests use the real generator.
}
