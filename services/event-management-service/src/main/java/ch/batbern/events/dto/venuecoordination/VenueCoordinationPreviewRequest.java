package ch.batbern.events.dto.venuecoordination;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VenueCoordinationPreviewRequest {

    @NotBlank
    private String templateKey;

    /** Recipient roles to address in the preview — same shape as the send request. */
    @NotEmpty
    private List<VenueCoordinationConfig.Role> recipients;

    /** Locale (de | en). Falls back to de when the requested locale has no template. */
    private String locale;

    /** Free-form additional notes the organizer wants in the email body. Optional. */
    private String notes;
}
