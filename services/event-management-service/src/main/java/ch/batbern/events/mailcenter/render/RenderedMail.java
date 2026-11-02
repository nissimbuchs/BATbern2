package ch.batbern.events.mailcenter.render;

/**
 * A mail as sent: subject (null when the content had none and the caller supplies its own) and HTML.
 */
public record RenderedMail(String subject, String html) {
}
