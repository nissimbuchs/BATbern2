package ch.batbern.events.service;

import ch.batbern.events.domain.AiPrompt;
import ch.batbern.events.repository.AiPromptRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Service for managing organizer-editable AI prompts (Story 10.x).
 *
 * Three prompts are seeded by migration V82:
 *   - event_description  — GPT-4o event description generation
 *   - theme_image        — DALL-E theme image generation
 *   - abstract_quality   — GPT-4o abstract quality review
 *
 * Used by BatbernAiService to retrieve the current prompt text at call time.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AiPromptService {

    private final AiPromptRepository repository;

    public List<AiPrompt> findAll() {
        return repository.findAll();
    }

    public AiPrompt findByKey(String promptKey) {
        return repository.findById(promptKey)
                .orElseThrow(() -> new EntityNotFoundException("AI prompt not found: " + promptKey));
    }

    /**
     * Returns the current prompt text for the given key.
     * Used internally by BatbernAiService. Falls back to empty string if key missing.
     */
    public String getPromptText(String promptKey) {
        return repository.findById(promptKey)
                .map(AiPrompt::getPromptText)
                .orElse("");
    }

    @Transactional
    public AiPrompt update(String promptKey, String promptText) {
        AiPrompt prompt = findByKey(promptKey);
        prompt.setPromptText(promptText);
        log.info("AI prompt '{}' updated", promptKey);
        return repository.save(prompt);
    }

    @Transactional
    public AiPrompt reset(String promptKey) {
        AiPrompt prompt = findByKey(promptKey);
        prompt.setPromptText(prompt.getDefaultText());
        log.info("AI prompt '{}' reset to default", promptKey);
        return repository.save(prompt);
    }
}
