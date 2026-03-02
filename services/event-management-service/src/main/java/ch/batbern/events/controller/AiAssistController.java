package ch.batbern.events.controller;

import ch.batbern.events.config.AiConfig;
import ch.batbern.events.dto.generated.AbstractAnalysisResponse;
import ch.batbern.events.dto.generated.AiDescriptionRequest;
import ch.batbern.events.dto.generated.AiDescriptionResponse;
import ch.batbern.events.dto.generated.AiThemeImageRequest;
import ch.batbern.events.dto.generated.AiThemeImageResponse;
import ch.batbern.events.dto.generated.AnalyzeAbstractRequest;
import ch.batbern.events.dto.generated.FeatureFlagsResponse;
import ch.batbern.events.service.BatbernAiService;
import lombok.RequiredArgsConstructor;
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
            request.getTopicTitle(), request.getTopicCategory(), extractEventNumber(eventCode));
        return desc.map(d -> ResponseEntity.ok(new AiDescriptionResponse().description(d)))
                   .orElse(ResponseEntity.status(503).build());
    }

    @PostMapping("/events/{eventCode}/ai/theme-image")
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<AiThemeImageResponse> generateThemeImage(
            @PathVariable String eventCode,
            @RequestBody AiThemeImageRequest request) {
        Optional<BatbernAiService.ThemeImageResult> result = aiService.generateThemeImage(
            request.getTopicTitle(), request.getTopicCategory());
        return result.map(r -> ResponseEntity.ok(
                    new AiThemeImageResponse().imageUrl(r.imageUrl()).s3Key(r.s3Key())))
                     .orElse(ResponseEntity.status(503).build());
    }

    @PostMapping("/speakers/{speakerId}/ai/analyze-abstract")
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<AbstractAnalysisResponse> analyzeAbstract(
            @PathVariable String speakerId,
            @RequestBody AnalyzeAbstractRequest request) {
        Optional<BatbernAiService.AbstractAnalysisResult> result =
            aiService.analyzeAbstract(request.getAbstract(), request.getSpeakerName());
        return result.map(r -> ResponseEntity.ok(new AbstractAnalysisResponse()
                    .qualityScore(r.qualityScore())
                    .suggestion(r.suggestion())
                    .improvedAbstract(r.improvedAbstract())
                    .keyThemes(r.keyThemes())))
                     .orElse(ResponseEntity.status(503).build());
    }

    private int extractEventNumber(String eventCode) {
        // e.g. "BATbern42" → 42
        String digits = eventCode.replaceAll("[^0-9]", "");
        return digits.isEmpty() ? 0 : Integer.parseInt(digits);
    }
}
