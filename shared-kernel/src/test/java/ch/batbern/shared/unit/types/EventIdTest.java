package ch.batbern.shared.unit.types;

import ch.batbern.shared.exceptions.ValidationException;
import ch.batbern.shared.types.EventId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class EventIdTest {

    @Test
    @DisplayName("should_createValidEventId_when_validUUIDProvided")
    void should_createValidEventId_when_validUUIDProvided() {
        UUID uuid = UUID.randomUUID();

        EventId eventId = EventId.from(uuid);

        assertThat(eventId).isNotNull();
        assertThat(eventId.getValue()).isEqualTo(uuid);
    }

    @Test
    @DisplayName("should_throwValidationException_when_invalidUUIDProvided")
    void should_throwValidationException_when_invalidUUIDProvided() {
        String invalidUuid = "invalid-uuid";

        assertThatThrownBy(() -> EventId.from(invalidUuid))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("Invalid EventId format");
    }

    @Test
    @DisplayName("should_generateNewEventId_when_generateCalled")
    void should_generateNewEventId_when_generateCalled() {
        EventId eventId1 = EventId.generate();
        EventId eventId2 = EventId.generate();

        assertThat(eventId1).isNotNull();
        assertThat(eventId2).isNotNull();
        assertThat(eventId1).isNotEqualTo(eventId2);
        assertThat(eventId1.getValue()).isNotEqualTo(eventId2.getValue());
    }

    @Test
    @DisplayName("should_createEventIdFromString_when_validUUIDStringProvided")
    void should_createEventIdFromString_when_validUUIDStringProvided() {
        String validUuid = UUID.randomUUID().toString();

        EventId eventId = EventId.from(validUuid);

        assertThat(eventId).isNotNull();
        assertThat(eventId.toString()).isEqualTo(validUuid);
    }

    @Test
    @DisplayName("should_throwValidationException_when_nullProvided")
    void should_throwValidationException_when_nullProvided() {
        assertThatThrownBy(() -> EventId.from((UUID) null))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("EventId cannot be null");
    }

    @Test
    @DisplayName("should_implementEqualsCorrectly_when_comparingEventIds")
    void should_implementEqualsCorrectly_when_comparingEventIds() {
        UUID uuid = UUID.randomUUID();
        EventId eventId1 = EventId.from(uuid);
        EventId eventId2 = EventId.from(uuid);
        EventId eventId3 = EventId.generate();

        assertThat(eventId1).isEqualTo(eventId2);
        assertThat(eventId1).isNotEqualTo(eventId3);
        assertThat(eventId1.hashCode()).isEqualTo(eventId2.hashCode());
        assertThat(eventId1.hashCode()).isNotEqualTo(eventId3.hashCode());
    }

    @Test
    @DisplayName("should_beImmutable_when_created")
    void should_beImmutable_when_created() {
        EventId eventId = EventId.generate();
        UUID originalValue = eventId.getValue();

        assertThat(eventId.getValue()).isEqualTo(originalValue);
        assertThat(eventId.getValue()).isNotSameAs(originalValue);
    }
}