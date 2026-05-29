package ch.batbern.events.dto.venuecoordination;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VenueCoordinationPreviewResponse {
    private String subject;
    private String htmlBody;
    private String toName;
    private String toEmail;
    private String replyToEmail;
}
