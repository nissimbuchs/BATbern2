package ch.batbern.shared.events;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.NonNull;

import java.util.UUID;

/**
 * Domain event published when a speaker is added to an event's speaker pool.
 *
 * This event triggers workflow transitions when the event is in CREATED or TOPIC_SELECTION state
 * and a speaker is added to the pool, indicating speaker identification has begun.
 *
 * @see ch.batbern.shared.types.EventWorkflowState#SPEAKER_IDENTIFICATION
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SpeakerAddedToPoolEvent extends DomainEvent<UUID> {

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

    @JsonProperty("assignedOrganizerId")
    private String assignedOrganizerId;

    @JsonProperty("addedBy")
    @NonNull
    private String addedBy;

    public SpeakerAddedToPoolEvent(UUID eventId, String eventCode, UUID speakerPoolId,
                                   String speakerName, String company, String expertise,
                                   String assignedOrganizerId, String addedBy) {
        super(eventId, "SpeakerAddedToPoolEvent", addedBy);
        if (eventCode == null) {
            throw new NullPointerException("eventCode is marked non-null but is null");
        }
        if (speakerPoolId == null) {
            throw new NullPointerException("speakerPoolId is marked non-null but is null");
        }
        if (speakerName == null) {
            throw new NullPointerException("speakerName is marked non-null but is null");
        }
        if (addedBy == null) {
            throw new NullPointerException("addedBy is marked non-null but is null");
        }
        this.eventCode = eventCode;
        this.speakerPoolId = speakerPoolId;
        this.speakerName = speakerName;
        this.company = company;
        this.expertise = expertise;
        this.assignedOrganizerId = assignedOrganizerId;
        this.addedBy = addedBy;
    }

    public static SpeakerAddedToPoolEventBuilder builder() {
        return new SpeakerAddedToPoolEventBuilder();
    }

    public static class SpeakerAddedToPoolEventBuilder {
        private UUID eventId;
        private String eventCode;
        private UUID speakerPoolId;
        private String speakerName;
        private String company;
        private String expertise;
        private String assignedOrganizerId;
        private String addedBy;

        public SpeakerAddedToPoolEventBuilder eventId(UUID eventId) {
            this.eventId = eventId;
            return this;
        }

        public SpeakerAddedToPoolEventBuilder eventCode(String eventCode) {
            this.eventCode = eventCode;
            return this;
        }

        public SpeakerAddedToPoolEventBuilder speakerPoolId(UUID speakerPoolId) {
            this.speakerPoolId = speakerPoolId;
            return this;
        }

        public SpeakerAddedToPoolEventBuilder speakerName(String speakerName) {
            this.speakerName = speakerName;
            return this;
        }

        public SpeakerAddedToPoolEventBuilder company(String company) {
            this.company = company;
            return this;
        }

        public SpeakerAddedToPoolEventBuilder expertise(String expertise) {
            this.expertise = expertise;
            return this;
        }

        public SpeakerAddedToPoolEventBuilder assignedOrganizerId(String assignedOrganizerId) {
            this.assignedOrganizerId = assignedOrganizerId;
            return this;
        }

        public SpeakerAddedToPoolEventBuilder addedBy(String addedBy) {
            this.addedBy = addedBy;
            return this;
        }

        public SpeakerAddedToPoolEvent build() {
            return new SpeakerAddedToPoolEvent(eventId, eventCode, speakerPoolId, speakerName,
                company, expertise, assignedOrganizerId, addedBy);
        }
    }

    @Override
    public UUID getAggregateId() {
        return (UUID) super.getAggregateId();
    }

    @Override
    @JsonIgnore
    public String getEventName() {
        return "SpeakerAddedToPoolEvent";
    }
}
