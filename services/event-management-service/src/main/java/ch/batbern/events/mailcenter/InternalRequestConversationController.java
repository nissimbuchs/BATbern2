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

import java.util.Map;
import java.util.UUID;

/**
 * #1237 and #1246: company-user-management opens and closes the Mail Center conversation of a profile
 * merge request or a company logo request. No JWT: service to service in the VPC. {@code /internal/**}
 * is not routed by the API gateway, which also denies it, and it is not part of a public OpenAPI
 * contract, like
 * {@link InternalMailController}. Approving and declining stay in company-user-management
 * ({@code /api/v1/users/merge-requests/*} and {@code /api/v1/users/company-logo-requests/*},
 * organizers only).
 */
@RestController
@RequiredArgsConstructor
public class InternalRequestConversationController {

    private final RequestConversations conversations;

    @PostMapping("/internal/mail-center/merge-requests")
    public ResponseEntity<Map<String, String>> open(@Valid @RequestBody OpenRequest request) {
        String key = conversations.open(RequestConversations.Kind.MERGE, request.requestId(),
                request.requesterName(), request.requesterEmail(), request.subject(), request.note());
        return ResponseEntity.ok(Map.of("conversationKey", key));
    }

    @PostMapping("/internal/mail-center/merge-requests/{requestId}/done")
    public ResponseEntity<Void> done(@PathVariable UUID requestId, @Valid @RequestBody DoneRequest request) {
        conversations.close(RequestConversations.Kind.MERGE, requestId, request.decidedBy());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/internal/mail-center/logo-requests")
    public ResponseEntity<Map<String, String>> openLogo(@Valid @RequestBody OpenRequest request) {
        String key = conversations.open(RequestConversations.Kind.LOGO, request.requestId(), request.requesterName(),
                request.requesterEmail(), request.subject(), request.note());
        return ResponseEntity.ok(Map.of("conversationKey", key));
    }

    @PostMapping("/internal/mail-center/logo-requests/{requestId}/done")
    public ResponseEntity<Void> doneLogo(@PathVariable UUID requestId, @Valid @RequestBody DoneRequest request) {
        conversations.close(RequestConversations.Kind.LOGO, requestId, request.decidedBy());
        return ResponseEntity.noContent().build();
    }

    public record OpenRequest(
            @NotNull UUID requestId,
            @Size(max = 255) String requesterName,
            @NotBlank @Pattern(regexp = EmailAddresses.OPENAPI_PATTERN) @Size(max = 320) String requesterEmail,
            @NotBlank @Size(max = 500) String subject,
            @Size(max = 500) String note) {
    }

    public record DoneRequest(@NotBlank @Size(max = 100) String decidedBy) {
    }
}
