package ch.batbern.events.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Response DTO for publishing preview
 * Shows what content is published and what would be visible
 * Story BAT-11 (5.7): Slot Assignment & Progressive Publishing
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PublishPreviewResponse {

    @JsonProperty("eventCode")
    private String eventCode;

    @JsonProperty("currentPhase")
    private String currentPhase; // TOPIC, SPEAKERS, AGENDA

    @JsonProperty("topicPublished")
    private Boolean topicPublished;

    @JsonProperty("speakersPublished")
    private Boolean speakersPublished;

    @JsonProperty("agendaPublished")
    private Boolean agendaPublished;

    @JsonProperty("speakers")
    private List<SpeakerPreview> speakers;

    @JsonProperty("sessions")
    private List<SessionPreview> sessions;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SpeakerPreview {
        private String name;
        private String company;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SessionPreview {
        private String title;
        private String startTime;
        private String endTime;
        private String room;
    }
}
