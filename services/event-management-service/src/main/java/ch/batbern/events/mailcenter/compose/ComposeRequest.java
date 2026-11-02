package ch.batbern.events.mailcenter.compose;

import ch.batbern.shared.mail.MailContext;

import java.util.List;

/**
 * One composer request, independent of the generated preview/send DTOs (Story 16.8).
 *
 * @param audience            who the mail goes to
 * @param contentSource       template or own text (own text arrives with Story 16.5)
 * @param templateKey         the template; null = automatic (speakers) or the default (newsletter)
 * @param locale              {@code de} or {@code en}; registrants and speakers get their own
 * @param recipientIndex      which recipient a preview or a test send renders for
 * @param newsletterReminder  newsletter only: "Reminder:" prefix
 * @param speakerReminderType speakers only: {@code RESPONSE} or {@code CONTENT}
 * @param venueRecipients     venue only: {@code VENUE}, {@code CATERING}; first is To, the rest Cc
 * @param venueNotes          venue only: free text
 * @param ownSubject          own text only: the subject, one line (Story 16.5)
 * @param ownBodyHtml         own text only: the body HTML, the layout is added
 * @param speakerStatuses     speakers with own text only: which speakers get it
 * @param confirmResend       registrants with own text only: send although this subject went out
 */
public record ComposeRequest(
        MailContext.Audience audience,
        MailContext.ContentSource contentSource,
        String templateKey,
        String locale,
        int recipientIndex,
        boolean newsletterReminder,
        String speakerReminderType,
        List<String> venueRecipients,
        String venueNotes,
        String ownSubject,
        String ownBodyHtml,
        List<String> speakerStatuses,
        boolean confirmResend) {

    public ComposeRequest {
        contentSource = contentSource == null ? MailContext.ContentSource.TEMPLATE : contentSource;
        locale = "en".equalsIgnoreCase(locale) ? "en" : "de";
        venueRecipients = venueRecipients == null ? List.of() : List.copyOf(venueRecipients);
        templateKey = templateKey == null || templateKey.isBlank() ? null : templateKey;
        speakerStatuses = speakerStatuses == null ? List.of() : List.copyOf(speakerStatuses);
    }

    /** A template request (Story 16.8 shape). */
    public ComposeRequest(MailContext.Audience audience, MailContext.ContentSource contentSource, String templateKey,
            String locale, int recipientIndex, boolean newsletterReminder, String speakerReminderType,
            List<String> venueRecipients, String venueNotes) {
        this(audience, contentSource, templateKey, locale, recipientIndex, newsletterReminder, speakerReminderType,
                venueRecipients, venueNotes, null, null, List.of(), false);
    }

    public boolean ownText() {
        return contentSource == MailContext.ContentSource.OWN_TEXT;
    }
}
