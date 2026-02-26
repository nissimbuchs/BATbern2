package ch.batbern.events.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for POST /api/v1/events/{eventCode}/topic-similarity (Story 10.4 AC: 32).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TopicSimilarityRequest {

    @NotBlank(message = "Topic must not be blank")
    private String topic;
}
