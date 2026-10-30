package ch.batbern.shared.mail;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * What a send is about, passed with it so the platform can record what went out (Story 16.1a).
 * {@code EmailService} hands the context and the rendered mail to the {@link OutboundMailRecorder}
 * after SES answers; a send without a context is not recorded.
 *
 * @param sendGroupId      one recorded mail per group id; SES tag {@code mail_group}. Letters,
 *                         digits, {@code _} and {@code -} only (SES tag value rules)
 * @param eventCode        the event the mail belongs to, or null
 * @param audience         who the mail goes to
 * @param templateKey      the email template it was rendered from, or null for own text
 * @param contentSource    template or own text
 * @param senderUsername   the organizer who sent it, or null for the system
 * @param newsletterSendId the {@code newsletter_sends} row of a newsletter or registrant notice, or null
 * @param test             an organizer test send (e.g. newsletter test mode)
 * @param secrets          plain-text credentials in this mail (temporary password, registration
 *                         code) that must not be stored; token links are masked anyway, see
 *                         {@link MailRedaction}
 */
public record MailContext(
        String sendGroupId,
        String eventCode,
        Audience audience,
        String templateKey,
        ContentSource contentSource,
        String senderUsername,
        UUID newsletterSendId,
        boolean test,
        List<String> secrets) {

    private static final int MAX_GROUP_ID_LENGTH = 256;

    /** Who a mail goes to. */
    public enum Audience { NEWSLETTER, REGISTRANTS, SPEAKERS, VENUE, PARTNERS, ORGANIZERS, SYSTEM }

    /** Where the text came from. */
    public enum ContentSource { TEMPLATE, OWN_TEXT }

    public MailContext {
        Objects.requireNonNull(sendGroupId, "sendGroupId must not be null");
        Objects.requireNonNull(audience, "audience must not be null");
        Objects.requireNonNull(contentSource, "contentSource must not be null");
        secrets = secrets == null ? List.of() : secrets.stream()
                .filter(s -> s != null && !s.isBlank())
                .toList();
    }

    /** A mail that stands alone, e.g. a registration confirmation: its own send group. */
    public static MailContext individual(String eventCode, Audience audience, String templateKey) {
        return of(MailTrigger.system(), eventCode, audience, templateKey);
    }

    /** A mail of a run or request: one send group per trigger, event and template key. */
    public static MailContext of(MailTrigger trigger, String eventCode, Audience audience, String templateKey) {
        String groupId = sanitize(trigger.runId() + "_" + (eventCode != null ? eventCode : "general")
                + "_" + (templateKey != null ? templateKey : "own-text"));
        return new MailContext(groupId, eventCode, audience, templateKey, ContentSource.TEMPLATE,
                trigger.senderUsername(), null, false, List.of());
    }

    /** The same context with credentials that appear in this mail's text, to be redacted. */
    public MailContext withSecrets(String... values) {
        List<String> all = new ArrayList<>(secrets);
        for (String value : values) {
            all.add(value);
        }
        return new MailContext(sendGroupId, eventCode, audience, templateKey, contentSource, senderUsername,
                newsletterSendId, test, all);
    }

    /** The same context linked to its {@code newsletter_sends} row. */
    public MailContext forNewsletterSend(UUID sendId, boolean testSend) {
        return new MailContext(sendGroupId, eventCode, audience, templateKey, contentSource, senderUsername,
                sendId, testSend, secrets);
    }

    private static String sanitize(String raw) {
        String safe = raw.replaceAll("[^A-Za-z0-9_-]", "-");
        return safe.length() > MAX_GROUP_ID_LENGTH ? safe.substring(0, MAX_GROUP_ID_LENGTH) : safe;
    }
}
