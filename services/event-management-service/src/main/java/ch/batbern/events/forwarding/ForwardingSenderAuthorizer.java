package ch.batbern.events.forwarding;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * May this sender use this alias? (port of the Lambda's {@code sender-auth.ts}, hardened).
 *
 * <p>Contact aliases ({@link ForwardingAlias#openToAnyone()}) accept everyone. Every other alias
 * is organizer-only, and "organizer" needs two facts: the From address is a primary or
 * additional email of an ORGANIZER, <b>and</b> SES authenticated that From (DMARC pass, or an
 * aligned DKIM signature when the domain has no DMARC policy). Without the second check, anyone
 * could type an organizer's address into From and mail every registrant of an event.
 *
 * <p>Fail-closed: if CUMS cannot be read, the exception propagates. Nothing is sent, and
 * {@link EmailForwardingService} gives the mail back to SQS for a retry instead of recording a
 * permanent rejection for what was only an outage.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ForwardingSenderAuthorizer {

    private final ForwardingDirectory directory;

    public boolean isAuthorized(ForwardingAlias alias, String senderEmail, MailAuthentication authentication) {
        if (alias.openToAnyone()) {
            return true;
        }
        if (senderEmail == null) {
            return false;
        }
        String sender = senderEmail.toLowerCase(Locale.ROOT);
        if (!directory.organizerEmails().contains(sender)) {
            return false;
        }
        if (!authentication.verifiesSender(sender)) {
            log.warn("Forwarding: organizer From for {}@ is not authenticated (dmarc={}), rejecting",
                    alias.localPart(), authentication.dmarc());
            return false;
        }
        return true;
    }
}
