package ch.batbern.shared.unit.types;

import ch.batbern.shared.exceptions.ValidationException;
import ch.batbern.shared.types.SpeakerId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class SpeakerIdTest {

    @Test
    @DisplayName("should_createValidSpeakerId_when_validDataProvided")
    void should_createValidSpeakerId_when_validDataProvided() {
        UUID uuid = UUID.randomUUID();

        SpeakerId speakerId = SpeakerId.from(uuid);

        assertThat(speakerId).isNotNull();
        assertThat(speakerId.getValue()).isEqualTo(uuid);
    }

    @Test
    @DisplayName("should_throwValidationException_when_invalidUUIDProvided")
    void should_throwValidationException_when_invalidUUIDProvided() {
        String invalidUuid = "not-a-valid-uuid";

        assertThatThrownBy(() -> SpeakerId.from(invalidUuid))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("Invalid SpeakerId format");
    }

    @Test
    @DisplayName("should_generateNewSpeakerId_when_generateCalled")
    void should_generateNewSpeakerId_when_generateCalled() {
        SpeakerId speakerId1 = SpeakerId.generate();
        SpeakerId speakerId2 = SpeakerId.generate();

        assertThat(speakerId1).isNotNull();
        assertThat(speakerId2).isNotNull();
        assertThat(speakerId1).isNotEqualTo(speakerId2);
        assertThat(speakerId1.getValue()).isNotEqualTo(speakerId2.getValue());
    }

    @Test
    @DisplayName("should_createSpeakerIdFromString_when_validUUIDStringProvided")
    void should_createSpeakerIdFromString_when_validUUIDStringProvided() {
        String validUuid = UUID.randomUUID().toString();

        SpeakerId speakerId = SpeakerId.from(validUuid);

        assertThat(speakerId).isNotNull();
        assertThat(speakerId.toString()).isEqualTo(validUuid);
    }

    @Test
    @DisplayName("should_throwValidationException_when_nullProvided")
    void should_throwValidationException_when_nullProvided() {
        assertThatThrownBy(() -> SpeakerId.from((UUID) null))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("SpeakerId cannot be null");
    }

    @Test
    @DisplayName("should_throwValidationException_when_emptyStringProvided")
    void should_throwValidationException_when_emptyStringProvided() {
        assertThatThrownBy(() -> SpeakerId.from(""))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("SpeakerId cannot be empty");
    }

    @Test
    @DisplayName("should_implementEqualsCorrectly_when_comparingSpeakerIds")
    void should_implementEqualsCorrectly_when_comparingSpeakerIds() {
        UUID uuid = UUID.randomUUID();
        SpeakerId speakerId1 = SpeakerId.from(uuid);
        SpeakerId speakerId2 = SpeakerId.from(uuid);
        SpeakerId speakerId3 = SpeakerId.generate();

        assertThat(speakerId1).isEqualTo(speakerId2);
        assertThat(speakerId1).isNotEqualTo(speakerId3);
        assertThat(speakerId1.hashCode()).isEqualTo(speakerId2.hashCode());
        assertThat(speakerId1.hashCode()).isNotEqualTo(speakerId3.hashCode());
    }

    @Test
    @DisplayName("should_beImmutable_when_created")
    void should_beImmutable_when_created() {
        SpeakerId speakerId = SpeakerId.generate();
        UUID originalValue = speakerId.getValue();

        assertThat(speakerId.getValue()).isEqualTo(originalValue);
        assertThat(speakerId.getValue()).isNotSameAs(originalValue);
    }
}