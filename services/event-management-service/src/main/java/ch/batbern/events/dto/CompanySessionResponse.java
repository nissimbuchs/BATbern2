package ch.batbern.events.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Response DTO for sessions filtered by company.
 * Used by GlobalSessionController for the company detail Sessions tab.
 *
 * Combines Session + Event + Speaker data so the frontend can display
 * all context in one request.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompanySessionResponse {

    private String sessionSlug;
    private String eventCode;
    private String eventTitle;
    private String eventDate; // ISO-8601

    private String title;
    private String sessionType;
    private String startTime; // ISO-8601
    private String endTime;   // ISO-8601
    private String room;

    /** All speakers of this session (includes speakers from other companies too). */
    private List<SessionSpeakerResponse> speakers;
}
