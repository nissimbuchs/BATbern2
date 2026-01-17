package ch.batbern.events.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cloudfront.CloudFrontClient;
import software.amazon.awssdk.services.cloudfront.model.CreateInvalidationRequest;
import software.amazon.awssdk.services.cloudfront.model.CreateInvalidationResponse;
import software.amazon.awssdk.services.cloudfront.model.Invalidation;
import software.amazon.awssdk.services.eventbridge.EventBridgeAsyncClient;
import software.amazon.awssdk.services.eventbridge.model.PutEventsRequest;
import software.amazon.awssdk.services.eventbridge.model.PutEventsResponse;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;
import java.util.concurrent.CompletableFuture;

/**
 * Local development configuration for AWS beans
 * Story 2.2: Architecture Compliance Refactoring
 * Story 2.5.3a: Added S3Client for MinIO support
 *
 * Provides mock AWS clients for local development without requiring AWS credentials
 * This configuration is active when SPRING_PROFILES_ACTIVE=local
 * - EventBridge: Events are logged but not actually published
 * - S3: Connects to local MinIO instead of AWS S3
 */
@Configuration
@Profile("local")
@Slf4j
public class LocalAwsConfig {

    /**
     * S3 Client configured for MinIO (local S3-compatible storage)
     * Story 2.5.3a: Event Theme Image Upload
     * Provides S3 operations for GenericLogoService
     */
    @Bean
    @Primary
    public S3Client s3Client() {
        log.info("🪣 [LOCAL] Creating S3Client configured for MinIO (http://localhost:8450)");

        // Configure S3 client to use MinIO endpoint
        Region region = Region.EU_CENTRAL_1;

        // Create S3 client configuration for MinIO
        software.amazon.awssdk.services.s3.S3Configuration s3Config =
            software.amazon.awssdk.services.s3.S3Configuration.builder()
                .pathStyleAccessEnabled(true)  // MinIO requires path-style access
                .build();

        // Create credentials provider for MinIO (minioadmin/minioadmin)
        AwsBasicCredentials credentials =
            AwsBasicCredentials.create("minioadmin", "minioadmin");

        StaticCredentialsProvider credentialsProvider =
            StaticCredentialsProvider.create(credentials);

        // Create endpoint override for MinIO
        URI minioEndpoint = URI.create("http://localhost:8450");

        // Build the S3Client
        S3Client s3Client = S3Client.builder()
            .region(region)
            .credentialsProvider(credentialsProvider)
            .endpointOverride(minioEndpoint)
            .serviceConfiguration(s3Config)
            .build();

        log.info("🪣 [LOCAL] S3Client configured successfully for MinIO");
        log.info("🪣 [LOCAL] Endpoint: http://localhost:8450");
        log.info("🪣 [LOCAL] Bucket: batbern-development-company-logos");

        return s3Client;
    }

    /**
     * S3 Presigner configured for MinIO (local S3-compatible storage)
     * Story 5.9: Session Materials Upload
     * Generates real presigned URLs that work with MinIO running on localhost:8450
     */
    @Bean
    @Primary
    public S3Presigner s3Presigner() {
        log.info("🪣 [LOCAL] Creating S3Presigner configured for MinIO (http://localhost:8450)");

        // Configure S3 presigner to use MinIO endpoint
        Region region = Region.EU_CENTRAL_1;

        // Create S3 client configuration for MinIO
        software.amazon.awssdk.services.s3.S3Configuration s3Config =
            software.amazon.awssdk.services.s3.S3Configuration.builder()
                .pathStyleAccessEnabled(true)  // MinIO requires path-style access
                .build();

        // Create credentials provider for MinIO (minioadmin/minioadmin)
        AwsBasicCredentials credentials =
            AwsBasicCredentials.create("minioadmin", "minioadmin");

        StaticCredentialsProvider credentialsProvider =
            StaticCredentialsProvider.create(credentials);

        // Create endpoint override for MinIO
        URI minioEndpoint = URI.create("http://localhost:8450");

        // Build the S3Presigner
        S3Presigner presigner = S3Presigner.builder()
            .region(region)
            .credentialsProvider(credentialsProvider)
            .endpointOverride(minioEndpoint)
            .serviceConfiguration(s3Config)
            .build();

        log.info("🪣 [LOCAL] S3Presigner configured successfully for MinIO");

        return presigner;
    }

    /**
     * Mock EventBridge client for local development
     * Logs events instead of publishing to AWS
     */
    @Bean
    @Primary
    public EventBridgeAsyncClient eventBridgeAsyncClient() {
        log.info("📨 [LOCAL] Using mock EventBridgeAsyncClient - events will be logged but not published");

        return new EventBridgeAsyncClient() {
            @Override
            public String serviceName() {
                return "eventbridge";
            }

            @Override
            public void close() {
                // No-op for mock
            }

            @Override
            public CompletableFuture<PutEventsResponse> putEvents(PutEventsRequest request) {
                log.info("📨 [LOCAL] Mock EventBridge - Would publish {} events", request.entries().size());
                request.entries().forEach(entry ->
                    log.debug("📨 [LOCAL] Event: source={}, detailType={}, detail={}",
                        entry.source(), entry.detailType(), entry.detail())
                );

                PutEventsResponse successResponse = PutEventsResponse.builder()
                    .failedEntryCount(0)
                    .build();

                return CompletableFuture.completedFuture(successResponse);
            }
        };
    }

    /**
     * Mock CloudFront client for local development
     * Story BAT-16 (AC6): CDN Cache Invalidation
     * Logs invalidation requests instead of actually invalidating CloudFront
     */
    @Bean
    @Primary
    public CloudFrontClient cloudFrontClient() {
        log.info("☁️ [LOCAL] Using mock CloudFrontClient - invalidations will be logged but not executed");

        return new CloudFrontClient() {
            @Override
            public String serviceName() {
                return "cloudfront";
            }

            @Override
            public void close() {
                // No-op for mock
            }

            @Override
            public CreateInvalidationResponse createInvalidation(CreateInvalidationRequest request) {
                log.info("☁️ [LOCAL] Mock CloudFront - Would invalidate {} paths for distribution {}",
                        request.invalidationBatch().paths().quantity(),
                        request.distributionId());
                log.debug("☁️ [LOCAL] Paths: {}", request.invalidationBatch().paths().items());

                // Return a mock response
                String mockInvalidationId = "mock-invalidation-" + request.invalidationBatch().callerReference();
                Invalidation mockInvalidation = Invalidation.builder()
                        .id(mockInvalidationId)
                        .status("Completed")
                        .build();

                return CreateInvalidationResponse.builder()
                        .invalidation(mockInvalidation)
                        .build();
            }
        };
    }

    /**
     * ObjectMapper for EventBridge event serialization
     * Required by EventBridgeEventPublisher from shared-kernel
     */
    @Bean
    @Qualifier("eventBridgeObjectMapper")
    public ObjectMapper eventBridgeObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
        return mapper;
    }
}
