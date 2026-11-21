package ch.batbern.events.service;

import ch.batbern.events.client.UserApiClient;
import ch.batbern.events.domain.Event;
import ch.batbern.events.dto.generated.users.UserResponse;
import ch.batbern.events.mailcenter.render.MailLocales;
import ch.batbern.events.mailcenter.render.MailPipeline;
import ch.batbern.events.mailcenter.render.RenderedMail;
import ch.batbern.events.mailcenter.variables.MailVariable;
import ch.batbern.shared.mail.MailContext;
import ch.batbern.shared.mail.MailTrigger;
import ch.batbern.shared.service.EmailService;
import ch.batbern.shared.utils.LoggingUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The mail that tells registrants their event was cancelled (#824 item 2), template
 * {@code registration-event-cancelled} in German or English by the person's language (the common
 * mail rule, {@link MailLocales}).
 *
 * <p>A transactional mail, not a newsletter: it goes to every person whose registration the
 * cancellation ended, including the waitlist and people with a newsletter opt-out. Each address once.
 * One {@code @Async} run sends one mail after the other, throttled like the newsletter for SES, and a
 * failure for one person never stops the others. Every mail is recorded for the Mail Center under one
 * send group of the organizer who cancelled.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RegistrationCancellationEmailService {

    static final String TEMPLATE_KEY = "registration-event-cancelled";

    private final EmailService emailService;
    private final UserApiClient userApiClient;
    private final MailPipeline mailPipeline;

    /** Same SES throttle as the newsletter (~14 mails/s at 70 ms). */
    @Value("${newsletter.send.rate-delay-ms:70}")
    private long sendRateDelayMs;

    /**
     * One person to tell, taken from the registration before it was cancelled.
     *
     * @param username  the attendee's username, null for none
     * @param firstName for the salutation
     * @param email     the registration's address, null to take the user's
     */
    public record Recipient(String registrationCode, String username, String firstName, String email) {
    }

    /**
     * Sends the cancellation mail to each recipient. Runs after the cancellation committed.
     *
     * @param cancelledBy the organizer who cancelled the event (Mail Center sender)
     */
    @Async
    public void sendEventCancelled(Event event, List<Recipient> recipients, String cancelledBy) {
        MailTrigger trigger = MailTrigger.organizer(cancelledBy);
        Set<String> seen = new HashSet<>();
        int sent = 0;
        int failed = 0;
        for (Recipient recipient : recipients) {
            UserResponse user = lookUp(recipient.username());
            String email = recipient.email() != null && !recipient.email().isBlank()
                    ? recipient.email() : (user == null ? null : user.getEmail());
            if (email == null || email.isBlank()) {
                log.warn("Event-cancelled mail for {}: no address for registration {}",
                        event.getEventCode(), recipient.registrationCode());
                failed++;
                continue;
            }
            if (!seen.add(email.toLowerCase(Locale.ROOT))) {
                continue;
            }
            try {
                String locale = locale(recipient.username());
                RenderedMail mail = render(event, recipient, user, locale);
                MailContext context = MailContext.of(trigger, event.getEventCode(),
                        MailContext.Audience.REGISTRANTS, TEMPLATE_KEY).withSecrets(recipient.registrationCode());
                emailService.sendHtmlEmailSync(context, email, EventMailCopies.of(user), mail.subject(),
                        mail.html(), null);
                sent++;
            } catch (Exception e) {
                log.error("Event-cancelled mail for {} to {} failed", event.getEventCode(),
                        LoggingUtils.maskEmail(email), e);
                failed++;
            }
            sleepQuietly();
        }
        log.info("Event-cancelled mails for {}: {} sent, {} failed", event.getEventCode(), sent, failed);
    }

    private RenderedMail render(Event event, Recipient recipient, UserResponse user, String locale) {
        String name = recipient.firstName() != null && !recipient.firstName().isBlank() ? recipient.firstName()
                : user != null && user.getFirstName() != null ? user.getFirstName() : "";
        RenderedMail mail = mailPipeline.renderNotice(mailPipeline.template(TEMPLATE_KEY, locale),
                mailPipeline.variables().event(event, locale)
                        .put(MailVariable.RECIPIENT_NAME, name)
                        .asMap());
        String subject = mail.subject() != null ? mail.subject()
                : ("de".equals(locale) ? "Abgesagt: " : "Cancelled: ") + event.getTitle();
        return new RenderedMail(subject, mail.html());
    }

    private UserResponse lookUp(String username) {
        if (username == null || username.isBlank()) {
            return null;
        }
        try {
            return userApiClient.getUserByUsername(username);
        } catch (Exception e) {
            log.warn("Event-cancelled mail: no user {} ({})", username, e.getMessage());
            return null;
        }
    }

    private String locale(String username) {
        if (username == null || username.isBlank()) {
            return "de";
        }
        return MailLocales.languageOf(userApiClient.getPreferredLanguage(username));
    }

    private void sleepQuietly() {
        if (sendRateDelayMs <= 0) {
            return;
        }
        try {
            Thread.sleep(sendRateDelayMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
