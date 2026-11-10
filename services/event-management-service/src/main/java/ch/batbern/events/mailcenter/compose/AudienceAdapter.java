package ch.batbern.events.mailcenter.compose;

import ch.batbern.events.mailcenter.render.MailContent;
import ch.batbern.events.mailcenter.variables.MailVariable;
import ch.batbern.events.mailcenter.variables.MailVariable.Scope;
import ch.batbern.shared.mail.MailContext;
import ch.batbern.shared.mail.MailTrigger;

import java.time.Instant;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

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

    /**
     * Catalog scopes this audience fills beside {@code SYSTEM} and {@code EVENT}, which every
     * composed mail has (Story 16.11 AC9).
     */
    Set<Scope> scopes();

    /**
     * Every placeholder name (canonical and alias) a template for this audience may use, derived
     * from the catalog scopes (Story 16.5 turns a subset into chips).
     */
    default Set<String> allowedVariables() {
        return MailVariable.namesOf(allScopes());
    }

    /**
     * The variables offered as chips in the own-text editor (Story 16.5 AC6): a short, plain subset
     * of {@link #allowedVariables()}, in display order.
     */
    List<String> offeredVariables();

    /**
     * Own text as this audience sends it (Story 16.5): the request's subject and body in the chosen
     * language with the BATbern layout. The newsletter adds its unsubscribe footer.
     */
    default MailContent ownTextContent(ComposeContext context) {
        return context.request().ownTextContent();
    }

    /**
     * When own text with this request's subject already went to this audience for the event, or
     * empty (Story 16.5 AC9; only registrants have a one-shot guard).
     */
    default Optional<Instant> ownTextAlreadySent(ComposeContext context) {
        return Optional.empty();
    }

    /** Variables of this audience's scopes that carry a credential; a test send gets dummies. */
    default Set<String> credentialVariables() {
        Set<Scope> scopes = allScopes();
        return Arrays.stream(MailVariable.values())
                .filter(v -> v.credential() && scopes.contains(v.scope()))
                .flatMap(v -> v.names().stream())
                .collect(Collectors.toUnmodifiableSet());
    }

    private Set<Scope> allScopes() {
        Set<Scope> all = EnumSet.of(Scope.SYSTEM, Scope.EVENT);
        all.addAll(scopes());
        return all;
    }

    /**
     * The variables {@link #render} fills for {@code recipient}, built from the catalog (Story 16.11
     * AC9). With {@code test} every credential value is a dummy, as in {@link #render}. Own text
     * (Story 16.5) renders with this map, so it can use what the template could.
     */
    Map<String, String> variables(ComposeContext context, ComposeRecipient recipient, boolean test);

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
