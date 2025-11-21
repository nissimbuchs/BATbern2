package ch.batbern.shared.events;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.NonNull;

import java.time.LocalDate;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EventCreatedEvent extends DomainEvent<String> {

    @JsonProperty("createdEventCode")
    @NonNull
    private String createdEventCode;

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
    private String organizerId;

    public EventCreatedEvent(String eventCode, String title, String eventType,
                           LocalDate eventDate, String venue, String organizerId) {
        super(eventCode, "EventCreatedEvent", organizerId);
        if (eventCode == null) {
            throw new NullPointerException("eventCode is marked non-null but is null");
        }
        if (title == null) {
            throw new NullPointerException("title is marked non-null but is null");
        }
        if (eventType == null) {
            throw new NullPointerException("eventType is marked non-null but is null");
        }
        if (eventDate == null) {
            throw new NullPointerException("eventDate is marked non-null but is null");
        }
        if (venue == null) {
            throw new NullPointerException("venue is marked non-null but is null");
        }
        if (organizerId == null) {
            throw new NullPointerException("organizerId is marked non-null but is null");
        }
        this.createdEventCode = eventCode;
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
        private String createdEventCode;
        private String title;
        private String eventType;
        private LocalDate eventDate;
        private String venue;
        private String organizerId;

        public EventCreatedEventBuilder eventCode(String eventCode) {
            this.createdEventCode = eventCode;
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

        public EventCreatedEventBuilder organizerId(String organizerId) {
            this.organizerId = organizerId;
            return this;
        }

        public EventCreatedEvent build() {
            return new EventCreatedEvent(createdEventCode, title, eventType, eventDate, venue, organizerId);
        }
    }

    @Override
    public String getAggregateId() {
        return createdEventCode;
    }

    @Override
    @JsonIgnore
    public String getEventName() {
        return "EventCreatedEvent";
    }
}