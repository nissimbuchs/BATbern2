package ch.batbern.shared.events;

/**
 * Publishes a domain event to the listeners in the same service.
 *
 * <p>Story 17.2 part B: domain events stay in process. The only implementation,
 * {@link SpringDomainEventPublisher}, hands the event to Spring's
 * {@code ApplicationEventPublisher}; there is no message bus behind it.
 */
public interface DomainEventPublisher {

    void publish(DomainEvent<?> event);
}
