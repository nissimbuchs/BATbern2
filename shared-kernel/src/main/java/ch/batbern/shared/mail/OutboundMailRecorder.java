package ch.batbern.shared.mail;

/**
 * Records what went out (Story 16.1a). {@code EmailService} calls it once per send that carries a
 * {@link MailContext}, after SES answered (success or SES error); a send blocked earlier (reserved
 * domain, ADR-015 test marker) is never recorded.
 *
 * <p>Optional: a service without an implementation simply records nothing. Implementations must be
 * safe to call from {@code @Async} threads; exceptions are logged by the caller and never fail the send.
 */
public interface OutboundMailRecorder {

    void record(MailContext context, OutboundMail mail);
}
