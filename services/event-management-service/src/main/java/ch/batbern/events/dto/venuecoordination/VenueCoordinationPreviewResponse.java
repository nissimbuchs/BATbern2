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
public class VenueCoordinationPreviewResponse {
    private String subject;
    private String htmlBody;
    /** Primary recipient (SES To header). */
    private String toEmail;
    /** Additional recipients (SES Cc header). Empty when only one role selected. */
    private List<String> ccEmails;
    /** Reply-To header (organizer-on-duty's email). */
    private String replyToEmail;
}
