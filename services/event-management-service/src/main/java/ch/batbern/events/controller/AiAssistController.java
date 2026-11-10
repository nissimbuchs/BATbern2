package ch.batbern.events.controller;

import ch.batbern.events.config.CacheConfig;
import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.Session;
import ch.batbern.events.domain.SpeakerPool;
import ch.batbern.events.domain.Topic;
import ch.batbern.events.ai.api.generated.AiAssistApi;
import ch.batbern.events.ai.dto.generated.AbstractAnalysisResponse;
import ch.batbern.events.ai.dto.generated.AiDescriptionResponse;
import ch.batbern.events.ai.dto.generated.AiThemeImageJob;
import ch.batbern.events.ai.dto.generated.ApplyThemeImageRequest;
import ch.batbern.events.exception.EventNotFoundException;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.repository.SessionRepository;
import ch.batbern.events.repository.SpeakerPoolRepository;
import ch.batbern.events.repository.TopicRepository;
import ch.batbern.events.service.BatbernAiService;
import ch.batbern.events.service.ThemeImageJobs;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;
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
public class AiAssistController implements AiAssistApi {

    private final BatbernAiService aiService;
    private final ThemeImageJobs themeImageJobs;
    private final EventRepository eventRepository;
    private final TopicRepository topicRepository;
    private final SessionRepository sessionRepository;
    private final SpeakerPoolRepository speakerPoolRepository;

    @org.springframework.beans.factory.annotation.Value("${aws.cloudfront.domain:https://cdn.batbern.ch}")
    private String cloudFrontDomain;

    @Override
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<AiDescriptionResponse> generateEventDescription(String eventCode) {
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

    @Override
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<AiThemeImageJob> generateThemeImage(
            String eventCode,
            String seed) {
        Event event = eventRepository.findByEventCode(eventCode)
                .orElseThrow(() -> new EventNotFoundException("Event not found: " + eventCode));
        if (!aiService.isAvailable()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }
        Topic topic = resolveTopicForEvent(event);

        Map<String, String> vars = new LinkedHashMap<>();
        vars.put("TOPIC_TITLE", topicTitle(topic));
        vars.put("TOPIC_DESCRIPTION", topicDescription(topic));
        vars.put("TOPIC_CATEGORY", topicCategory(topic));
        vars.put("EVENT_TITLE", safeStr(event.getTitle()));
        vars.put("EVENT_DESCRIPTION", safeStr(event.getDescription()));

        // ~40 s per image, the HTTP API allows 30 s: generate in the background, the page polls
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(toDto(themeImageJobs.start(eventCode, vars, seed)));
    }

    @Override
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<AiThemeImageJob> getThemeImageJob(String eventCode, UUID jobId) {
        return themeImageJobs.get(jobId)
                .map(job -> ResponseEntity.ok(toDto(job)))
                .orElse(ResponseEntity.notFound().build());
    }

    private static AiThemeImageJob toDto(ThemeImageJobs.Job job) {
        return new AiThemeImageJob()
                .jobId(job.id())
                .status(AiThemeImageJob.StatusEnum.fromValue(job.status().name()))
                .imageUrl(job.imageUrl())
                .s3Key(job.s3Key());
    }

    @Override
    @PreAuthorize("hasRole('ORGANIZER')")
    @CacheEvict(value = CacheConfig.EVENT_WITH_INCLUDES_CACHE, allEntries = true)
    public ResponseEntity<Void> applyThemeImage(
            String eventCode,
            ApplyThemeImageRequest request) {
        // Story 15.9 (LLM08): require the path-separator so a look-alike host
        // (e.g. https://cdn.batbern.ch.evil.com/x.png) cannot satisfy a bare startsWith.
        if (request.getImageUrl() == null || !request.getImageUrl().startsWith(cloudFrontDomain + "/")) {
            return ResponseEntity.badRequest().build();
        }
        Event event = eventRepository.findByEventCode(eventCode)
                .orElseThrow(() -> new EventNotFoundException("Event not found: " + eventCode));
        event.setThemeImageUrl(request.getImageUrl());
        eventRepository.save(event);
        return ResponseEntity.ok().build();
    }

    @Override
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<AbstractAnalysisResponse> analyzeAbstract(String eventCode, UUID speakerId) {
        Event event = eventRepository.findByEventCode(eventCode)
                .orElseThrow(() -> new EventNotFoundException("Event not found: " + eventCode));
        SpeakerPool pool = speakerPoolRepository.findByIdAndEventId(speakerId, event.getId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Speaker pool entry not found in " + eventCode + ": " + speakerId));

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
