package ch.batbern.companyuser.config;

import org.mockito.Mockito;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import software.amazon.awssdk.services.eventbridge.EventBridgeAsyncClient;
import software.amazon.awssdk.services.eventbridge.model.PutEventsRequest;
import software.amazon.awssdk.services.eventbridge.model.PutEventsResponse;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

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

    @Bean
    @Primary
    public S3Presigner s3Presigner() {
        return Mockito.mock(S3Presigner.class);
    }
}
