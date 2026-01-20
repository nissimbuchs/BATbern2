package ch.batbern.events.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Request DTO for sending bulk speaker invitations - Story 6.1 AC7.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BulkSendInvitationRequest {

    /**
     * List of speaker usernames to invite.
     */
    @NotEmpty(message = "At least one username is required")
    @Size(max = 50, message = "Maximum 50 invitations per batch")
    private List<String> usernames;

    /**
     * Optional personal message to include in all invitation emails.
     */
    @Size(max = 2000, message = "Personal message must be at most 2000 characters")
    private String personalMessage;

    /**
     * Number of days until invitations expire (default: 14).
     */
    @Builder.Default
    private Integer expirationDays = 14;
}
