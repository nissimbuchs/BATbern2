package ch.batbern.events.mailcenter.inbound;

import ch.batbern.events.mailcenter.MailCenterMetrics;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Finds where an inbound mail belongs (Story 16.2a AC3), first hit wins:
 * <ol>
 *   <li>the conversation token in {@code replies+{token}@};</li>
 *   <li>{@code In-Reply-To}, then {@code References} newest first, against the stored ids: the full
 *       id against {@code mail_message.message_id_header} (inbound mail) and
 *       {@code mail_recipient.message_id_header} (captured from the SES event, 16.1b), and its local
 *       part against {@code mail_recipient.ses_message_id}, because SES writes the header as
 *       {@code <{MessageId}@{region}.amazonses.com>} (verified 2026-11-02 on SendEmail and
 *       SendRawEmail mail);</li>
 *   <li>the event of the {@code batbern{N}-moderator@} alias the mail was addressed to.</li>
 * </ol>
 * Reads only; {@link InboundMailStore} writes.
 */
@Component
@RequiredArgsConstructor
public class ConversationThreader {

    private static final Pattern TOKEN_ADDRESS = Pattern.compile("^replies\\+([a-z2-7]{16})@(.+)$");

    /** The stored mail an id names: its conversation (if any), event and audience. */
    private static final String BY_MESSAGE_HEADER = """
            SELECT conversation_id, event_code, audience FROM mail_message
             WHERE message_id_header = :id ORDER BY created_at DESC LIMIT 1
            """;
    private static final String BY_RECIPIENT_HEADER = """
            SELECT m.conversation_id, m.event_code, m.audience FROM mail_recipient r
              JOIN mail_message m ON m.id = r.mail_message_id
             WHERE r.message_id_header = :id ORDER BY r.created_at DESC LIMIT 1
            """;
    private static final String BY_SES_ID = """
            SELECT m.conversation_id, m.event_code, m.audience FROM mail_recipient r
              JOIN mail_message m ON m.id = r.mail_message_id
             WHERE r.ses_message_id = :id ORDER BY r.created_at DESC LIMIT 1
            """;

    private final NamedParameterJdbcTemplate jdbc;

    /**
     * Where a mail belongs.
     *
     * @param conversationId the conversation it joins, or null: open or join by counterpart in {@code eventCode}
     * @param eventCode      the event, or null
     * @param audience       the audience of the matched mail, or null when matched by alias
     * @param metric         the {@link MailCenterMetrics} name of the rule that matched
     */
    public record Target(UUID conversationId, String eventCode, String audience, String metric) {
    }

    /** Rule 1: {@code replies+{token}@{domain}} among the addresses. */
    public Optional<Target> byToken(List<String> addresses, String domain) {
        for (String address : addresses) {
            Matcher m = TOKEN_ADDRESS.matcher(address.toLowerCase(Locale.ROOT));
            if (m.matches() && m.group(2).equalsIgnoreCase(domain)) {
                List<Target> hits = jdbc.query(
                        "SELECT id, event_code, audience FROM mail_conversation WHERE reply_token = :token",
                        new MapSqlParameterSource("token", m.group(1)),
                        (rs, i) -> new Target(rs.getObject("id", UUID.class), rs.getString("event_code"),
                                rs.getString("audience"), MailCenterMetrics.THREAD_MATCHED_BY_TOKEN));
                if (!hits.isEmpty()) {
                    return Optional.of(hits.get(0));
                }
            }
        }
        return Optional.empty();
    }

    /** Rule 2: the first id of In-Reply-To, then References newest first, that names a stored mail. */
    public Optional<Target> byHeaders(InboundMail mail) {
        for (String id : candidateIds(mail)) {
            Optional<Target> hit = first(BY_MESSAGE_HEADER, id, MailCenterMetrics.THREAD_MATCHED_BY_HEADER)
                    .or(() -> first(BY_RECIPIENT_HEADER, id, MailCenterMetrics.THREAD_MATCHED_BY_HEADER))
                    .or(() -> first(BY_SES_ID, localPart(id), MailCenterMetrics.THREAD_MATCHED_BY_SES_ID));
            if (hit.isPresent()) {
                return hit;
            }
        }
        return Optional.empty();
    }

    /** Within an event: the OPEN or WAITING conversation with this counterpart, newest first. */
    public Optional<UUID> openConversationWith(String eventCode, String counterpart) {
        List<UUID> ids = jdbc.query("""
                SELECT id FROM mail_conversation
                 WHERE event_code = :eventCode AND counterpart_address = :counterpart
                   AND status IN ('OPEN', 'WAITING')
                 ORDER BY last_message_at DESC LIMIT 1
                """, new MapSqlParameterSource().addValue("eventCode", eventCode).addValue("counterpart", counterpart),
                (rs, i) -> rs.getObject("id", UUID.class));
        return ids.stream().findFirst();
    }

    static List<String> candidateIds(InboundMail mail) {
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        if (mail.inReplyTo() != null) {
            ids.add(mail.inReplyTo());
        }
        List<String> references = new ArrayList<>(mail.references());
        for (int i = references.size() - 1; i >= 0; i--) {
            ids.add(references.get(i));
        }
        return List.copyOf(ids);
    }

    private static String localPart(String id) {
        int at = id.indexOf('@');
        return at < 0 ? id : id.substring(0, at);
    }

    private Optional<Target> first(String sql, String id, String metric) {
        List<Target> hits = jdbc.query(sql, new MapSqlParameterSource("id", id),
                (rs, i) -> new Target(rs.getObject("conversation_id", UUID.class), rs.getString("event_code"),
                        rs.getString("audience"), metric));
        return hits.stream().findFirst();
    }
}
