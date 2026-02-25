package ch.batbern.events.mapper;

import ch.batbern.events.domain.EmailTemplate;
import ch.batbern.events.dto.generated.EmailTemplateResponse;
import org.springframework.stereotype.Component;

/**
 * Pure mapper for converting between EmailTemplate entity and generated DTOs (Story 10.2, ADR-006).
 *
 * Field mapping only — no business logic.
 */
@Component
public class EmailTemplateMapper {

    /**
     * Converts EmailTemplate entity to the generated EmailTemplateResponse DTO.
     */
    public EmailTemplateResponse toResponse(EmailTemplate entity) {
        if (entity == null) {
            return null;
        }
        EmailTemplateResponse response = new EmailTemplateResponse();
        response.setTemplateKey(entity.getTemplateKey());
        response.setLocale(entity.getLocale());
        response.setCategory(entity.getCategory() != null
                ? EmailTemplateResponse.CategoryEnum.fromValue(entity.getCategory())
                : null);
        response.setSubject(entity.getSubject());
        response.setHtmlBody(entity.getHtmlBody());
        response.setVariables(entity.getVariables());
        response.setIsLayout(entity.isLayout());
        response.setLayoutKey(entity.getLayoutKey());
        response.setIsSystemTemplate(entity.isSystemTemplate());
        response.setUpdatedAt(entity.getUpdatedAt());
        return response;
    }
}
