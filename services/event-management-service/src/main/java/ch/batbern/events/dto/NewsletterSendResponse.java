package ch.batbern.events.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Response DTO for a newsletter send record (Story 10.7 — AC10).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NewsletterSendResponse {

    private UUID id;
    private Instant sentAt;
    private boolean reminder;
    private String locale;
    private int recipientCount;
    private String sentByUsername;
}
