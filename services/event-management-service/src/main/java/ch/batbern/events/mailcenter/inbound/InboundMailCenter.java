package ch.batbern.events.mailcenter.inbound;

import ch.batbern.events.domain.Event;
import ch.batbern.events.forwarding.CalendarReply;
import ch.batbern.events.forwarding.EmailForwardingProperties;
import ch.batbern.events.forwarding.EmailForwardingService;
import ch.batbern.events.forwarding.ForwardingAlias;
import ch.batbern.events.forwarding.ForwardingDirectory;
import ch.batbern.events.mailcenter.MailCenterMetrics;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.service.CalendarInviteUid;
import ch.batbern.events.service.InboundEmailRouter;
import ch.batbern.shared.mail.MailContext;
import ch.batbern.shared.utils.LoggingUtils;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The Mail Center's side of inbound mail (Story 16.2a): after the existing processing (keyword
 * router, forwarding) has run, an inbound mail is either stored in a conversation or counted.
 *
 * <p>Which human mail opens or joins a conversation (16.2a, widened by 16.2b): mail to
 * {@code batbern{N}-moderator@}, {@code info@}, {@code events@} or {@code support@}; every reply at
 * {@code replies@} the keyword router did not act on; a {@code replies+{token}@} reply. Not: organizer
 * sends to {@code -speaker@}, {@code -participants@}, {@code partner@}, or {@code ok@}. An organizer
 * is a counterpart only when writing to a contact alias. Without a match the conversation has no
 * event and audience {@code PUBLIC}. Automatic mail is counted per event, never stored.
 *
 * <p>Never throws: forwarding is the safety net until Story 16.6, so a Mail Center failure is
 * logged and counted ({@code MailCenterIngestFailed}) and the caller carries on.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class InboundMailCenter {

    static final String PATH_REPLIES = "REPLIES";
    static final String PATH_ALIAS = "ALIAS";
    private static final Pattern EVENT_CODE = Pattern.compile("BATbern\\d+");

    private final ConversationThreader threader;
    private final InboundMailStore store;
    private final AutomaticMailCounter counter;
    private final ForwardingDirectory directory;
    private final EventRepository events;
    private final EmailForwardingProperties forwarding;
    private final NamedParameterJdbcTemplate jdbc;
    private final MailCenterMetrics metrics;

    /**
     * A mail to {@code replies@}, after the keyword router and forwarding.
     *
     * @param routeResult what the router did; null for an iCal reply, which the router never sees
     */
    public void ingestReply(String s3Key, byte[] raw, InboundEmailRouter.RouteResult routeResult) {
        safely(s3Key, () -> ingest(s3Key, raw, PATH_REPLIES, routeResult));
    }

    /** A mail to one or more aliases, after {@link EmailForwardingService#forward}. */
    public void ingestAlias(String s3Key, byte[] raw, EmailForwardingService.Result forwardResult) {
        safely(s3Key, () -> ingest(s3Key, raw, PATH_ALIAS, null));
    }

    /**
     * Story 16.6 AC4: whether the mail is addressed to {@code replies+{token}@} with the token of an
     * existing conversation. Such a reply skips the keyword router. Counted per path
     * ({@code ConversationReplies}). Never throws: when in doubt, false, and the mail is handled
     * like any other {@code replies@} mail.
     *
     * @param path {@code replies} or {@code alias}, the SES rule that received it
     */
    public boolean isConversationReply(byte[] raw, String path) {
        try {
            InboundMail mail = InboundMailParser.parse(raw);
            List<String> addresses = new ArrayList<>(mail.to());
            addresses.addAll(mail.cc());
            boolean known = threader.byToken(addresses, forwarding.domain()).isPresent();
            if (known) {
                metrics.increment(MailCenterMetrics.CONVERSATION_REPLIES, "path", path);
            }
            return known;
        } catch (RuntimeException e) {
            log.warn("Mail Center: could not check for a conversation token: {}", e.toString());
            return false;
        }
    }

    private void safely(String s3Key, Runnable work) {
        try {
            work.run();
        } catch (InboundMailStore.DuplicateInboundMailException e) {
            log.info("Mail Center: {} stored already", s3Key);
        } catch (RuntimeException e) {
            log.error("Mail Center: inbound mail {} not recorded: {}", s3Key, e.toString(), e);
            metrics.increment(MailCenterMetrics.INGEST_FAILED);
        }
    }

    private void ingest(String s3Key, byte[] raw, String path, InboundEmailRouter.RouteResult routeResult) {
        InboundMail mail = InboundMailParser.parse(raw);
        String groupId = groupId(mail, s3Key);
        String domain = forwarding.domain();

        if (routeResult != null && routeResult.acted()) {
            counter.count(keywordKind(routeResult), eventInSubject(mail.subject()), groupId);
            return;
        }
        List<String> addresses = new ArrayList<>(mail.to());
        addresses.addAll(mail.cc());
        List<ForwardingAlias> aliases = addresses.stream()
                .map(address -> ForwardingAlias.parse(address, domain))
                .flatMap(Optional::stream)
                .toList();
        Optional<ForwardingAlias> moderator = aliases.stream()
                .filter(alias -> alias.kind() == ForwardingAlias.Kind.EVENT_MODERATOR)
                .findFirst();
        Optional<ForwardingAlias> contact = aliases.stream().filter(InboundMailCenter::isContactAlias).findFirst();
        String aliasEvent = moderator.map(ForwardingAlias::eventCode).orElse(null);

        InboundClassifier.Kind kind = InboundClassifier.classify(mail, raw, domain);
        if (kind != InboundClassifier.Kind.HUMAN) {
            countAutomatic(kind, mail, raw, groupId, aliasEvent);
            return;
        }
        if (mail.fromAddress() == null || store.exists(groupId)) {
            return;
        }

        Optional<ConversationThreader.Target> token = threader.byToken(addresses, domain);
        boolean replyPath = PATH_REPLIES.equals(path);
        if (token.isEmpty() && !replyPath && moderator.isEmpty() && contact.isEmpty()) {
            return; // 16.2b: organizer sends (-speaker@, -participants@, partner@) and ok@ open nothing
        }
        boolean organizer = directory.organizerEmails().contains(mail.fromAddress());
        if (organizer && contact.isEmpty()) {
            return; // 16.2a AC6 / 16.2b AC6: an organizer is a counterpart only on a contact alias
        }

        ConversationThreader.Target t = token
                .or(() -> threader.byHeaders(mail))
                .or(() -> Optional.ofNullable(aliasEvent).map(event -> new ConversationThreader.Target(null, event,
                        MailContext.Audience.SPEAKERS.name(), MailCenterMetrics.THREAD_MATCHED_BY_ALIAS)))
                .or(() -> threader.bySubject(mail.subject()))
                .orElseGet(() -> new ConversationThreader.Target(null, null, null,
                        MailCenterMetrics.THREAD_MATCHED_NONE));
        String eventCode = t.eventCode();
        String audience = t.audience() != null ? t.audience() : MailContext.Audience.PUBLIC.name();
        UUID conversationId = t.conversationId() != null ? t.conversationId()
                : threader.openConversationWith(eventCode, mail.fromAddress()).orElse(null);
        String assignee = conversationId != null || eventCode == null
                || !MailContext.Audience.SPEAKERS.name().equals(audience) ? null
                : events.findByEventCode(eventCode).map(Event::getOrganizerUsername)
                        .filter(username -> !username.isBlank()).orElse(null);
        String aliasLocalPart = moderator.or(() -> contact).map(ForwardingAlias::localPart).orElse(null);
        String key = store.store(mail, groupId, path, aliasLocalPart,
                new InboundMailStore.Placement(conversationId, eventCode, audience, assignee));
        metrics.increment(t.metric());
        log.info("Mail Center: mail from {} stored in {} ({})", LoggingUtils.maskEmail(mail.fromAddress()), key,
                t.metric());
    }

    /** {@code info@}, {@code events@}, {@code support@}: open to anyone (not {@code ok@}). */
    private static boolean isContactAlias(ForwardingAlias alias) {
        return (alias.kind() == ForwardingAlias.Kind.ORGANIZERS || alias.kind() == ForwardingAlias.Kind.SUPPORT)
                && alias.openToAnyone();
    }

    private void countAutomatic(InboundClassifier.Kind kind, InboundMail mail, byte[] raw, String groupId,
            String aliasEvent) {
        if (kind == InboundClassifier.Kind.CALENDAR_REPLY) {
            calendar(raw).ifPresent(reply -> counter.count(calendarKind(reply.partStat()),
                    registrationEvent(reply.uid()), groupId));
            return;
        }
        String eventCode = threader.byHeaders(mail).map(ConversationThreader.Target::eventCode).orElse(aliasEvent);
        AutomaticMailCounter.Kind counted = switch (kind) {
            case OUT_OF_OFFICE -> AutomaticMailCounter.Kind.OUT_OF_OFFICE;
            case BOUNCE -> AutomaticMailCounter.Kind.BOUNCE;
            default -> AutomaticMailCounter.Kind.LOOP;
        };
        counter.count(counted, eventCode, groupId);
    }

    private static Optional<CalendarReply> calendar(byte[] raw) {
        try {
            return CalendarReply.parse(new MimeMessage(Session.getInstance(new Properties()),
                    new ByteArrayInputStream(raw)));
        } catch (MessagingException e) {
            return Optional.empty();
        }
    }

    /** The event of the registration whose invite UID ({@code reg-{calendarUid}@}) the reply names. */
    private String registrationEvent(String uid) {
        return CalendarInviteUid.parse(uid)
                .flatMap(calendarUid -> jdbc.query("""
                        SELECT e.event_code FROM registrations r JOIN events e ON e.id = r.event_id
                         WHERE r.calendar_uid = :uid
                        """, new MapSqlParameterSource("uid", calendarUid), (rs, i) -> rs.getString(1))
                        .stream().findFirst())
                .orElse(null);
    }

    private static AutomaticMailCounter.Kind calendarKind(String partStat) {
        return switch (partStat == null ? "" : partStat) {
            case "ACCEPTED" -> AutomaticMailCounter.Kind.CALENDAR_ACCEPTED;
            case "DECLINED" -> AutomaticMailCounter.Kind.CALENDAR_DECLINED;
            default -> AutomaticMailCounter.Kind.CALENDAR_TENTATIVE;
        };
    }

    private static AutomaticMailCounter.Kind keywordKind(InboundEmailRouter.RouteResult result) {
        return switch (result) {
            case CONFIRMED -> AutomaticMailCounter.Kind.KEYWORD_ACCEPT;
            case DEREGISTRATION_LINK -> AutomaticMailCounter.Kind.KEYWORD_CANCEL;
            default -> AutomaticMailCounter.Kind.UNSUBSCRIBE;
        };
    }

    private static String eventInSubject(String subject) {
        Matcher m = EVENT_CODE.matcher(subject == null ? "" : subject);
        return m.find() ? m.group() : null;
    }

    /**
     * AC4: one key per mail, whichever alias or path it arrived on: the Message-ID header, else the
     * S3 key. {@code in_} + 40 hex fits {@code send_group_id} and {@code dedupe_key}.
     */
    static String groupId(InboundMail mail, String s3Key) {
        String basis = mail.messageId() != null ? mail.messageId() : s3Key;
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(basis.getBytes(StandardCharsets.UTF_8));
            return "in_" + HexFormat.of().formatHex(hash).substring(0, 40);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
