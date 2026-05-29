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
public class VenueCoordinationSendRequest {

    @NotBlank
    private String templateKey;

    /** Locale (de | en). */
    private String locale;

    /** Free-form additional notes appended to the email body. Optional. */
    private String notes;

    /** Which recipient roles to send to. At least one. */
    @NotEmpty
    private List<VenueCoordinationConfig.Role> recipients;
}
