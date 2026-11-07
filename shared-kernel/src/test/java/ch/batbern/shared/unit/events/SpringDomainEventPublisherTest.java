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
        context = new AnnotationConfigApplicationContext();
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
    @DisplayName("should_logNoError_when_listenerSucceeds")
    void should_logNoError_when_listenerSucceeds() {
        DomainEventPublisher publisher = publisherWith(RecordingListener.class);

        publisher.publish(sampleEvent());

        assertThat(context.getBean(RecordingListener.class).received).hasSize(1);
        assertThat(logAppender.list).noneMatch(e -> e.getLevel() == Level.ERROR);
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
