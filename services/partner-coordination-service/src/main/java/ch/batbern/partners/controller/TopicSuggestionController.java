package ch.batbern.partners.controller;

import ch.batbern.partners.domain.TopicSuggestion;
import ch.batbern.partners.dto.generated.SuggestionStatus;
import ch.batbern.partners.dto.generated.SubmitSuggestionRequest;
import ch.batbern.partners.dto.generated.TopicSuggestionResponse;
import ch.batbern.partners.service.TopicSuggestionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST controller for Topic Suggestion operations.
 *
 * Provides endpoints for:
 * - Listing partner suggestions
 * - Submitting new topic suggestions
 *
 * All business logic delegated to TopicSuggestionService.
 */
@RestController
@RequestMapping("/api/v1/partners/{companyName}/suggestions")
@RequiredArgsConstructor
@Slf4j
public class TopicSuggestionController {

    private final TopicSuggestionService topicSuggestionService;

    /**
     * List all suggestions submitted by a partner.
     *
     * @param companyName Company name (meaningful ID)
     * @return List of suggestions
     */
    @GetMapping
    public ResponseEntity<List<TopicSuggestionResponse>> getPartnerSuggestions(
            @PathVariable String companyName) {

        log.debug("GET /partners/{}/suggestions", companyName);

        List<TopicSuggestion> suggestions = topicSuggestionService.getPartnerSuggestions(companyName);
        List<TopicSuggestionResponse> responses = suggestions.stream()
                .map(this::mapToResponse)
                .toList();

        return ResponseEntity.ok(responses);
    }

    /**
     * Submit a new topic suggestion.
     *
     * @param companyName       Company name (meaningful ID)
     * @param suggestionRequest Suggestion request containing topic, description, justification
     * @return Created topic suggestion
     */
    @PostMapping
    public ResponseEntity<TopicSuggestionResponse> submitSuggestion(
            @PathVariable String companyName,
            @RequestBody Map<String, Object> suggestionRequest) {

        log.info("POST /partners/{}/suggestions", companyName);

        String suggestedTopic = (String) suggestionRequest.get("suggestedTopic");
        String description = (String) suggestionRequest.get("description");
        String businessJustification = (String) suggestionRequest.get("businessJustification");

        TopicSuggestion suggestion = topicSuggestionService.submitSuggestion(
                companyName,
                suggestedTopic,
                description,
                businessJustification
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(mapToResponse(suggestion));
    }

    /**
     * Map TopicSuggestion entity to response DTO.
     *
     * @param suggestion Topic suggestion entity
     * @return Topic suggestion response DTO
     */
    private TopicSuggestionResponse mapToResponse(TopicSuggestion suggestion) {
        TopicSuggestionResponse response = new TopicSuggestionResponse();
        response.setSuggestedTopic(suggestion.getSuggestedTopic());
        response.setDescription(suggestion.getDescription());
        response.setBusinessJustification(suggestion.getBusinessJustification());
        // Map domain enum to DTO enum using builder (UPPERCASE per coding standards)
        response.setStatus(suggestion.getStatus().name());
        response.setSuggestedAt(suggestion.getSuggestedAt().toString());
        if (suggestion.getReviewedAt() != null) {
            response.setReviewedAt(suggestion.getReviewedAt().toString());
        }
        if (suggestion.getReviewedBy() != null) {
            response.setReviewedBy(suggestion.getReviewedBy().toString());
        }
        return response;
    }

    /**
     * Response DTO for topic suggestion.
     */
    @lombok.Data
    public static class TopicSuggestionResponse {
        private String suggestedTopic;
        private String description;
        private String businessJustification;
        private String status;
        private String suggestedAt;
        private String reviewedAt;
        private String reviewedBy;
    }
}
