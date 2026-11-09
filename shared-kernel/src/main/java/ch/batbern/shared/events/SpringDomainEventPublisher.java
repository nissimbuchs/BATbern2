package ch.batbern.shared.events;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Metrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.SimpleApplicationEventMulticaster;
import org.springframework.stereotype.Component;

/**
 * {@link DomainEventPublisher} that delivers domain events to in-process Spring listeners
 * ({@code @EventListener}, {@code @TransactionalEventListener}, {@code @Async} listeners).
 *
 * <p>A listener that throws does not fail the caller: the exception is logged at ERROR with the
 * event type and swallowed. That keeps the behaviour the services had before Story 17.2 part B,
 * when the local publish was swallowed the same way. Whether a listener failure should fail the caller is open
 * (Epic 17, story 17.12). So that the failure is not silent, each one also increments the counter
 * {@value #LISTENER_FAILURES}, tagged {@code event_type} and {@code listener}
 * ({@code SimpleClassName.method} of the listener that threw, {@code unknown} when the stack does
 * not show it). Only failures of synchronous listeners reach this point: an {@code @Async}
 * listener fails on its own thread, a {@code @TransactionalEventListener} at commit.
 */
@Component
public class SpringDomainEventPublisher implements DomainEventPublisher {

    /** Counter of listener failures swallowed by {@link #publish}. */
    public static final String LISTENER_FAILURES = "domain.events.listener.failures";

    private static final Logger LOGGER = LoggerFactory.getLogger(SpringDomainEventPublisher.class);
    private static final String MULTICASTER = SimpleApplicationEventMulticaster.class.getName();

    private final ApplicationEventPublisher springEventPublisher;
    private final MeterRegistry meterRegistry;

    /**
     * @param meterRegistry the service's registry (Spring Boot actuator); Micrometer's global
     *                      registry when the context has none (plain Spring contexts in tests)
     */
    public SpringDomainEventPublisher(ApplicationEventPublisher springEventPublisher,
                                      ObjectProvider<MeterRegistry> meterRegistry) {
        this.springEventPublisher = springEventPublisher;
        this.meterRegistry = meterRegistry.getIfAvailable(() -> Metrics.globalRegistry);
    }

    @Override
    public void publish(DomainEvent<?> event) {
        validateEvent(event);
        try {
            springEventPublisher.publishEvent(event);
            LOGGER.debug("Published domain event {} ({} {})",
                    event.getEventType(), event.getAggregateType(), event.getAggregateId());
        } catch (RuntimeException e) {
            String listener = listenerOf(e);
            LOGGER.error("Listener {} failed for domain event {} ({} {}); the caller continues",
                    listener, event.getEventType(), event.getAggregateType(), event.getAggregateId(), e);
            Counter.builder(LISTENER_FAILURES)
                    .description("Domain event listener failures swallowed by the publisher")
                    .tag("event_type", event.getEventType())
                    .tag("listener", listener)
                    .register(meterRegistry)
                    .increment();
        }
    }

    /**
     * The listener method that threw: the frame nearest to the multicaster's call that is not
     * framework or JDK code (reflection, proxies, the listener adapter).
     */
    static String listenerOf(Throwable e) {
        StackTraceElement[] stack = e.getStackTrace();
        int multicaster = -1;
        for (int i = 0; i < stack.length; i++) {
            if (stack[i].getClassName().equals(MULTICASTER)) {
                multicaster = i;
                break;
            }
        }
        for (int i = multicaster - 1; i >= 0; i--) {
            String className = stack[i].getClassName();
            if (!isFramework(className)) {
                String simple = className.substring(className.lastIndexOf('.') + 1);
                return simple.substring(simple.lastIndexOf('$') + 1) + "." + stack[i].getMethodName();
            }
        }
        return "unknown";
    }

    private static boolean isFramework(String className) {
        return className.startsWith("org.springframework.") || className.startsWith("java.")
                || className.startsWith("jdk.") || className.startsWith("sun.") || className.contains("$$");
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
