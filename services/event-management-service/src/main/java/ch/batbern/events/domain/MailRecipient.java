package ch.batbern.events.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * One address a recorded mail went to (Story 16.1a). {@code status} starts at SENT or FAILED;
 * Story 16.1b moves it to DELIVERED or FAILED from the SES events. {@code bodyHtml} is set only when
 * this recipient's text differs from the mail's (personalised mails).
 */
@Entity
@Table(name = "mail_recipient")
@Getter
@Setter
public class MailRecipient {

    public enum Kind { TO, CC }

    public enum Status { SENT, DELIVERED, FAILED }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "mail_message_id", nullable = false)
    private UUID mailMessageId;

    @Column(name = "address", nullable = false)
    private String address;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false)
    private Kind kind;

    @Column(name = "ses_message_id")
    private String sesMessageId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private Status status;

    @Column(name = "failure_reason")
    private String failureReason;

    @Column(name = "body_html")
    private String bodyHtml;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
