package ch.batbern.events.mailcenter.compose;

import ch.batbern.events.client.UserApiClient;
import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.NewsletterSend;
import ch.batbern.events.exception.EventNotFoundException;
import ch.batbern.events.mailcenter.JpaOutboundMailRecorder;
import ch.batbern.events.mailcenter.MailMessageQueryService;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.repository.NewsletterSendRepository;
import ch.batbern.events.service.NewsletterEmailService;
import ch.batbern.shared.exception.NotFoundException;
import ch.batbern.shared.mail.MailContext;
import ch.batbern.shared.mail.MailRedaction;
import ch.batbern.shared.mail.MailTrigger;
import ch.batbern.shared.service.EmailService;
import ch.batbern.shared.utils.LoggingUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * One composer for every audience (Story 16.8, Winston's review 2026-10-30). The flow is the same
 * for all: check the request, resolve the recipients, render one recipient, then preview, send a
 * test to the organizers, or send to the audience. Everything audience-specific is in an
 * {@link AudienceAdapter}; this class has no audience {@code if}.
 *
 * <p>Preview and send share the adapter's render, and the preview is redacted exactly like the
 * record, so the preview of recipient i equals what is recorded for recipient i (AC3).
 */
@Slf4j
@Service
public class MailComposerService {

    /** Prefix of every organizer test send (AC5). */
    public static final String TEST_PREFIX = "[TEST] ";
    private static final int SAMPLE_SIZE = 8;

    private final Map<MailContext.Audience, AudienceAdapter> adapters;
    private final EventRepository events;
    private final UserApiClient userApiClient;
    private final EmailService emailService;
    private final JpaOutboundMailRecorder recorder;
    private final MailMessageQueryService queries;
    private final NewsletterSendRepository newsletterSends;
    private final NewsletterEmailService newsletterEmailService;

    @Value("${batbern.ses.configuration-set-name:#{null}}")
    private String configurationSetName;

    public MailComposerService(List<AudienceAdapter> adapters, EventRepository events, UserApiClient userApiClient,
            EmailService emailService, JpaOutboundMailRecorder recorder, MailMessageQueryService queries,
            NewsletterSendRepository newsletterSends, NewsletterEmailService newsletterEmailService) {
        this.adapters = new EnumMap<>(MailContext.Audience.class);
        adapters.forEach(adapter -> this.adapters.put(adapter.audience(), adapter));
        this.events = events;
        this.userApiClient = userApiClient;
        this.emailService = emailService;
        this.recorder = recorder;
        this.queries = queries;
        this.newsletterSends = newsletterSends;
        this.newsletterEmailService = newsletterEmailService;
    }

    /** Where a send goes. */
    public enum Target { RECIPIENTS, ORGANIZERS }

    /**
     * One recipient's mail, redacted for display.
     *
     * @param subject             subject as sent, redacted
     * @param html                body as sent, redacted
     * @param recipientIndex      which recipient
     * @param recipientCount      how many get the mail
     * @param recipient           the recipient shown
     * @param sampleRecipients    up to eight names
     * @param personalized        false when everyone gets the same text
     * @param templateKey         the template the recipient gets
     * @param locale              the language the recipient gets
     * @param replyTo             Reply-To, or null for the default
     * @param organizerCount      how many organizers a test send reaches
     * @param skipped             who is left out, and why
     * @param unknownPlaceholders placeholders left after rendering
     */
    public record Preview(String subject, String html, int recipientIndex, int recipientCount,
            ComposeRecipient recipient, List<String> sampleRecipients, boolean personalized, String templateKey,
            String locale, String replyTo, int organizerCount, List<SkippedRecipient> skipped,
            List<String> unknownPlaceholders) {
    }

    /** What a send produced: the recorded mail key(s), how many it reached, who was left out. */
    public record SendResult(List<String> mailKeys, Target target, int recipientCount,
            List<SkippedRecipient> skipped) {
    }

    private record Prepared(AudienceAdapter adapter, ComposeContext context, Recipients recipients) {
    }

    public Preview preview(String eventCode, ComposeRequest request) {
        Prepared prepared = prepare(eventCode, request, null);
        List<ComposeRecipient> recipients = prepared.recipients().recipients();
        ComposeRecipient recipient = recipients.get(request.recipientIndex());
        Rendering rendering = prepared.adapter().render(prepared.context(), recipient, false);
        String subject = MailRedaction.redact(rendering.subject(), rendering.secrets());
        String html = MailRedaction.redact(rendering.html(), rendering.secrets());
        return new Preview(subject, html, request.recipientIndex(), recipients.size(),
                new ComposeRecipient(recipient.displayName(), recipient.address(), recipient.cc(), null),
                recipients.stream().limit(SAMPLE_SIZE).map(ComposeRecipient::displayName).toList(),
                prepared.adapter().personalized(), rendering.templateKey(), rendering.locale(), rendering.replyTo(),
                organizerAddresses().size(), prepared.recipients().skipped(),
                PlaceholderScanner.scan(subject, html));
    }

    public SendResult send(String eventCode, ComposeRequest request, Target target, String organizerUsername) {
        Prepared prepared = prepare(eventCode, request, organizerUsername);
        MailTrigger trigger = MailTrigger.organizer(organizerUsername);
        return target == Target.ORGANIZERS
                ? sendTest(prepared, request.recipientIndex(), trigger)
                : sendToRecipients(prepared, trigger);
    }

    /**
     * AC5: recipient i's mail, with dummy credentials, to every organizer, subject prefixed. No real
     * recipient, no send audit row, no one-shot guard touched.
     */
    private SendResult sendTest(Prepared prepared, int recipientIndex, MailTrigger trigger) {
        ComposeRecipient recipient = prepared.recipients().recipients().get(recipientIndex);
        Rendering rendering = prepared.adapter().render(prepared.context(), recipient, true);
        List<String> organizers = organizerAddresses();
        if (organizers.isEmpty()) {
            throw new IllegalArgumentException("No organizer has an email address for a test send");
        }
        String eventCode = prepared.context().event().getEventCode();
        MailContext context = MailContext.of(trigger, eventCode, prepared.adapter().audience(),
                        rendering.templateKey())
                .withSecrets(rendering.secrets().toArray(String[]::new))
                .asTest();
        String subject = TEST_PREFIX + rendering.subject();
        String mailKey = recorder.open(context, MailRedaction.redact(subject, rendering.secrets()),
                MailRedaction.redact(rendering.html(), rendering.secrets()));
        int sent = 0;
        for (String address : organizers) {
            try {
                if (rendering.attachments().isEmpty()) {
                    emailService.sendHtmlEmailSync(context, address, null, subject, rendering.html(),
                            configurationSetName, rendering.replyTo());
                } else {
                    emailService.sendHtmlEmailSyncWithAttachments(context, address, subject, rendering.html(),
                            rendering.attachments(), configurationSetName);
                }
                sent++;
            } catch (RuntimeException e) {
                // The exception message can carry the address; log its type only.
                log.error("Test send of {} to organizer {} failed: {}", mailKey, LoggingUtils.maskEmail(address),
                        e.getClass().getSimpleName());
            }
        }
        log.info("Test send {} ({}, {}) to {} of {} organizers", mailKey, prepared.adapter().audience(),
                rendering.templateKey(), sent, organizers.size());
        return new SendResult(List.of(mailKey), Target.ORGANIZERS, sent, List.of());
    }

    /** AC6: the existing send code, then the record opened so its key can be returned at once. */
    private SendResult sendToRecipients(Prepared prepared, MailTrigger trigger) {
        List<DeliveredMail> delivered = prepared.adapter().deliver(prepared.context(), prepared.recipients(),
                trigger);
        Set<String> mailKeys = new LinkedHashSet<>();
        for (DeliveredMail mail : delivered) {
            Rendering rendering = mail.rendering();
            mailKeys.add(recorder.open(mail.context(), MailRedaction.redact(rendering.subject(), rendering.secrets()),
                    MailRedaction.redact(rendering.html(), rendering.secrets())));
        }
        log.info("Composer send {} ({}) by {} to {} recipients", mailKeys, prepared.adapter().audience(),
                prepared.context().organizerUsername(), prepared.recipients().recipients().size());
        return new SendResult(List.copyOf(mailKeys), Target.RECIPIENTS, prepared.recipients().recipients().size(),
                prepared.recipients().skipped());
    }

    /**
     * AC8: retries a PARTIAL or FAILED newsletter. 400 for anything that is not a newsletter, 409
     * (IllegalStateException) when it completed or a send is running.
     */
    public SendResult retry(String eventCode, String mailKey, String organizerUsername) {
        Event event = loadEvent(eventCode);
        MailMessageQueryService.MailEntry entry = queries.get(eventCode, mailKey)
                .orElseThrow(() -> new NotFoundException("Mail not found: " + mailKey))
                .entry();
        if (entry.newsletterSendId() == null || !MailContext.Audience.NEWSLETTER.name().equals(entry.audience())) {
            throw new IllegalArgumentException("Only a newsletter can be retried: " + mailKey);
        }
        NewsletterSend send = newsletterSends.findById(entry.newsletterSendId())
                .orElseThrow(() -> new NotFoundException("Newsletter send not found for " + mailKey));
        newsletterEmailService.retryFailedRecipients(send, event, organizerUsername);
        return new SendResult(List.of(mailKey), Target.RECIPIENTS,
                send.getRecipientCount() != null ? send.getRecipientCount() : 0, List.of());
    }

    /** AC10: the backend owns validation; the frontend checks are convenience only. */
    private Prepared prepare(String eventCode, ComposeRequest request, String organizerUsername) {
        Event event = loadEvent(eventCode);
        if (request.contentSource() == MailContext.ContentSource.OWN_TEXT) {
            throw new IllegalArgumentException("Own text is not available yet (Story 16.5)");
        }
        AudienceAdapter adapter = adapters.get(request.audience());
        if (adapter == null) {
            throw new IllegalArgumentException("The composer does not send to " + request.audience());
        }
        adapter.validate(request);
        if (!adapter.allowsTemplate(request.templateKey())) {
            throw new IllegalArgumentException("Template '" + request.templateKey() + "' cannot be sent to "
                    + request.audience().name().toLowerCase(Locale.ROOT));
        }
        ComposeContext context = new ComposeContext(event, request, organizerUsername);
        Recipients recipients = adapter.resolveRecipients(context);
        if (recipients.recipients().isEmpty()) {
            throw new IllegalArgumentException("Nobody in this audience would get the mail");
        }
        if (request.recipientIndex() < 0 || request.recipientIndex() >= recipients.recipients().size()) {
            throw new IllegalArgumentException("recipientIndex " + request.recipientIndex() + " is outside 0.."
                    + (recipients.recipients().size() - 1));
        }
        return new Prepared(adapter, context, recipients);
    }

    private Event loadEvent(String eventCode) {
        return events.findByEventCode(eventCode)
                .orElseThrow(() -> new EventNotFoundException("Event not found: " + eventCode));
    }

    /** Every organizer's address, once each; an organizer CUMS cannot resolve is left out. */
    private List<String> organizerAddresses() {
        Map<String, String> byLowerCase = new LinkedHashMap<>();
        List<String> failed = new ArrayList<>();
        for (String username : userApiClient.getOrganizerUsernames()) {
            try {
                String email = userApiClient.getUserByUsername(username).getEmail();
                if (email != null && !email.isBlank()) {
                    byLowerCase.putIfAbsent(email.toLowerCase(Locale.ROOT), email);
                }
            } catch (RuntimeException e) {
                failed.add(username);
            }
        }
        if (!failed.isEmpty()) {
            log.warn("Test send: {} organizer(s) could not be resolved and get no copy", failed.size());
        }
        return List.copyOf(byLowerCase.values());
    }
}
