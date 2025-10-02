package ch.batbern.shared.events;

import software.amazon.awssdk.services.eventbridge.model.PutEventsRequestEntry;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface DomainEventPublisher {

    void publish(DomainEvent<?> event);

    CompletableFuture<Void> publishAsync(DomainEvent<?> event);

    void publishBatch(List<? extends DomainEvent<?>> events);

    void publishWithRetry(DomainEvent<?> event, int maxRetries);

    PutEventsRequestEntry createEventBridgeEntry(DomainEvent<?> event);

    void ensureEventBusExists(String eventBusName);

    boolean isDeadLetterQueueConfigured(String dlqName);
}