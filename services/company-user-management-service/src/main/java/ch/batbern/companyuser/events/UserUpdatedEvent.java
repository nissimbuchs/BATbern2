package ch.batbern.companyuser.events;

import ch.batbern.shared.events.DomainEvent;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.NonNull;

import java.util.Map;

/**
 * Domain event published when a user is updated
 * Story 1.14-2, AC: 2, 11 (Event Publishing)
 * Story 1.16.2: aggregateId uses username (String), not UUID
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserUpdatedEvent extends DomainEvent<String> {

    @JsonProperty("username")
    @NonNull
    private String username;

    @JsonProperty("updatedFields")
    @NonNull
    private Map<String, Object> updatedFields;

    @JsonProperty("previousValues")
    private Map<String, Object> previousValues;

    public UserUpdatedEvent(String username, Map<String, Object> updatedFields,
                           Map<String, Object> previousValues, String updatedBy) {
        super(username, "UserUpdatedEvent", updatedBy);
        if (username == null) throw new NullPointerException("username is marked non-null but is null");
        if (updatedFields == null) throw new NullPointerException("updatedFields is marked non-null but is null");

        this.username = username;
        this.updatedFields = updatedFields;
        this.previousValues = previousValues;
    }

    public static UserUpdatedEventBuilder builder() {
        return new UserUpdatedEventBuilder();
    }

    public static class UserUpdatedEventBuilder {
        private String username;
        private Map<String, Object> updatedFields;
        private Map<String, Object> previousValues;
        private String updatedBy;

        public UserUpdatedEventBuilder username(String username) {
            this.username = username;
            return this;
        }

        public UserUpdatedEventBuilder updatedFields(Map<String, Object> updatedFields) {
            this.updatedFields = updatedFields;
            return this;
        }

        public UserUpdatedEventBuilder previousValues(Map<String, Object> previousValues) {
            this.previousValues = previousValues;
            return this;
        }

        public UserUpdatedEventBuilder updatedBy(String updatedBy) {
            this.updatedBy = updatedBy;
            return this;
        }

        public UserUpdatedEvent build() {
            return new UserUpdatedEvent(username, updatedFields, previousValues, updatedBy);
        }
    }

    @Override
    public String getAggregateId() {
        return username;
    }

    @Override
    @JsonIgnore
    public String getEventName() {
        return "UserUpdatedEvent";
    }

    @Override
    @JsonIgnore
    public String getAggregateType() {
        return "User";
    }
}
