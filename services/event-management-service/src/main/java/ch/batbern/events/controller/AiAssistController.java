package ch.batbern.events.controller;

import ch.batbern.events.config.AiConfig;
import ch.batbern.events.config.CacheConfig;
import ch.batbern.events.domain.Event;
import ch.batbern.events.dto.generated.AbstractAnalysisResponse;
import ch.batbern.events.dto.generated.AiDescriptionRequest;
import ch.batbern.events.dto.generated.AiDescriptionResponse;
import ch.batbern.events.dto.generated.AiThemeImageRequest;
import ch.batbern.events.dto.generated.AiThemeImageResponse;
import ch.batbern.events.dto.generated.AnalyzeAbstractRequest;
import ch.batbern.events.dto.generated.FeatureFlagsResponse;
import ch.batbern.events.exception.EventNotFoundException;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.service.BatbernAiService;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class AiAssistController {

    private final BatbernAiService aiService;
    private final AiConfig aiConfig;
    private final EventRepository eventRepository;

    /** Public: no auth required — used by frontend feature flag check */
    @GetMapping("/public/settings/features")
    public ResponseEntity<FeatureFlagsResponse> getFeatureFlags() {
        return ResponseEntity.ok(new FeatureFlagsResponse().aiContentEnabled(aiConfig.isAiEnabled()));
    }

    @PostMapping("/events/{eventCode}/ai/description")
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<AiDescriptionResponse> generateDescription(
            @PathVariable String eventCode,
            @RequestBody AiDescriptionRequest request) {
        Optional<String> desc = aiService.generateEventDescription(
            eventCode, request.getTopicTitle(), request.getTopicCategory(), extractEventNumber(eventCode),
            request.getEventTitle(), request.getEventDate() != null ? request.getEventDate().toString() : null);
        return desc.map(d -> ResponseEntity.ok(new AiDescriptionResponse().description(d)))
                   .orElse(ResponseEntity.status(503).build());
    }

    @PostMapping("/events/{eventCode}/ai/theme-image")
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<AiThemeImageResponse> generateThemeImage(
            @PathVariable String eventCode,
            @RequestBody AiThemeImageRequest request,
            @org.springframework.web.bind.annotation.RequestParam(required = false) String seed,
            @org.springframework.web.bind.annotation.RequestParam(required = false) String description) {
        Optional<BatbernAiService.ThemeImageResult> result = aiService.generateThemeImage(
            eventCode, request.getTopicTitle(), request.getTopicCategory(), request.getEventTitle(), description, seed);
        return result.map(r -> ResponseEntity.ok(
                    new AiThemeImageResponse().imageUrl(r.imageUrl()).s3Key(r.s3Key())))
                     .orElse(ResponseEntity.status(503).build());
    }

    /** Simple request body for applying an AI-generated image to an event. */
    record ApplyThemeImageRequest(String imageUrl) {}

    @PostMapping("/events/{eventCode}/ai/theme-image/apply")
    @PreAuthorize("hasRole('ORGANIZER')")
    @CacheEvict(value = CacheConfig.EVENT_WITH_INCLUDES_CACHE, allEntries = true)
    public ResponseEntity<Void> applyThemeImage(
            @PathVariable String eventCode,
            @RequestBody ApplyThemeImageRequest request) {
        Event event = eventRepository.findByEventCode(eventCode)
                .orElseThrow(() -> new EventNotFoundException("Event not found: " + eventCode));
        event.setThemeImageUrl(request.imageUrl());
        eventRepository.save(event);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/speakers/{speakerId}/ai/analyze-abstract")
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<AbstractAnalysisResponse> analyzeAbstract(
            @PathVariable String speakerId,
            @RequestBody AnalyzeAbstractRequest request) {
        Optional<BatbernAiService.AbstractAnalysisResult> result =
            aiService.analyzeAbstract(request.getAbstract(), request.getSpeakerName());
        return result.map(r -> ResponseEntity.ok(new AbstractAnalysisResponse()
                    .noPromotionScore(r.noPromotionScore())
                    .noPromotionFeedback(r.noPromotionFeedback())
                    .lessonsLearnedScore(r.lessonsLearnedScore())
                    .lessonsLearnedFeedback(r.lessonsLearnedFeedback())
                    .wordCount(r.wordCount())
                    .shortenedAbstract(r.shortenedAbstract())))
                     .orElse(ResponseEntity.status(503).build());
    }

    private int extractEventNumber(String eventCode) {
        // e.g. "BATbern42" → 42
        String digits = eventCode.replaceAll("[^0-9]", "");
        return digits.isEmpty() ? 0 : Integer.parseInt(digits);
    }
}
