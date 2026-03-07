package ch.batbern.events.mapper;

import ch.batbern.events.domain.AiPrompt;
import ch.batbern.events.dto.generated.AiPromptResponse;
import org.springframework.stereotype.Component;

/**
 * Pure mapper: AiPrompt entity → AiPromptResponse DTO (ADR-006).
 */
@Component
public class AiPromptMapper {

    public AiPromptResponse toResponse(AiPrompt prompt) {
        return new AiPromptResponse(
                prompt.getPromptKey(),
                prompt.getDisplayName(),
                prompt.getPromptText(),
                prompt.getDefaultText(),
                prompt.getUpdatedAt()
        );
    }
}
