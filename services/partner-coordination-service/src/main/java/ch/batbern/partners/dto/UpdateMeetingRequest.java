package ch.batbern.partners.dto;

import lombok.Data;

import java.time.LocalTime;

/**
 * Request to update a partner meeting — Story 8.3 (AC2, AC4).
 *
 * All fields are optional — only non-null fields are applied.
 */
@Data
public class UpdateMeetingRequest {

    /** Free-text agenda (pre-meeting). */
    private String agenda;

    /** Free-text post-meeting notes. */
    private String notes;

    /** Venue location. */
    private String location;

    private LocalTime startTime;

    private LocalTime endTime;
}
