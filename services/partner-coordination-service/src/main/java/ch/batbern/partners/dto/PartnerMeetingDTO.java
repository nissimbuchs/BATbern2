package ch.batbern.partners.dto;

import ch.batbern.partners.domain.MeetingType;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Value;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/**
 * Read-only view of a partner meeting — Story 8.3.
 */
@Value
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PartnerMeetingDTO {

    UUID id;
    String eventCode;
    MeetingType meetingType;
    LocalDate meetingDate;
    LocalTime startTime;
    LocalTime endTime;
    String location;
    String agenda;
    String notes;
    Instant inviteSentAt;
    String createdBy;
    Instant createdAt;
    Instant updatedAt;
}
