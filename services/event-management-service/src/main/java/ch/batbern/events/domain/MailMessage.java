package ch.batbern.events.domain;

import ch.batbern.shared.mail.MailContext;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
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

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
