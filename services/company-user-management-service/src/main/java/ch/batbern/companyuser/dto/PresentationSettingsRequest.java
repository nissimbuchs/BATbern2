package ch.batbern.companyuser.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for updating moderator presentation page settings.
 *
 * Story 10.8a: Moderator Presentation Page — Functional
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PresentationSettingsRequest {

    @JsonProperty("aboutText")
    @NotBlank(message = "aboutText must not be blank")
    private String aboutText;

    @JsonProperty("partnerCount")
    @Min(value = 0, message = "partnerCount must be 0 or greater")
    private int partnerCount;
}
