package ch.batbern.events.mailcenter;

import ch.batbern.events.domain.MailMessage;
import ch.batbern.events.domain.MailRecipient;
import ch.batbern.events.repository.MailMessageRepository;
import ch.batbern.events.repository.MailRecipientRepository;
import ch.batbern.shared.mail.MailContext;
import ch.batbern.shared.mail.OutboundMail;
import ch.batbern.shared.mail.OutboundMailRecorder;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Writes what went out into {@code mail_message} + {@code mail_recipient} (Story 16.1a).
 *
 * <p>One {@code mail_message} per send group, created by the group's first send; every send adds
 * its addresses. A newsletter adds only to the count: its per-recipient status lives in
 * {@code newsletter_recipients} (AC3). A recipient row keeps its own body only when its text differs
 * from the mail's, i.e. for personalised mails.
 *
 * <p>Runs in its own transaction: the mail has already left, so the record must survive a rollback
 * of the caller's transaction.
 */
@Component
@RequiredArgsConstructor
public class JpaOutboundMailRecorder implements OutboundMailRecorder {

    /**
     * Creates the group's row unless it exists. The NOT EXISTS keeps {@code nextval} from being
     * spent on every later send of the group, so mail keys stay dense; ON CONFLICT covers the race
     * of two first sends.
     */
    private static final String INSERT_IF_ABSENT = """
            INSERT INTO mail_message (mail_key, send_group_id, event_code, audience, template_key,
                content_source, subject, body_html, sender_username, newsletter_send_id, is_test)
            SELECT COALESCE(:eventCode, 'general') || '-' || nextval('mail_message_seq'), :groupId,
                :eventCode, :audience, :templateKey, :contentSource, :subject, :bodyHtml, :sender,
                :newsletterSendId, :test
            WHERE NOT EXISTS (SELECT 1 FROM mail_message WHERE send_group_id = :groupId)
            ON CONFLICT (send_group_id) DO NOTHING
            """;

    private final NamedParameterJdbcTemplate jdbc;
    private final MailMessageRepository messages;
    private final MailRecipientRepository recipients;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(MailContext context, OutboundMail mail) {
        jdbc.update(INSERT_IF_ABSENT, new MapSqlParameterSource()
                .addValue("eventCode", context.eventCode())
                .addValue("groupId", context.sendGroupId())
                .addValue("audience", context.audience().name())
                .addValue("templateKey", context.templateKey())
                .addValue("contentSource", context.contentSource().name())
                .addValue("subject", Objects.requireNonNullElse(mail.subject(), ""))
                .addValue("bodyHtml", Objects.requireNonNullElse(mail.htmlBody(), ""))
                .addValue("sender", context.senderUsername())
                .addValue("newsletterSendId", context.newsletterSendId())
                .addValue("test", context.test()));
        MailMessage message = messages.findBySendGroupId(context.sendGroupId())
                .orElseThrow(() -> new IllegalStateException("mail_message missing for " + context.sendGroupId()));

        int addressCount = 1 + mail.cc().size();
        if (context.audience() != MailContext.Audience.NEWSLETTER) {
            String ownBody = Objects.equals(mail.htmlBody(), message.getBodyHtml()) ? null : mail.htmlBody();
            List<MailRecipient> rows = new ArrayList<>();
            rows.add(recipient(message, mail, mail.to(), MailRecipient.Kind.TO, ownBody));
            for (String cc : mail.cc()) {
                rows.add(recipient(message, mail, cc, MailRecipient.Kind.CC, ownBody));
            }
            recipients.saveAll(rows);
        }
        messages.addRecipients(message.getId(), addressCount);
    }

    private static MailRecipient recipient(MailMessage message, OutboundMail mail, String address,
            MailRecipient.Kind kind, String ownBody) {
        MailRecipient row = new MailRecipient();
        row.setMailMessageId(message.getId());
        row.setAddress(address);
        row.setKind(kind);
        row.setSesMessageId(mail.sesMessageId());
        row.setStatus(mail.failed() ? MailRecipient.Status.FAILED : MailRecipient.Status.SENT);
        row.setFailureReason(mail.failureReason());
        row.setBodyHtml(ownBody);
        row.setUpdatedAt(Instant.now());
        return row;
    }
}
