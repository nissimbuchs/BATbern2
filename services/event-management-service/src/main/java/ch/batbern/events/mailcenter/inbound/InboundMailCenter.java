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
import ch.batbern.events.forwarding.InboundEmailRouter;
import ch.batbern.shared.mail.MailContext;
import ch.batbern.shared.exception.NotFoundException;
import ch.batbern.shared.exception.ValidationException;
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
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;
import java.util.function.Supplier;
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
 * <p>{@code ingestReply}/{@code ingestAlias} never throw: while forwarding is the safety net, a Mail
 * Center failure is logged and counted ({@code MailCenterIngestFailed}) and the caller carries on.
 * Story 16.6: with a group switched to the Mail Center, the {@code *Strict} variants run first and
 * throw, so the mail is retried instead of being notified without being stored.
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
    private final InboundAttachmentStore attachments;
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
     * What a stored mail became, for the notification that replaces its forwarded copy (Story 16.6).
     *
     * @param eventCode the conversation's event, or null
     */
    public record Stored(String conversationKey, String eventCode, String fromName, String subject) {
    }

    /**
     * Story 16.6 AC9: with a group switched on, the mail is stored before anything is sent. Same
     * rules as {@link #ingestReply}, but a failure is thrown, so SQS retries and dead-letters.
     *
     * @return the conversation it was stored in (also when it was stored already); empty when the
     *         mail was not stored (automatic mail, out of scope)
     */
    public Optional<Stored> ingestReplyStrict(String s3Key, byte[] raw, InboundEmailRouter.RouteResult routeResult) {
        return strictly(s3Key, raw, () -> ingest(s3Key, raw, PATH_REPLIES, routeResult));
    }

    /** As {@link #ingestReplyStrict}, for mail to the aliases. */
    public Optional<Stored> ingestAliasStrict(String s3Key, byte[] raw) {
        return strictly(s3Key, raw, () -> ingest(s3Key, raw, PATH_ALIAS, null));
    }

    private Optional<Stored> strictly(String s3Key, byte[] raw, Supplier<Optional<Stored>> work) {
        try {
            return work.get();
        } catch (InboundMailStore.DuplicateInboundMailException e) {
            return storedFor(groupId(InboundMailParser.parse(raw), s3Key));
        } catch (RuntimeException e) {
            metrics.increment(MailCenterMetrics.INGEST_FAILED);
            throw e;
        }
    }

    /** The conversation a stored mail belongs to, by its group id. */
    private Optional<Stored> storedFor(String groupId) {
        return jdbc.query("""
                SELECT c.conversation_key, c.event_code, m.from_name, m.subject
                  FROM mail_message m JOIN mail_conversation c ON c.id = m.conversation_id
                 WHERE m.send_group_id = :groupId
                """, new MapSqlParameterSource("groupId", groupId),
                (rs, i) -> new Stored(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4)))
                .stream().findFirst();
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

    private void safely(String s3Key, Supplier<Optional<Stored>> work) {
        try {
            work.get();
        } catch (InboundMailStore.DuplicateInboundMailException e) {
            log.info("Mail Center: {} stored already", s3Key);
        } catch (RuntimeException e) {
            log.error("Mail Center: inbound mail {} not recorded: {}", s3Key, e.toString(), e);
            metrics.increment(MailCenterMetrics.INGEST_FAILED);
        }
    }

    private Optional<Stored> ingest(String s3Key, byte[] raw, String path,
            InboundEmailRouter.RouteResult routeResult) {
        InboundMail mail = InboundMailParser.parse(raw);
        String groupId = groupId(mail, s3Key);
        String domain = forwarding.domain();

        if (routeResult != null && routeResult.acted()) {
            counter.count(keywordKind(routeResult), eventInSubject(mail.subject()), groupId);
            return Optional.empty();
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
        if (kind == InboundClassifier.Kind.VIRUS) {
            // Story 16.7 AC4: never stored; the S3 key is the only trace
            log.warn("Mail Center: virus verdict on {}, not stored", s3Key);
            counter.count(AutomaticMailCounter.Kind.VIRUS, aliasEvent, groupId);
            return Optional.empty();
        }
        if (kind == InboundClassifier.Kind.SPAM) {
            // Story 16.7 AC3: Spam list only, no conversation, not open
            Optional<String> key = store.storeSpam(mail, groupId, path,
                    moderator.or(() -> contact).map(ForwardingAlias::localPart).orElse(null));
            if (key.isPresent()) {
                counter.count(AutomaticMailCounter.Kind.SPAM, aliasEvent, groupId);
                log.info("Mail Center: spam from {} stored as {}", LoggingUtils.maskEmail(mail.fromAddress()),
                        key.get());
            }
            return Optional.empty();
        }
        if (kind != InboundClassifier.Kind.HUMAN) {
            countAutomatic(kind, mail, raw, groupId, aliasEvent);
            return Optional.empty();
        }
        if (mail.fromAddress() == null) {
            return Optional.empty();
        }
        if (store.exists(groupId)) {
            return storedFor(groupId);
        }

        Optional<ConversationThreader.Target> token = threader.byToken(addresses, domain);
        boolean replyPath = PATH_REPLIES.equals(path);
        if (token.isEmpty() && !replyPath && moderator.isEmpty() && contact.isEmpty()) {
            // 16.2b: organizer sends (-speaker@, -participants@, partner@) and ok@ open nothing
            return Optional.empty();
        }
        boolean organizer = directory.organizerEmails().contains(mail.fromAddress());
        if (organizer && contact.isEmpty()) {
            return Optional.empty(); // 16.2a AC6 / 16.2b AC6: an organizer is a counterpart only on a contact alias
        }

        Placed placed = place(mail, token, aliasEvent);
        String eventCode = placed.placement().eventCode();
        String aliasLocalPart = moderator.or(() -> contact).map(ForwardingAlias::localPart).orElse(null);
        InboundMail kept = mail.attachments().isEmpty() || !attachments.enabled() ? mail
                : mail.withAttachments(attachments.store(groupId, mail.attachments(),
                        InboundMailParser.attachmentContents(raw)));
        String key = store.store(kept, groupId, path, aliasLocalPart, placed.placement());
        ConversationThreader.Target t = placed.target();
        metrics.increment(t.metric());
        log.info("Mail Center: mail from {} stored in {} ({})", LoggingUtils.maskEmail(mail.fromAddress()), key,
                t.metric());
        return Optional.of(new Stored(key, eventCode != null ? eventCode : eventOf(key), mail.fromName(),
                mail.subject()));
    }

    /** Where a human mail goes, by the 16.2a/16.2b rules after the token: headers, alias, subject, none. */
    private record Placed(ConversationThreader.Target target, InboundMailStore.Placement placement) {
    }

    private Placed place(InboundMail mail, Optional<ConversationThreader.Target> token, String aliasEvent) {
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
        return new Placed(t, new InboundMailStore.Placement(conversationId, eventCode, audience, assignee));
    }

    /**
     * Story 16.7 AC3 ("Not spam"): threads a stored spam mail like a new arrival, from the headers it
     * was stored with, so it lands in an event's conversation or in "No event". Sends nothing.
     *
     * @return the conversation key
     * @throws NotFoundException   no such mail
     * @throws ValidationException the mail is not spam
     */
    public String releaseSpam(String mailKey) {
        List<SpamRow> rows = jdbc.query("""
                SELECT id, is_spam, from_address, from_name, reply_to_address, to_addresses, subject, body_text,
                       message_id_header, in_reply_to, references_header, alias_local_part
                  FROM mail_message WHERE mail_key = :key
                """, new MapSqlParameterSource("key", mailKey), (rs, i) -> new SpamRow(rs.getObject("id", UUID.class),
                rs.getBoolean("is_spam"), new InboundMail(rs.getString("message_id_header"),
                rs.getString("in_reply_to"), splitOn(rs.getString("references_header"), " "),
                rs.getString("from_address"), rs.getString("from_name"), rs.getString("reply_to_address"),
                splitOn(rs.getString("to_addresses"), ","), List.of(), rs.getString("subject"),
                rs.getString("body_text"), null, null, null, null, null, null, null, null, false),
                rs.getString("alias_local_part")));
        SpamRow row = rows.stream().findFirst()
                .orElseThrow(() -> new NotFoundException("Mail", mailKey));
        if (row.mail().fromAddress() == null) {
            throw new ValidationException("spam", "Mail has no sender to answer: " + mailKey);
        }
        if (!row.spam()) {
            throw new ValidationException("spam", "Mail is not flagged as spam: " + mailKey);
        }
        String domain = forwarding.domain();
        String aliasEvent = row.mail().to().stream()
                .map(address -> ForwardingAlias.parse(address, domain))
                .flatMap(Optional::stream)
                .filter(alias -> alias.kind() == ForwardingAlias.Kind.EVENT_MODERATOR)
                .map(ForwardingAlias::eventCode)
                .findFirst().orElse(null);
        Placed placed = place(row.mail(), threader.byToken(row.mail().to(), domain), aliasEvent);
        String key = store.release(row.id(), row.mail(), placed.placement());
        log.info("Mail Center: {} is not spam, now in {}", mailKey, key);
        return key;
    }

    private record SpamRow(UUID id, boolean spam, InboundMail mail, String aliasLocalPart) {
    }

    private static List<String> splitOn(String value, String separator) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return Arrays.stream(value.split(separator)).map(String::trim).filter(v -> !v.isEmpty()).toList();
    }

    /** The conversation's event, or null for a conversation without one. */
    private String eventOf(String conversationKey) {
        // filter, not findFirst alone: a no-event row maps to null, and findFirst throws on a null element
        return jdbc.query("SELECT event_code FROM mail_conversation WHERE conversation_key = :key",
                new MapSqlParameterSource("key", conversationKey), (rs, i) -> rs.getString(1))
                .stream().filter(Objects::nonNull).findFirst().orElse(null);
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
            case SPAM -> AutomaticMailCounter.Kind.SPAM;
            case VIRUS -> AutomaticMailCounter.Kind.VIRUS;
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
