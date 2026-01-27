package ch.batbern.events.controller;

import ch.batbern.events.domain.SpeakerPool;
import ch.batbern.events.repository.SpeakerPoolRepository;
import ch.batbern.events.service.MagicLinkService;
import ch.batbern.shared.types.TokenAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * E2E Test Token Controller
 *
 * Provides endpoints for generating magic link tokens for E2E testing.
 * ONLY enabled in development and test profiles.
 *
 * WARNING: Never enable this in production - it bypasses normal security.
 */
@RestController
@RequestMapping("/api/v1/e2e-test")
@Profile({"dev", "local", "test", "development"})
public class E2ETestTokenController {

    private static final Logger LOG = LoggerFactory.getLogger(E2ETestTokenController.class);

    private final MagicLinkService magicLinkService;
    private final SpeakerPoolRepository speakerPoolRepository;

    public E2ETestTokenController(MagicLinkService magicLinkService, SpeakerPoolRepository speakerPoolRepository) {
        this.magicLinkService = magicLinkService;
        this.speakerPoolRepository = speakerPoolRepository;
    }

    /**
     * Generate a test token for a speaker by speaker pool ID.
     *
     * @param speakerPoolId The ID of the speaker pool entry
     * @param action The token action (RESPOND, SUBMIT, VIEW)
     * @param expiryDays Optional expiry in days (default 30)
     * @return The plaintext token for E2E testing
     */
    @PostMapping("/tokens/generate")
    public ResponseEntity<Map<String, Object>> generateToken(
            @RequestParam UUID speakerPoolId,
            @RequestParam TokenAction action,
            @RequestParam(defaultValue = "30") long expiryDays) {

        LOG.warn("E2E TEST ENDPOINT: Generating test token for speakerPoolId={}, action={}", speakerPoolId, action);

        Optional<SpeakerPool> speakerOpt = speakerPoolRepository.findById(speakerPoolId);
        if (speakerOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        SpeakerPool speaker = speakerOpt.get();
        String token = magicLinkService.generateToken(speakerPoolId, action, expiryDays);

        Map<String, Object> response = new HashMap<>();
        response.put("token", token);
        response.put("speakerPoolId", speakerPoolId);
        response.put("speakerName", speaker.getSpeakerName());
        response.put("action", action.name());
        response.put("expiryDays", expiryDays);

        return ResponseEntity.ok(response);
    }

    /**
     * Generate test tokens for a speaker by username and event code.
     *
     * @param eventCode The event code
     * @param username The speaker's username
     * @return Tokens for all actions (RESPOND, VIEW)
     */
    @PostMapping("/tokens/generate-for-speaker")
    public ResponseEntity<Map<String, Object>> generateTokensForSpeaker(
            @RequestParam String eventCode,
            @RequestParam String username) {

        LOG.warn("E2E TEST ENDPOINT: Generating test tokens for event={}, username={}", eventCode, username);

        Optional<SpeakerPool> speakerOpt = speakerPoolRepository.findByEventCodeAndUsername(eventCode, username);
        if (speakerOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        SpeakerPool speaker = speakerOpt.get();
        UUID speakerPoolId = speaker.getId();

        // Generate tokens for different actions
        String respondToken = magicLinkService.generateToken(speakerPoolId, TokenAction.RESPOND, 30);
        String viewToken = magicLinkService.generateToken(speakerPoolId, TokenAction.VIEW, 30);

        Map<String, Object> response = new HashMap<>();
        response.put("speakerPoolId", speakerPoolId);
        response.put("speakerName", speaker.getSpeakerName());
        response.put("eventCode", eventCode);
        response.put("username", username);
        response.put("sessionId", speaker.getSessionId());
        response.put("hasSessionAssigned", speaker.getSessionId() != null);
        response.put("tokens", Map.of(
                "RESPOND", respondToken,
                "VIEW", viewToken
        ));

        return ResponseEntity.ok(response);
    }

    /**
     * Generate all E2E test tokens needed for the speaker onboarding flow tests.
     *
     * @param eventCode The event code to use for test speakers
     * @return All tokens needed for E2E tests
     */
    @PostMapping("/tokens/generate-e2e-set")
    public ResponseEntity<Map<String, Object>> generateE2ETokenSet(
            @RequestParam(defaultValue = "BAT-SEED-2026") String eventCode) {

        LOG.warn("E2E TEST ENDPOINT: Generating full E2E token set for event={}", eventCode);

        Map<String, Object> result = new HashMap<>();
        result.put("eventCode", eventCode);

        // PageRequest for limiting to 1 result
        PageRequest limitOne = PageRequest.of(0, 1);

        // Find speakers with different states for testing
        // Speaker in INVITED status (for full onboarding flow)
        List<SpeakerPool> invitedList = speakerPoolRepository
                .findByEventCodeAndStatusOrderByCreatedAtDesc(eventCode, "invited", limitOne);
        Optional<SpeakerPool> invitedSpeaker = invitedList.isEmpty() ? Optional.empty() : Optional.of(invitedList.get(0));

        // Speaker in ACCEPTED status with session (for profile/content tests)
        List<SpeakerPool> acceptedWithSessionList = speakerPoolRepository
                .findByEventCodeAndStatusAndSessionIdIsNotNullOrderByCreatedAtDesc(eventCode, "accepted", limitOne);
        Optional<SpeakerPool> acceptedWithSession = acceptedWithSessionList.isEmpty() ? Optional.empty() : Optional.of(acceptedWithSessionList.get(0));

        // Speaker in ACCEPTED status without session (for no-session test)
        List<SpeakerPool> acceptedWithoutSessionList = speakerPoolRepository
                .findByEventCodeAndStatusAndSessionIdIsNullOrderByCreatedAtDesc(eventCode, "accepted", limitOne);
        Optional<SpeakerPool> acceptedWithoutSession = acceptedWithoutSessionList.isEmpty() ? Optional.empty() : Optional.of(acceptedWithoutSessionList.get(0));

        // Fallback: Use any speaker if specific states not found
        List<SpeakerPool> anyList = speakerPoolRepository
                .findByEventCodeOrderByCreatedAtDesc(eventCode, limitOne);
        Optional<SpeakerPool> anySpeaker = anyList.isEmpty() ? Optional.empty() : Optional.of(anyList.get(0));

        Map<String, String> tokens = new HashMap<>();

        // Generate ONBOARDING token (for invited speaker)
        if (invitedSpeaker.isPresent()) {
            SpeakerPool speaker = invitedSpeaker.get();
            String token = magicLinkService.generateToken(speaker.getId(), TokenAction.RESPOND, 30);
            tokens.put("E2E_SPEAKER_ONBOARDING_TOKEN", token);
            result.put("onboardingSpeaker", Map.of(
                    "id", speaker.getId(),
                    "name", speaker.getSpeakerName(),
                    "status", speaker.getStatus()
            ));
        }

        // Generate PROFILE token (for accepted speaker, prefer one with session)
        SpeakerPool profileSpeaker = acceptedWithSession.orElse(anySpeaker.orElse(null));
        if (profileSpeaker != null) {
            String token = magicLinkService.generateToken(profileSpeaker.getId(), TokenAction.VIEW, 30);
            tokens.put("E2E_SPEAKER_PROFILE_TOKEN", token);
            result.put("profileSpeaker", Map.of(
                    "id", profileSpeaker.getId(),
                    "name", profileSpeaker.getSpeakerName(),
                    "status", profileSpeaker.getStatus(),
                    "hasSession", profileSpeaker.getSessionId() != null
            ));
        }

        // Generate CONTENT token (same as profile, needs session)
        if (acceptedWithSession.isPresent()) {
            SpeakerPool speaker = acceptedWithSession.get();
            String token = magicLinkService.generateToken(speaker.getId(), TokenAction.VIEW, 30);
            tokens.put("E2E_SPEAKER_CONTENT_TOKEN", token);
            result.put("contentSpeaker", Map.of(
                    "id", speaker.getId(),
                    "name", speaker.getSpeakerName(),
                    "sessionId", speaker.getSessionId()
            ));
        }

        // Generate NO_SESSION token (for testing session-not-assigned state)
        if (acceptedWithoutSession.isPresent()) {
            SpeakerPool speaker = acceptedWithoutSession.get();
            String token = magicLinkService.generateToken(speaker.getId(), TokenAction.VIEW, 30);
            tokens.put("E2E_SPEAKER_NO_SESSION_TOKEN", token);
            result.put("noSessionSpeaker", Map.of(
                    "id", speaker.getId(),
                    "name", speaker.getSpeakerName()
            ));
        }

        result.put("tokens", tokens);

        // Generate export commands for shell
        StringBuilder exportCommands = new StringBuilder();
        exportCommands.append("\n# E2E Test Tokens - copy these to your environment:\n");
        for (Map.Entry<String, String> entry : tokens.entrySet()) {
            exportCommands.append(String.format("export %s='%s'\n", entry.getKey(), entry.getValue()));
        }
        result.put("exportCommands", exportCommands.toString());

        return ResponseEntity.ok(result);
    }
}
