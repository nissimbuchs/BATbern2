package ch.batbern.shared.types;

import ch.batbern.shared.exceptions.ValidationException;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

import java.util.Objects;

@Value
public class UserId {
    private static final String ANONYMOUS_USER = "anonymous";

    String value;

    @JsonCreator
    private UserId(@JsonProperty("value") String value) {
        if (value == null) {
            throw ValidationException.nullValue("UserId");
        }
        if (value.trim().isEmpty()) {
            if (value.length() > 0) {
                // String has only whitespace
                throw ValidationException.blankValue("UserId");
            } else {
                // String is empty
                throw ValidationException.emptyValue("UserId");
            }
        }

        this.value = value;
    }

    public static UserId from(String userId) {
        return new UserId(userId);
    }

    public static UserId anonymous() {
        return new UserId(ANONYMOUS_USER);
    }

    @JsonIgnore
    public boolean isAnonymous() {
        return ANONYMOUS_USER.equals(value);
    }

    @JsonIgnore
    public String getProvider() {
        if (value.contains("google_")) {
            return "google";
        } else if (value.contains("facebook_")) {
            return "facebook";
        } else if (value.contains(":") && value.matches("^[a-z]{2}-[a-z]+-\\d+:.*")) {
            return "cognito";
        } else {
            return "cognito";
        }
    }

    @Override
    public String toString() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UserId userId = (UserId) o;
        return Objects.equals(value, userId.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }
}