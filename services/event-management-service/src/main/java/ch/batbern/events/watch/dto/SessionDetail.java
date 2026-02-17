package ch.batbern.events.watch.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

/**
 * Session data for Watch active events response.
 * W2.3: Event Join & Schedule Sync
 */
@Getter
@AllArgsConstructor
public class SessionDetail {
    private final String sessionSlug;
    private final String title;
    private final String sessionAbstract;  // 'abstract' is a Java keyword; serialized as "abstract" via @JsonProperty
    private final String sessionType;
    private final String scheduledStartTime;   // ISO 8601
    private final String scheduledEndTime;     // ISO 8601
    private final Integer durationMinutes;
    private final List<SpeakerDetail> speakers;
    private final String status;               // SCHEDULED, ACTIVE, COMPLETED, SKIPPED
    private final String actualStartTime;      // ISO 8601 or null
    private final String actualEndTime;        // ISO 8601 or null
    private final Integer overrunMinutes;
    private final String completedBy;

    // 'abstract' is a Java keyword so the field is named sessionAbstract.
    // Override the getter name so Jackson serializes it as "abstract".
    @com.fasterxml.jackson.annotation.JsonProperty("abstract")
    public String getSessionAbstract() {
        return sessionAbstract;
    }
}
