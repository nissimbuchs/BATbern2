package ch.batbern.shared.unit.events;

import ch.batbern.shared.events.SpeakerInvitedEvent;
import ch.batbern.shared.types.EventId;
import ch.batbern.shared.types.SpeakerId;
import ch.batbern.shared.types.UserId;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.BeforeEach;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.*;

class SpeakerInvitedEventTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
    }

    @Test
    @DisplayName("should_createSpeakerInvitedEvent_when_speakerInvited")
    void should_createSpeakerInvitedEvent_when_speakerInvited() {
        EventId eventId = EventId.generate();
        SpeakerId speakerId = SpeakerId.generate();
        String speakerName = "Dr. Jane Smith";
        String speakerEmail = "jane.smith@example.com";
        String sessionTitle = "Future of AI in Business";
        LocalDateTime sessionTime = LocalDateTime.of(2024, 11, 15, 14, 0);
        UserId invitedBy = UserId.from("organizer-123");

        SpeakerInvitedEvent event = SpeakerInvitedEvent.builder()
            .eventId(eventId)
            .speakerId(speakerId)
            .speakerName(speakerName)
            .speakerEmail(speakerEmail)
            .sessionTitle(sessionTitle)
            .sessionTime(sessionTime)
            .invitedBy(invitedBy)
            .build();

        assertThat(event).isNotNull();
        assertThat(event.getTargetEventId()).isEqualTo(eventId);
        assertThat(event.getSpeakerId()).isEqualTo(speakerId);
        assertThat(event.getSpeakerName()).isEqualTo(speakerName);
        assertThat(event.getSpeakerEmail()).isEqualTo(speakerEmail);
        assertThat(event.getSessionTitle()).isEqualTo(sessionTitle);
        assertThat(event.getSessionTime()).isEqualTo(sessionTime);
        assertThat(event.getInvitedBy()).isEqualTo(invitedBy);
        assertThat(event.getAggregateId()).isEqualTo(speakerId);
        assertThat(event.getEventName()).isEqualTo("SpeakerInvitedEvent");
    }

    @Test
    @DisplayName("should_serializeToJSON_when_publishingEvent")
    void should_serializeToJSON_when_publishingEvent() throws Exception {
        SpeakerInvitedEvent event = SpeakerInvitedEvent.builder()
            .eventId(EventId.generate())
            .speakerId(SpeakerId.generate())
            .speakerName("Dr. Jane Smith")
            .speakerEmail("jane.smith@example.com")
            .sessionTitle("Future of AI in Business")
            .sessionTime(LocalDateTime.of(2024, 11, 15, 14, 0))
            .invitedBy(UserId.from("organizer-123"))
            .build();

        String json = objectMapper.writeValueAsString(event);

        assertThat(json).isNotNull();
        assertThat(json).contains("\"speakerName\":\"Dr. Jane Smith\"");
        assertThat(json).contains("\"speakerEmail\":\"jane.smith@example.com\"");
        assertThat(json).contains("\"sessionTitle\":\"Future of AI in Business\"");
        assertThat(json).contains("\"sessionTime\"");
        assertThat(json).contains("\"invitedBy\"");
    }

    @Test
    @DisplayName("should_deserializeFromJSON_when_receivingEvent")
    void should_deserializeFromJSON_when_receivingEvent() throws Exception {
        SpeakerInvitedEvent originalEvent = SpeakerInvitedEvent.builder()
            .eventId(EventId.generate())
            .speakerId(SpeakerId.generate())
            .speakerName("Dr. Jane Smith")
            .speakerEmail("jane.smith@example.com")
            .sessionTitle("Future of AI in Business")
            .sessionTime(LocalDateTime.of(2024, 11, 15, 14, 0))
            .invitedBy(UserId.from("organizer-123"))
            .build();

        String json = objectMapper.writeValueAsString(originalEvent);
        SpeakerInvitedEvent deserializedEvent = objectMapper.readValue(json, SpeakerInvitedEvent.class);

        assertThat(deserializedEvent).isNotNull();
        assertThat(deserializedEvent.getSpeakerName()).isEqualTo(originalEvent.getSpeakerName());
        assertThat(deserializedEvent.getSpeakerEmail()).isEqualTo(originalEvent.getSpeakerEmail());
        assertThat(deserializedEvent.getSessionTitle()).isEqualTo(originalEvent.getSessionTitle());
        assertThat(deserializedEvent.getSessionTime()).isEqualTo(originalEvent.getSessionTime());
    }

    @Test
    @DisplayName("should_includeInvitationStatus_when_eventCreated")
    void should_includeInvitationStatus_when_eventCreated() {
        SpeakerInvitedEvent event = SpeakerInvitedEvent.builder()
            .eventId(EventId.generate())
            .speakerId(SpeakerId.generate())
            .speakerName("Dr. Jane Smith")
            .speakerEmail("jane.smith@example.com")
            .sessionTitle("Future of AI in Business")
            .sessionTime(LocalDateTime.of(2024, 11, 15, 14, 0))
            .invitedBy(UserId.from("organizer-123"))
            .invitationStatus("PENDING")
            .build();

        assertThat(event.getInvitationStatus()).isEqualTo("PENDING");
    }

    @Test
    @DisplayName("should_defaultToPendingStatus_when_statusNotProvided")
    void should_defaultToPendingStatus_when_statusNotProvided() {
        SpeakerInvitedEvent event = SpeakerInvitedEvent.builder()
            .eventId(EventId.generate())
            .speakerId(SpeakerId.generate())
            .speakerName("Dr. Jane Smith")
            .speakerEmail("jane.smith@example.com")
            .sessionTitle("Future of AI in Business")
            .sessionTime(LocalDateTime.of(2024, 11, 15, 14, 0))
            .invitedBy(UserId.from("organizer-123"))
            .build();

        assertThat(event.getInvitationStatus()).isEqualTo("PENDING");
    }

    @Test
    @DisplayName("should_includeEventMetadata_when_eventCreated")
    void should_includeEventMetadata_when_eventCreated() {
        SpeakerInvitedEvent event = SpeakerInvitedEvent.builder()
            .eventId(EventId.generate())
            .speakerId(SpeakerId.generate())
            .speakerName("Dr. Jane Smith")
            .speakerEmail("jane.smith@example.com")
            .sessionTitle("Future of AI in Business")
            .sessionTime(LocalDateTime.of(2024, 11, 15, 14, 0))
            .invitedBy(UserId.from("organizer-123"))
            .build();

        assertThat(event.getOccurredAt()).isNotNull();
        assertThat(event.getVersion()).isEqualTo("1.0");
        assertThat(event.getMetadata()).isNotNull();
    }

    @Test
    @DisplayName("should_validateRequiredFields_when_eventCreated")
    void should_validateRequiredFields_when_eventCreated() {
        assertThatThrownBy(() -> SpeakerInvitedEvent.builder()
            .eventId(null)
            .speakerId(SpeakerId.generate())
            .speakerName("Dr. Jane Smith")
            .speakerEmail("jane.smith@example.com")
            .sessionTitle("Future of AI")
            .sessionTime(LocalDateTime.now())
            .invitedBy(UserId.from("organizer"))
            .build())
            .isInstanceOf(NullPointerException.class);

        assertThatThrownBy(() -> SpeakerInvitedEvent.builder()
            .eventId(EventId.generate())
            .speakerId(null)
            .speakerName("Dr. Jane Smith")
            .speakerEmail("jane.smith@example.com")
            .sessionTitle("Future of AI")
            .sessionTime(LocalDateTime.now())
            .invitedBy(UserId.from("organizer"))
            .build())
            .isInstanceOf(NullPointerException.class);
    }
}