package ch.batbern.events.forwarding;

import ch.batbern.events.domain.EmailForwardingOutcome;
import ch.batbern.shared.service.EmailService;
import ch.batbern.shared.util.ReservedEmailDomain;
import ch.batbern.shared.utils.LoggingUtils;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Forwards one inbound mail addressed to {@code @batbern.ch} aliases (Story 10.26, moved from the
 * forwarder Lambda into EMS so that no public endpoint is needed to resolve recipients).
 *
 * <p>Order of checks, each one cheaper and earlier than the next:
 * <ol>
 *   <li>iMIP calendar replies, mail we forwarded ourselves (loop), mail without From → dropped</li>
 *   <li>only {@code noreply@}/{@code calendar@}/unknown aliases → dropped, not counted as a rejection</li>
 *   <li>automatic replies (out-of-office) → dropped</li>
 *   <li>claim the S3 key: a redelivered SQS message is forwarded at most once</li>
 *   <li>per alias: sender authorization, then recipient resolution</li>
 *   <li>send: one visible mail for {@code batbern{N}-speaker@} (speakers in To, moderator in Cc),
 *       otherwise one private copy per recipient</li>
 * </ol>
 * A failure before the first send gives the claim back and rethrows, so SQS retries; after the
 * first send nothing is retried (a gap is better than a double blast to every registrant).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmailForwardingService {

    public enum Result {
        DROPPED_CALENDAR_REPLY,
        DROPPED_LOOP,
        DROPPED_MALFORMED,
        DROPPED_NO_REPLY,
        DROPPED_AUTO_REPLY,
        DUPLICATE,
        REJECTED,
        UNRESOLVED,
        FORWARDED,
        PARTIAL,
        FAILED
    }

    private final EmailForwardingProperties properties;
    private final ForwardingSenderAuthorizer authorizer;
    private final ForwardingRecipientResolver resolver;
    private final ForwardedMessageRewriter rewriter;
    private final EmailForwardingLogService forwardingLog;
    private final ForwardingMetrics metrics;
    private final EmailService emailService;

    public Result forward(String s3Key, byte[] raw) {
        if (MailClassifier.isCalendarReply(new String(raw, StandardCharsets.ISO_8859_1))) {
            log.info("Forwarding: dropping iMIP calendar reply {}", s3Key);
            metrics.increment(ForwardingMetrics.CALENDAR_REPLIES_DROPPED);
            return Result.DROPPED_CALENDAR_REPLY;
        }

        MimeMessage message;
        InternetAddress from;
        try {
            message = new MimeMessage(Session.getInstance(new Properties()), new ByteArrayInputStream(raw));
            String[] fromHeaders = message.getHeader("From");
            if (fromHeaders == null || fromHeaders.length != 1) {
                // RFC 5322 allows exactly one From. Several would let the authorization check one
                // address while the recipient's client shows another.
                log.warn("Forwarding: mail {} has {} From headers, dropping", s3Key,
                        fromHeaders == null ? 0 : fromHeaders.length);
                return Result.DROPPED_MALFORMED;
            }
            from = firstAddress(fromHeaders[0]);
        } catch (MessagingException e) {
            log.warn("Forwarding: unparseable mail {}: {}", s3Key, e.getMessage());
            return Result.DROPPED_MALFORMED;
        }
        if (from == null || from.getAddress() == null) {
            log.warn("Forwarding: mail {} has no From address", s3Key);
            return Result.DROPPED_MALFORMED;
        }
        String sender = from.getAddress().toLowerCase(Locale.ROOT);
        String domain = properties.domain().toLowerCase(Locale.ROOT);

        if (header(message, MailClassifier.FORWARDED_MARKER_HEADER) != null || sender.endsWith("@" + domain)) {
            log.info("Forwarding: dropping looped mail {}", s3Key);
            metrics.increment(ForwardingMetrics.LOOPS_DROPPED);
            return Result.DROPPED_LOOP;
        }

        List<ForwardingAlias> addressed = addressedAliases(message, domain);
        List<ForwardingAlias> forwardable = addressed.stream().filter(ForwardingAlias::forwardable).toList();
        String aliasNames = forwardable.stream().map(ForwardingAlias::localPart).collect(Collectors.joining(","));
        if (forwardable.isEmpty()) {
            if (addressed.stream().anyMatch(a -> a.kind() == ForwardingAlias.Kind.NO_REPLY)) {
                metrics.increment(ForwardingMetrics.NO_REPLY_DROPPED);
                return Result.DROPPED_NO_REPLY;
            }
            log.warn("Forwarding: mail {} addresses no known alias ({})", s3Key,
                    addressed.stream().map(ForwardingAlias::localPart).collect(Collectors.joining(",")));
            metrics.increment(ForwardingMetrics.UNRESOLVED);
            return Result.UNRESOLVED;
        }

        if (MailClassifier.isAutoReply(header(message, "Auto-Submitted"), header(message, "Precedence"),
                header(message, "X-Autoreply"), header(message, "X-Autorespond"))) {
            log.info("Forwarding: dropping automatic reply to {}", aliasNames);
            metrics.increment(ForwardingMetrics.AUTO_REPLIES_DROPPED);
            return Result.DROPPED_AUTO_REPLY;
        }

        if (!forwardingLog.claim(s3Key)) {
            if (forwardingLog.isStuckAfterSending(s3Key)) {
                // Sending began and never finished (task died mid-send). Not retried, to avoid a
                // double blast; reported so an operator can check who is missing the mail.
                log.error("Forwarding: {} stalled after sending began; some recipients may lack it", s3Key);
                metrics.increment(ForwardingMetrics.FAILED);
            } else {
                log.info("Forwarding: {} already processed, skipping redelivery", s3Key);
                metrics.increment(ForwardingMetrics.DUPLICATES_SKIPPED);
            }
            return Result.DUPLICATE;
        }

        MailAuthentication authentication = MailAuthentication.fromHeaders(headers(message, "Authentication-Results"));
        String maskedSender = LoggingUtils.maskEmail(sender);
        Delivery delivery;
        // Everything between the claim and markSending is retryable: nothing has been sent yet.
        // Any failure here gives the claim back and rethrows, so SQS redelivers (then DLQ + alarm).
        try {
            Plan plan = plan(forwardable, sender, authentication);
            if (!plan.anyAuthorized) {
                log.warn("Forwarding: sender {} may not use {}", maskedSender, aliasNames);
                metrics.increment(ForwardingMetrics.REJECTED);
                forwardingLog.complete(s3Key, EmailForwardingOutcome.REJECTED, aliasNames, 0, 0);
                return Result.REJECTED;
            }
            delivery = prepare(raw, plan, from.getPersonal(), sender);
            if (delivery == null) {
                return unresolved(s3Key, aliasNames);
            }
            forwardingLog.markSending(s3Key);
        } catch (RuntimeException e) {
            forwardingLog.release(s3Key);
            throw e;
        }

        Result result = delivery.broadcast()
                ? sendBroadcast(s3Key, delivery, aliasNames)
                : sendIndividually(s3Key, delivery, aliasNames);
        log.info("Forwarding: {} from {} to {} → {}", s3Key, maskedSender, aliasNames, result);
        return result;
    }

    /**
     * The rewritten message and its recipients. {@code broadcast}: one send to all of
     * {@code recipients} (speaker alias, visible To + Cc); otherwise one private copy each.
     */
    private record Delivery(byte[] message, List<String> recipients, boolean broadcast) {
    }

    /** @return null when no recipient is left to send to */
    private Delivery prepare(byte[] raw, Plan plan, String senderName, String sender) {
        if (plan.speakerMode) {
            Set<String> to = new LinkedHashSet<>(plan.speakerTo);
            to.addAll(plan.individual);
            List<String> cc = plan.moderatorCc.stream().filter(m -> !to.contains(m)).toList();
            if (to.isEmpty()) {
                return null;
            }
            List<String> toList = List.copyOf(to);
            byte[] rewritten = rewriter.rewrite(raw, senderName, sender, properties.sender(), toList, cc);
            List<String> destinations = new ArrayList<>(toList);
            destinations.addAll(cc);
            return new Delivery(rewritten, List.copyOf(destinations), true);
        }
        if (plan.individual.isEmpty()) {
            return null;
        }
        byte[] rewritten = rewriter.rewrite(raw, senderName, sender, properties.sender(), null, null);
        return new Delivery(rewritten, List.copyOf(plan.individual), false);
    }

    private Plan plan(List<ForwardingAlias> aliases, String sender, MailAuthentication authentication) {
        Plan plan = new Plan();
        for (ForwardingAlias alias : aliases) {
            if (!authorizer.isAuthorized(alias, sender, authentication)) {
                log.warn("Forwarding: unauthorized sender {} for {}@", LoggingUtils.maskEmail(sender),
                        alias.localPart());
                continue;
            }
            plan.anyAuthorized = true;
            Set<String> resolved = resolver.resolve(alias);
            switch (alias.kind()) {
                case EVENT_SPEAKERS -> {
                    plan.speakerMode = true;
                    plan.speakerTo.addAll(resolved);
                    ForwardingAlias moderator = new ForwardingAlias(
                            alias.localPart().replace("-speaker", "-moderator"),
                            ForwardingAlias.Kind.EVENT_MODERATOR, alias.eventCode());
                    plan.moderatorCc.addAll(resolver.resolve(moderator));
                }
                case EVENT_MODERATOR -> {
                    // Standalone -moderator@ makes the moderator a real destination. Also feeds the
                    // Cc so a mail to both -speaker@ and -moderator@ still Cc's the moderator once.
                    plan.individual.addAll(resolved);
                    plan.moderatorCc.addAll(resolved);
                }
                default -> plan.individual.addAll(resolved);
            }
        }
        plan.dropReserved();
        return plan;
    }

    private Result sendBroadcast(String s3Key, Delivery delivery, String aliasNames) {
        try {
            emailService.sendRawMessage(delivery.message(), delivery.recipients(), properties.configurationSet());
        } catch (RuntimeException e) {
            log.error("Forwarding: speaker broadcast {} failed: {}", s3Key, e.toString());
            metrics.increment(ForwardingMetrics.FAILED);
            forwardingLog.complete(s3Key, EmailForwardingOutcome.FAILED, aliasNames, 0, delivery.recipients().size());
            return Result.FAILED;
        }
        metrics.increment(ForwardingMetrics.FORWARDED);
        forwardingLog.complete(s3Key, EmailForwardingOutcome.FORWARDED, aliasNames, delivery.recipients().size(), 0);
        return Result.FORWARDED;
    }

    private Result sendIndividually(String s3Key, Delivery delivery, String aliasNames) {
        List<String> recipients = delivery.recipients();
        int sent = 0;
        int failed = 0;
        for (int i = 0; i < recipients.size(); i++) {
            try {
                emailService.sendRawMessage(delivery.message(), List.of(recipients.get(i)),
                        properties.configurationSet());
                sent++;
            } catch (RuntimeException e) {
                failed++;
                log.error("Forwarding: copy of {} to {} failed: {}", s3Key,
                        LoggingUtils.maskEmail(recipients.get(i)), e.toString());
            }
            if (i < recipients.size() - 1) {
                pause();
            }
        }
        EmailForwardingOutcome outcome;
        Result result;
        if (failed == 0) {
            outcome = EmailForwardingOutcome.FORWARDED;
            result = Result.FORWARDED;
        } else if (sent == 0) {
            outcome = EmailForwardingOutcome.FAILED;
            result = Result.FAILED;
        } else {
            outcome = EmailForwardingOutcome.PARTIAL;
            result = Result.PARTIAL;
        }
        if (sent > 0) {
            metrics.increment(ForwardingMetrics.FORWARDED);
        }
        if (failed > 0) {
            metrics.increment(ForwardingMetrics.FAILED);
        }
        forwardingLog.complete(s3Key, outcome, aliasNames, sent, failed);
        return result;
    }

    private Result unresolved(String s3Key, String aliasNames) {
        log.warn("Forwarding: no recipients resolved for {}", aliasNames);
        metrics.increment(ForwardingMetrics.UNRESOLVED);
        forwardingLog.complete(s3Key, EmailForwardingOutcome.UNRESOLVED, aliasNames, 0, 0);
        return Result.UNRESOLVED;
    }

    private void pause() {
        if (properties.sendDelayMs() <= 0) {
            return;
        }
        try {
            Thread.sleep(properties.sendDelayMs());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static List<ForwardingAlias> addressedAliases(MimeMessage message, String domain) {
        Map<String, ForwardingAlias> aliases = new LinkedHashMap<>();
        for (String headerName : List.of("To", "Cc")) {
            String value = header(message, headerName);
            if (value == null) {
                continue;
            }
            InternetAddress[] parsed;
            try {
                parsed = InternetAddress.parseHeader(value, false);
            } catch (AddressException e) {
                continue;
            }
            for (InternetAddress address : parsed) {
                ForwardingAlias.parse(address.getAddress(), domain)
                        .ifPresent(alias -> aliases.putIfAbsent(alias.localPart(), alias));
            }
        }
        return List.copyOf(aliases.values());
    }

    private static InternetAddress firstAddress(String header) {
        if (header == null) {
            return null;
        }
        try {
            InternetAddress[] parsed = InternetAddress.parseHeader(header, false);
            return parsed.length > 0 ? parsed[0] : null;
        } catch (AddressException e) {
            return null;
        }
    }

    private static String header(MimeMessage message, String name) {
        try {
            return message.getHeader(name, ",");
        } catch (MessagingException e) {
            return null;
        }
    }

    private static String[] headers(MimeMessage message, String name) {
        try {
            return message.getHeader(name);
        } catch (MessagingException e) {
            return null;
        }
    }

    /** Recipients of one inbound mail, split by delivery mode. */
    private static final class Plan {
        private boolean anyAuthorized;
        private boolean speakerMode;
        private final Set<String> individual = new LinkedHashSet<>();
        private final Set<String> speakerTo = new LinkedHashSet<>();
        private final Set<String> moderatorCc = new LinkedHashSet<>();

        private void dropReserved() {
            int before = individual.size() + speakerTo.size() + moderatorCc.size();
            individual.removeIf(ReservedEmailDomain::isReserved);
            speakerTo.removeIf(ReservedEmailDomain::isReserved);
            moderatorCc.removeIf(ReservedEmailDomain::isReserved);
            int removed = before - individual.size() - speakerTo.size() - moderatorCc.size();
            if (removed > 0) {
                log.warn("Forwarding: skipped {} reserved-domain recipient(s)", removed);
            }
        }
    }
}
