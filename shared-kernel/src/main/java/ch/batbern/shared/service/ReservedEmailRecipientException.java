package ch.batbern.shared.service;

/**
 * Thrown by {@link EmailService} when an outbound email targets a recipient on
 * a reserved RFC 2606 / RFC 6761 domain (example.com, *.test, *.invalid, …).
 *
 * Sending to these domains is forbidden because:
 *   - They burn SES quota for no business value.
 *   - They bounce or are silently swallowed, dragging down our SES reputation.
 *   - They are emitted by our own OWASP ZAP scanner and similar fuzzers, so
 *     blocking them at the SES boundary is the simplest defense against
 *     scripted abuse of public POST endpoints.
 *
 * Callers that send mail from async paths (RegistrationEmailService, etc.) wrap
 * outbound calls in a broad try/catch and swallow exceptions, so this is a
 * runtime exception by design — it stops the SES call without surfacing an
 * error to the end user.
 */
public class ReservedEmailRecipientException extends RuntimeException {

    private final String recipient;

    public ReservedEmailRecipientException(String recipient) {
        super("Refusing to send to reserved RFC 2606/6761 domain: " + recipient);
        this.recipient = recipient;
    }

    public String getRecipient() {
        return recipient;
    }
}
