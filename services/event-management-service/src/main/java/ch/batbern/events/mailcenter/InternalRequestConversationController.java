package ch.batbern.events.mailcenter;

import ch.batbern.shared.utils.EmailAddresses;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

/**
 * Event-management implements the Mail Center request contract that company-user-management owns
 * (ADR-016 decision 4, Epic 17 entry 7; spec {@code docs/api/internal/cums-internal.openapi.yml}):
 * CUMS opens the conversation of a profile merge request (#1237) or a company logo request (#1246)
 * while it files the request, and tells the decision once it is committed. Both calls are
 * idempotent. {@code /internal/**} is VPC only: the API gateway does not route it and denies it. A
 * forwarded JWT is optional and changes nothing; the conversation records the decider CUMS sends.
 * Approving and declining stay in company-user-management.
 */
@RestController
@RequiredArgsConstructor
public class InternalRequestConversationController {

    private final RequestConversations conversations;

    @PostMapping("/internal/mail-center-requests")
    public ResponseEntity<Void> opened(@Valid @RequestBody Opened request) {
        conversations.open(request.kind(), request.requestId(), request.requesterName(), request.requesterEmail(),
                request.subject(), request.note());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/internal/mail-center-requests/{requestId}/decision")
    public ResponseEntity<Void> decided(@PathVariable UUID requestId, @Valid @RequestBody Decided decision) {
        conversations.close(decision.kind(), requestId, decision.decidedBy(), decision.decidedAt());
        return ResponseEntity.noContent().build();
    }

    public record Opened(
            @NotNull RequestConversations.Kind kind,
            @NotNull UUID requestId,
            @Size(max = 255) String requesterName,
            @NotBlank @Pattern(regexp = EmailAddresses.OPENAPI_PATTERN) @Size(max = 320) String requesterEmail,
            @NotBlank @Size(max = 500) String subject,
            @Size(max = 500) String note) {
    }

    public enum Status { APPROVED, DECLINED }

    public record Decided(
            @NotNull RequestConversations.Kind kind,
            @NotNull Status status,
            @NotBlank @Size(max = 100) String decidedBy,
            @NotNull Instant decidedAt) {
    }
}
