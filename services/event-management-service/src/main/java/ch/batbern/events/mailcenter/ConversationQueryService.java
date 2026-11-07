package ch.batbern.events.mailcenter;

import ch.batbern.events.domain.MailMessage;
import ch.batbern.events.forwarding.EmailForwardingProperties;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.shared.exception.NotFoundException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Story 16.2a: the conversations of an event ("Needs an answer") and one conversation with its
 * messages. Native SQL like {@link MailMessageQueryService}.
 */
@Service
@RequiredArgsConstructor
public class ConversationQueryService {

    /** Each conversation with its message count and where its first mail arrived. */
    private static final String SELECT = """
            SELECT c.id, c.conversation_key, c.event_code, c.audience, c.subject, c.counterpart_address,
                   c.counterpart_name, c.status, c.assignee_username, c.last_message_at, c.merge_request_id,
                   c.logo_request_id,
                   (SELECT count(*) FROM mail_message m WHERE m.conversation_id = c.id) AS message_count,
                   (SELECT m.alias_local_part FROM mail_message m WHERE m.conversation_id = c.id
                     ORDER BY m.created_at LIMIT 1) AS first_alias
              FROM mail_conversation c
            """;

    private final NamedParameterJdbcTemplate jdbc;
    private final EventRepository events;
    private final ObjectMapper objectMapper;
    private final EmailForwardingProperties forwarding;

    /** One conversation as listed. {@code via} is the alias address, or {@code reply}. */
    public record ConversationEntry(UUID id, String conversationKey, String eventCode, String audience,
            String subject, String counterpartAddress, String counterpartName, String status, String assigneeUsername,
            String via, Instant lastMessageAt, int messageCount, UUID mergeRequestId, UUID logoRequestId) {
    }

    public record ConversationPage(List<ConversationEntry> data, long total) {
    }

    /** One message of a conversation, inbound or (from 16.6) outbound. */
    public record ConversationMessage(String mailKey, Instant sentAt, String direction, String senderUsername,
            String audience, String templateKey, String contentSource, String subject, String bodyHtml,
            String fromAddress, String fromName, String eventCode, int recipientCount,
            List<MailMessage.Attachment> attachments) {
    }

    public record ConversationDetail(ConversationEntry conversation, List<ConversationMessage> messages) {
    }

    /** OPEN and WAITING first, then DONE; newest activity first within each. */
    public ConversationPage list(String eventCode, String status, int page, int limit) {
        if (events.findByEventCode(eventCode).isEmpty()) {
            throw new NotFoundException("Event", eventCode);
        }
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("eventCode", eventCode)
                .addValue("status", status)
                .addValue("limit", limit)
                .addValue("offset", (long) (page - 1) * limit);
        String where = " WHERE c.event_code = :eventCode AND (CAST(:status AS TEXT) IS NULL OR c.status = :status)";
        Long total = jdbc.queryForObject("SELECT count(*) FROM mail_conversation c" + where, params, Long.class);
        List<ConversationEntry> data = jdbc.query(SELECT + where
                + " ORDER BY CASE WHEN c.status = 'DONE' THEN 1 ELSE 0 END, c.last_message_at DESC"
                + " LIMIT :limit OFFSET :offset", params, this::entry);
        return new ConversationPage(data, total == null ? 0 : total);
    }

    /**
     * Story 16.7: conversations of all events and without event, same order as {@link #list}.
     *
     * @param statuses only these statuses; empty for all
     * @param eventCode only this event, or null
     * @param noEvent  true: only without event; false: only with one; null: both
     */
    public ConversationPage listAll(List<String> statuses, String eventCode, Boolean noEvent, int page, int limit) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("statuses", statuses.isEmpty() ? List.of("") : statuses)
                .addValue("anyStatus", statuses.isEmpty())
                .addValue("eventCode", eventCode)
                .addValue("noEvent", noEvent)
                .addValue("limit", limit)
                .addValue("offset", (long) (page - 1) * limit);
        String where = """
                 WHERE (:anyStatus OR c.status IN (:statuses))
                   AND (CAST(:eventCode AS TEXT) IS NULL OR c.event_code = :eventCode)
                   AND (CAST(:noEvent AS BOOLEAN) IS NULL OR (c.event_code IS NULL) = CAST(:noEvent AS BOOLEAN))
                """;
        Long total = jdbc.queryForObject("SELECT count(*) FROM mail_conversation c" + where, params, Long.class);
        List<ConversationEntry> data = jdbc.query(SELECT + where
                + " ORDER BY CASE WHEN c.status = 'DONE' THEN 1 ELSE 0 END, c.last_message_at DESC"
                + " LIMIT :limit OFFSET :offset", params, this::entry);
        return new ConversationPage(data, total == null ? 0 : total);
    }

    /** Story 16.7 AC2: badge and section counts across all events. */
    public record ConversationCounts(int open, int waiting, int noEvent, int spam) {
    }

    public ConversationCounts counts() {
        return jdbc.queryForObject("""
                SELECT (SELECT count(*) FROM mail_conversation WHERE status = 'OPEN') AS open,
                       (SELECT count(*) FROM mail_conversation WHERE status = 'WAITING') AS waiting,
                       (SELECT count(*) FROM mail_conversation
                         WHERE event_code IS NULL AND status <> 'DONE') AS no_event,
                       (SELECT count(*) FROM mail_message WHERE is_spam) AS spam
                """, new MapSqlParameterSource(), (rs, i) -> new ConversationCounts(rs.getInt("open"),
                rs.getInt("waiting"), rs.getInt("no_event"), rs.getInt("spam")));
    }

    public Optional<ConversationEntry> find(String conversationKey) {
        return jdbc.query(SELECT + " WHERE c.conversation_key = :key",
                new MapSqlParameterSource("key", conversationKey), this::entry).stream().findFirst();
    }

    /** The conversation and every message, oldest first. */
    public Optional<ConversationDetail> get(String conversationKey) {
        return find(conversationKey).map(conversation -> new ConversationDetail(conversation, jdbc.query("""
                SELECT mail_key, created_at, direction, sender_username, audience, template_key, content_source,
                       subject, body_html, from_address, from_name, event_code, recipient_count,
                       CAST(attachments AS TEXT) AS attachments
                  FROM mail_message WHERE conversation_id = :id ORDER BY created_at, mail_key
                """, new MapSqlParameterSource("id", conversation.id()), this::message)));
    }

    private ConversationEntry entry(ResultSet rs, int row) throws SQLException {
        String alias = rs.getString("first_alias");
        return new ConversationEntry(rs.getObject("id", UUID.class), rs.getString("conversation_key"),
                rs.getString("event_code"), rs.getString("audience"), rs.getString("subject"),
                rs.getString("counterpart_address"), rs.getString("counterpart_name"), rs.getString("status"),
                rs.getString("assignee_username"),
                alias != null ? alias + "@" + forwarding.domain() : "reply",
                rs.getTimestamp("last_message_at").toInstant(), rs.getInt("message_count"),
                rs.getObject("merge_request_id", UUID.class), rs.getObject("logo_request_id", UUID.class));
    }

    private ConversationMessage message(ResultSet rs, int row) throws SQLException {
        return new ConversationMessage(rs.getString("mail_key"), rs.getTimestamp("created_at").toInstant(),
                rs.getString("direction"), rs.getString("sender_username"), rs.getString("audience"),
                rs.getString("template_key"), rs.getString("content_source"), rs.getString("subject"),
                rs.getString("body_html"), rs.getString("from_address"), rs.getString("from_name"),
                rs.getString("event_code"), rs.getInt("recipient_count"), attachments(rs.getString("attachments")));
    }

    private List<MailMessage.Attachment> attachments(String json) {
        if (json == null) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<MailMessage.Attachment>>() { });
        } catch (JsonProcessingException e) {
            return List.of();
        }
    }
}
