package ch.batbern.events.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Request DTO for overriding topic staleness score (Story 5.2 AC7).
 */
public class OverrideStalenesRequest {

    @NotNull(message = "Staleness score is required")
    @Min(value = 0, message = "Staleness score must be between 0 and 100")
    @Max(value = 100, message = "Staleness score must be between 0 and 100")
    private Integer stalenessScore;

    @NotBlank(message = "Justification is required for staleness override")
    private String justification;

    public OverrideStalenesRequest() {
    }

    public OverrideStalenesRequest(Integer stalenessScore, String justification) {
        this.stalenessScore = stalenessScore;
        this.justification = justification;
    }

    public Integer getStalenessScore() {
        return stalenessScore;
    }

    public void setStalenessScore(Integer stalenessScore) {
        this.stalenessScore = stalenessScore;
    }

    public String getJustification() {
        return justification;
    }

    public void setJustification(String justification) {
        this.justification = justification;
    }
}
