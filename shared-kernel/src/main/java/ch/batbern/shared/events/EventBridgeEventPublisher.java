package ch.batbern.shared.events;

import ch.batbern.shared.utils.LoggingUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.eventbridge.EventBridgeAsyncClient;
import software.amazon.awssdk.services.eventbridge.model.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Component
public class EventBridgeEventPublisher implements DomainEventPublisher {

    private static final Logger logger = LoggerFactory.getLogger(EventBridgeEventPublisher.class);
    private static final int MAX_BATCH_SIZE = 10; // EventBridge limit

    private final EventBridgeAsyncClient eventBridgeClient;
    private final ObjectMapper objectMapper;

    @Value("${aws.eventbridge.bus-name:batbern-events}")
    private String eventBusName;

    @Value("${aws.eventbridge.source:batbern.event-management}")
    private String defaultSource;

    @Value("${aws.eventbridge.retry.max-attempts:3}")
    private int defaultMaxRetries;

    @Value("${aws.eventbridge.retry.delay-ms:1000}")
    private long retryDelayMs;

    public EventBridgeEventPublisher(EventBridgeAsyncClient eventBridgeClient,
                                    @Qualifier("eventBridgeObjectMapper") ObjectMapper objectMapper) {
        this.eventBridgeClient = eventBridgeClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public void publish(DomainEvent<?> event) {
        validateEvent(event);

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
            logger.error("Failed to publish event: {}", event.getEventType(), e);
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
                logger.error("Async event publishing failed: {}", event.getEventType(), throwable);
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

            logger.info("Published batch of {} events", batch.size());
        } catch (Exception e) {
            logger.error("Failed to publish batch of {} events", batch.size(), e);
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
                    logger.warn("Publish attempt {} failed for event {}, retrying in {} ms",
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

        logger.error("Failed to publish event {} after {} attempts",
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
            logger.info("Event bus '{}' exists", eventBusName);
        } catch (Exception e) {
            // Event bus doesn't exist, create it
            try {
                CreateEventBusRequest createRequest = CreateEventBusRequest.builder()
                    .name(eventBusName)
                    .build();

                eventBridgeClient.createEventBus(createRequest).get(5, TimeUnit.SECONDS);
                logger.info("Created event bus '{}'", eventBusName);
            } catch (Exception createException) {
                logger.error("Failed to create event bus '{}'", eventBusName, createException);
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
                logger.error("Failed to publish event {}: {} - {}",
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

        logger.info(LoggingUtils.formatStructuredMessage("Event published successfully", context));
    }

}