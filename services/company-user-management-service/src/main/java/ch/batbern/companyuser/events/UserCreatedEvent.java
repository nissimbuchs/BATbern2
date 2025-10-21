package ch.batbern.companyuser.events;

import ch.batbern.shared.events.DomainEvent;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.NonNull;

/**
 * Domain event published when a new user is created
 * Story 1.14-2, AC: 2, 11 (Event Publishing)
 * Story 1.16.2: aggregateId uses username (String), not UUID
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserCreatedEvent extends DomainEvent<String> {

    @JsonProperty("username")
    @NonNull
    private String username;

    @JsonProperty("email")
    @NonNull
    private String email;

    @JsonProperty("firstName")
    @NonNull
    private String firstName;

    @JsonProperty("lastName")
    @NonNull
    private String lastName;

    @JsonProperty("companyId")
    private String companyId;

    @JsonProperty("cognitoUserId")
    @NonNull
    private String cognitoUserId;

    public UserCreatedEvent(String username, String email, String firstName, String lastName,
                           String companyId, String cognitoUserId, String createdBy) {
        super(username, "UserCreatedEvent", createdBy);
        if (username == null) throw new NullPointerException("username is marked non-null but is null");
        if (email == null) throw new NullPointerException("email is marked non-null but is null");
        if (firstName == null) throw new NullPointerException("firstName is marked non-null but is null");
        if (lastName == null) throw new NullPointerException("lastName is marked non-null but is null");
        if (cognitoUserId == null) throw new NullPointerException("cognitoUserId is marked non-null but is null");

        this.username = username;
        this.email = email;
        this.firstName = firstName;
        this.lastName = lastName;
        this.companyId = companyId;
        this.cognitoUserId = cognitoUserId;
    }

    public static UserCreatedEventBuilder builder() {
        return new UserCreatedEventBuilder();
    }

    public static class UserCreatedEventBuilder {
        private String username;
        private String email;
        private String firstName;
        private String lastName;
        private String companyId;
        private String cognitoUserId;
        private String createdBy;

        public UserCreatedEventBuilder username(String username) {
            this.username = username;
            return this;
        }

        public UserCreatedEventBuilder email(String email) {
            this.email = email;
            return this;
        }

        public UserCreatedEventBuilder firstName(String firstName) {
            this.firstName = firstName;
            return this;
        }

        public UserCreatedEventBuilder lastName(String lastName) {
            this.lastName = lastName;
            return this;
        }

        public UserCreatedEventBuilder companyId(String companyId) {
            this.companyId = companyId;
            return this;
        }

        public UserCreatedEventBuilder cognitoUserId(String cognitoUserId) {
            this.cognitoUserId = cognitoUserId;
            return this;
        }

        public UserCreatedEventBuilder createdBy(String createdBy) {
            this.createdBy = createdBy;
            return this;
        }

        public UserCreatedEvent build() {
            return new UserCreatedEvent(username, email, firstName, lastName, companyId, cognitoUserId, createdBy);
        }
    }

    @Override
    public String getAggregateId() {
        return username;
    }

    @Override
    @JsonIgnore
    public String getEventName() {
        return "UserCreatedEvent";
    }

    @Override
    @JsonIgnore
    public String getAggregateType() {
        return "User";
    }
}
