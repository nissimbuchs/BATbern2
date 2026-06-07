package ch.batbern.shared.unit.events;

import ch.batbern.shared.events.SpeakerPromotedToReadyEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Test for SpeakerPromotedToReadyEvent (CONTACTED → READY provisioning gate per ADR-009 §0.2).
 *
 * Story 11.B.1: New event signalling the provisioning moment. Consumed by Phase E
 * (Story 11.E.2) to send the Cognito invitation email containing login link + temporary
 * password.
 */
class SpeakerPromotedToReadyEventTest {

    private ObjectMapper objectMapper;

    private static final UUID SPEAKER_POOL_ID = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final String EVENT_CODE = "BATbern56";
    private static final String USERNAME = "john.doe";
    private static final String EMAIL = "john.doe@example.com";
    private static final String PROMOTED_BY = "organizer.user";

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
    }

    @Test
    @DisplayName("should_createSpeakerPromotedToReadyEvent_when_validFieldsProvided")
    void should_createSpeakerPromotedToReadyEvent_when_validFieldsProvided() {
        Instant promotedAt = Instant.parse("2026-05-15T10:00:00Z");

        SpeakerPromotedToReadyEvent event = SpeakerPromotedToReadyEvent.builder()
            .speakerPoolId(SPEAKER_POOL_ID)
            .eventCode(EVENT_CODE)
            .username(USERNAME)
            .email(EMAIL)
            .promotedAt(promotedAt)
            .promotedByUsername(PROMOTED_BY)
            .build();

        assertThat(event).isNotNull();
        assertThat(event.getSpeakerPoolId()).isEqualTo(SPEAKER_POOL_ID);
        assertThat(event.getEventCode()).isEqualTo(EVENT_CODE);
        assertThat(event.getUsername()).isEqualTo(USERNAME);
        assertThat(event.getEmail()).isEqualTo(EMAIL);
        assertThat(event.getPromotedAt()).isEqualTo(promotedAt);
        assertThat(event.getPromotedByUsername()).isEqualTo(PROMOTED_BY);
        assertThat(event.getUserId()).isEqualTo(PROMOTED_BY);
        assertThat(event.getEventType()).isEqualTo("SpeakerPromotedToReadyEvent");
    }

    @Test
    @DisplayName("should_serializeToJSON_when_publishingEvent")
    void should_serializeToJSON_when_publishingEvent() throws Exception {
        Instant promotedAt = Instant.parse("2026-05-15T10:00:00Z");
        SpeakerPromotedToReadyEvent event = SpeakerPromotedToReadyEvent.builder()
            .speakerPoolId(SPEAKER_POOL_ID)
            .eventCode(EVENT_CODE)
            .username(USERNAME)
            .email(EMAIL)
            .promotedAt(promotedAt)
            .promotedByUsername(PROMOTED_BY)
            .build();

        String json = objectMapper.writeValueAsString(event);

        assertThat(json).isNotNull();
        assertThat(json).contains("\"speakerPoolId\":\"" + SPEAKER_POOL_ID + "\"");
        assertThat(json).contains("\"eventCode\":\"" + EVENT_CODE + "\"");
        assertThat(json).contains("\"username\":\"" + USERNAME + "\"");
        assertThat(json).contains("\"email\":\"" + EMAIL + "\"");
        assertThat(json).contains("\"promotedAt\"");
        assertThat(json).contains("\"promotedByUsername\":\"" + PROMOTED_BY + "\"");
        assertThat(json).contains("\"eventType\":\"SpeakerPromotedToReadyEvent\"");
    }

    @Test
    @DisplayName("should_throwNullPointerException_when_speakerPoolIdIsNull")
    void should_throwNullPointerException_when_speakerPoolIdIsNull() {
        assertThatThrownBy(() -> SpeakerPromotedToReadyEvent.builder()
            .speakerPoolId(null)
            .eventCode(EVENT_CODE)
            .username(USERNAME)
            .email(EMAIL)
            .promotedAt(Instant.now())
            .promotedByUsername(PROMOTED_BY)
            .build())
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("speakerPoolId");
    }

    @Test
    @DisplayName("should_throwNullPointerException_when_eventCodeIsNull")
    void should_throwNullPointerException_when_eventCodeIsNull() {
        assertThatThrownBy(() -> SpeakerPromotedToReadyEvent.builder()
            .speakerPoolId(SPEAKER_POOL_ID)
            .eventCode(null)
            .username(USERNAME)
            .email(EMAIL)
            .promotedAt(Instant.now())
            .promotedByUsername(PROMOTED_BY)
            .build())
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("eventCode");
    }

    @Test
    @DisplayName("should_throwNullPointerException_when_usernameIsNull")
    void should_throwNullPointerException_when_usernameIsNull() {
        assertThatThrownBy(() -> SpeakerPromotedToReadyEvent.builder()
            .speakerPoolId(SPEAKER_POOL_ID)
            .eventCode(EVENT_CODE)
            .username(null)
            .email(EMAIL)
            .promotedAt(Instant.now())
            .promotedByUsername(PROMOTED_BY)
            .build())
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("username");
    }

    @Test
    @DisplayName("should_throwNullPointerException_when_emailIsNull")
    void should_throwNullPointerException_when_emailIsNull() {
        assertThatThrownBy(() -> SpeakerPromotedToReadyEvent.builder()
            .speakerPoolId(SPEAKER_POOL_ID)
            .eventCode(EVENT_CODE)
            .username(USERNAME)
            .email(null)
            .promotedAt(Instant.now())
            .promotedByUsername(PROMOTED_BY)
            .build())
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("email");
    }

    @Test
    @DisplayName("should_throwNullPointerException_when_promotedByUsernameIsNull")
    void should_throwNullPointerException_when_promotedByUsernameIsNull() {
        assertThatThrownBy(() -> SpeakerPromotedToReadyEvent.builder()
            .speakerPoolId(SPEAKER_POOL_ID)
            .eventCode(EVENT_CODE)
            .username(USERNAME)
            .email(EMAIL)
            .promotedAt(Instant.now())
            .promotedByUsername(null)
            .build())
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("promotedByUsername");
    }

    @Test
    @DisplayName("should_returnSpeakerPoolIdAsAggregateId_when_eventCreated")
    void should_returnSpeakerPoolIdAsAggregateId_when_eventCreated() {
        SpeakerPromotedToReadyEvent event = SpeakerPromotedToReadyEvent.builder()
            .speakerPoolId(SPEAKER_POOL_ID)
            .eventCode(EVENT_CODE)
            .username(USERNAME)
            .email(EMAIL)
            .promotedAt(Instant.now())
            .promotedByUsername(PROMOTED_BY)
            .build();

        assertThat(event.getAggregateId()).isEqualTo(SPEAKER_POOL_ID);
    }

    @Test
    @DisplayName("should_returnSpeakerPromotedToReadyEvent_when_getEventNameCalled")
    void should_returnSpeakerPromotedToReadyEvent_when_getEventNameCalled() {
        SpeakerPromotedToReadyEvent event = SpeakerPromotedToReadyEvent.builder()
            .speakerPoolId(SPEAKER_POOL_ID)
            .eventCode(EVENT_CODE)
            .username(USERNAME)
            .email(EMAIL)
            .promotedAt(Instant.now())
            .promotedByUsername(PROMOTED_BY)
            .build();

        assertThat(event.getEventName()).isEqualTo("SpeakerPromotedToReadyEvent");
    }

    @Test
    @DisplayName("should_defaultPromotedAtToNow_when_notProvided")
    void should_defaultPromotedAtToNow_when_notProvided() {
        Instant before = Instant.now().minus(1, ChronoUnit.SECONDS);

        SpeakerPromotedToReadyEvent event = SpeakerPromotedToReadyEvent.builder()
            .speakerPoolId(SPEAKER_POOL_ID)
            .eventCode(EVENT_CODE)
            .username(USERNAME)
            .email(EMAIL)
            .promotedAt(null)
            .promotedByUsername(PROMOTED_BY)
            .build();

        Instant after = Instant.now().plus(1, ChronoUnit.SECONDS);
        assertThat(event.getPromotedAt()).isBetween(before, after);
    }

    @Test
    @DisplayName("should_roundTripThroughJackson_when_serialisedAndDeserialised")
    void should_roundTripThroughJackson_when_serialisedAndDeserialised() throws Exception {
        Instant promotedAt = Instant.parse("2026-05-15T10:00:00Z");
        SpeakerPromotedToReadyEvent original = SpeakerPromotedToReadyEvent.builder()
            .speakerPoolId(SPEAKER_POOL_ID)
            .eventCode(EVENT_CODE)
            .username(USERNAME)
            .email(EMAIL)
            .promotedAt(promotedAt)
            .promotedByUsername(PROMOTED_BY)
            .build();

        String json = objectMapper.writeValueAsString(original);
        SpeakerPromotedToReadyEvent restored = objectMapper.readValue(json, SpeakerPromotedToReadyEvent.class);

        assertThat(restored.getSpeakerPoolId()).isEqualTo(SPEAKER_POOL_ID);
        assertThat(restored.getEventCode()).isEqualTo(EVENT_CODE);
        assertThat(restored.getUsername()).isEqualTo(USERNAME);
        assertThat(restored.getEmail()).isEqualTo(EMAIL);
        assertThat(restored.getPromotedAt()).isEqualTo(promotedAt);
        assertThat(restored.getPromotedByUsername()).isEqualTo(PROMOTED_BY);
    }

    @Test
    @DisplayName("should_roundTripNonAsciiFields_when_serialisedAndDeserialised")
    void should_roundTripNonAsciiFields_when_serialisedAndDeserialised() throws Exception {
        String unicodeUsername = "luc.müller";
        String unicodeEmail = "lüönd@müller.ch";
        Instant promotedAt = Instant.parse("2026-05-15T10:00:00Z");
        SpeakerPromotedToReadyEvent original = SpeakerPromotedToReadyEvent.builder()
            .speakerPoolId(SPEAKER_POOL_ID)
            .eventCode(EVENT_CODE)
            .username(unicodeUsername)
            .email(unicodeEmail)
            .promotedAt(promotedAt)
            .promotedByUsername(PROMOTED_BY)
            .build();

        String json = objectMapper.writeValueAsString(original);
        SpeakerPromotedToReadyEvent restored = objectMapper.readValue(json, SpeakerPromotedToReadyEvent.class);

        assertThat(restored.getUsername()).isEqualTo(unicodeUsername);
        assertThat(restored.getEmail()).isEqualTo(unicodeEmail);
    }
}
