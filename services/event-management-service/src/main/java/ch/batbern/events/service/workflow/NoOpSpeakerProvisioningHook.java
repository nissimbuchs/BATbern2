package ch.batbern.events.service.workflow;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Default {@link SpeakerProvisioningHook}: just logs that the SPEAKER role grant is deferred
 * to Story 11.E.2 (Cognito provisioning at READY).
 */
@Component
@Slf4j
public class NoOpSpeakerProvisioningHook implements SpeakerProvisioningHook {

    @Override
    public void grantSpeakerRole(String username, String email) {
        log.info(
                "Stub: SPEAKER role grant deferred to Story 11.E.2 for username={} email={}",
                username,
                email
        );
    }
}
