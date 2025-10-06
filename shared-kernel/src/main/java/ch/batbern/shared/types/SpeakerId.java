package ch.batbern.shared.types;

import ch.batbern.shared.exceptions.ValidationException;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

import java.util.Objects;
import java.util.UUID;

@Value
public class SpeakerId {
    UUID value;

    @JsonCreator
    private SpeakerId(@JsonProperty("value") UUID value) {
        if (value == null) {
            throw ValidationException.nullValue("SpeakerId");
        }
        this.value = value;
    }

    public static SpeakerId from(UUID uuid) {
        return new SpeakerId(uuid);
    }

    public static SpeakerId from(String uuidString) {
        if (uuidString == null) {
            throw ValidationException.nullValue("SpeakerId");
        }
        if (uuidString.trim().isEmpty()) {
            throw ValidationException.emptyValue("SpeakerId");
        }
        try {
            return new SpeakerId(UUID.fromString(uuidString));
        } catch (IllegalArgumentException e) {
            throw new ValidationException("Invalid SpeakerId format: " + uuidString, e);
        }
    }

    public static SpeakerId generate() {
        return new SpeakerId(UUID.randomUUID());
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
        SpeakerId speakerId = (SpeakerId) o;
        return Objects.equals(value, speakerId.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }
}