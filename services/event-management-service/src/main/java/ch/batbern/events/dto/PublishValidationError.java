package ch.batbern.events.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * DTO for publishing validation errors
 * Story BAT-11 (5.7): Slot Assignment & Progressive Publishing
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PublishValidationError {

    @JsonProperty("error")
    private String error;

    @JsonProperty("message")
    private String message;

    @JsonProperty("validationErrors")
    private List<ValidationDetail> validationErrors;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ValidationDetail {
        @JsonProperty("sessionSlug")
        private String sessionSlug;

        @JsonProperty("field")
        private String field;

        @JsonProperty("message")
        private String message;
    }
}
