package ch.batbern.shared.unit.events;

import ch.batbern.shared.events.EventCreatedEvent;
import ch.batbern.shared.types.EventId;
import ch.batbern.shared.types.UserId;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.BeforeEach;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.*;

class EventCreatedEventTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
    }

    @Test
    @DisplayName("should_publishEventCreatedEvent_when_eventEstablished")
    void should_publishEventCreatedEvent_when_eventEstablished() {
        EventId eventId = EventId.generate();
        String title = "BATbern 2024";
        String eventType = "CONFERENCE";
        LocalDate eventDate = LocalDate.of(2024, 11, 15);
        String venue = "Kursaal Bern";
        UserId organizerId = UserId.from("organizer-123");

        EventCreatedEvent event = EventCreatedEvent.builder()
            .eventId(eventId)
            .title(title)
            .eventType(eventType)
            .eventDate(eventDate)
            .venue(venue)
            .organizerId(organizerId)
            .build();

        assertThat(event).isNotNull();
        assertThat(event.getCreatedEventId()).isEqualTo(eventId);
        assertThat(event.getTitle()).isEqualTo(title);
        assertThat(event.getEventType()).isEqualTo(eventType);
        assertThat(event.getEventDate()).isEqualTo(eventDate);
        assertThat(event.getVenue()).isEqualTo(venue);
        assertThat(event.getOrganizerId()).isEqualTo(organizerId);
        assertThat(event.getAggregateId()).isEqualTo(eventId);
        assertThat(event.getEventName()).isEqualTo("EventCreatedEvent");
    }

    @Test
    @DisplayName("should_serializeEventToJSON_when_publishingToEventBridge")
    void should_serializeEventToJSON_when_publishingToEventBridge() throws Exception {
        EventCreatedEvent event = EventCreatedEvent.builder()
            .eventId(EventId.generate())
            .title("BATbern 2024")
            .eventType("CONFERENCE")
            .eventDate(LocalDate.of(2024, 11, 15))
            .venue("Kursaal Bern")
            .organizerId(UserId.from("organizer-123"))
            .build();

        String json = objectMapper.writeValueAsString(event);

        assertThat(json).isNotNull();
        assertThat(json).contains("\"title\":\"BATbern 2024\"");
        assertThat(json).contains("\"eventType\":\"CONFERENCE\"");
        assertThat(json).contains("\"venue\":\"Kursaal Bern\"");
        assertThat(json).contains("\"eventDate\"");
        assertThat(json).contains("\"organizerId\"");
    }

    @Test
    @DisplayName("should_deserializeEventFromJSON_when_receivedFromEventBridge")
    void should_deserializeEventFromJSON_when_receivedFromEventBridge() throws Exception {
        EventCreatedEvent originalEvent = EventCreatedEvent.builder()
            .eventId(EventId.generate())
            .title("BATbern 2024")
            .eventType("CONFERENCE")
            .eventDate(LocalDate.of(2024, 11, 15))
            .venue("Kursaal Bern")
            .organizerId(UserId.from("organizer-123"))
            .build();

        String json = objectMapper.writeValueAsString(originalEvent);
        EventCreatedEvent deserializedEvent = objectMapper.readValue(json, EventCreatedEvent.class);

        assertThat(deserializedEvent).isNotNull();
        assertThat(deserializedEvent.getTitle()).isEqualTo(originalEvent.getTitle());
        assertThat(deserializedEvent.getEventType()).isEqualTo(originalEvent.getEventType());
        assertThat(deserializedEvent.getEventDate()).isEqualTo(originalEvent.getEventDate());
        assertThat(deserializedEvent.getVenue()).isEqualTo(originalEvent.getVenue());
    }

    @Test
    @DisplayName("should_includeEventMetadata_when_eventCreated")
    void should_includeEventMetadata_when_eventCreated() {
        EventCreatedEvent event = EventCreatedEvent.builder()
            .eventId(EventId.generate())
            .title("BATbern 2024")
            .eventType("CONFERENCE")
            .eventDate(LocalDate.of(2024, 11, 15))
            .venue("Kursaal Bern")
            .organizerId(UserId.from("organizer-123"))
            .build();

        assertThat(event.getOccurredAt()).isNotNull();
        assertThat(event.getVersion()).isEqualTo("1.0");
        assertThat(event.getMetadata()).isNotNull();
        assertThat(event.getMetadata()).containsKey("source");
    }

    @Test
    @DisplayName("should_supportEventTypesEnum_when_differentTypesProvided")
    void should_supportEventTypesEnum_when_differentTypesProvided() {
        String[] eventTypes = {"CONFERENCE", "WORKSHOP", "MEETUP", "WEBINAR"};

        for (String type : eventTypes) {
            EventCreatedEvent event = EventCreatedEvent.builder()
                .eventId(EventId.generate())
                .title("Test Event")
                .eventType(type)
                .eventDate(LocalDate.now())
                .venue("Test Venue")
                .organizerId(UserId.from("organizer-123"))
                .build();

            assertThat(event.getEventType()).isEqualTo(type);
        }
    }

    @Test
    @DisplayName("should_validateRequiredFields_when_eventCreated")
    void should_validateRequiredFields_when_eventCreated() {
        assertThatThrownBy(() -> EventCreatedEvent.builder()
            .eventId(null)
            .title("BATbern 2024")
            .eventType("CONFERENCE")
            .eventDate(LocalDate.of(2024, 11, 15))
            .venue("Kursaal Bern")
            .organizerId(UserId.from("organizer-123"))
            .build())
            .isInstanceOf(NullPointerException.class);

        assertThatThrownBy(() -> EventCreatedEvent.builder()
            .eventId(EventId.generate())
            .title(null)
            .eventType("CONFERENCE")
            .eventDate(LocalDate.of(2024, 11, 15))
            .venue("Kursaal Bern")
            .organizerId(UserId.from("organizer-123"))
            .build())
            .isInstanceOf(NullPointerException.class);
    }
}