package ch.batbern.shared.events;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.NonNull;

import java.util.UUID;

/**
 * Domain event published when a speaker accepts an invitation to speak at an event.
 *
 * This event triggers workflow transitions when the event is in SPEAKER_IDENTIFICATION state
 * and a speaker accepts, indicating progress toward having enough confirmed speakers.
 *
 * The event listener will check if the minimum speaker threshold is met (1+ accepted speakers)
 * and automatically transition to SLOT_ASSIGNMENT state.
 *
 * @see ch.batbern.shared.types.EventWorkflowState#SLOT_ASSIGNMENT
 * @see ch.batbern.shared.types.SpeakerWorkflowState#ACCEPTED
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SpeakerAcceptedEvent extends DomainEvent<UUID> {

    @JsonProperty("eventCode")
    @NonNull
    private String eventCode;

    @JsonProperty("speakerPoolId")
    @NonNull
    private UUID speakerPoolId;

    @JsonProperty("speakerName")
    @NonNull
    private String speakerName;

    @JsonProperty("company")
    private String company;

    @JsonProperty("expertise")
    private String expertise;

    @JsonProperty("acceptedBy")
    @NonNull
    private String acceptedBy;

    public SpeakerAcceptedEvent(UUID eventId, String eventCode, UUID speakerPoolId,
                                String speakerName, String company, String expertise,
                                String acceptedBy) {
        super(eventId, "SpeakerAcceptedEvent", acceptedBy);
        if (eventCode == null) {
            throw new NullPointerException("eventCode is marked non-null but is null");
        }
        if (speakerPoolId == null) {
            throw new NullPointerException("speakerPoolId is marked non-null but is null");
        }
        if (speakerName == null) {
            throw new NullPointerException("speakerName is marked non-null but is null");
        }
        if (acceptedBy == null) {
            throw new NullPointerException("acceptedBy is marked non-null but is null");
        }
        this.eventCode = eventCode;
        this.speakerPoolId = speakerPoolId;
        this.speakerName = speakerName;
        this.company = company;
        this.expertise = expertise;
        this.acceptedBy = acceptedBy;
    }

    public static SpeakerAcceptedEventBuilder builder() {
        return new SpeakerAcceptedEventBuilder();
    }

    public static class SpeakerAcceptedEventBuilder {
        private UUID eventId;
        private String eventCode;
        private UUID speakerPoolId;
        private String speakerName;
        private String company;
        private String expertise;
        private String acceptedBy;

        public SpeakerAcceptedEventBuilder eventId(UUID eventId) {
            this.eventId = eventId;
            return this;
        }

        public SpeakerAcceptedEventBuilder eventCode(String eventCode) {
            this.eventCode = eventCode;
            return this;
        }

        public SpeakerAcceptedEventBuilder speakerPoolId(UUID speakerPoolId) {
            this.speakerPoolId = speakerPoolId;
            return this;
        }

        public SpeakerAcceptedEventBuilder speakerName(String speakerName) {
            this.speakerName = speakerName;
            return this;
        }

        public SpeakerAcceptedEventBuilder company(String company) {
            this.company = company;
            return this;
        }

        public SpeakerAcceptedEventBuilder expertise(String expertise) {
            this.expertise = expertise;
            return this;
        }

        public SpeakerAcceptedEventBuilder acceptedBy(String acceptedBy) {
            this.acceptedBy = acceptedBy;
            return this;
        }

        public SpeakerAcceptedEvent build() {
            return new SpeakerAcceptedEvent(eventId, eventCode, speakerPoolId, speakerName,
                company, expertise, acceptedBy);
        }
    }

    @Override
    public UUID getAggregateId() {
        return (UUID) super.getAggregateId();
    }

    @Override
    @JsonIgnore
    public String getEventName() {
        return "SpeakerAcceptedEvent";
    }
}
