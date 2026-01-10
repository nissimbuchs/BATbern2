package ch.batbern.shared.events;

import ch.batbern.shared.utils.LoggingUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
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
import java.util.function.Supplier;

@Component
public class EventBridgeEventPublisher implements DomainEventPublisher {

    private static final Logger LOGGER = LoggerFactory.getLogger(EventBridgeEventPublisher.class);
    private static final int MAX_BATCH_SIZE = 10; // EventBridge limit

    private final EventBridgeAsyncClient eventBridgeClient;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher springEventPublisher;

    // Resilience4j components for fault tolerance
    private final CircuitBreaker circuitBreaker;
    private final Bulkhead bulkhead;
    private final Retry retry;

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
                                    ApplicationEventPublisher springEventPublisher,
                                    CircuitBreakerRegistry circuitBreakerRegistry,
                                    BulkheadRegistry bulkheadRegistry,
                                    RetryRegistry retryRegistry) {
        this.eventBridgeClient = eventBridgeClient;
        this.objectMapper = objectMapper;
        this.springEventPublisher = springEventPublisher;

        // Initialize Resilience4j components
        this.circuitBreaker = circuitBreakerRegistry.circuitBreaker("eventBridgePublisher");
        this.bulkhead = bulkheadRegistry.bulkhead("eventBridgePublisher");
        this.retry = retryRegistry.retry("eventBridgePublisher");

        // Log circuit breaker state changes
        this.circuitBreaker.getEventPublisher()
            .onStateTransition(event -> LOGGER.warn("Circuit breaker state transition: {}", event))
            .onFailureRateExceeded(event -> LOGGER.error("Circuit breaker failure rate exceeded: {}", event));
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

        // Fire-and-forget async publish to EventBridge with Resilience4j patterns
        // This eliminates the blocking .get(5, TimeUnit.SECONDS) that caused OOM crashes
        publishAsyncWithResilience(event)
            .exceptionally(throwable -> {
                LOGGER.error("Async event publishing failed: {} (will be retried by Resilience4j)",
                        event.getEventType(), throwable);
                // Don't throw - fire-and-forget means we log but don't block the caller
                return null;
            });
    }

    /**
     * Publishes event asynchronously with Resilience4j circuit breaker, bulkhead, and retry patterns.
     * This method is fire-and-forget and never blocks the calling thread.
     */
    private CompletableFuture<Void> publishAsyncWithResilience(DomainEvent<?> event) {
        Supplier<CompletableFuture<PutEventsResponse>> publishSupplier = () -> {
            PutEventsRequestEntry entry = createEventBridgeEntry(event);
            PutEventsRequest request = PutEventsRequest.builder()
                .entries(entry)
                .build();

            return eventBridgeClient.putEvents(request);
        };

        // Apply Resilience4j patterns: Retry -> CircuitBreaker -> Bulkhead
        Supplier<CompletableFuture<PutEventsResponse>> decoratedSupplier =
            Bulkhead.decorateSupplier(bulkhead,
                CircuitBreaker.decorateSupplier(circuitBreaker,
                    Retry.decorateSupplier(retry, publishSupplier)));

        return decoratedSupplier.get()
            .thenAccept(response -> {
                if (response.failedEntryCount() > 0) {
                    handleFailedEntries(response.entries(), List.of(event));
                } else {
                    logSuccessfulPublish(event);
                }
            });
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
        // Process all batches concurrently with CompletableFuture.allOf() for non-blocking
        List<CompletableFuture<Void>> batchFutures = new java.util.ArrayList<>();

        for (int i = 0; i < events.size(); i += MAX_BATCH_SIZE) {
            List<? extends DomainEvent<?>> batch = events.subList(i, Math.min(i + MAX_BATCH_SIZE, events.size()));
            batchFutures.add(publishSingleBatchAsync(batch));
        }

        // Fire-and-forget: don't block waiting for completion
        CompletableFuture.allOf(batchFutures.toArray(new CompletableFuture[0]))
            .thenRun(() -> LOGGER.info("Published {} events in {} batches", events.size(), batchFutures.size()))
            .exceptionally(throwable -> {
                LOGGER.error("Batch publishing failed for {} events", events.size(), throwable);
                return null;
            });
    }

    /**
     * Publishes a single batch asynchronously with Resilience4j patterns.
     * This method is non-blocking and returns immediately.
     */
    private CompletableFuture<Void> publishSingleBatchAsync(List<? extends DomainEvent<?>> batch) {
        Supplier<CompletableFuture<PutEventsResponse>> publishSupplier = () -> {
            List<PutEventsRequestEntry> entries = batch.stream()
                .map(this::createEventBridgeEntry)
                .toList();

            PutEventsRequest request = PutEventsRequest.builder()
                .entries(entries)
                .build();

            return eventBridgeClient.putEvents(request);
        };

        // Apply Resilience4j patterns: Retry -> CircuitBreaker -> Bulkhead
        Supplier<CompletableFuture<PutEventsResponse>> decoratedSupplier =
            Bulkhead.decorateSupplier(bulkhead,
                CircuitBreaker.decorateSupplier(circuitBreaker,
                    Retry.decorateSupplier(retry, publishSupplier)));

        return decoratedSupplier.get()
            .thenAccept(response -> {
                if (response.failedEntryCount() > 0) {
                    handleFailedEntries(response.entries(), batch);
                } else {
                    LOGGER.info("Published batch of {} events", batch.size());
                }
            });
    }

    /**
     * @deprecated Use {@link #publish(DomainEvent)} instead. Retry logic is now handled
     * automatically by Resilience4j (configured in application-shared.yml).
     * This method now delegates to publish() for backward compatibility.
     */
    @Deprecated(since = "2026-01-10", forRemoval = true)
    @Override
    public void publishWithRetry(DomainEvent<?> event, int maxRetries) {
        LOGGER.warn("publishWithRetry() is deprecated. Use publish() instead - "
                + "retry is now automatic via Resilience4j (configured retries: {})", defaultMaxRetries);
        // Delegate to new publish() method which has built-in retry via Resilience4j
        publish(event);
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
        // Check if event bus exists (async, non-blocking)
        DescribeEventBusRequest describeRequest = DescribeEventBusRequest.builder()
            .name(eventBusName)
            .build();

        eventBridgeClient.describeEventBus(describeRequest)
            .thenAccept(response -> LOGGER.info("Event bus '{}' exists", eventBusName))
            .exceptionally(throwable -> {
                // Event bus doesn't exist, create it
                LOGGER.info("Event bus '{}' not found, creating it", eventBusName);
                CreateEventBusRequest createRequest = CreateEventBusRequest.builder()
                    .name(eventBusName)
                    .build();

                eventBridgeClient.createEventBus(createRequest)
                    .thenAccept(response -> LOGGER.info("Created event bus '{}'", eventBusName))
                    .exceptionally(createException -> {
                        LOGGER.error("Failed to create event bus '{}'", eventBusName, createException);
                        return null;
                    });

                return null;
            });
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