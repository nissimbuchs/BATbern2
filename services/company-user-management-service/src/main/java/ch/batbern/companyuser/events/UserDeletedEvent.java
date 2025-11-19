package ch.batbern.companyuser.events;

import ch.batbern.shared.events.DomainEvent;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.NonNull;

/**
 * Domain event published when a user is deleted
 * Story 1.14-2, AC: 2, 11 (Event Publishing)
 * Story 1.16.2: aggregateId uses username (String), not UUID
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserDeletedEvent extends DomainEvent<String> {

    @JsonProperty("username")
    @NonNull
    private String username;

    @JsonProperty("email")
    @NonNull
    private String email;

    @JsonProperty("reason")
    private String reason;

    public UserDeletedEvent(String username, String email, String reason, String deletedBy) {
        super(username, "UserDeletedEvent", deletedBy);
        if (username == null) {
            throw new NullPointerException("username is marked non-null but is null");
        }
        if (email == null) {
            throw new NullPointerException("email is marked non-null but is null");
        }

        this.username = username;
        this.email = email;
        this.reason = reason;
    }

    public static UserDeletedEventBuilder builder() {
        return new UserDeletedEventBuilder();
    }

    public static class UserDeletedEventBuilder {
        private String username;
        private String email;
        private String reason;
        private String deletedBy;

        public UserDeletedEventBuilder username(String username) {
            this.username = username;
            return this;
        }

        public UserDeletedEventBuilder email(String email) {
            this.email = email;
            return this;
        }

        public UserDeletedEventBuilder reason(String reason) {
            this.reason = reason;
            return this;
        }

        public UserDeletedEventBuilder deletedBy(String deletedBy) {
            this.deletedBy = deletedBy;
            return this;
        }

        public UserDeletedEvent build() {
            return new UserDeletedEvent(username, email, reason, deletedBy);
        }
    }

    @Override
    public String getAggregateId() {
        return username;
    }

    @Override
    @JsonIgnore
    public String getEventName() {
        return "UserDeletedEvent";
    }

    @Override
    @JsonIgnore
    public String getAggregateType() {
        return "User";
    }
}
