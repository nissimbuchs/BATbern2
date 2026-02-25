package ch.batbern.events.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Response DTO for a newsletter subscriber (Story 10.7 — AC10).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubscriberResponse {

    private UUID id;
    private String email;
    private String firstName;
    private String language;
    private String source;
    private String username;
    private Instant subscribedAt;
}
