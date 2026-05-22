package ch.batbern.events.service.workflow;

/**
 * Provisioning seam invoked by {@code SpeakerWorkflowService.transition()} on the
 * {@code CONTACTED → READY} transition.
 *
 * <p>The default {@link NoOpSpeakerProvisioningHook} just logs — the Cognito-backed
 * implementation lands in Story 11.E.2 without changing
 * {@link ch.batbern.events.service.SpeakerWorkflowService}.
 */
public interface SpeakerProvisioningHook {

    /**
     * Grant the SPEAKER role to a freshly-provisioned user. Idempotent: callers may invoke this
     * for users who already hold the SPEAKER role.
     *
     * @param username speaker's username (ADR-003 meaningful ID)
     * @param email speaker's email address
     */
    void grantSpeakerRole(String username, String email);
}
