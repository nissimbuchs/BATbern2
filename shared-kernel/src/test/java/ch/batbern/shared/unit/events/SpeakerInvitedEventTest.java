package ch.batbern.shared.unit.events;

import ch.batbern.shared.events.SpeakerInvitedEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.BeforeEach;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
        String eventCode = "BATbern56";
        String speakerUsername = "jane.smith";
        String speakerName = "Dr. Jane Smith";
        String speakerEmail = "jane.smith@example.com";
        String sessionTitle = "Future of AI in Business";
        LocalDateTime sessionTime = LocalDateTime.of(2024, 11, 15, 14, 0);
        String invitedBy = "organizer.user";

        SpeakerInvitedEvent event = SpeakerInvitedEvent.builder()
            .eventCode(eventCode)
            .speakerUsername(speakerUsername)
            .speakerName(speakerName)
            .speakerEmail(speakerEmail)
            .sessionTitle(sessionTitle)
            .sessionTime(sessionTime)
            .invitedBy(invitedBy)
            .build();

        assertThat(event).isNotNull();
        assertThat(event.getTargetEventCode()).isEqualTo(eventCode);
        assertThat(event.getSpeakerUsername()).isEqualTo(speakerUsername);
        assertThat(event.getSpeakerName()).isEqualTo(speakerName);
        assertThat(event.getSpeakerEmail()).isEqualTo(speakerEmail);
        assertThat(event.getSessionTitle()).isEqualTo(sessionTitle);
        assertThat(event.getSessionTime()).isEqualTo(sessionTime);
        assertThat(event.getInvitedBy()).isEqualTo(invitedBy);
        assertThat(event.getAggregateId()).isEqualTo(speakerUsername);
        assertThat(event.getEventName()).isEqualTo("SpeakerInvitedEvent");
    }

    @Test
    @DisplayName("should_serializeToJSON_when_publishingEvent")
    void should_serializeToJSON_when_publishingEvent() throws Exception {
        SpeakerInvitedEvent event = SpeakerInvitedEvent.builder()
            .eventCode("BATbern56")
            .speakerUsername("jane.smith")
            .speakerName("Dr. Jane Smith")
            .speakerEmail("jane.smith@example.com")
            .sessionTitle("Future of AI in Business")
            .sessionTime(LocalDateTime.of(2024, 11, 15, 14, 0))
            .invitedBy("organizer.user")
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
            .eventCode("BATbern56")
            .speakerUsername("jane.smith")
            .speakerName("Dr. Jane Smith")
            .speakerEmail("jane.smith@example.com")
            .sessionTitle("Future of AI in Business")
            .sessionTime(LocalDateTime.of(2024, 11, 15, 14, 0))
            .invitedBy("organizer.user")
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
            .eventCode("BATbern56")
            .speakerUsername("jane.smith")
            .speakerName("Dr. Jane Smith")
            .speakerEmail("jane.smith@example.com")
            .sessionTitle("Future of AI in Business")
            .sessionTime(LocalDateTime.of(2024, 11, 15, 14, 0))
            .invitedBy("organizer.user")
            .invitationStatus("PENDING")
            .build();

        assertThat(event.getInvitationStatus()).isEqualTo("PENDING");
    }

    @Test
    @DisplayName("should_defaultToPendingStatus_when_statusNotProvided")
    void should_defaultToPendingStatus_when_statusNotProvided() {
        SpeakerInvitedEvent event = SpeakerInvitedEvent.builder()
            .eventCode("BATbern56")
            .speakerUsername("jane.smith")
            .speakerName("Dr. Jane Smith")
            .speakerEmail("jane.smith@example.com")
            .sessionTitle("Future of AI in Business")
            .sessionTime(LocalDateTime.of(2024, 11, 15, 14, 0))
            .invitedBy("organizer.user")
            .build();

        assertThat(event.getInvitationStatus()).isEqualTo("PENDING");
    }

    @Test
    @DisplayName("should_includeEventMetadata_when_eventCreated")
    void should_includeEventMetadata_when_eventCreated() {
        SpeakerInvitedEvent event = SpeakerInvitedEvent.builder()
            .eventCode("BATbern56")
            .speakerUsername("jane.smith")
            .speakerName("Dr. Jane Smith")
            .speakerEmail("jane.smith@example.com")
            .sessionTitle("Future of AI in Business")
            .sessionTime(LocalDateTime.of(2024, 11, 15, 14, 0))
            .invitedBy("organizer.user")
            .build();

        assertThat(event.getOccurredAt()).isNotNull();
        assertThat(event.getVersion()).isEqualTo("1.0");
        assertThat(event.getMetadata()).isNotNull();
    }

    @Test
    @DisplayName("should_validateRequiredFields_when_eventCreated")
    void should_validateRequiredFields_when_eventCreated() {
        assertThatThrownBy(() -> SpeakerInvitedEvent.builder()
            .eventCode(null)
            .speakerUsername("jane.smith")
            .speakerName("Dr. Jane Smith")
            .speakerEmail("jane.smith@example.com")
            .sessionTitle("Future of AI")
            .sessionTime(LocalDateTime.now())
            .invitedBy("organizer.user")
            .build())
            .isInstanceOf(NullPointerException.class);

        assertThatThrownBy(() -> SpeakerInvitedEvent.builder()
            .eventCode("BATbern56")
            .speakerUsername(null)
            .speakerName("Dr. Jane Smith")
            .speakerEmail("jane.smith@example.com")
            .sessionTitle("Future of AI")
            .sessionTime(LocalDateTime.now())
            .invitedBy("organizer.user")
            .build())
            .isInstanceOf(NullPointerException.class);
    }
}
