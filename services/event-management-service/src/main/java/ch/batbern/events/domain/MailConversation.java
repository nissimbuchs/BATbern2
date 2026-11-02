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
 * A thread of mail with one counterpart (Story 16.2a): the speaker's question and everything that
 * follows, with a status and an organizer who answers it. The messages are {@link MailMessage} rows
 * that point here. {@code conversationKey} ({@code conv-{n}}) is the public id; keys are global
 * because 16.7 can move a conversation to another event. See {@code V139__mail_conversations.sql}.
 */
@Entity
@Table(name = "mail_conversation")
@Getter
@Setter
public class MailConversation {

    /** Open: needs an answer. Waiting: answered, waiting for the counterpart. Done: nothing to do. */
    public enum Status { OPEN, WAITING, DONE }

    @Id
    private UUID id;

    @Column(name = "conversation_key", nullable = false)
    private String conversationKey;

    @Column(name = "event_code")
    private String eventCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "audience", nullable = false)
    private MailContext.Audience audience;

    @Column(name = "subject", nullable = false)
    private String subject;

    /** Lowercased. */
    @Column(name = "counterpart_address", nullable = false)
    private String counterpartAddress;

    @Column(name = "counterpart_name")
    private String counterpartName;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private Status status;

    @Column(name = "assignee_username")
    private String assigneeUsername;

    /** For {@code replies+{token}@}: handed out by Story 16.6. */
    @Column(name = "reply_token")
    private String replyToken;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by")
    private String updatedBy;

    @Column(name = "last_message_at", nullable = false)
    private Instant lastMessageAt;

    @Column(name = "last_inbound_at")
    private Instant lastInboundAt;
}
