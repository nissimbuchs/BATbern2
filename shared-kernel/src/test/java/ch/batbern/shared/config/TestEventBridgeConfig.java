package ch.batbern.shared.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.mockito.Mockito;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import software.amazon.awssdk.services.eventbridge.EventBridgeAsyncClient;
import software.amazon.awssdk.services.eventbridge.model.PutEventsRequest;
import software.amazon.awssdk.services.eventbridge.model.PutEventsResponse;

import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Test configuration for EventBridge
 * Provides mocked EventBridgeAsyncClient for fast, reliable tests without AWS dependencies
 */
@TestConfiguration
public class TestEventBridgeConfig {

    @Bean
    @Primary
    public EventBridgeAsyncClient testEventBridgeAsyncClient() {
        EventBridgeAsyncClient mockClient = Mockito.mock(EventBridgeAsyncClient.class);

        // Configure mock to return successful response for putEvents
        PutEventsResponse successResponse = PutEventsResponse.builder()
                .failedEntryCount(0)
                .build();

        when(mockClient.putEvents(any(PutEventsRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(successResponse));

        // Configure mock for createEventBus (used in ensureEventBusExists)
        software.amazon.awssdk.services.eventbridge.model.CreateEventBusResponse createBusResponse =
                software.amazon.awssdk.services.eventbridge.model.CreateEventBusResponse.builder()
                        .eventBusArn("arn:aws:events:us-east-1:123456789012:event-bus/batbern-test-events")
                        .build();

        when(mockClient.createEventBus(any(software.amazon.awssdk.services.eventbridge.model.CreateEventBusRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(createBusResponse));

        return mockClient;
    }

    @Bean(name = "eventBridgeObjectMapper")
    @Primary
    public ObjectMapper eventBridgeObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        mapper.disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.enable(com.fasterxml.jackson.databind.SerializationFeature.INDENT_OUTPUT);
        return mapper;
    }
}