package ch.batbern.events.mailcenter.render;

import ch.batbern.shared.mail.MailContext;

/**
 * What a mail says, before any variable is filled in (Story 16.11 AC5): resolved once per send, from
 * the template store or, from Story 16.5 on, from the organizer's own text. Every recipient of a
 * send renders from the same instance, so an edit of the template during the send changes nothing.
 *
 * @param templateKey   the template, or null for own text
 * @param locale        {@code de} or {@code en}: the content's language and the layout's
 * @param subject       subject with placeholders, or null when the template has none (the caller's
 *                      fallback applies)
 * @param bodyHtml      body with placeholders
 * @param layoutKey     layout to merge into, or null for a standalone body
 * @param contentSource template or own text
 */
public record MailContent(String templateKey, String locale, String subject, String bodyHtml, String layoutKey,
        MailContext.ContentSource contentSource) {

    public MailContent {
        bodyHtml = bodyHtml == null ? "" : bodyHtml;
    }

    /** The same content in {@code layout} when it names none (the composer audiences always had one). */
    public MailContent withDefaultLayout(String layout) {
        return layoutKey != null ? this
                : new MailContent(templateKey, locale, subject, bodyHtml, layout, contentSource);
    }
}
