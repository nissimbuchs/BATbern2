package ch.batbern.events.dto.venuecoordination;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VenueCoordinationSendResponse {
    /** Roles successfully sent (any failed roles trigger an HTTP error response). */
    private List<VenueCoordinationConfig.Role> sentTo;
}
