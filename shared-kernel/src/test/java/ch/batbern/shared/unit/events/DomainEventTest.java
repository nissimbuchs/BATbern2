package ch.batbern.shared.unit.events;

import ch.batbern.shared.events.DomainEvent;
import ch.batbern.shared.types.EventId;
import ch.batbern.shared.types.UserId;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class DomainEventTest {

    @Test
    @DisplayName("should_createDomainEvent_when_validDataProvided")
    void should_createDomainEvent_when_validDataProvided() {
        EventId aggregateId = EventId.generate();
        String eventType = "EventCreated";
        UserId userId = UserId.from("test-user");

        TestDomainEvent event = new TestDomainEvent(aggregateId, eventType, userId);

        assertThat(event).isNotNull();
        assertThat(event.getEventId()).isNotNull();
        assertThat(event.getAggregateId()).isEqualTo(aggregateId);
        assertThat(event.getEventType()).isEqualTo(eventType);
        assertThat(event.getUserId()).isEqualTo(userId);
        assertThat(event.getVersion()).isEqualTo("1.0");
    }

    @Test
    @DisplayName("should_includeTimestamp_when_eventCreated")
    void should_includeTimestamp_when_eventCreated() {
        Instant before = Instant.now();
        TestDomainEvent event = new TestDomainEvent(
            EventId.generate(),
            "EventCreated",
            UserId.from("test-user")
        );
        Instant after = Instant.now();

        assertThat(event.getOccurredAt()).isNotNull();
        assertThat(event.getOccurredAt()).isAfterOrEqualTo(before);
        assertThat(event.getOccurredAt()).isBeforeOrEqualTo(after);
    }

    @Test
    @DisplayName("should_generateUniqueEventId_when_eventCreated")
    void should_generateUniqueEventId_when_eventCreated() {
        TestDomainEvent event1 = new TestDomainEvent(
            EventId.generate(),
            "EventCreated",
            UserId.from("test-user")
        );
        TestDomainEvent event2 = new TestDomainEvent(
            EventId.generate(),
            "EventCreated",
            UserId.from("test-user")
        );

        assertThat(event1.getEventId()).isNotEqualTo(event2.getEventId());
    }

    @Test
    @DisplayName("should_includeMetadata_when_eventCreated")
    void should_includeMetadata_when_eventCreated() {
        TestDomainEvent event = new TestDomainEvent(
            EventId.generate(),
            "EventCreated",
            UserId.from("test-user")
        );

        assertThat(event.getMetadata()).isNotNull();
        assertThat(event.getMetadata()).containsKey("source");
        assertThat(event.getMetadata().get("source")).isEqualTo("shared-kernel");
    }

    @Test
    @DisplayName("should_setCorrelationId_when_provided")
    void should_setCorrelationId_when_provided() {
        String correlationId = UUID.randomUUID().toString();
        TestDomainEvent event = new TestDomainEvent(
            EventId.generate(),
            "EventCreated",
            UserId.from("test-user")
        );
        event.setCorrelationId(correlationId);

        assertThat(event.getCorrelationId()).isEqualTo(correlationId);
    }

    @Test
    @DisplayName("should_setCausationId_when_provided")
    void should_setCausationId_when_provided() {
        String causationId = UUID.randomUUID().toString();
        TestDomainEvent event = new TestDomainEvent(
            EventId.generate(),
            "EventCreated",
            UserId.from("test-user")
        );
        event.setCausationId(causationId);

        assertThat(event.getCausationId()).isEqualTo(causationId);
    }

    @Test
    @DisplayName("should_serializeToJson_when_objectMapperUsed")
    void should_serializeToJson_when_objectMapperUsed() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());

        TestDomainEvent event = new TestDomainEvent(
            EventId.generate(),
            "EventCreated",
            UserId.from("test-user")
        );

        String json = mapper.writeValueAsString(event);

        assertThat(json).isNotNull();
        assertThat(json).contains("eventId");
        assertThat(json).contains("aggregateId");
        assertThat(json).contains("eventType");
        assertThat(json).contains("occurredAt");
        assertThat(json).contains("version");
    }

    @Test
    @DisplayName("should_deserializeFromJson_when_validJsonProvided")
    void should_deserializeFromJson_when_validJsonProvided() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());

        TestDomainEvent originalEvent = new TestDomainEvent(
            EventId.generate(),
            "EventCreated",
            UserId.from("test-user")
        );

        String json = mapper.writeValueAsString(originalEvent);
        TestDomainEvent deserializedEvent = mapper.readValue(json, TestDomainEvent.class);

        assertThat(deserializedEvent).isNotNull();
        assertThat(deserializedEvent.getEventId()).isEqualTo(originalEvent.getEventId());
        assertThat(deserializedEvent.getAggregateId()).isEqualTo(originalEvent.getAggregateId());
        assertThat(deserializedEvent.getEventType()).isEqualTo(originalEvent.getEventType());
    }

    static class TestDomainEvent extends DomainEvent<EventId> {
        public TestDomainEvent(EventId aggregateId, String eventType, UserId userId) {
            super(aggregateId, eventType, userId);
        }

        protected TestDomainEvent() {
            super();
        }

        @Override
        public EventId getAggregateId() {
            return super.getAggregateId();
        }
    }
}