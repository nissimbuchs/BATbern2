package ch.batbern.companyuser.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Statistics DTO for company resource expansion
 * AC15: Support ?include=statistics parameter
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Company statistics including event, speaker, and partner counts")
public class CompanyStatistics {

    @Schema(description = "Total number of events associated with this company", example = "5")
    private int totalEvents;

    @Schema(description = "Total number of speakers from this company", example = "12")
    private int totalSpeakers;

    @Schema(description = "Total number of partners from this company", example = "3")
    private int totalPartners;
}
