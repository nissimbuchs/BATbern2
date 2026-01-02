package ch.batbern.shared.events;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.NonNull;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Domain event published when a session's timing (start time) is assigned.
 *
 * This event triggers workflow transitions when all sessions have timing assigned
 * and the event's published phase is "speakers" or "agenda", automatically transitioning
 * to AGENDA_PUBLISHED state.
 *
 * This eliminates manual state transitions and ensures the workflow accurately reflects
 * that the agenda is ready to be published.
 *
 * @see ch.batbern.shared.types.EventWorkflowState#AGENDA_PUBLISHED
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SessionTimingAssignedEvent extends DomainEvent<UUID> {

    @JsonProperty("eventCode")
    @NonNull
    private String eventCode;

    @JsonProperty("sessionId")
    @NonNull
    private UUID sessionId;

    @JsonProperty("sessionTitle")
    @NonNull
    private String sessionTitle;

    @JsonProperty("startTime")
    @NonNull
    private LocalDateTime startTime;

    @JsonProperty("duration")
    private Integer duration;

    @JsonProperty("speakerPoolId")
    private UUID speakerPoolId;

    @JsonProperty("speakerName")
    private String speakerName;

    @JsonProperty("assignedBy")
    @NonNull
    private String assignedBy;

    public SessionTimingAssignedEvent(UUID eventId, String eventCode, UUID sessionId,
                                      String sessionTitle, LocalDateTime startTime,
                                      Integer duration, UUID speakerPoolId,
                                      String speakerName, String assignedBy) {
        super(eventId, "SessionTimingAssignedEvent", assignedBy);
        if (eventCode == null) {
            throw new NullPointerException("eventCode is marked non-null but is null");
        }
        if (sessionId == null) {
            throw new NullPointerException("sessionId is marked non-null but is null");
        }
        if (sessionTitle == null) {
            throw new NullPointerException("sessionTitle is marked non-null but is null");
        }
        if (startTime == null) {
            throw new NullPointerException("startTime is marked non-null but is null");
        }
        if (assignedBy == null) {
            throw new NullPointerException("assignedBy is marked non-null but is null");
        }
        this.eventCode = eventCode;
        this.sessionId = sessionId;
        this.sessionTitle = sessionTitle;
        this.startTime = startTime;
        this.duration = duration;
        this.speakerPoolId = speakerPoolId;
        this.speakerName = speakerName;
        this.assignedBy = assignedBy;
    }

    public static SessionTimingAssignedEventBuilder builder() {
        return new SessionTimingAssignedEventBuilder();
    }

    public static class SessionTimingAssignedEventBuilder {
        private UUID eventId;
        private String eventCode;
        private UUID sessionId;
        private String sessionTitle;
        private LocalDateTime startTime;
        private Integer duration;
        private UUID speakerPoolId;
        private String speakerName;
        private String assignedBy;

        public SessionTimingAssignedEventBuilder eventId(UUID eventId) {
            this.eventId = eventId;
            return this;
        }

        public SessionTimingAssignedEventBuilder eventCode(String eventCode) {
            this.eventCode = eventCode;
            return this;
        }

        public SessionTimingAssignedEventBuilder sessionId(UUID sessionId) {
            this.sessionId = sessionId;
            return this;
        }

        public SessionTimingAssignedEventBuilder sessionTitle(String sessionTitle) {
            this.sessionTitle = sessionTitle;
            return this;
        }

        public SessionTimingAssignedEventBuilder startTime(LocalDateTime startTime) {
            this.startTime = startTime;
            return this;
        }

        public SessionTimingAssignedEventBuilder duration(Integer duration) {
            this.duration = duration;
            return this;
        }

        public SessionTimingAssignedEventBuilder speakerPoolId(UUID speakerPoolId) {
            this.speakerPoolId = speakerPoolId;
            return this;
        }

        public SessionTimingAssignedEventBuilder speakerName(String speakerName) {
            this.speakerName = speakerName;
            return this;
        }

        public SessionTimingAssignedEventBuilder assignedBy(String assignedBy) {
            this.assignedBy = assignedBy;
            return this;
        }

        public SessionTimingAssignedEvent build() {
            return new SessionTimingAssignedEvent(eventId, eventCode, sessionId, sessionTitle,
                startTime, duration, speakerPoolId, speakerName, assignedBy);
        }
    }

    @Override
    public UUID getAggregateId() {
        return (UUID) super.getAggregateId();
    }

    @Override
    @JsonIgnore
    public String getEventName() {
        return "SessionTimingAssignedEvent";
    }
}
