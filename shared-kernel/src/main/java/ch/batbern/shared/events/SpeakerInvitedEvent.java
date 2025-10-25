package ch.batbern.shared.events;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SpeakerInvitedEvent extends DomainEvent<String> {

    @JsonProperty("targetEventCode")
    @NonNull
    private String targetEventCode;

    @JsonProperty("speakerUsername")
    @NonNull
    private String speakerUsername;

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
    private String invitedBy;

    @JsonProperty("invitationStatus")
    private String invitationStatus = "PENDING";

    public SpeakerInvitedEvent(String eventCode, String speakerUsername, String speakerName,
                              String speakerEmail, String sessionTitle, LocalDateTime sessionTime,
                              String invitedBy, String invitationStatus) {
        super(speakerUsername, "SpeakerInvitedEvent", invitedBy);
        if (eventCode == null) throw new NullPointerException("eventCode is marked non-null but is null");
        if (speakerUsername == null) throw new NullPointerException("speakerUsername is marked non-null but is null");
        if (speakerName == null) throw new NullPointerException("speakerName is marked non-null but is null");
        if (speakerEmail == null) throw new NullPointerException("speakerEmail is marked non-null but is null");
        if (sessionTitle == null) throw new NullPointerException("sessionTitle is marked non-null but is null");
        if (sessionTime == null) throw new NullPointerException("sessionTime is marked non-null but is null");
        if (invitedBy == null) throw new NullPointerException("invitedBy is marked non-null but is null");
        this.targetEventCode = eventCode;
        this.speakerUsername = speakerUsername;
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
        private String targetEventCode;
        private String speakerUsername;
        private String speakerName;
        private String speakerEmail;
        private String sessionTitle;
        private LocalDateTime sessionTime;
        private String invitedBy;
        private String invitationStatus;

        public SpeakerInvitedEventBuilder eventCode(String eventCode) {
            this.targetEventCode = eventCode;
            return this;
        }

        public SpeakerInvitedEventBuilder speakerUsername(String speakerUsername) {
            this.speakerUsername = speakerUsername;
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

        public SpeakerInvitedEventBuilder invitedBy(String invitedBy) {
            this.invitedBy = invitedBy;
            return this;
        }

        public SpeakerInvitedEventBuilder invitationStatus(String invitationStatus) {
            this.invitationStatus = invitationStatus;
            return this;
        }

        public SpeakerInvitedEvent build() {
            return new SpeakerInvitedEvent(targetEventCode, speakerUsername, speakerName, speakerEmail,
                sessionTitle, sessionTime, invitedBy, invitationStatus);
        }
    }

    @Override
    public String getAggregateId() {
        return speakerUsername;
    }

    @Override
    @JsonIgnore
    public String getEventName() {
        return "SpeakerInvitedEvent";
    }
}