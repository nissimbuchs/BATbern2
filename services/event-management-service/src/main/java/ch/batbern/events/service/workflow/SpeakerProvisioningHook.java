package ch.batbern.events.service.workflow;

/**
 * Former provisioning seam of the {@code CONTACTED → READY} transition. Nothing calls it: since
 * Story 11.D.1 the transition provisions the speaker with one
 * {@code UserApiClient.provisionUserWithRole} call, and {@code SpeakerWorkflowService} no longer
 * holds the hook (17.0 deferred work, 2026-11-09). Kept only as the reference capability interface
 * of the extension-point design ({@code docs/plans/ems-modularization-extension-points.md}, 17.12).
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
