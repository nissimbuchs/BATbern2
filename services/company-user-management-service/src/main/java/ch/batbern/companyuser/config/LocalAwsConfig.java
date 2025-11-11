package ch.batbern.companyuser.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import software.amazon.awssdk.services.cloudwatch.CloudWatchClient;
import software.amazon.awssdk.services.cloudwatch.model.*;
import software.amazon.awssdk.services.eventbridge.EventBridgeAsyncClient;
import software.amazon.awssdk.services.eventbridge.model.*;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.*;

import java.util.concurrent.CompletableFuture;

/**
 * Local development configuration for AWS beans
 * Provides mock AWS clients for local development without requiring AWS credentials
 *
 * This configuration is active when SPRING_PROFILES_ACTIVE=local
 * Events are logged but not actually published to EventBridge
 */
@Configuration
@Profile("local")
@Slf4j
public class LocalAwsConfig {

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
     * S3 Client configured for MinIO (local S3-compatible storage)
     * Story 1.16.3: Generic File Upload Service
     * Provides S3 operations for GenericLogoService and LogoCleanupService
     */
    @Bean
    @Primary
    public S3Client s3Client() {
        log.info("🪣 [LOCAL] Creating S3Client configured for MinIO (http://localhost:8450)");

        // Configure S3 client to use MinIO endpoint
        software.amazon.awssdk.regions.Region region = software.amazon.awssdk.regions.Region.EU_CENTRAL_1;

        // Create S3 client configuration for MinIO
        software.amazon.awssdk.services.s3.S3Configuration s3Config =
            software.amazon.awssdk.services.s3.S3Configuration.builder()
                .pathStyleAccessEnabled(true)  // MinIO requires path-style access
                .build();

        // Create credentials provider for MinIO (minioadmin/minioadmin)
        software.amazon.awssdk.auth.credentials.AwsBasicCredentials credentials =
            software.amazon.awssdk.auth.credentials.AwsBasicCredentials.create("minioadmin", "minioadmin");

        software.amazon.awssdk.auth.credentials.StaticCredentialsProvider credentialsProvider =
            software.amazon.awssdk.auth.credentials.StaticCredentialsProvider.create(credentials);

        // Create endpoint override for MinIO
        java.net.URI minioEndpoint = java.net.URI.create("http://localhost:8450");

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
     * Generates real presigned URLs that work with MinIO running on localhost:8450
     */
    @Bean
    @Primary
    public S3Presigner s3Presigner() {
        log.info("🪣 [LOCAL] Using S3Presigner configured for MinIO (http://localhost:8450)");

        // Configure S3 client to use MinIO endpoint
        software.amazon.awssdk.regions.Region region = software.amazon.awssdk.regions.Region.EU_CENTRAL_1;

        // Create S3 client configuration for MinIO
        software.amazon.awssdk.services.s3.S3Configuration s3Config =
            software.amazon.awssdk.services.s3.S3Configuration.builder()
                .pathStyleAccessEnabled(true)  // MinIO requires path-style access
                .build();

        // Create credentials provider for MinIO (minioadmin/minioadmin)
        software.amazon.awssdk.auth.credentials.AwsBasicCredentials credentials =
            software.amazon.awssdk.auth.credentials.AwsBasicCredentials.create("minioadmin", "minioadmin");

        software.amazon.awssdk.auth.credentials.StaticCredentialsProvider credentialsProvider =
            software.amazon.awssdk.auth.credentials.StaticCredentialsProvider.create(credentials);

        // Create endpoint override for MinIO
        java.net.URI minioEndpoint = java.net.URI.create("http://localhost:8450");

        // Build the S3Presigner
        S3Presigner presigner = S3Presigner.builder()
            .region(region)
            .credentialsProvider(credentialsProvider)
            .endpointOverride(minioEndpoint)
            .serviceConfiguration(s3Config)
            .build();

        log.info("🪣 [LOCAL] S3Presigner configured successfully for MinIO");
        log.info("🪣 [LOCAL] Endpoint: http://localhost:8450");
        log.info("🪣 [LOCAL] Bucket: batbern-development-company-logos");

        return presigner;
    }

    /**
     * Mock CloudWatch client for local development
     * Logs metrics instead of publishing to AWS CloudWatch
     */
    @Bean
    @Primary
    public CloudWatchClient cloudWatchClient() {
        log.info("📊 [LOCAL] Using mock CloudWatchClient - metrics will be logged but not published");

        return new CloudWatchClient() {
            @Override
            public String serviceName() {
                return "cloudwatch";
            }

            @Override
            public void close() {
                // No-op for mock
            }

            @Override
            public PutMetricDataResponse putMetricData(PutMetricDataRequest request) {
                log.info("📊 [LOCAL] Mock CloudWatch - Would publish {} metrics to namespace: {}",
                    request.metricData().size(), request.namespace());
                request.metricData().forEach(metric ->
                    log.debug("📊 [LOCAL] Metric: name={}, value={}, unit={}, dimensions={}",
                        metric.metricName(), metric.value(), metric.unit(), metric.dimensions())
                );
                return PutMetricDataResponse.builder().build();
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
