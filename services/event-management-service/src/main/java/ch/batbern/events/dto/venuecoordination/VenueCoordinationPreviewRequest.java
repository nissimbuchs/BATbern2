package ch.batbern.events.dto.venuecoordination;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VenueCoordinationPreviewRequest {

    @NotBlank
    private String templateKey;

    /** Which contact's salutation to render — VENUE or CATERING. */
    @NotNull
    private VenueCoordinationConfig.Role recipientRole;

    /** Locale (de | en). Falls back to de when the requested locale has no template. */
    private String locale;

    /** Free-form additional notes the organizer wants in the email body. Optional. */
    private String notes;
}
