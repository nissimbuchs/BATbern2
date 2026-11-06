package ch.batbern.events.mailcenter;

import ch.batbern.events.mailcenter.inbound.InboundMail;
import ch.batbern.events.mailcenter.inbound.InboundMailStore;
import ch.batbern.shared.mail.MailContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * #1237: the Mail Center conversation in which organizers decide a profile merge request from
 * company-user-management. It opens with one inbound message from the requester (their note), has
 * no event and the audience SYSTEM, and carries {@code merge_request_id} so the Mail Center shows the
 * request with approve and decline. Answering in it mails the requester like any conversation.
 * Both operations are idempotent.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MergeRequestConversations {

    private final InboundMailStore store;
    private final NamedParameterJdbcTemplate jdbc;

    /** Opens the conversation unless it exists; returns its key. */
    public String open(UUID requestId, String requesterName, String requesterEmail, String subject, String note) {
        String existing = keyOf(requestId);
        if (existing != null) {
            return existing;
        }
        String address = requesterEmail.trim().toLowerCase(Locale.ROOT);
        InboundMail mail = new InboundMail(null, null, List.of(), address, requesterName, null, List.of(),
                List.of(), subject, text(requesterName, note), null, List.of(), "text/plain", null, null, null,
                null, null, false);
        String key;
        try {
            key = store.store(mail, groupId(requestId), null, null,
                    new InboundMailStore.Placement(null, null, MailContext.Audience.SYSTEM.name(), null));
        } catch (InboundMailStore.DuplicateInboundMailException e) {
            return keyOf(requestId);
        }
        jdbc.update("UPDATE mail_conversation SET merge_request_id = :id WHERE conversation_key = :key",
                new MapSqlParameterSource("id", requestId).addValue("key", key));
        log.info("MERGE_REQUEST_CONVERSATION opened request={} conversation={}", requestId, key);
        return key;
    }

    /** Marks the conversation DONE; nothing happens when there is none. */
    public void close(UUID requestId, String decidedBy) {
        int updated = jdbc.update("""
                UPDATE mail_conversation SET status = 'DONE', updated_at = now(), updated_by = :by
                 WHERE merge_request_id = :id AND status <> 'DONE'
                """, new MapSqlParameterSource("id", requestId).addValue("by", decidedBy));
        log.info("MERGE_REQUEST_CONVERSATION closed request={} updated={}", requestId, updated);
    }

    private String keyOf(UUID requestId) {
        return jdbc.query("SELECT conversation_key FROM mail_conversation WHERE merge_request_id = :id",
                new MapSqlParameterSource("id", requestId), rs -> rs.next() ? rs.getString(1) : null);
    }

    static String groupId(UUID requestId) {
        return "merge-request-" + requestId;
    }

    private static String text(String requesterName, String note) {
        String intro = requesterName + " möchte ein früheres Profil ohne Login mit dem eigenen Konto"
                + " zusammenführen und hat keinen Zugang mehr zur Adresse des früheren Profils."
                + " / " + requesterName + " asks to combine an earlier profile without login with their"
                + " account and can no longer read the earlier profile's mailbox.";
        return note == null || note.isBlank() ? intro : intro + "\n\n" + note.trim();
    }
}
