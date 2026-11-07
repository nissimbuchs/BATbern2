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
 * The Mail Center conversation in which organizers decide a request from company-user-management:
 * a profile merge request (#1237, {@code merge_request_id}) or a company logo request (#1246,
 * {@code logo_request_id}). It opens with one inbound message from the requester (an intro and their
 * note), has no event and the audience SYSTEM, and carries the request id so the Mail Center shows
 * the request with its decision buttons. Answering in it mails the requester like any conversation.
 * Both operations are idempotent.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RequestConversations {

    /** The request kinds; each has its own id column on {@code mail_conversation}. */
    public enum Kind {
        MERGE("merge_request_id", "merge-request-",
                "%s möchte ein früheres Profil ohne Login mit dem eigenen Konto zusammenführen und hat keinen"
                        + " Zugang mehr zur Adresse des früheren Profils. / %s asks to combine an earlier profile"
                        + " without login with their account and can no longer read the earlier profile's mailbox."),
        LOGO("logo_request_id", "logo-request-",
                "%s beantragt ein neues Logo für die eigene Firma. Das bisherige und das neue Logo stehen"
                        + " oben. / %s asks for a new logo of their company. The current and the new logo are"
                        + " shown above.");

        private final String column;
        private final String groupPrefix;
        private final String intro;

        Kind(String column, String groupPrefix, String intro) {
            this.column = column;
            this.groupPrefix = groupPrefix;
            this.intro = intro;
        }

        public String groupId(UUID requestId) {
            return groupPrefix + requestId;
        }

        String text(String requesterName, String note) {
            String name = requesterName == null || requesterName.isBlank() ? "?" : requesterName;
            String first = String.format(intro, name, name);
            return note == null || note.isBlank() ? first : first + "\n\n" + note.trim();
        }
    }

    private final InboundMailStore store;
    private final NamedParameterJdbcTemplate jdbc;

    /** Opens the conversation unless it exists; returns its key. */
    public String open(Kind kind, UUID requestId, String requesterName, String requesterEmail, String subject,
            String note) {
        String existing = keyOf(kind, requestId);
        if (existing != null) {
            return existing;
        }
        String address = requesterEmail.trim().toLowerCase(Locale.ROOT);
        InboundMail mail = new InboundMail(null, null, List.of(), address, requesterName, null, List.of(),
                List.of(), subject, kind.text(requesterName, note), null, List.of(), "text/plain", null, null, null,
                null, null, false);
        String key;
        try {
            key = store.store(mail, kind.groupId(requestId), null, null,
                    new InboundMailStore.Placement(null, null, MailContext.Audience.SYSTEM.name(), null));
        } catch (InboundMailStore.DuplicateInboundMailException e) {
            return keyOf(kind, requestId);
        }
        jdbc.update("UPDATE mail_conversation SET " + kind.column + " = :id WHERE conversation_key = :key",
                new MapSqlParameterSource("id", requestId).addValue("key", key));
        log.info("REQUEST_CONVERSATION opened kind={} request={} conversation={}", kind, requestId, key);
        return key;
    }

    /** Marks the conversation DONE; nothing happens when there is none. */
    public void close(Kind kind, UUID requestId, String decidedBy) {
        int updated = jdbc.update("UPDATE mail_conversation SET status = 'DONE', updated_at = now(), updated_by = :by"
                + " WHERE " + kind.column + " = :id AND status <> 'DONE'",
                new MapSqlParameterSource("id", requestId).addValue("by", decidedBy));
        log.info("REQUEST_CONVERSATION closed kind={} request={} updated={}", kind, requestId, updated);
    }

    private String keyOf(Kind kind, UUID requestId) {
        return jdbc.query("SELECT conversation_key FROM mail_conversation WHERE " + kind.column + " = :id",
                new MapSqlParameterSource("id", requestId), rs -> rs.next() ? rs.getString(1) : null);
    }
}
