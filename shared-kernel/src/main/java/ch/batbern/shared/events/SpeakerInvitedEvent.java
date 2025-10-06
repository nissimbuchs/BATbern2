package ch.batbern.shared.events;

import ch.batbern.shared.types.EventId;
import ch.batbern.shared.types.SpeakerId;
import ch.batbern.shared.types.UserId;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SpeakerInvitedEvent extends DomainEvent<SpeakerId> {

    @JsonProperty("targetEventId")
    @NonNull
    private EventId targetEventId;

    @JsonProperty("speakerId")
    @NonNull
    private SpeakerId speakerId;

    @JsonProperty("speakerName")
    @NonNull
    private String speakerName;

    @JsonProperty("speakerEmail")
    @NonNull
    private String speakerEmail;

    @JsonProperty("sessionTitle")
    @NonNull
    private String sessionTitle;

    @JsonProperty("sessionTime")
    @NonNull
    private LocalDateTime sessionTime;

    @JsonProperty("invitedBy")
    @NonNull
    private UserId invitedBy;

    @JsonProperty("invitationStatus")
    private String invitationStatus = "PENDING";

    public SpeakerInvitedEvent(EventId eventId, SpeakerId speakerId, String speakerName,
                              String speakerEmail, String sessionTitle, LocalDateTime sessionTime,
                              UserId invitedBy, String invitationStatus) {
        super(speakerId, "SpeakerInvitedEvent", invitedBy);
        if (eventId == null) throw new NullPointerException("eventId is marked non-null but is null");
        if (speakerId == null) throw new NullPointerException("speakerId is marked non-null but is null");
        if (speakerName == null) throw new NullPointerException("speakerName is marked non-null but is null");
        if (speakerEmail == null) throw new NullPointerException("speakerEmail is marked non-null but is null");
        if (sessionTitle == null) throw new NullPointerException("sessionTitle is marked non-null but is null");
        if (sessionTime == null) throw new NullPointerException("sessionTime is marked non-null but is null");
        if (invitedBy == null) throw new NullPointerException("invitedBy is marked non-null but is null");
        this.targetEventId = eventId;
        this.speakerId = speakerId;
        this.speakerName = speakerName;
        this.speakerEmail = speakerEmail;
        this.sessionTitle = sessionTitle;
        this.sessionTime = sessionTime;
        this.invitedBy = invitedBy;
        this.invitationStatus = invitationStatus != null ? invitationStatus : "PENDING";
    }

    public static SpeakerInvitedEventBuilder builder() {
        return new SpeakerInvitedEventBuilder();
    }

    public static class SpeakerInvitedEventBuilder {
        private EventId targetEventId;
        private SpeakerId speakerId;
        private String speakerName;
        private String speakerEmail;
        private String sessionTitle;
        private LocalDateTime sessionTime;
        private UserId invitedBy;
        private String invitationStatus;

        public SpeakerInvitedEventBuilder eventId(EventId eventId) {
            this.targetEventId = eventId;
            return this;
        }

        public SpeakerInvitedEventBuilder speakerId(SpeakerId speakerId) {
            this.speakerId = speakerId;
            return this;
        }

        public SpeakerInvitedEventBuilder speakerName(String speakerName) {
            this.speakerName = speakerName;
            return this;
        }

        public SpeakerInvitedEventBuilder speakerEmail(String speakerEmail) {
            this.speakerEmail = speakerEmail;
            return this;
        }

        public SpeakerInvitedEventBuilder sessionTitle(String sessionTitle) {
            this.sessionTitle = sessionTitle;
            return this;
        }

        public SpeakerInvitedEventBuilder sessionTime(LocalDateTime sessionTime) {
            this.sessionTime = sessionTime;
            return this;
        }

        public SpeakerInvitedEventBuilder invitedBy(UserId invitedBy) {
            this.invitedBy = invitedBy;
            return this;
        }

        public SpeakerInvitedEventBuilder invitationStatus(String invitationStatus) {
            this.invitationStatus = invitationStatus;
            return this;
        }

        public SpeakerInvitedEvent build() {
            return new SpeakerInvitedEvent(targetEventId, speakerId, speakerName, speakerEmail,
                sessionTitle, sessionTime, invitedBy, invitationStatus);
        }
    }

    @Override
    public SpeakerId getAggregateId() {
        return speakerId;
    }

    @Override
    @JsonIgnore
    public String getEventName() {
        return "SpeakerInvitedEvent";
    }
}