package ch.batbern.events.mailcenter;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
 * #1237: company-user-management opens and closes the Mail Center conversation of a profile merge
 * request. No JWT: service to service in the VPC. {@code /internal/**} is not routed by the API
 * gateway, which also denies it, and it is not part of a public OpenAPI contract, like
 * {@link InternalMailController}. Approving and declining stay in company-user-management
 * ({@code /api/v1/users/merge-requests/*}, organizers only).
 */
@RestController
@RequiredArgsConstructor
public class InternalMergeRequestController {

    private final MergeRequestConversations conversations;

    @PostMapping("/internal/mail-center/merge-requests")
    public ResponseEntity<Map<String, String>> open(@Valid @RequestBody OpenRequest request) {
        String key = conversations.open(request.requestId(), request.requesterName(), request.requesterEmail(),
                request.subject(), request.note());
        return ResponseEntity.ok(Map.of("conversationKey", key));
    }

    @PostMapping("/internal/mail-center/merge-requests/{requestId}/done")
    public ResponseEntity<Void> done(@PathVariable UUID requestId, @Valid @RequestBody DoneRequest request) {
        conversations.close(requestId, request.decidedBy());
        return ResponseEntity.noContent().build();
    }

    public record OpenRequest(
            @NotNull UUID requestId,
            @Size(max = 255) String requesterName,
            @NotBlank @Email @Size(max = 320) String requesterEmail,
            @NotBlank @Size(max = 500) String subject,
            @Size(max = 500) String note) {
    }

    public record DoneRequest(@NotBlank @Size(max = 100) String decidedBy) {
    }
}
