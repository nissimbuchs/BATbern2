package ch.batbern.events.mailcenter;

import ch.batbern.events.client.UserApiClient;
import ch.batbern.events.domain.MailConversation;
import ch.batbern.events.dto.generated.users.UserResponse;
import ch.batbern.events.forwarding.EmailForwardingProperties;
import ch.batbern.events.repository.MailConversationRepository;
import ch.batbern.shared.exception.NotFoundException;
import ch.batbern.shared.exception.ValidationException;
import ch.batbern.shared.mail.ComposedMail;
import ch.batbern.shared.mail.MailContext;
import ch.batbern.shared.mail.MailTrigger;
import ch.batbern.shared.service.EmailService;
import ch.batbern.shared.util.ReservedEmailDomain;
import ch.batbern.shared.utils.EmailAddresses;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.HtmlUtils;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Story 16.6 AC1–AC3, AC6: an organizer answers a conversation by mail. The answer goes to the
 * counterpart from {@code "{name} via BATbern" <info@…>} (owner 2026-11-03, was noreply@) with Reply-To
 * {@code replies+{token}@…},
 * so the next reply threads back into the conversation; it is recorded through the 16.1a recorder
 * and linked to the conversation by its send group.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ConversationReplyService {

    static final int MAX_TEXT_LENGTH = 20_000;
    /** Answers come from info@: a reply typed to the sender by hand still reaches the Mail Center. */
    static final String FROM_LOCAL_PART = "info";
    private static final Pattern REPLY_PREFIX = Pattern.compile("^(re|aw)\\s*:", Pattern.CASE_INSENSITIVE);
    private static final DateTimeFormatter QUOTE_DATE =
            DateTimeFormatter.ofPattern("d.M.yyyy HH:mm", Locale.ROOT).withZone(ZoneId.of("Europe/Zurich"));

    private final MailConversationRepository conversations;
    private final NamedParameterJdbcTemplate jdbc;
    private final UserApiClient userApiClient;
    private final EmailService emailService;
    private final EmailForwardingProperties forwarding;

    /** The last inbound mail of a conversation: where the answer goes and what it quotes. */
    record LastInbound(String fromAddress, String fromName, String replyTo, String messageId, String references,
            String text, Instant receivedAt) {
    }

    /**
     * Sends the answer and records it in the conversation.
     *
     * @return the recorded mail's key
     * @throws NotFoundException    unknown conversation
     * @throws ValidationException  empty or too long text (400)
     * @throws IllegalStateException no counterpart address that can be mailed (409)
     */
    @Transactional
    public String reply(String conversationKey, String text, boolean markDone, String organizer) {
        MailConversation conversation = conversations.findByConversationKey(conversationKey)
                .orElseThrow(() -> new NotFoundException("Conversation", conversationKey));
        if (text == null || text.isBlank()) {
            throw new ValidationException("text", "The answer is empty");
        }
        if (text.length() > MAX_TEXT_LENGTH) {
            throw new ValidationException("text", "The answer is longer than " + MAX_TEXT_LENGTH + " characters");
        }
        Optional<LastInbound> last = lastInbound(conversation.getId());
        String to = last.map(l -> l.replyTo() != null ? l.replyTo() : l.fromAddress())
                .orElse(conversation.getCounterpartAddress());
        if (to == null || !EmailAddresses.isAcceptable(to) || ReservedEmailDomain.isReserved(to)) {
            throw new IllegalStateException("Conversation " + conversationKey + " has no address that can be mailed");
        }

        String domain = forwarding.domain();
        String messageId = "<" + UUID.randomUUID() + "@" + domain + ">";
        String inReplyTo = last.map(LastInbound::messageId).map(ConversationReplyService::bracket).orElse(null);
        List<String> references = new ArrayList<>();
        last.map(LastInbound::references).filter(r -> !r.isBlank())
                .ifPresent(r -> Arrays.stream(r.trim().split("\\s+")).map(ConversationReplyService::bracket)
                        .forEach(references::add));
        if (inReplyTo != null && !references.contains(inReplyTo)) {
            references.add(inReplyTo);
        }

        String trimmed = text.strip();
        ComposedMail mail = new ComposedMail(displayName(organizer), FROM_LOCAL_PART + "@" + domain, to,
                subject(conversation.getSubject()),
                html(trimmed, last), plain(trimmed, last), "replies+" + conversation.getReplyToken() + "@" + domain,
                messageId, inReplyTo, references);
        MailContext context = MailContext.ownText(MailTrigger.organizer(organizer), conversation.getEventCode(),
                conversation.getAudience());
        emailService.sendComposed(context, mail);

        jdbc.update("""
                UPDATE mail_message SET conversation_id = :conversationId, message_id_header = :messageId
                 WHERE send_group_id = :groupId
                """, new MapSqlParameterSource()
                .addValue("conversationId", conversation.getId())
                .addValue("messageId", messageId.substring(1, messageId.length() - 1))
                .addValue("groupId", context.sendGroupId()));
        Instant now = Instant.now();
        conversation.setStatus(markDone ? MailConversation.Status.DONE : MailConversation.Status.WAITING);
        conversation.setLastMessageAt(now);
        conversation.setUpdatedAt(now);
        conversation.setUpdatedBy(organizer);
        conversations.saveAndFlush(conversation);

        return jdbc.query("SELECT mail_key FROM mail_message WHERE send_group_id = :groupId",
                new MapSqlParameterSource("groupId", context.sendGroupId()), (rs, i) -> rs.getString(1))
                .stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("Answer to " + conversationKey + " was not recorded"));
    }

    private Optional<LastInbound> lastInbound(UUID conversationId) {
        return jdbc.query("""
                SELECT from_address, from_name, reply_to_address, message_id_header, references_header, body_text,
                       created_at
                  FROM mail_message WHERE conversation_id = :id AND direction = 'IN'
                 ORDER BY created_at DESC LIMIT 1
                """, new MapSqlParameterSource("id", conversationId),
                (rs, i) -> new LastInbound(rs.getString("from_address"), rs.getString("from_name"),
                        rs.getString("reply_to_address"), rs.getString("message_id_header"),
                        rs.getString("references_header"), rs.getString("body_text"),
                        rs.getTimestamp("created_at").toInstant()))
                .stream().findFirst();
    }

    /** {@code Anna Muster via BATbern}; the username when the user service cannot say. */
    private String displayName(String organizer) {
        String name = organizer;
        try {
            UserResponse user = userApiClient.getUserByUsername(organizer);
            String full = ((user.getFirstName() == null ? "" : user.getFirstName()) + " "
                    + (user.getLastName() == null ? "" : user.getLastName())).trim();
            if (!full.isEmpty()) {
                name = full;
            }
        } catch (RuntimeException e) {
            log.warn("Organizer name not available, answering as {}: {}", organizer, e.getMessage());
        }
        return name + " via BATbern";
    }

    static String subject(String subject) {
        String s = subject == null ? "" : subject.strip();
        return REPLY_PREFIX.matcher(s).find() ? s : "Re: " + s;
    }

    private static String bracket(String id) {
        return id.startsWith("<") ? id : "<" + id + ">";
    }

    private static String quoteHeader(LastInbound last) {
        String who = last.fromName() != null && !last.fromName().isBlank() ? last.fromName() : last.fromAddress();
        return "On " + QUOTE_DATE.format(last.receivedAt()) + ", " + who + " wrote:";
    }

    static String plain(String text, Optional<LastInbound> last) {
        StringBuilder body = new StringBuilder(text);
        last.filter(l -> l.text() != null && !l.text().isBlank()).ifPresent(l -> {
            body.append("\n\n").append(quoteHeader(l)).append('\n');
            for (String line : l.text().strip().split("\\R", -1)) {
                body.append(line.isEmpty() ? ">" : "> " + line).append('\n');
            }
        });
        return body.toString();
    }

    static String html(String text, Optional<LastInbound> last) {
        StringBuilder body = new StringBuilder("<div>")
                .append(HtmlUtils.htmlEscape(text).replaceAll("\\R", "<br>")).append("</div>");
        last.filter(l -> l.text() != null && !l.text().isBlank()).ifPresent(l -> body
                .append("<p>").append(HtmlUtils.htmlEscape(quoteHeader(l))).append("</p>")
                .append("<blockquote style=\"margin:0 0 0 .8ex;border-left:1px solid #ccc;padding-left:1ex\">")
                .append(HtmlUtils.htmlEscape(l.text().strip()).replaceAll("\\R", "<br>"))
                .append("</blockquote>"));
        return body.toString();
    }
}
