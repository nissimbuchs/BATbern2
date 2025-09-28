package ch.batbern.shared.types;

import ch.batbern.shared.exceptions.ValidationException;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

import java.util.Objects;
import java.util.UUID;

@Value
public class EventId {
    UUID value;

    @JsonCreator
    private EventId(@JsonProperty("value") UUID value) {
        if (value == null) {
            throw ValidationException.nullValue("EventId");
        }
        this.value = value;
    }

    public static EventId from(UUID uuid) {
        return new EventId(uuid);
    }

    public static EventId from(String uuidString) {
        if (uuidString == null) {
            throw ValidationException.nullValue("EventId");
        }
        if (uuidString.trim().isEmpty()) {
            throw ValidationException.emptyValue("EventId");
        }
        try {
            return new EventId(UUID.fromString(uuidString));
        } catch (IllegalArgumentException e) {
            throw new ValidationException("Invalid EventId format: " + uuidString, e);
        }
    }

    public static EventId generate() {
        return new EventId(UUID.randomUUID());
    }

    public UUID getValue() {
        return UUID.fromString(value.toString());
    }

    @Override
    public String toString() {
        return value.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EventId eventId = (EventId) o;
        return Objects.equals(value, eventId.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }
}