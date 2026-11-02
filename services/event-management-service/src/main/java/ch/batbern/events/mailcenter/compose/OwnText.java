package ch.batbern.events.mailcenter.compose;

import ch.batbern.events.mailcenter.render.MailContent;
import ch.batbern.shared.mail.MailContext;

import java.nio.charset.StandardCharsets;

/**
 * Own text (Story 16.5): the organizer's subject and body as one more {@link MailContent}, rendered
 * by the same pipeline and with the same variables as a template of the audience (16.11).
 */
public final class OwnText {

    /**
     * Template keys stored on a {@code newsletter_sends} row of an own-text send (the column is NOT
     * NULL). Newsletter and registrant notices share the table, so each has its own.
     */
    public static final String NEWSLETTER_ROW_KEY = "own-text-newsletter";
    public static final String REGISTRANTS_ROW_KEY = "own-text-registrants";
    static final int MAX_SUBJECT_LENGTH = 500;
    static final int MAX_BODY_BYTES = 200 * 1024;

    private OwnText() {
    }

    /** AC13: subject 1–500 characters on one line, body 1 character to 200 KB (400 otherwise). */
    public static void validateShape(ComposeRequest request) {
        String subject = request.ownSubject();
        String body = request.ownBodyHtml();
        if (subject == null || subject.isBlank()) {
            throw new IllegalArgumentException("ownSubject is required for own text");
        }
        if (subject.length() > MAX_SUBJECT_LENGTH) {
            throw new IllegalArgumentException("ownSubject is longer than " + MAX_SUBJECT_LENGTH + " characters");
        }
        if (subject.indexOf('\r') >= 0 || subject.indexOf('\n') >= 0) {
            throw new IllegalArgumentException("ownSubject must be one line");
        }
        if (body == null || body.isBlank()) {
            throw new IllegalArgumentException("ownBodyHtml is required for own text");
        }
        if (body.getBytes(StandardCharsets.UTF_8).length > MAX_BODY_BYTES) {
            throw new IllegalArgumentException("ownBodyHtml is larger than 200 KB");
        }
    }

    /** The request's own text in the chosen language, with the BATbern layout. */
    public static MailContent content(ComposeRequest request) {
        return content(request.ownSubject(), request.ownBodyHtml(), request.locale());
    }

    /** Own text stored on a send row (newsletter, registrant notices). */
    public static MailContent content(String subject, String bodyHtml, String locale) {
        return new MailContent(null, locale, subject, bodyHtml, MailRenderer.LAYOUT_KEY,
                MailContext.ContentSource.OWN_TEXT);
    }
}
