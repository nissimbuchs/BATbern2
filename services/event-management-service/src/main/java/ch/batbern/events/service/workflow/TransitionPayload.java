package ch.batbern.events.service.workflow;

import ch.batbern.events.dto.SpeakerResponsePreferences;
import lombok.Builder;

import java.util.Map;

/**
 * Optional fields used by side-effect hooks of
 * {@link ch.batbern.events.service.SpeakerWorkflowService#transition}.
 *
 * <p>Unspecified fields default to {@code null}. The precondition checks in the workflow service
 * enforce the right fields per target state (e.g. {@code email} is required when transitioning to
 * {@code READY}; {@code reason} is required when declining from {@code INVITED+}).
 */
@Builder
public record TransitionPayload(
        String email,
        String firstName,
        String lastName,
        String reason,
        SpeakerResponsePreferences responsePreferences,
        Map<String, Object> inviteContext
) {
}
