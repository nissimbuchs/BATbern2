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
import software.amazon.awssdk.services.eventbridge.EventBridgeAsyncClient;
import software.amazon.awssdk.services.eventbridge.model.*;
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
     * Mock S3 Presigner for local development
     * S3 operations will be logged but not executed
     */
    @Bean
    @Primary
    public S3Presigner s3Presigner() {
        log.info("🪣 [LOCAL] Using mock S3Presigner - presigned URLs will not work");

        return new S3Presigner() {
            @Override
            public void close() {
                // No-op for mock
            }

            @Override
            public PresignedPutObjectRequest presignPutObject(PutObjectPresignRequest request) {
                log.debug("🪣 [LOCAL] Mock presignPutObject");
                return null; // Not implemented for local
            }

            @Override
            public PresignedGetObjectRequest presignGetObject(GetObjectPresignRequest request) {
                log.debug("🪣 [LOCAL] Mock presignGetObject");
                return null; // Not implemented for local
            }


            @Override
            public PresignedCreateMultipartUploadRequest presignCreateMultipartUpload(CreateMultipartUploadPresignRequest request) {
                log.debug("🪣 [LOCAL] Mock presignCreateMultipartUpload");
                return null; // Not implemented for local
            }

            @Override
            public PresignedUploadPartRequest presignUploadPart(UploadPartPresignRequest request) {
                log.debug("🪣 [LOCAL] Mock presignUploadPart");
                return null; // Not implemented for local
            }

            @Override
            public PresignedCompleteMultipartUploadRequest presignCompleteMultipartUpload(CompleteMultipartUploadPresignRequest request) {
                log.debug("🪣 [LOCAL] Mock presignCompleteMultipartUpload");
                return null; // Not implemented for local
            }

            @Override
            public PresignedAbortMultipartUploadRequest presignAbortMultipartUpload(AbortMultipartUploadPresignRequest request) {
                log.debug("🪣 [LOCAL] Mock presignAbortMultipartUpload");
                return null; // Not implemented for local
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
