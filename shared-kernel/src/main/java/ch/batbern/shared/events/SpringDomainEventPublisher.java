package ch.batbern.shared.events;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * {@link DomainEventPublisher} that delivers domain events to in-process Spring listeners
 * ({@code @EventListener}, {@code @TransactionalEventListener}, {@code @Async} listeners).
 *
 * <p>A listener that throws does not fail the caller: the exception is logged at ERROR with the
 * event type and swallowed. That keeps the behaviour the services had before Story 17.2 part B,
 * when the local publish was swallowed the same way. Whether a listener failure should fail the caller is open
 * (Epic 17, story 17.12).
 */
@Component
public class SpringDomainEventPublisher implements DomainEventPublisher {

    private static final Logger LOGGER = LoggerFactory.getLogger(SpringDomainEventPublisher.class);

    private final ApplicationEventPublisher springEventPublisher;

    public SpringDomainEventPublisher(ApplicationEventPublisher springEventPublisher) {
        this.springEventPublisher = springEventPublisher;
    }

    @Override
    public void publish(DomainEvent<?> event) {
        validateEvent(event);
        try {
            springEventPublisher.publishEvent(event);
            LOGGER.debug("Published domain event {} ({} {})",
                    event.getEventType(), event.getAggregateType(), event.getAggregateId());
        } catch (RuntimeException e) {
            LOGGER.error("Listener failed for domain event {} ({} {}); the caller continues",
                    event.getEventType(), event.getAggregateType(), event.getAggregateId(), e);
        }
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
}
