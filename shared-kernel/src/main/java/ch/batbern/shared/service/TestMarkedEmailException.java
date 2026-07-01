package ch.batbern.shared.service;

import ch.batbern.shared.util.EmailContentTestMarker;

/**
 * Thrown by {@link EmailService} (and by the newsletter send entry point) when an outbound
 * email carries a {@link EmailContentTestMarker canonical test-content marker} — i.e. it was
 * generated from test data and must never reach real recipients.
 *
 * <p>This is the <b>second wall of defence</b> for the 2026-07-01 newsletter incident: even if
 * a test bypasses the Playwright tag/env guards and drives a real send on staging (= production),
 * the marked subject/body is caught at the SES boundary and the send is refused.
 *
 * <p>It fires ONLY on the real-send path (a live {@code SesClient}); local dev, where
 * {@code LocalEmailCapture} intercepts, is unaffected so dev-only send specs still exercise the
 * pipeline end-to-end. Runtime exception by design — async send paths wrap outbound calls in a
 * broad try/catch, so this stops the SES call without surfacing an error to any end user.
 */
public class TestMarkedEmailException extends RuntimeException {

    public TestMarkedEmailException(String context) {
        super("Refusing to send email containing a test marker (" + EmailContentTestMarker.describe()
                + ") on a live SES client: " + context);
    }
}
