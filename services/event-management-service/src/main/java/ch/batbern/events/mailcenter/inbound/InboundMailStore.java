package ch.batbern.events.mailcenter.inbound;

import ch.batbern.events.domain.MailConversation;
import ch.batbern.events.repository.MailConversationRepository;
import ch.batbern.shared.mail.MailContext;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.HtmlUtils;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Writes one inbound mail and its conversation (Story 16.2a), in one transaction of its own: a mail
 * that turns out to be stored already (same Message-ID, AC4) leaves nothing behind, and a failure here
 * never marks a caller's transaction for rollback.
 */
@Component
@RequiredArgsConstructor
public class InboundMailStore {

    private static final String BASE32 = "abcdefghijklmnopqrstuvwxyz234567";
    private static final SecureRandom RANDOM = new SecureRandom();

    private static final String INSERT_MESSAGE = """
            INSERT INTO mail_message (mail_key, send_group_id, event_code, direction, audience, content_source,
                subject, body_html, recipient_count, conversation_id, from_address, from_name, to_addresses,
                message_id_header, in_reply_to, references_header, body_text, inbound_path, alias_local_part,
                attachments, reply_to_address)
            VALUES (COALESCE(:eventCode, 'general') || '-' || nextval('mail_message_seq'), :groupId, :eventCode,
                'IN', :audience, 'OWN_TEXT', :subject, :bodyHtml, 0, :conversationId, :fromAddress, :fromName,
                :toAddresses, :messageId, :inReplyTo, :references, :bodyText, :path, :aliasLocalPart,
                CAST(:attachments AS JSONB), :replyTo)
            ON CONFLICT (send_group_id) DO NOTHING
            """;

    private final NamedParameterJdbcTemplate jdbc;
    private final MailConversationRepository conversations;
    private final ObjectMapper objectMapper;

    /** A mail with this group id is stored already. */
    public boolean exists(String groupId) {
        Integer count = jdbc.queryForObject("SELECT count(*) FROM mail_message WHERE send_group_id = :groupId",
                new MapSqlParameterSource("groupId", groupId), Integer.class);
        return count != null && count > 0;
    }

    /**
     * Where a mail goes.
     *
     * @param conversationId the conversation it joins, or null to open one with the other fields
     * @param eventCode      event of a new conversation
     * @param audience       audience of a new conversation
     * @param assignee       assignee of a new conversation, or null
     */
    public record Placement(UUID conversationId, String eventCode, String audience, String assignee) {
    }

    /**
     * Stores the mail in the conversation of {@code placement}, or in a new one.
     *
     * @return the conversation key
     * @throws DuplicateInboundMailException when the mail was stored meanwhile (rolls back)
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String store(InboundMail mail, String groupId, String path, String aliasLocalPart, Placement placement) {
        MailConversation conversation = conversationFor(mail, placement, Instant.now());

        int inserted = jdbc.update(INSERT_MESSAGE, new MapSqlParameterSource()
                .addValue("eventCode", conversation.getEventCode())
                .addValue("groupId", groupId)
                .addValue("audience", conversation.getAudience().name())
                .addValue("subject", mail.subject())
                .addValue("bodyHtml", bodyHtml(mail))
                .addValue("conversationId", conversation.getId())
                .addValue("fromAddress", mail.fromAddress())
                .addValue("fromName", mail.fromName())
                .addValue("toAddresses", String.join(",", concat(mail)))
                .addValue("messageId", mail.messageId())
                .addValue("inReplyTo", mail.inReplyTo())
                .addValue("references", mail.references().isEmpty() ? null : String.join(" ", mail.references()))
                .addValue("bodyText", mail.text())
                .addValue("path", path)
                .addValue("aliasLocalPart", aliasLocalPart)
                .addValue("attachments", json(mail))
                .addValue("replyTo", mail.replyTo()));
        if (inserted == 0) {
            throw new DuplicateInboundMailException(groupId);
        }
        return conversation.getConversationKey();
    }

    /**
     * Story 16.7 AC3: a mail SES flagged as spam. Stored as inbound mail without event or conversation,
     * flagged, so it shows only in the Spam list. Attachments by name only (no file is kept).
     *
     * @return the mail key, or empty when it was stored already
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<String> storeSpam(InboundMail mail, String groupId, String path, String aliasLocalPart) {
        List<String> keys = jdbc.query("""
                INSERT INTO mail_message (mail_key, send_group_id, direction, audience, content_source, subject,
                    body_html, recipient_count, from_address, from_name, to_addresses, message_id_header,
                    in_reply_to, references_header, body_text, inbound_path, alias_local_part, attachments,
                    reply_to_address, is_spam)
                VALUES ('general-' || nextval('mail_message_seq'), :groupId, 'IN', 'PUBLIC', 'OWN_TEXT', :subject,
                    :bodyHtml, 0, :fromAddress, :fromName, :toAddresses, :messageId, :inReplyTo, :references,
                    :bodyText, :path, :aliasLocalPart, CAST(:attachments AS JSONB), :replyTo, TRUE)
                ON CONFLICT (send_group_id) DO NOTHING
                RETURNING mail_key
                """, new MapSqlParameterSource()
                .addValue("groupId", groupId)
                .addValue("subject", mail.subject())
                .addValue("bodyHtml", bodyHtml(mail))
                .addValue("fromAddress", mail.fromAddress())
                .addValue("fromName", mail.fromName())
                .addValue("toAddresses", String.join(",", concat(mail)))
                .addValue("messageId", mail.messageId())
                .addValue("inReplyTo", mail.inReplyTo())
                .addValue("references", mail.references().isEmpty() ? null : String.join(" ", mail.references()))
                .addValue("bodyText", mail.text())
                .addValue("path", path)
                .addValue("aliasLocalPart", aliasLocalPart)
                .addValue("attachments", json(mail))
                .addValue("replyTo", mail.replyTo()), (rs, i) -> rs.getString(1));
        return keys.stream().findFirst();
    }

    /**
     * Story 16.7 AC3 ("Not spam"): puts a stored spam mail where it belongs, as if it had just arrived:
     * into the conversation of {@code placement} or a new one, with the conversation's event and
     * audience; the flag is cleared. The mail key does not change.
     *
     * @return the conversation key
     */
    @Transactional
    public String release(UUID messageId, InboundMail mail, Placement placement) {
        MailConversation conversation = conversationFor(mail, placement, Instant.now());
        jdbc.update("""
                UPDATE mail_message SET is_spam = FALSE, conversation_id = :conversationId, event_code = :eventCode,
                       audience = :audience
                 WHERE id = :id
                """, new MapSqlParameterSource()
                .addValue("conversationId", conversation.getId())
                .addValue("eventCode", conversation.getEventCode())
                .addValue("audience", conversation.getAudience().name())
                .addValue("id", messageId));
        return conversation.getConversationKey();
    }

    /** The conversation of {@code placement}, or a new one; reopened and touched as by a new mail. */
    private MailConversation conversationFor(InboundMail mail, Placement placement, Instant now) {
        MailConversation conversation = placement.conversationId() != null
                ? conversations.findById(placement.conversationId()).orElseThrow()
                : open(mail, placement.eventCode(), placement.audience(), placement.assignee(), now);
        if (conversation.getStatus() != MailConversation.Status.OPEN) {
            conversation.setStatus(MailConversation.Status.OPEN);
        }
        conversation.setLastMessageAt(now);
        conversation.setLastInboundAt(now);
        conversation.setUpdatedAt(now);
        return conversations.saveAndFlush(conversation);
    }

    /** Thrown inside the transaction so a conversation opened for a duplicate is rolled back. */
    public static class DuplicateInboundMailException extends RuntimeException {
        DuplicateInboundMailException(String groupId) {
            super("Inbound mail " + groupId + " is stored already");
        }
    }

    private MailConversation open(InboundMail mail, String eventCode, String audience, String assignee,
            Instant now) {
        Long n = jdbc.queryForObject("SELECT nextval('mail_conversation_seq')", new MapSqlParameterSource(),
                Long.class);
        MailConversation conversation = new MailConversation();
        conversation.setId(UUID.randomUUID());
        conversation.setConversationKey("conv-" + n);
        conversation.setEventCode(eventCode);
        conversation.setAudience(MailContext.Audience.valueOf(audience));
        conversation.setSubject(mail.subject().isBlank() ? "(no subject)" : mail.subject());
        conversation.setCounterpartAddress(mail.fromAddress());
        conversation.setCounterpartName(mail.fromName());
        conversation.setStatus(MailConversation.Status.OPEN);
        conversation.setAssigneeUsername(assignee);
        conversation.setReplyToken(token());
        conversation.setCreatedAt(now);
        return conversation;
    }

    /** Sanitized HTML when the mail had no text part, otherwise the text, escaped. Never empty. */
    public static String bodyHtml(InboundMail mail) {
        if (mail.html() != null && !mail.html().isBlank()) {
            return mail.html();
        }
        return "<div style=\"white-space: pre-wrap\">" + HtmlUtils.htmlEscape(mail.text()) + "</div>";
    }

    private static List<String> concat(InboundMail mail) {
        List<String> all = new ArrayList<>(mail.to());
        all.addAll(mail.cc());
        return all;
    }

    private String json(InboundMail mail) {
        if (mail.attachments().isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(mail.attachments());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    /** 16 characters of lowercase base32 (80 random bits) for {@code replies+{token}@}. */
    static String token() {
        StringBuilder token = new StringBuilder(16);
        for (int i = 0; i < 16; i++) {
            token.append(BASE32.charAt(RANDOM.nextInt(BASE32.length())));
        }
        return token.toString();
    }
}
