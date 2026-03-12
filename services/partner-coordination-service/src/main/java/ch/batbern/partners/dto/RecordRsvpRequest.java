package ch.batbern.partners.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Request body for the internal RSVP recording endpoint — Story 10.27 (AC6).
 */
@Data
@NoArgsConstructor
public class RecordRsvpRequest {

    @NotNull
    private UUID meetingId;

    @NotBlank
    @Email
    private String attendeeEmail;

    @NotBlank
    private String partStat;
}
