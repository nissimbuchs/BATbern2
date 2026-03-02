package ch.batbern.events.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Request DTO for newsletter send / preview (Story 10.7 — AC10).
 */
@Data
public class NewsletterSendRequest {

    @NotNull(message = "isReminder flag is required")
    private Boolean isReminder;

    @NotNull(message = "locale is required")
    private String locale;

    /** Optional template key override. Null → use default 'newsletter-event'. */
    private String templateKey;
}
