package ch.batbern.partners.dto;

import ch.batbern.shared.utils.EmailAddresses;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Request body of the calendar reply contract event-management owns ({@code CalendarReply} in
 * {@code docs/api/internal/ems-internal.openapi.yml}, Epic 17 entry 8b; Story 10.27 AC6).
 */
@Data
@NoArgsConstructor
public class RecordRsvpRequest {

    @NotNull
    private UUID meetingId;

    /** The #1102 rule (ASCII local part, any domain), as the contract spec and the generated DTOs. */
    @NotBlank
    @Pattern(regexp = EmailAddresses.OPENAPI_PATTERN)
    @Size(max = 255)
    private String attendeeEmail;

    @NotBlank
    private String partStat;
}
