package ch.batbern.events.mailcenter.compose;

import ch.batbern.shared.mail.MailContext;
import ch.batbern.shared.mail.MailTrigger;

import java.util.List;
import java.util.Set;

/**
 * Everything audience-specific about composing a mail (Story 16.8, Winston's review 2026-10-30).
 * {@link MailComposerService} holds the flow; an adapter only knows its audience: which templates it
 * may use, who receives it, how one recipient's mail is rendered, and how the existing send code
 * delivers it. Render and deliver must use the same code, so the preview equals the sent mail.
 */
public interface AudienceAdapter {

    MailContext.Audience audience();

    /** False when everyone gets the same text (newsletter, venue): the preview does not step. */
    boolean personalized();

    /** Template categories offered for this audience (the admin template store). */
    Set<String> allowedCategories();

    /** Whether this template may be sent to this audience; null means the adapter's default. */
    boolean allowsTemplate(String templateKey);

    /** Variables this audience provides (Story 16.5 turns a subset into chips). */
    Set<String> allowedVariables();

    /** Variables that carry a credential; a test send gets dummies instead. */
    Set<String> credentialVariables();

    /** Audience-specific request checks; throws {@link IllegalArgumentException} (400). */
    void validate(ComposeRequest request);

    /** The audience in send order, plus who is left out and why. */
    Recipients resolveRecipients(ComposeContext context);

    /**
     * The mail as {@code recipient} gets it. With {@code test} every credential variable is a
     * dummy, so an organizer's test copy never carries a working link of a real recipient.
     */
    Rendering render(ComposeContext context, ComposeRecipient recipient, boolean test);

    /**
     * Sends to the audience through the existing send code, as part of {@code trigger}, and returns
     * the recorded mail(s) it produces.
     */
    List<DeliveredMail> deliver(ComposeContext context, Recipients recipients, MailTrigger trigger);
}
