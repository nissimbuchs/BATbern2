package ch.batbern.partners.config;

import ch.batbern.shared.events.DomainEvent;
import ch.batbern.shared.events.DomainEventPublisher;
import ch.batbern.shared.service.EmailService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import software.amazon.awssdk.services.eventbridge.EventBridgeAsyncClient;
import software.amazon.awssdk.services.eventbridge.model.PutEventsRequest;
import software.amazon.awssdk.services.eventbridge.model.PutEventsRequestEntry;
import software.amazon.awssdk.services.eventbridge.model.PutEventsResponse;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Test configuration for AWS beans
 * Provides mock AWS clients and DomainEventPublisher for integration tests
 */
@TestConfiguration
@Profile("test")
public class TestAwsConfig {

    @Bean
    @Primary
    public EventBridgeAsyncClient eventBridgeAsyncClient() {
        EventBridgeAsyncClient mockClient = mock(EventBridgeAsyncClient.class);

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
     * Mock DomainEventPublisher for integration tests
     * Prevents actual AWS EventBridge calls during testing
     */
    @Bean
    @Primary
    public DomainEventPublisher domainEventPublisher() {
        return new DomainEventPublisher() {
            @Override
            public void publish(DomainEvent<?> event) {
                // No-op for tests
            }

            @Override
            public CompletableFuture<Void> publishAsync(DomainEvent<?> event) {
                return CompletableFuture.completedFuture(null);
            }

            @Override
            public void publishBatch(List<? extends DomainEvent<?>> events) {
                // No-op for tests
            }

            @Override
            public void publishWithRetry(DomainEvent<?> event, int maxRetries) {
                // No-op for tests
            }

            @Override
            public PutEventsRequestEntry createEventBridgeEntry(DomainEvent<?> event) {
                return PutEventsRequestEntry.builder().build();
            }

            @Override
            public void ensureEventBusExists(String eventBusName) {
                // No-op for tests
            }

            @Override
            public boolean isDeadLetterQueueConfigured(String dlqName) {
                return true;
            }
        };
    }

    /**
     * Mock EmailService for testing email functionality
     * Provides mock shared-kernel EmailService for integration tests
     */
    @Bean
    @Primary
    public EmailService emailService() {
        EmailService mockEmailService = mock(EmailService.class);
        // sendEmail is void, no need to configure
        return mockEmailService;
    }
}
