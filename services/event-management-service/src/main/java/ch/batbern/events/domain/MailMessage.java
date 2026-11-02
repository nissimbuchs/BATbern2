package ch.batbern.events.domain;

import ch.batbern.shared.mail.MailContext;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * One recorded mail: everything one send group put out (Story 16.1a). Written by
 * {@code JpaOutboundMailRecorder}; the UUID never leaves the service, {@code mailKey} is the public id.
 * See {@code V135__create_mail_message.sql}.
 */
@Entity
@Table(name = "mail_message")
@Getter
@Setter
public class MailMessage {

    @Id
    private UUID id;

    @Column(name = "mail_key", nullable = false)
    private String mailKey;

    @Column(name = "send_group_id", nullable = false)
    private String sendGroupId;

    @Column(name = "event_code")
    private String eventCode;

    @Column(name = "direction", nullable = false)
    private String direction;

    @Enumerated(EnumType.STRING)
    @Column(name = "audience", nullable = false)
    private MailContext.Audience audience;

    @Column(name = "template_key")
    private String templateKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "content_source", nullable = false)
    private MailContext.ContentSource contentSource;

    @Column(name = "subject", nullable = false)
    private String subject;

    @Column(name = "body_html", nullable = false)
    private String bodyHtml;

    @Column(name = "sender_username")
    private String senderUsername;

    @Column(name = "newsletter_send_id")
    private UUID newsletterSendId;

    @Column(name = "recipient_count", nullable = false)
    private int recipientCount;

    @Column(name = "is_test", nullable = false)
    private boolean test;

    /** Story 16.9: what the mail is about beyond its event, e.g. {@code partner-meeting:{id}}; see V137. */
    @Column(name = "reference")
    private String reference;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    // ── Story 16.2a: inbound mail (direction IN) and threading; null on outbound rows ──────────

    /** The conversation an inbound (and, from 16.6, an answering outbound) mail belongs to. */
    @Column(name = "conversation_id")
    private UUID conversationId;

    @Column(name = "from_address")
    private String fromAddress;

    @Column(name = "from_name")
    private String fromName;

    /** To and Cc of an inbound mail, comma-separated. */
    @Column(name = "to_addresses")
    private String toAddresses;

    /** The mail's own Message-ID header, without angle brackets. */
    @Column(name = "message_id_header")
    private String messageIdHeader;

    @Column(name = "in_reply_to")
    private String inReplyTo;

    @Column(name = "references_header")
    private String referencesHeader;

    /** Plain text of an inbound mail, as received. */
    @Column(name = "body_text")
    private String bodyText;

    /** {@code REPLIES} ({@code replies@}) or {@code ALIAS} ({@code batbern{N}-moderator@} and co). */
    @Column(name = "inbound_path")
    private String inboundPath;

    @Column(name = "alias_local_part")
    private String aliasLocalPart;

    /**
     * Names, types and sizes; Story 16.6: files up to 25 MB also have the key under which they are
     * kept ({@code mail-attachments/} in the inbound bucket), larger ones only their name.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "attachments")
    private List<Attachment> attachments;

    /**
     * One attachment of an inbound mail.
     *
     * @param storageKey S3 key of the kept file, or null when only the name is kept
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Attachment(String name, String contentType, long size, String storageKey) {

        public Attachment(String name, String contentType, long size) {
            this(name, contentType, size, null);
        }

        public Attachment withStorageKey(String key) {
            return new Attachment(name, contentType, size, key);
        }
    }
}
