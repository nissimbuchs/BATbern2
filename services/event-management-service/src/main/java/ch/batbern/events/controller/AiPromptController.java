package ch.batbern.events.controller;

import ch.batbern.events.api.generated.AiPromptsApi;
import ch.batbern.events.dto.generated.AiPromptResponse;
import ch.batbern.events.dto.generated.UpdateAiPromptRequest;
import ch.batbern.events.mapper.AiPromptMapper;
import ch.batbern.events.service.AiPromptService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Controller for AI Prompt Management API.
 *
 * Implements generated AiPromptsApi interface (ADR-006 contract-first).
 * All endpoints require ORGANIZER role.
 *
 * Endpoints:
 * - GET  /api/v1/ai-prompts              - list all 3 prompts
 * - GET  /api/v1/ai-prompts/{key}        - get single prompt
 * - PUT  /api/v1/ai-prompts/{key}        - update prompt text
 * - POST /api/v1/ai-prompts/{key}/reset  - reset to default
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasRole('ORGANIZER')")
public class AiPromptController implements AiPromptsApi {

    private final AiPromptService aiPromptService;
    private final AiPromptMapper aiPromptMapper;

    @Override
    public ResponseEntity<List<AiPromptResponse>> listAiPrompts() {
        log.debug("GET /api/v1/ai-prompts");
        return ResponseEntity.ok(
                aiPromptService.findAll().stream()
                        .map(aiPromptMapper::toResponse)
                        .toList()
        );
    }

    @Override
    public ResponseEntity<AiPromptResponse> getAiPrompt(String promptKey) {
        log.debug("GET /api/v1/ai-prompts/{}", promptKey);
        try {
            return ResponseEntity.ok(aiPromptMapper.toResponse(aiPromptService.findByKey(promptKey)));
        } catch (EntityNotFoundException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @Override
    public ResponseEntity<AiPromptResponse> updateAiPrompt(String promptKey, UpdateAiPromptRequest request) {
        log.debug("PUT /api/v1/ai-prompts/{}", promptKey);
        try {
            return ResponseEntity.ok(aiPromptMapper.toResponse(
                    aiPromptService.update(promptKey, request.getPromptText())));
        } catch (EntityNotFoundException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @Override
    public ResponseEntity<AiPromptResponse> resetAiPrompt(String promptKey) {
        log.debug("POST /api/v1/ai-prompts/{}/reset", promptKey);
        try {
            return ResponseEntity.ok(aiPromptMapper.toResponse(aiPromptService.reset(promptKey)));
        } catch (EntityNotFoundException e) {
            return ResponseEntity.notFound().build();
        }
    }
}
