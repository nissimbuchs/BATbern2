package ch.batbern.companyuser.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for moderator presentation page settings.
 *
 * Story 10.8a: Moderator Presentation Page — Functional
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PresentationSettingsResponse {

    @JsonProperty("aboutText")
    private String aboutText;

    @JsonProperty("partnerCount")
    private int partnerCount;
}
