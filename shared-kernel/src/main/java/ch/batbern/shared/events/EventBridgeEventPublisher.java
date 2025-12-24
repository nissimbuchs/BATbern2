package ch.batbern.shared.events;

import ch.batbern.shared.utils.LoggingUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.eventbridge.EventBridgeAsyncClient;
import software.amazon.awssdk.services.eventbridge.model.CreateEventBusRequest;
import software.amazon.awssdk.services.eventbridge.model.DescribeEventBusRequest;
import software.amazon.awssdk.services.eventbridge.model.PutEventsRequest;
import software.amazon.awssdk.services.eventbridge.model.PutEventsRequestEntry;
import software.amazon.awssdk.services.eventbridge.model.PutEventsResponse;
import software.amazon.awssdk.services.eventbridge.model.PutEventsResultEntry;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Component
public class EventBridgeEventPublisher implements DomainEventPublisher {

    private static final Logger LOGGER = LoggerFactory.getLogger(EventBridgeEventPublisher.class);
    private static final int MAX_BATCH_SIZE = 10; // EventBridge limit

    private final EventBridgeAsyncClient eventBridgeClient;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher springEventPublisher;

    @Value("${aws.eventbridge.bus-name:batbern-events}")
    private String eventBusName;

    @Value("${aws.eventbridge.source:batbern.event-management}")
    private String defaultSource;

    @Value("${aws.eventbridge.retry.max-attempts:3}")
    private int defaultMaxRetries;

    @Value("${aws.eventbridge.retry.delay-ms:1000}")
    private long retryDelayMs;

    public EventBridgeEventPublisher(EventBridgeAsyncClient eventBridgeClient,
                                    @Qualifier("eventBridgeObjectMapper") ObjectMapper objectMapper,
                                    ApplicationEventPublisher springEventPublisher) {
        this.eventBridgeClient = eventBridgeClient;
        this.objectMapper = objectMapper;
        this.springEventPublisher = springEventPublisher;
    }

    @Override
    public void publish(DomainEvent<?> event) {
        validateEvent(event);

        // First, publish to Spring's local event system for same-service listeners
        try {
            springEventPublisher.publishEvent(event);
            LOGGER.debug("Published event {} to local Spring event system", event.getEventType());
        } catch (Exception e) {
            LOGGER.warn("Failed to publish event {} to local Spring event system: {}",
                    event.getEventType(), e.getMessage());
            // Continue with EventBridge publishing even if local publishing fails
        }

        // Then, publish to EventBridge for cross-service communication
        try {
            PutEventsRequestEntry entry = createEventBridgeEntry(event);
            PutEventsRequest request = PutEventsRequest.builder()
                .entries(entry)
                .build();

            CompletableFuture<PutEventsResponse> future = eventBridgeClient.putEvents(request);
            PutEventsResponse response = future.get(5, TimeUnit.SECONDS);

            if (response.failedEntryCount() > 0) {
                handleFailedEntries(response.entries(), List.of(event));
            }

            logSuccessfulPublish(event);
        } catch (Exception e) {
            LOGGER.error("Failed to publish event: {}", event.getEventType(), e);
            throw new RuntimeException("Event publishing failed", e);
        }
    }

    @Override
    public CompletableFuture<Void> publishAsync(DomainEvent<?> event) {
        validateEvent(event);

        PutEventsRequestEntry entry = createEventBridgeEntry(event);
        PutEventsRequest request = PutEventsRequest.builder()
            .entries(entry)
            .build();

        return eventBridgeClient.putEvents(request)
            .thenAccept(response -> {
                if (response.failedEntryCount() > 0) {
                    handleFailedEntries(response.entries(), List.of(event));
                }
                logSuccessfulPublish(event);
            })
            .exceptionally(throwable -> {
                LOGGER.error("Async event publishing failed: {}", event.getEventType(), throwable);
                throw new RuntimeException("Async event publishing failed", throwable);
            });
    }

    @Override
    public void publishBatch(List<? extends DomainEvent<?>> events) {
        if (events.isEmpty()) {
            return;
        }

        // Split into batches if necessary (EventBridge has a limit of 10 entries per request)
        for (int i = 0; i < events.size(); i += MAX_BATCH_SIZE) {
            List<? extends DomainEvent<?>> batch = events.subList(i, Math.min(i + MAX_BATCH_SIZE, events.size()));
            publishSingleBatch(batch);
        }
    }

    private void publishSingleBatch(List<? extends DomainEvent<?>> batch) {
        try {
            List<PutEventsRequestEntry> entries = batch.stream()
                .map(this::createEventBridgeEntry)
                .toList();

            PutEventsRequest request = PutEventsRequest.builder()
                .entries(entries)
                .build();

            CompletableFuture<PutEventsResponse> future = eventBridgeClient.putEvents(request);
            PutEventsResponse response = future.get(5, TimeUnit.SECONDS);

            if (response.failedEntryCount() > 0) {
                handleFailedEntries(response.entries(), batch);
            }

            LOGGER.info("Published batch of {} events", batch.size());
        } catch (Exception e) {
            LOGGER.error("Failed to publish batch of {} events", batch.size(), e);
            throw new RuntimeException("Batch event publishing failed", e);
        }
    }

    @Override
    public void publishWithRetry(DomainEvent<?> event, int maxRetries) {
        int attempts = 0;
        Exception lastException = null;

        while (attempts < maxRetries) {
            try {
                publish(event);
                return; // Success
            } catch (Exception e) {
                lastException = e;
                attempts++;

                if (attempts < maxRetries) {
                    long delay = retryDelayMs * (long) Math.pow(2, attempts - 1); // Exponential backoff
                    LOGGER.warn("Publish attempt {} failed for event {}, retrying in {} ms",
                        attempts, event.getEventType(), delay);

                    try {
                        Thread.sleep(delay);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Retry interrupted", ie);
                    }
                }
            }
        }

        LOGGER.error("Failed to publish event {} after {} attempts",
            event.getEventType(), maxRetries, lastException);
        throw new RuntimeException("Event publishing failed after " + maxRetries + " attempts", lastException);
    }

    @Override
    public PutEventsRequestEntry createEventBridgeEntry(DomainEvent<?> event) {
        try {
            Map<String, Object> eventData = Map.of(
                "aggregateId", event.getAggregateId().toString(),
                "aggregateType", event.getAggregateType(),
                "occurredAt", event.getOccurredAt().toString(),
                "eventVersion", event.getEventVersion(),
                "eventData", event
            );

            String detailJson = objectMapper.writeValueAsString(eventData);

            return PutEventsRequestEntry.builder()
                .eventBusName(eventBusName)
                .source(defaultSource)
                .detailType(event.getEventName())
                .detail(detailJson)
                .time(event.getOccurredAt())
                .build();
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize event", e);
        }
    }

    @Override
    public void ensureEventBusExists(String eventBusName) {
        try {
            // Check if event bus exists
            DescribeEventBusRequest describeRequest = DescribeEventBusRequest.builder()
                .name(eventBusName)
                .build();

            eventBridgeClient.describeEventBus(describeRequest).get(5, TimeUnit.SECONDS);
            LOGGER.info("Event bus '{}' exists", eventBusName);
        } catch (Exception e) {
            // Event bus doesn't exist, create it
            try {
                CreateEventBusRequest createRequest = CreateEventBusRequest.builder()
                    .name(eventBusName)
                    .build();

                eventBridgeClient.createEventBus(createRequest).get(5, TimeUnit.SECONDS);
                LOGGER.info("Created event bus '{}'", eventBusName);
            } catch (Exception createException) {
                LOGGER.error("Failed to create event bus '{}'", eventBusName, createException);
                throw new RuntimeException("Failed to ensure event bus exists", createException);
            }
        }
    }

    @Override
    public boolean isDeadLetterQueueConfigured(String dlqName) {
        // In a real implementation, this would check if the DLQ is configured
        // For now, we'll return true as a placeholder
        return true;
    }

    private void validateEvent(DomainEvent<?> event) {
        if (event == null) {
            throw new IllegalArgumentException("Invalid event: event cannot be null");
        }
        if (event.getEventType() == null) {
            throw new IllegalArgumentException("Invalid event: event type cannot be null");
        }
        if (event.getAggregateId() == null) {
            throw new IllegalArgumentException("Invalid event: aggregate ID cannot be null");
        }
        if (event.getOccurredAt() == null) {
            throw new IllegalArgumentException("Invalid event: occurred at cannot be null");
        }
    }

    private void handleFailedEntries(List<PutEventsResultEntry> entries,
                                    List<? extends DomainEvent<?>> events) {
        for (int i = 0; i < entries.size(); i++) {
            PutEventsResultEntry entry = entries.get(i);
            if (entry.errorCode() != null) {
                DomainEvent<?> failedEvent = events.get(i);
                LOGGER.error("Failed to publish event {}: {} - {}",
                    failedEvent.getEventType(),
                    entry.errorCode(),
                    entry.errorMessage());
            }
        }
    }

    private void logSuccessfulPublish(DomainEvent<?> event) {
        Map<String, Object> context = Map.of(
            "eventType", event.getEventType(),
            "aggregateId", event.getAggregateId().toString(),
            "aggregateType", event.getAggregateType(),
            "timestamp", event.getOccurredAt().toString()
        );

        LOGGER.info(LoggingUtils.formatStructuredMessage("Event published successfully", context));
    }

}