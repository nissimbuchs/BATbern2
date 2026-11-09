package ch.batbern.shared.unit.events;

import ch.batbern.shared.events.DomainEvent;
import ch.batbern.shared.events.DomainEventPublisher;
import ch.batbern.shared.events.EventCreatedEvent;
import ch.batbern.shared.events.SpringDomainEventPublisher;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.event.EventListener;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Story 17.2 part B: the in-process publisher, run against a real Spring context with real
 * listeners (no mocked {@code ApplicationEventPublisher}).
 */
class SpringDomainEventPublisherTest {

    private AnnotationConfigApplicationContext context;
    private ListAppender<ILoggingEvent> logAppender;
    private Logger publisherLogger;

    @BeforeEach
    void setUp() {
        publisherLogger = (Logger) LoggerFactory.getLogger(SpringDomainEventPublisher.class);
        logAppender = new ListAppender<>();
        logAppender.start();
        publisherLogger.addAppender(logAppender);
    }

    @AfterEach
    void tearDown() {
        publisherLogger.detachAppender(logAppender);
        if (context != null) {
            context.close();
        }
    }

    private DomainEventPublisher publisherWith(Class<?>... listeners) {
        return publisherWith(null, listeners);
    }

    private DomainEventPublisher publisherWith(MeterRegistry registry, Class<?>... listeners) {
        context = new AnnotationConfigApplicationContext();
        if (registry != null) {
            context.registerBean(MeterRegistry.class, () -> registry);
        }
        context.register(SpringDomainEventPublisher.class);
        context.register(listeners);
        context.refresh();
        return context.getBean(DomainEventPublisher.class);
    }

    private static EventCreatedEvent sampleEvent() {
        return EventCreatedEvent.builder()
            .eventCode("BATbern99")
            .title("BATbern 99")
            .eventType("CONFERENCE")
            .eventDate(LocalDate.of(2027, 5, 20))
            .venue("Kursaal Bern")
            .organizerId("organizer.test")
            .build();
    }

    @Test
    @DisplayName("should_deliverEventToLocalListener_when_published")
    void should_deliverEventToLocalListener_when_published() {
        DomainEventPublisher publisher = publisherWith(RecordingListener.class);
        EventCreatedEvent event = sampleEvent();

        publisher.publish(event);

        RecordingListener listener = context.getBean(RecordingListener.class);
        assertThat(listener.received).containsExactly(event);
    }

    @Test
    @DisplayName("should_throwIllegalArgument_when_eventIsNull")
    void should_throwIllegalArgument_when_eventIsNull() {
        DomainEventPublisher publisher = publisherWith(RecordingListener.class);

        assertThatThrownBy(() -> publisher.publish(null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("event cannot be null");
        assertThat(context.getBean(RecordingListener.class).received).isEmpty();
    }

    @Test
    @DisplayName("should_completeCallerAndLogErrorWithEventType_when_listenerThrows")
    void should_completeCallerAndLogErrorWithEventType_when_listenerThrows() {
        DomainEventPublisher publisher = publisherWith(ThrowingListener.class);
        EventCreatedEvent event = sampleEvent();

        assertThatNoException().isThrownBy(() -> publisher.publish(event));

        // positive control: the listener did run and throw
        assertThat(context.getBean(ThrowingListener.class).calls).isEqualTo(1);
        assertThat(logAppender.list)
            .filteredOn(e -> e.getLevel() == Level.ERROR)
            .singleElement()
            .satisfies(e -> {
                assertThat(e.getFormattedMessage()).contains(event.getEventType());
                assertThat(e.getThrowableProxy().getMessage()).isEqualTo("listener boom");
            });
    }

    @Test
    @DisplayName("should_startErrorLineWithAlarmMarker_when_listenerThrows")
    void should_startErrorLineWithAlarmMarker_when_listenerThrows() {
        // The CloudWatch metric filter behind the {Service}-Event-Listener-Failures alarm
        // (infrastructure/lib/constructs/ecs-service-alarms.ts) matches this literal term.
        DomainEventPublisher publisher = publisherWith(ThrowingListener.class);
        EventCreatedEvent event = sampleEvent();

        publisher.publish(event);

        assertThat(SpringDomainEventPublisher.LOG_MARKER).isEqualTo("DOMAIN_EVENT_LISTENER_FAILED");
        assertThat(logAppender.list)
            .filteredOn(e -> e.getLevel() == Level.ERROR)
            .singleElement()
            .satisfies(e -> assertThat(e.getFormattedMessage())
                .startsWith("DOMAIN_EVENT_LISTENER_FAILED ")
                .contains("listener=ThrowingListener.on")
                .contains("event_type=" + event.getEventType()));
    }

    @Test
    @DisplayName("should_logNoError_when_listenerSucceeds")
    void should_logNoError_when_listenerSucceeds() {
        DomainEventPublisher publisher = publisherWith(RecordingListener.class);

        publisher.publish(sampleEvent());

        assertThat(context.getBean(RecordingListener.class).received).hasSize(1);
        assertThat(logAppender.list).noneMatch(e -> e.getLevel() == Level.ERROR);
    }

    @Test
    @DisplayName("should_countFailureByEventTypeAndListener_when_listenerThrows")
    void should_countFailureByEventTypeAndListener_when_listenerThrows() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        DomainEventPublisher publisher = publisherWith(registry, ThrowingListener.class);
        EventCreatedEvent event = sampleEvent();

        publisher.publish(event);
        publisher.publish(event);

        Counter failures = registry.find(SpringDomainEventPublisher.LISTENER_FAILURES)
            .tag("event_type", event.getEventType())
            .tag("listener", "ThrowingListener.on")
            .counter();
        assertThat(failures).as("failure counter by event type and listener").isNotNull();
        assertThat(failures.count()).isEqualTo(2.0);
        assertThat(context.getBean(ThrowingListener.class).calls).isEqualTo(2);
    }

    @Test
    @DisplayName("should_countNothing_when_listenerSucceeds")
    void should_countNothing_when_listenerSucceeds() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        DomainEventPublisher publisher = publisherWith(registry, RecordingListener.class);

        publisher.publish(sampleEvent());

        assertThat(context.getBean(RecordingListener.class).received).as("positive control").hasSize(1);
        assertThat(registry.find(SpringDomainEventPublisher.LISTENER_FAILURES).counters()).isEmpty();
    }

    static class RecordingListener {
        final List<DomainEvent<?>> received = new ArrayList<>();

        @EventListener
        void on(EventCreatedEvent event) {
            received.add(event);
        }
    }

    static class ThrowingListener {
        int calls;

        @EventListener
        void on(EventCreatedEvent event) {
            calls++;
            throw new IllegalStateException("listener boom");
        }
    }
}
