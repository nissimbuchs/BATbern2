package ch.batbern.events.controller;

import ch.batbern.events.config.AiConfig;
import ch.batbern.events.config.CacheConfig;
import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.Session;
import ch.batbern.events.domain.SpeakerPool;
import ch.batbern.events.domain.Topic;
import ch.batbern.events.dto.generated.AbstractAnalysisResponse;
import ch.batbern.events.dto.generated.AiDescriptionResponse;
import ch.batbern.events.dto.generated.AiThemeImageResponse;
import ch.batbern.events.dto.generated.FeatureFlagsResponse;
import ch.batbern.events.exception.EventNotFoundException;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.repository.SessionRepository;
import ch.batbern.events.repository.SpeakerPoolRepository;
import ch.batbern.events.repository.TopicRepository;
import ch.batbern.events.service.BatbernAiService;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class AiAssistController {

    private final BatbernAiService aiService;
    private final AiConfig aiConfig;
    private final EventRepository eventRepository;
    private final TopicRepository topicRepository;
    private final SessionRepository sessionRepository;
    private final SpeakerPoolRepository speakerPoolRepository;

    @org.springframework.beans.factory.annotation.Value("${aws.cloudfront.domain:https://cdn.batbern.ch}")
    private String cloudFrontDomain;

    /** Public: no auth required — used by frontend feature flag check */
    @GetMapping("/public/settings/features")
    public ResponseEntity<FeatureFlagsResponse> getFeatureFlags() {
        return ResponseEntity.ok(new FeatureFlagsResponse().aiContentEnabled(aiConfig.isAiEnabled()));
    }

    @PostMapping("/events/{eventCode}/ai/description")
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<AiDescriptionResponse> generateDescription(@PathVariable String eventCode) {
        Event event = eventRepository.findByEventCode(eventCode)
                .orElseThrow(() -> new EventNotFoundException("Event not found: " + eventCode));
        Topic topic = resolveTopicForEvent(event);

        String eventDate = event.getDate() != null
                ? event.getDate().atZone(ZoneOffset.UTC).toLocalDate().toString()
                : "";
        Map<String, String> vars = new LinkedHashMap<>();
        vars.put("EVENT_NR", String.valueOf(event.getEventNumber() != null ? event.getEventNumber() : 0));
        vars.put("EVENT_TITLE", safeStr(event.getTitle()));
        vars.put("TOPIC_TITLE", topicTitle(topic));
        vars.put("TOPIC_DESCRIPTION", topicDescription(topic));
        vars.put("TOPIC_CATEGORY", topicCategory(topic));
        vars.put("EVENT_DATE", eventDate);
        vars.put("EVENT_DESCRIPTION", safeStr(event.getDescription()));

        Optional<String> desc = aiService.generateEventDescription(eventCode, vars);

        return desc.map(d -> ResponseEntity.ok(new AiDescriptionResponse().description(d)))
                   .orElse(ResponseEntity.status(503).build());
    }

    @PostMapping("/events/{eventCode}/ai/theme-image")
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<AiThemeImageResponse> generateThemeImage(
            @PathVariable String eventCode,
            @RequestParam(required = false) String seed) {
        Event event = eventRepository.findByEventCode(eventCode)
                .orElseThrow(() -> new EventNotFoundException("Event not found: " + eventCode));
        Topic topic = resolveTopicForEvent(event);

        Map<String, String> vars = new LinkedHashMap<>();
        vars.put("TOPIC_TITLE", topicTitle(topic));
        vars.put("TOPIC_DESCRIPTION", topicDescription(topic));
        vars.put("TOPIC_CATEGORY", topicCategory(topic));
        vars.put("EVENT_TITLE", safeStr(event.getTitle()));
        vars.put("EVENT_DESCRIPTION", safeStr(event.getDescription()));

        Optional<BatbernAiService.ThemeImageResult> result = aiService.generateThemeImage(eventCode, vars, seed);

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
        if (request.imageUrl() == null || !request.imageUrl().startsWith(cloudFrontDomain)) {
            return ResponseEntity.badRequest().build();
        }
        Event event = eventRepository.findByEventCode(eventCode)
                .orElseThrow(() -> new EventNotFoundException("Event not found: " + eventCode));
        event.setThemeImageUrl(request.imageUrl());
        eventRepository.save(event);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/speakers/{speakerId}/ai/analyze-abstract")
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<AbstractAnalysisResponse> analyzeAbstract(@PathVariable UUID speakerId) {
        SpeakerPool pool = speakerPoolRepository.findById(speakerId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Speaker pool entry not found: " + speakerId));

        String sessionTitle = "";
        String abstractText = "";
        if (pool.getSessionId() != null) {
            Optional<Session> session = sessionRepository.findById(pool.getSessionId());
            if (session.isPresent()) {
                sessionTitle = safeStr(session.get().getTitle());
                abstractText = safeStr(session.get().getDescription());
            }
        }

        Optional<BatbernAiService.AbstractAnalysisResult> result =
                aiService.analyzeAbstract(safeStr(pool.getSpeakerName()), sessionTitle, abstractText);

        return result.map(r -> ResponseEntity.ok(new AbstractAnalysisResponse()
                        .noPromotionScore(r.noPromotionScore())
                        .noPromotionFeedback(r.noPromotionFeedback())
                        .lessonsLearnedScore(r.lessonsLearnedScore())
                        .lessonsLearnedFeedback(r.lessonsLearnedFeedback())
                        .wordCount(r.wordCount())
                        .shortenedAbstract(r.shortenedAbstract())))
                     .orElse(ResponseEntity.status(503).build());
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private Topic resolveTopicForEvent(Event event) {
        if (event.getTopicCode() == null) {
            return null;
        }
        return topicRepository.findByTopicCode(event.getTopicCode()).orElse(null);
    }

    private static String topicTitle(Topic topic) {
        return topic != null ? safeStr(topic.getTitle()) : "";
    }

    private static String topicDescription(Topic topic) {
        return topic != null ? safeStr(topic.getDescription()) : "";
    }

    private static String topicCategory(Topic topic) {
        return topic != null ? safeStr(topic.getCategory()) : "";
    }

    private static String safeStr(String s) {
        return s != null ? s : "";
    }
}
