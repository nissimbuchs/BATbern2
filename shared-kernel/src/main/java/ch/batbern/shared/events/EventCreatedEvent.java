package ch.batbern.shared.events;

import ch.batbern.shared.types.EventId;
import ch.batbern.shared.types.UserId;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.time.LocalDate;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EventCreatedEvent extends DomainEvent<EventId> {

    @JsonProperty("createdEventId")
    @NonNull
    private EventId createdEventId;

    @JsonProperty("title")
    @NonNull
    private String title;

    @JsonProperty("eventType")
    @NonNull
    private String eventType;

    @JsonProperty("eventDate")
    @NonNull
    private LocalDate eventDate;

    @JsonProperty("venue")
    @NonNull
    private String venue;

    @JsonProperty("organizerId")
    @NonNull
    private UserId organizerId;

    public EventCreatedEvent(EventId eventId, String title, String eventType,
                           LocalDate eventDate, String venue, UserId organizerId) {
        super(eventId, "EventCreatedEvent", organizerId);
        if (eventId == null) throw new NullPointerException("eventId is marked non-null but is null");
        if (title == null) throw new NullPointerException("title is marked non-null but is null");
        if (eventType == null) throw new NullPointerException("eventType is marked non-null but is null");
        if (eventDate == null) throw new NullPointerException("eventDate is marked non-null but is null");
        if (venue == null) throw new NullPointerException("venue is marked non-null but is null");
        if (organizerId == null) throw new NullPointerException("organizerId is marked non-null but is null");
        this.createdEventId = eventId;
        this.title = title;
        this.eventType = eventType;
        this.eventDate = eventDate;
        this.venue = venue;
        this.organizerId = organizerId;
    }

    public static EventCreatedEventBuilder builder() {
        return new EventCreatedEventBuilder();
    }

    public static class EventCreatedEventBuilder {
        private EventId createdEventId;
        private String title;
        private String eventType;
        private LocalDate eventDate;
        private String venue;
        private UserId organizerId;

        public EventCreatedEventBuilder eventId(EventId eventId) {
            this.createdEventId = eventId;
            return this;
        }

        public EventCreatedEventBuilder title(String title) {
            this.title = title;
            return this;
        }

        public EventCreatedEventBuilder eventType(String eventType) {
            this.eventType = eventType;
            return this;
        }

        public EventCreatedEventBuilder eventDate(LocalDate eventDate) {
            this.eventDate = eventDate;
            return this;
        }

        public EventCreatedEventBuilder venue(String venue) {
            this.venue = venue;
            return this;
        }

        public EventCreatedEventBuilder organizerId(UserId organizerId) {
            this.organizerId = organizerId;
            return this;
        }

        public EventCreatedEvent build() {
            return new EventCreatedEvent(createdEventId, title, eventType, eventDate, venue, organizerId);
        }
    }

    @Override
    public EventId getAggregateId() {
        return createdEventId;
    }

    @Override
    @JsonIgnore
    public String getEventName() {
        return "EventCreatedEvent";
    }
}