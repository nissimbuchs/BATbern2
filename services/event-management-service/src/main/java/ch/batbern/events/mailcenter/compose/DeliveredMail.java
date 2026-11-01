package ch.batbern.events.mailcenter.compose;

import ch.batbern.shared.mail.MailContext;

/**
 * One recorded mail a delivery produces: its context (send group) and the rendering of its first
 * recipient, used to open the record before an asynchronous send reaches it.
 */
public record DeliveredMail(MailContext context, Rendering rendering) {
}
