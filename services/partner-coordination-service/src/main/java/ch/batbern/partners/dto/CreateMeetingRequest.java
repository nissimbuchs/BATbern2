package ch.batbern.partners.dto;

import ch.batbern.partners.domain.MeetingType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalTime;

/**
 * Request to create a new partner meeting — Story 8.3 (AC1).
 */
@Data
public class CreateMeetingRequest {

    @NotBlank
    private String eventCode;

    @NotNull
    private MeetingType meetingType;

    @NotNull
    private LocalTime startTime;

    @NotNull
    private LocalTime endTime;

    private String location;

    private String agenda;
}
