package ch.batbern.shared.service;

import ch.batbern.shared.mail.ComposedMail;
import ch.batbern.shared.mail.MailContext;
import ch.batbern.shared.mail.MailRedaction;
import ch.batbern.shared.mail.OutboundMail;
import ch.batbern.shared.mail.OutboundMailRecorder;
import ch.batbern.shared.util.EmailContentTestMarker;
import ch.batbern.shared.util.ReservedEmailDomain;
import ch.batbern.shared.utils.EmailAddresses;
import ch.batbern.shared.utils.LoggingUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.ses.model.Body;
import software.amazon.awssdk.services.ses.model.Content;
import software.amazon.awssdk.services.ses.model.Destination;
import software.amazon.awssdk.services.ses.model.MessageTag;
import software.amazon.awssdk.services.ses.model.RawMessage;
import software.amazon.awssdk.services.ses.model.SendEmailRequest;
import software.amazon.awssdk.services.ses.model.SendEmailResponse;
import software.amazon.awssdk.services.ses.model.SendRawEmailRequest;
import software.amazon.awssdk.services.ses.model.SendRawEmailResponse;
import software.amazon.awssdk.services.ses.model.SesException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.UUID;

import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;

/**
 * Shared email service using AWS SES for sending transactional emails.
 *
 * Features:
 * - HTML email templates
 * - File attachments (.ics calendar files, PDF tickets, etc.)
 * - Async sending for non-blocking operations
 * - Template variable substitution
 *
 * Story 2.2a Task B12: Registration confirmation emails
 */
@Service
@Slf4j
public class EmailService {

    /**
     * Optional — the real client in production/staging (bean defined in AwsSesConfig). Null only
     * in local dev, where EmailService falls back to LocalEmailCapture or logging. In integration
     * tests it is the test fixtures' {@code SesRecorder}: live for this class, nothing is sent
     * (17.0b3).
     */
    @Autowired(required = false)
    private SesClient sesClient;

    /**
     * Whether mail goes to an SES client: true in staging/production and in integration tests
     * (the {@code SesRecorder}, which records and sends nothing); false only in local dev, which
     * captures mail. Guards against sending test content key on this, like the per-email
     * {@code assertNotTestMarked} wall, which only runs on the SES branch, so both walls apply in
     * integration tests as in production.
     */
    public boolean isLiveDelivery() {
        return sesClient != null;
    }

    /** Optional — present only on 'local' Spring profile. Null in test/production. */
    @Autowired(required = false)
    private LocalEmailCapture localEmailCapture;

    /**
     * Story 16.1a: records sends that carry a {@link MailContext}. Optional; EMS provides one, the
     * other services send unrecorded.
     */
    @Autowired(required = false)
    private OutboundMailRecorder outboundMailRecorder;

    /** Failure reason of a send refused for an undeliverable address: SES's own error code for it. */
    static final String INVALID_ADDRESS = "InvalidParameterValue";

    /** SES message tag naming the send group, so SES events can be traced to the recorded mail. */
    static final String MAIL_GROUP_TAG = "mail_group";

    @Value("${app.email.from:noreply@batbern.ch}")
    private String fromEmail;

    @Value("${app.email.from-name:BATbern Platform}")
    private String fromName;

    @Value("${app.email.reply-to:replies@batbern.ch}")
    private String replyToEmail;

    /**
     * Default SES Configuration Set applied to every send. This is the TRANSACTIONAL set
     * (delivery/bounce/complaint/reject → CloudWatch metrics + a per-recipient SNS logger,
     * with NO auto-suppression) — it is wired in each service stack via
     * {@code BATBERN_SES_CONFIGURATION_SET_NAME}. Newsletter blasts deliberately override
     * this with the newsletter set (which routes bounces to SQS → BounceProcessingService
     * for suppression) — see {@code NewsletterEmailService}. Null in local/test or when not
     * configured. Explicit overrides via the {@code *Sync(... configurationSetName)}
     * overloads still work. See {@code spec-transactional-ses-config-set.md}.
     */
    @Value("${batbern.ses.configuration-set-name:#{null}}")
    private String configurationSetName;

    /**
     * Send a simple HTML email asynchronously.
     *
     * @param to Recipient email address
     * @param subject Email subject
     * @param htmlBody HTML content
     */
    @Async
    public void sendHtmlEmail(String to, String subject, String htmlBody) {
        sendHtmlEmailSync(to, java.util.Collections.emptyList(), subject, htmlBody, configurationSetName);
    }

    /**
     * Story 10.32 — async HTML email with CC support. Used by speaker / waitlist /
     * quality-review notification flows to deliver a copy to each of the recipient's
     * declared additional emails. Empty / null {@code cc} is equivalent to the 3-arg
     * signature.
     */
    @Async
    public void sendHtmlEmail(String to, List<String> cc, String subject, String htmlBody) {
        sendHtmlEmailSync(to, cc, subject, htmlBody, configurationSetName);
    }

    /**
     * Async HTML email with CC and an explicit Reply-To (Story 16.0: speaker mails answer to
     * {@code batbern{N}-moderator@}). A null or blank {@code replyTo} falls back to the
     * configured {@code app.email.reply-to}, exactly like the 4-arg variant.
     */
    @Async
    public void sendHtmlEmail(String to, List<String> cc, String subject, String htmlBody, String replyTo) {
        sendHtmlEmailSync(to, cc, subject, htmlBody, configurationSetName, replyTo);
    }

    /**
     * Story 16.1a: async HTML email that is recorded with its {@link MailContext}. Null or empty
     * {@code cc} and null {@code replyTo} behave as in the other overloads.
     */
    @Async
    public void sendHtmlEmail(MailContext context, String to, List<String> cc, String subject, String htmlBody,
            String replyTo) {
        sendHtmlEmailSync(context, to, cc, subject, htmlBody, configurationSetName, replyTo);
    }

    /**
     * Send a simple HTML email synchronously (blocking).
     *
     * <p>Use this variant when the caller is already running in a background thread
     * (e.g., a newsletter bulk-send job) and must not dispatch further async tasks
     * to avoid thread-pool overflow with large recipient lists.
     *
     * @param to       Recipient email address
     * @param subject  Email subject
     * @param htmlBody HTML content
     */
    public void sendHtmlEmailSync(String to, String subject, String htmlBody) {
        sendHtmlEmailSync(to, java.util.Collections.emptyList(), subject, htmlBody, configurationSetName);
    }

    /**
     * Story 10.29 AC4: Synchronous variant with optional SES Configuration Set.
     * When configurationSetName is non-null, BOUNCE/COMPLAINT events are routed
     * through the Configuration Set's event destinations.
     */
    public void sendHtmlEmailSync(String to, String subject, String htmlBody, String configurationSetName) {
        sendHtmlEmailSync(to, java.util.Collections.emptyList(), subject, htmlBody, configurationSetName);
    }

    /**
     * Story 10.32 — synchronous variant with CC + optional SES Configuration Set.
     * CC entries that match {@code to} case-insensitively are dropped to avoid
     * duplicate delivery (same rule as {@link #sendHtmlEmailWithAttachments}).
     */
    public void sendHtmlEmailSync(String to, List<String> cc, String subject, String htmlBody,
            String configurationSetName) {
        sendHtmlEmailSync(to, cc, subject, htmlBody, configurationSetName, null);
    }

    /**
     * Send an HTML email synchronously with an explicit Reply-To address.
     *
     * <p>Used when the Reply-To must differ from the globally-configured
     * {@code app.email.reply-to} — e.g. venue-coordination mails carry the
     * organizer-on-duty as Reply-To so the venue/caterer's replies land in
     * that person's inbox, not the shared mailbox.
     *
     * <p>{@code replyTo} may be {@code null} or blank, in which case the
     * configured default is used.
     */
    public void sendHtmlEmailSync(String to, List<String> cc, String subject, String htmlBody,
            String configurationSetName, String replyTo) {
        sendHtmlEmailSync(null, to, cc, subject, htmlBody, configurationSetName, replyTo);
    }

    /**
     * Story 16.1a: the synchronous send every HTML overload ends in. With a non-null
     * {@code context} the send is recorded after SES answers and tagged {@code mail_group}.
     */
    public void sendHtmlEmailSync(MailContext context, String to, List<String> cc, String subject,
            String htmlBody, String configurationSetName, String replyTo) {
        Objects.requireNonNull(to, "to recipient must not be null");
        assertSendable(to);
        sendSyncTo(context, List.of(to), cc, subject, htmlBody, configurationSetName, replyTo);
    }

    /**
     * One plain HTML mail with every address of {@code to} in To: (e.g. all partners of a
     * partner mail). Each address is checked on its own; see {@link #deliverableRecipients}.
     * Recorded once, with the To: addresses comma-joined, like a single send.
     */
    public void sendGroupHtmlEmailSync(MailContext context, List<String> to, List<String> cc, String subject,
            String htmlBody, String replyTo) {
        sendSyncTo(context, deliverableRecipients(to), cc, subject, htmlBody, configurationSetName, replyTo);
    }

    private void sendSyncTo(MailContext context, List<String> toList, List<String> cc, String subject,
            String htmlBody, String configurationSetName, String replyTo) {
        String to = String.join(",", toList);
        List<String> ccClean = deliverableCc(normaliseCc(cc, toList));
        // Null/blank guard: callers may pass null (no explicit Reply-To needed) or an empty string
        // from config. Both fall back to the global app.email.reply-to value so the behaviour is
        // identical to the 5-arg overload. An empty-string caller will not accidentally send an
        // empty Reply-To header to SES.
        String effectiveReplyTo = (replyTo != null && !replyTo.isBlank()) ? replyTo : replyToEmail;
        // 17.0b3: every address is checked before the SES branch, so local dev and tests refuse
        // what SES would refuse (#1176: a joined To: line passed everywhere but production).
        List<String> toSes = deliveryForms(toList);
        List<String> ccSes = deliveryForms(ccClean);

        if (sesClient == null) {
            log.warn("SES client not configured - skipping email send (local/test mode)");
            if (localEmailCapture != null) {
                UUID captured = localEmailCapture.capture(to, ccClean, subject, htmlBody, fromEmail, fromName,
                        List.of());
                record(context, to, ccClean, subject, htmlBody, "local-" + captured, null);
            } else {
                record(context, to, ccClean, subject, htmlBody, null, null);
                log.info("Would send email to: {}, ccCount: {}, subject: {}, replyTo: {}",
                        LoggingUtils.maskEmail(to), ccClean.size(), subject,
                        LoggingUtils.maskEmail(effectiveReplyTo));
            }
            return;
        }

        // Second wall: never let a test-marked email reach a live SES client (real-send path only).
        assertNotTestMarked(subject, htmlBody);

        try {
            log.debug("Sending HTML email (sync) to: {}, ccCount: {}, subject: {}, configSet: {}, replyTo: {}",
                    LoggingUtils.maskEmail(to), ccClean.size(), subject, configurationSetName,
                    LoggingUtils.maskEmail(effectiveReplyTo));

            Destination.Builder destBuilder = Destination.builder().toAddresses(toSes);
            if (!ccSes.isEmpty()) {
                destBuilder.ccAddresses(ccSes);
            }

            SendEmailRequest.Builder requestBuilder = SendEmailRequest.builder()
                    .source(String.format("%s <%s>", fromName, fromEmail))
                    .replyToAddresses(effectiveReplyTo)
                    .destination(destBuilder.build())
                    .message(software.amazon.awssdk.services.ses.model.Message.builder()
                            .subject(Content.builder().data(subject).charset("UTF-8").build())
                            .body(Body.builder()
                                    .html(Content.builder().data(htmlBody).charset("UTF-8").build())
                                    .build())
                            .build());

            if (configurationSetName != null && !configurationSetName.isBlank()) {
                requestBuilder.configurationSetName(configurationSetName);
            }
            if (context != null) {
                requestBuilder.tags(mailGroupTag(context));
            }

            SendEmailResponse response = sesClient.sendEmail(requestBuilder.build());
            log.info("Email sent (sync) to: {}, ccCount: {}, MessageId: {}",
                    LoggingUtils.maskEmail(to), ccClean.size(), response.messageId());
            record(context, to, ccClean, subject, htmlBody, response.messageId(), null);

        } catch (SesException e) {
            log.error("Failed to send email (sync) to: {}, Error: {}",
                    LoggingUtils.maskEmail(to), e.awsErrorDetails().errorMessage(), e);
            record(context, to, ccClean, subject, htmlBody, null, errorCode(e));
            throw new EmailSendException("Failed to send email to: " + LoggingUtils.maskEmail(to), e);
        }
    }

    /**
     * Send an HTML email with attachments synchronously (e.g., .ics calendar file).
     *
     * <p>Intentionally NOT @Async — callers (RegistrationEmailService, PartnerInviteEmailService)
     * are already @Async themselves. A double-@Async dispatch causes exceptions to be silently
     * swallowed by SimpleAsyncUncaughtExceptionHandler instead of reaching the caller's try/catch.
     *
     * @param to Recipient email address
     * @param subject Email subject
     * @param htmlBody HTML content
     * @param attachments List of email attachments
     */
    public void sendHtmlEmailWithAttachments(
            String to,
            String subject,
            String htmlBody,
            List<EmailAttachment> attachments
    ) {
        sendHtmlEmailWithAttachments(to, java.util.Collections.emptyList(), subject, htmlBody, attachments);
    }

    /**
     * Story 10.32 — overload that CCs additional addresses. Used by EMS
     * registration-confirmation flow to deliver a copy to each of the
     * registrant's declared additional emails. Empty / null {@code cc} is
     * a no-op (equivalent to the original signature). CC entries that match
     * {@code to} case-insensitively are dropped to avoid duplicate delivery.
     */
    public void sendHtmlEmailWithAttachments(
            String to,
            List<String> cc,
            String subject,
            String htmlBody,
            List<EmailAttachment> attachments
    ) {
        sendHtmlEmailWithAttachments(null, to, cc, subject, htmlBody, attachments);
    }

    /** Story 16.1a: as above, recorded with its {@link MailContext} when that is non-null. */
    public void sendHtmlEmailWithAttachments(
            MailContext context,
            String to,
            List<String> cc,
            String subject,
            String htmlBody,
            List<EmailAttachment> attachments
    ) {
        Objects.requireNonNull(to, "to recipient must not be null");
        assertSendable(to);
        sendWithAttachmentsTo(context, List.of(to), cc, subject, htmlBody, attachments);
    }

    /** One mail with attachments to every address of {@code to} in To:, unrecorded. */
    public void sendGroupHtmlEmailWithAttachments(
            List<String> to,
            String subject,
            String htmlBody,
            List<EmailAttachment> attachments
    ) {
        sendGroupHtmlEmailWithAttachments(null, to, java.util.Collections.emptyList(), subject, htmlBody,
                attachments);
    }

    /**
     * One mail with attachments to every address of {@code to} in To: (partner meeting invites
     * and cancellations: every partner and organizer sees who was invited). Each address is
     * checked on its own; see {@link #deliverableRecipients}. Recorded once, with the To:
     * addresses comma-joined, like a single send.
     */
    public void sendGroupHtmlEmailWithAttachments(
            MailContext context,
            List<String> to,
            List<String> cc,
            String subject,
            String htmlBody,
            List<EmailAttachment> attachments
    ) {
        sendWithAttachmentsTo(context, deliverableRecipients(to), cc, subject, htmlBody, attachments);
    }

    private void sendWithAttachmentsTo(
            MailContext context,
            List<String> toList,
            List<String> cc,
            String subject,
            String htmlBody,
            List<EmailAttachment> attachments
    ) {
        String to = String.join(",", toList);
        // Story 10.32: normalise CC list the same way the SES MIME path below does,
        // so the local /dev/emails inbox shows exactly what staging/prod would deliver.
        List<String> ccClean = deliverableCc(normaliseCc(cc, toList));
        // 17.0b3: addresses are checked before the SES branch (see sendSyncTo).
        List<String> toSes = deliveryForms(toList);
        List<String> ccSes = deliveryForms(ccClean);
        // In test/local environments without SES, capture or log the email
        if (sesClient == null) {
            log.warn("SES client not configured - skipping email send (local/test mode)");
            if (localEmailCapture != null) {
                List<CapturedEmail.AttachmentInfo> attachmentInfos = attachments.stream()
                    .map(a -> new CapturedEmail.AttachmentInfo(a.filename(), a.mimeType(), a.content().length))
                    .toList();
                java.util.UUID emailId = localEmailCapture.capture(
                        to, ccClean, subject, htmlBody, fromEmail, fromName, attachmentInfos);
                for (EmailAttachment attachment : attachments) {
                    localEmailCapture.storeAttachmentBytes(emailId, attachment.filename(), attachment.content());
                }
                record(context, to, ccClean, subject, htmlBody, "local-" + emailId, null);
            } else {
                log.info("Would send email with {} attachment(s) to: {}, ccCount: {}, subject: {}",
                        attachments.size(), LoggingUtils.maskEmail(to), ccClean.size(), subject);
                record(context, to, ccClean, subject, htmlBody, null, null);
            }
            return;
        }

        // Second wall: never let a test-marked email reach a live SES client (real-send path only).
        assertNotTestMarked(subject, htmlBody);

        try {
            log.debug("Sending HTML email with {} attachment(s) to: {}",
                    attachments.size(), LoggingUtils.maskEmail(to));

            // Build raw MIME message using JavaMail API
            Session session = Session.getInstance(new Properties());
            // Story 16.2b AC7: a recorded mail carries our own Message-ID, known to the recorder
            String messageId = context != null ? newMessageId() : null;
            MimeMessage message = messageId != null ? new FixedIdMimeMessage(session, messageId)
                    : new MimeMessage(session);

            // Set headers
            message.setFrom(new InternetAddress(fromEmail, fromName));
            message.setRecipients(Message.RecipientType.TO, internetAddresses(toSes));
            // Story 10.32: CC additional emails (if any) — list normalised at method top.
            if (!ccSes.isEmpty()) {
                message.setRecipients(Message.RecipientType.CC, internetAddresses(ccSes));
            }
            message.setSubject(subject, "UTF-8");
            message.setReplyTo(InternetAddress.parse(replyToEmail));

            // Create multipart message
            MimeMultipart multipart = new MimeMultipart("mixed");

            // Add HTML body
            MimeBodyPart htmlPart = new MimeBodyPart();
            htmlPart.setContent(htmlBody, "text/html; charset=UTF-8");
            multipart.addBodyPart(htmlPart);

            // Add attachments
            for (EmailAttachment attachment : attachments) {
                MimeBodyPart attachmentPart = new MimeBodyPart();
                attachmentPart.setContent(attachment.content(), attachment.mimeType());
                // setFileName adds name= to Content-Type (helps clients identify the file)
                attachmentPart.setFileName(attachment.filename());
                if (attachment.inline()) {
                    // Override Content-Disposition to inline so calendar clients
                    // (Apple Mail, Outlook, Gmail) show accept/decline buttons
                    // rather than treating the part as a downloadable attachment.
                    attachmentPart.setDisposition(MimeBodyPart.INLINE);
                }
                multipart.addBodyPart(attachmentPart);
            }

            message.setContent(multipart);

            // Convert MimeMessage to raw bytes
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            message.writeTo(outputStream);
            ByteBuffer rawMessage = ByteBuffer.wrap(outputStream.toByteArray());

            // Send raw email via SES — attach the default configuration set so
            // BOUNCE/COMPLAINT events route through SNS → SQS for transactional emails too.
            SendRawEmailRequest.Builder rawRequestBuilder = SendRawEmailRequest.builder()
                    .rawMessage(RawMessage.builder()
                            .data(SdkBytes.fromByteBuffer(rawMessage))
                            .build());

            if (configurationSetName != null && !configurationSetName.isBlank()) {
                rawRequestBuilder.configurationSetName(configurationSetName);
            }
            if (context != null) {
                rawRequestBuilder.tags(mailGroupTag(context));
            }

            SendRawEmailResponse response = sesClient.sendRawEmail(rawRequestBuilder.build());
            log.info("Email with attachments sent successfully to {} recipient(s): {}, MessageId: {}",
                    toList.size(), LoggingUtils.maskEmail(to), response.messageId());
            record(context, new OutboundMail(to, ccClean, subject, htmlBody, response.messageId(), null, messageId));

        } catch (MessagingException | IOException e) {
            log.error("Failed to create MIME message for: {}", LoggingUtils.maskEmail(to), e);
            throw new EmailSendException("Failed to create email message", e);
        } catch (SesException e) {
            log.error("Failed to send email to: {}, Error: {}", LoggingUtils.maskEmail(to),
                    e.awsErrorDetails().errorMessage(), e);
            record(context, to, ccClean, subject, htmlBody, null, errorCode(e));
            throw new EmailSendException("Failed to send email to: " + to, e);
        }
    }

    /**
     * Send an HTML email with attachments synchronously (for use in newsletter send loops).
     * Supports configurationSetName for SES bounce/complaint tracking.
     */
    public void sendHtmlEmailSyncWithAttachments(
            String to,
            String subject,
            String htmlBody,
            List<EmailAttachment> attachments,
            String configurationSetName
    ) {
        sendHtmlEmailSyncWithAttachments(null, to, subject, htmlBody, attachments, configurationSetName);
    }

    /** Story 16.1a: as above, recorded with its {@link MailContext} when that is non-null. */
    public void sendHtmlEmailSyncWithAttachments(
            MailContext context,
            String to,
            String subject,
            String htmlBody,
            List<EmailAttachment> attachments,
            String configurationSetName
    ) {
        assertSendable(to);
        String toSes = EmailAddresses.toDeliveryForm(to); // 17.0b3: checked before the SES branch
        if (sesClient == null) {
            log.warn("SES client not configured - skipping email send (local/test mode)");
            if (localEmailCapture != null) {
                List<CapturedEmail.AttachmentInfo> attachmentInfos = attachments.stream()
                    .map(a -> new CapturedEmail.AttachmentInfo(a.filename(), a.mimeType(), a.content().length))
                    .toList();
                // Newsletter sync path — no CC (newsletter is email-keyed by design).
                java.util.UUID emailId = localEmailCapture.capture(
                        to, java.util.Collections.emptyList(), subject, htmlBody,
                        fromEmail, fromName, attachmentInfos);
                for (EmailAttachment attachment : attachments) {
                    localEmailCapture.storeAttachmentBytes(emailId, attachment.filename(), attachment.content());
                }
                record(context, to, List.of(), subject, htmlBody, "local-" + emailId, null);
            } else {
                record(context, to, List.of(), subject, htmlBody, null, null);
            }
            return;
        }

        // Second wall: never let a test-marked email reach a live SES client (real-send path only).
        assertNotTestMarked(subject, htmlBody);

        try {
            log.debug("Sending HTML email (sync) with {} attachment(s) to: {}", attachments.size(),
                    LoggingUtils.maskEmail(to));

            Session session = Session.getInstance(new Properties());
            // Story 16.2b AC7: a recorded mail carries our own Message-ID, known to the recorder
            String messageId = context != null ? newMessageId() : null;
            MimeMessage message = messageId != null ? new FixedIdMimeMessage(session, messageId)
                    : new MimeMessage(session);
            message.setFrom(new InternetAddress(fromEmail, fromName));
            message.setRecipients(Message.RecipientType.TO, internetAddresses(List.of(toSes)));
            message.setSubject(subject, "UTF-8");
            message.setReplyTo(InternetAddress.parse(replyToEmail));

            MimeMultipart multipart = new MimeMultipart("mixed");

            MimeBodyPart htmlPart = new MimeBodyPart();
            htmlPart.setContent(htmlBody, "text/html; charset=UTF-8");
            multipart.addBodyPart(htmlPart);

            for (EmailAttachment attachment : attachments) {
                MimeBodyPart attachmentPart = new MimeBodyPart();
                attachmentPart.setContent(attachment.content(), attachment.mimeType());
                attachmentPart.setFileName(attachment.filename());
                if (attachment.inline()) {
                    attachmentPart.setDisposition(MimeBodyPart.INLINE);
                }
                multipart.addBodyPart(attachmentPart);
            }

            message.setContent(multipart);

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            message.writeTo(outputStream);
            ByteBuffer rawMessage = ByteBuffer.wrap(outputStream.toByteArray());

            SendRawEmailRequest.Builder rawRequestBuilder = SendRawEmailRequest.builder()
                    .rawMessage(RawMessage.builder()
                            .data(SdkBytes.fromByteBuffer(rawMessage))
                            .build());

            if (configurationSetName != null && !configurationSetName.isBlank()) {
                rawRequestBuilder.configurationSetName(configurationSetName);
            }
            if (context != null) {
                rawRequestBuilder.tags(mailGroupTag(context));
            }

            SendRawEmailResponse response = sesClient.sendRawEmail(rawRequestBuilder.build());
            log.info("Email with attachments sent (sync) to: {}, MessageId: {}", LoggingUtils.maskEmail(to),
                    response.messageId());
            record(context, new OutboundMail(to, List.of(), subject, htmlBody, response.messageId(), null, messageId));

        } catch (MessagingException | IOException e) {
            log.error("Failed to create MIME message for: {}", LoggingUtils.maskEmail(to), e);
            throw new EmailSendException("Failed to create email message", e);
        } catch (SesException e) {
            log.error("Failed to send email to: {}, Error: {}", LoggingUtils.maskEmail(to),
                    e.awsErrorDetails().errorMessage(), e);
            record(context, to, List.of(), subject, htmlBody, null, errorCode(e));
            throw new EmailSendException("Failed to send email to: " + to, e);
        }
    }

    /**
     * Story 16.6: send a personal mail with headers the caller owns (an organizer's answer to a
     * conversation) through {@code SendRawEmail}, and record it with its {@code Message-ID}. The
     * same walls as every other send apply; on local dev the mail is captured to the dev inbox.
     * A refused send is recorded with the SES error code and rethrown.
     */
    public void sendComposed(MailContext context, ComposedMail mail) {
        assertSendable(mail.to());
        String toSes = EmailAddresses.toDeliveryForm(mail.to()); // 17.0b3: checked before the SES branch
        if (sesClient == null) {
            log.warn("SES client not configured - skipping composed email send (local/test mode)");
            String captured = null;
            if (localEmailCapture != null) {
                captured = "local-" + localEmailCapture.capture(mail.to(), List.of(), mail.subject(), mail.html(),
                        composedFrom(mail), mail.fromName(), List.of());
            }
            record(context, composedRecord(mail, captured, null));
            return;
        }
        assertNotTestMarked(mail.subject(), mail.html());
        byte[] mime;
        try {
            mime = composedMime(mail, toSes);
        } catch (MessagingException | IOException e) {
            throw new EmailSendException("Failed to create email message", e);
        }
        SendRawEmailRequest.Builder request = SendRawEmailRequest.builder()
                .rawMessage(RawMessage.builder().data(SdkBytes.fromByteArray(mime)).build());
        if (configurationSetName != null && !configurationSetName.isBlank()) {
            request.configurationSetName(configurationSetName);
        }
        if (context != null) {
            request.tags(mailGroupTag(context));
        }
        try {
            SendRawEmailResponse response = sesClient.sendRawEmail(request.build());
            log.info("Composed email sent to {}, MessageId: {}", LoggingUtils.maskEmail(mail.to()),
                    response.messageId());
            record(context, composedRecord(mail, response.messageId(), null));
        } catch (SesException e) {
            log.error("Failed to send composed email to {}: {}", LoggingUtils.maskEmail(mail.to()),
                    e.awsErrorDetails() != null ? e.awsErrorDetails().errorMessage() : e.getMessage(), e);
            record(context, composedRecord(mail, null, errorCode(e)));
            throw new EmailSendException("Failed to send email to: " + LoggingUtils.maskEmail(mail.to()), e);
        }
    }

    private String composedFrom(ComposedMail mail) {
        return mail.fromAddress() != null && !mail.fromAddress().isBlank() ? mail.fromAddress() : fromEmail;
    }

    private static OutboundMail composedRecord(ComposedMail mail, String sesMessageId, String failureReason) {
        return new OutboundMail(mail.to(), List.of(), mail.subject(), mail.html(), sesMessageId, failureReason,
                mail.messageId());
    }

    private byte[] composedMime(ComposedMail mail, String toSes) throws MessagingException, IOException {
        MimeMessage message = new FixedIdMimeMessage(Session.getInstance(new Properties()), mail.messageId());
        message.setFrom(new InternetAddress(composedFrom(mail), mail.fromName(), "UTF-8"));
        message.setRecipients(Message.RecipientType.TO, internetAddresses(List.of(toSes)));
        message.setSubject(mail.subject(), "UTF-8");
        if (mail.replyTo() != null && !mail.replyTo().isBlank()) {
            message.setReplyTo(InternetAddress.parse(mail.replyTo()));
        }
        if (mail.inReplyTo() != null && !mail.inReplyTo().isBlank()) {
            message.setHeader("In-Reply-To", mail.inReplyTo());
        }
        if (!mail.references().isEmpty()) {
            message.setHeader("References", String.join(" ", mail.references()));
        }
        MimeMultipart alternative = new MimeMultipart("alternative");
        MimeBodyPart textPart = new MimeBodyPart();
        textPart.setText(mail.text() != null ? mail.text() : "", "UTF-8");
        alternative.addBodyPart(textPart);
        MimeBodyPart htmlPart = new MimeBodyPart();
        htmlPart.setContent(mail.html() != null ? mail.html() : "", "text/html; charset=UTF-8");
        alternative.addBodyPart(htmlPart);
        message.setContent(alternative);
        message.saveChanges();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        message.writeTo(out);
        return out.toByteArray();
    }

    /** {@code <{uuid}@{domain of the sender address}>}. */
    private String newMessageId() {
        String domain = fromEmail != null && fromEmail.contains("@")
                ? fromEmail.substring(fromEmail.indexOf('@') + 1) : "batbern.ch";
        return "<" + UUID.randomUUID() + "@" + domain + ">";
    }

    /** Keeps the caller's Message-ID: JavaMail would replace it in {@code saveChanges()}. */
    private static final class FixedIdMimeMessage extends MimeMessage {
        private final String messageId;

        FixedIdMimeMessage(Session session, String messageId) {
            super(session);
            this.messageId = messageId;
        }

        @Override
        protected void updateMessageID() throws MessagingException {
            setHeader("Message-ID", messageId);
        }
    }

    /**
     * Send an already-built MIME message unchanged (email forwarding). The caller owns every
     * header, including From; SES only adds its own Return-Path and DKIM signature.
     *
     * <p>The same walls as every other send apply: reserved-domain recipients are refused, a
     * test-marked message never reaches a live SES client, and without SES (local/test) the
     * message is captured to the dev inbox instead.
     *
     * @param mime                     raw RFC 5322 message bytes
     * @param destinations             envelope recipients (not taken from the headers)
     * @param configurationSetOverride SES configuration set; null falls back to the default
     */
    public void sendRawMessage(byte[] mime, List<String> destinations, String configurationSetOverride) {
        sendRaw(mime, destinations, configurationSetOverride);
    }

    /**
     * Story 16.2a: send an already-built MIME message to {@code to} + {@code cc} in one SES call and
     * record it as one mail (the {@code batbern{N}-speaker@} broadcast: speakers in To, moderator in
     * Cc). {@code subject} and {@code htmlBody} are what the record shows; the MIME goes out unchanged.
     * A refused send is recorded with the SES error code and rethrown.
     */
    public void sendRawMessage(MailContext context, byte[] mime, List<String> to, List<String> cc, String subject,
            String htmlBody, String configurationSetOverride) {
        List<String> destinations = new ArrayList<>(to);
        destinations.addAll(cc);
        String messageId;
        try {
            messageId = sendRaw(mime, destinations, configurationSetOverride);
        } catch (EmailSendException e) {
            if (e.getCause() instanceof SesException ses) {
                record(context, String.join(",", to), cc, subject, htmlBody, null, errorCode(ses));
            }
            throw e;
        } catch (IllegalArgumentException e) {
            // An address refused before SES (17.0b3) is recorded as SES recorded it before.
            record(context, String.join(",", to), cc, subject, htmlBody, null, INVALID_ADDRESS);
            throw e;
        }
        record(context, String.join(",", to), cc, subject, htmlBody, messageId, null);
    }

    /** @return the SES Message-ID, {@code local-{id}} when captured on local dev, or null */
    private String sendRaw(byte[] mime, List<String> destinations, String configurationSetOverride) {
        Objects.requireNonNull(mime, "mime must not be null");
        if (destinations == null || destinations.isEmpty()) {
            throw new IllegalArgumentException("destinations must not be empty");
        }
        destinations.forEach(EmailService::assertSendable);
        List<String> destinationsSes = deliveryForms(destinations); // 17.0b3: checked before the SES branch

        String raw = new String(mime, StandardCharsets.UTF_8);
        String subject = rawSubject(mime);

        if (sesClient == null) {
            log.warn("SES client not configured - skipping raw email send (local/test mode)");
            if (localEmailCapture != null) {
                UUID captured = localEmailCapture.capture(destinations.get(0),
                        List.copyOf(destinations.subList(1, destinations.size())),
                        subject, "<pre>" + escapeHtml(raw) + "</pre>", fromEmail, fromName, List.of());
                return "local-" + captured;
            }
            log.info("Would send raw email to {} recipient(s), subject: {}", destinations.size(), subject);
            return null;
        }

        assertNotTestMarked(subject, raw);

        String configurationSet = configurationSetOverride != null && !configurationSetOverride.isBlank()
                ? configurationSetOverride
                : configurationSetName;
        SendRawEmailRequest.Builder request = SendRawEmailRequest.builder()
                .destinations(destinationsSes)
                .rawMessage(RawMessage.builder().data(SdkBytes.fromByteArray(mime)).build());
        if (configurationSet != null && !configurationSet.isBlank()) {
            request.configurationSetName(configurationSet);
        }
        try {
            SendRawEmailResponse response = sesClient.sendRawEmail(request.build());
            log.info("Raw email sent to {} recipient(s), MessageId: {}", destinations.size(), response.messageId());
            return response.messageId();
        } catch (SesException e) {
            log.error("Failed to send raw email to {} recipient(s): {}",
                    destinations.size(), e.awsErrorDetails().errorMessage(), e);
            throw new EmailSendException("Failed to send raw email", e);
        }
    }

    /**
     * Story 16.1a: hand a send to the recorder, credentials redacted. Never throws: a recording
     * failure must not turn a delivered mail into an error for the caller.
     */
    private void record(MailContext context, String to, List<String> cc, String subject, String htmlBody,
            String messageId, String failureReason) {
        record(context, new OutboundMail(to, cc, subject, htmlBody, messageId, failureReason));
    }

    /** As above, for a mail whose subject and body are still to be redacted. */
    private void record(MailContext context, OutboundMail mail) {
        if (context == null || outboundMailRecorder == null) {
            return;
        }
        try {
            outboundMailRecorder.record(context, new OutboundMail(mail.to(), mail.cc(),
                    MailRedaction.redact(mail.subject(), context.secrets()),
                    MailRedaction.redact(mail.htmlBody(), context.secrets()), mail.sesMessageId(),
                    mail.failureReason(), mail.messageIdHeader()));
        } catch (RuntimeException e) {
            log.error("Failed to record outbound mail (group {}): {}", context.sendGroupId(), e.getMessage(), e);
        }
    }

    private static MessageTag mailGroupTag(MailContext context) {
        return MessageTag.builder().name(MAIL_GROUP_TAG).value(context.sendGroupId()).build();
    }

    private static String errorCode(SesException e) {
        return e.awsErrorDetails() != null && e.awsErrorDetails().errorCode() != null
                ? e.awsErrorDetails().errorCode()
                : "SES_ERROR";
    }

    private static String rawSubject(byte[] mime) {
        try {
            MimeMessage message = new MimeMessage(Session.getInstance(new Properties()),
                    new ByteArrayInputStream(mime));
            String subject = message.getSubject();
            return subject != null ? subject : "";
        } catch (MessagingException e) {
            return "";
        }
    }

    private static String escapeHtml(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /**
     * Replace template variables in email content.
     * Supports simple variables {{variableName}} and Mustache-style conditionals {{#variableName}}...{{/variableName}}.
     *
     * Conditionals:
     * - If the variable value is null, empty, or blank: the entire block (including content) is removed
     * - If the variable has a non-empty value: the block tags are removed but content is kept
     *
     * Example:
     * replaceVariables("Hello {{firstName}}{{#lastName}}, {{lastName}}{{/lastName}}",
     *     Map.of("firstName", "John", "lastName", "")) returns "Hello John"
     *
     * @param template Email template with {{variable}} and {{#variable}}...{{/variable}} placeholders
     * @param variables Map of variable names to values
     * @return Email content with variables replaced and conditionals processed
     */
    public String replaceVariables(String template, java.util.Map<String, String> variables) {
        String result = template;

        // First, process conditional blocks {{#varName}}...{{/varName}}
        for (java.util.Map.Entry<String, String> entry : variables.entrySet()) {
            String varName = entry.getKey();
            String value = entry.getValue();
            boolean isEmpty = value == null || value.trim().isEmpty();

            // Pattern: {{#varName}}...{{/varName}} (including nested content)
            // Use non-greedy matching and DOTALL mode to match across lines
            String openTag = "\\{\\{#" + java.util.regex.Pattern.quote(varName) + "\\}\\}";
            String closeTag = "\\{\\{/" + java.util.regex.Pattern.quote(varName) + "\\}\\}";
            String conditionalPattern = openTag + "(.*?)" + closeTag;

            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
                    conditionalPattern, java.util.regex.Pattern.DOTALL);
            java.util.regex.Matcher matcher = pattern.matcher(result);

            if (isEmpty) {
                // Remove entire conditional block (tags + content)
                result = matcher.replaceAll("");
            } else {
                // Keep content, remove only the tags
                result = matcher.replaceAll("$1");
            }
        }

        // Then, replace simple variable placeholders {{varName}}
        for (java.util.Map.Entry<String, String> entry : variables.entrySet()) {
            String placeholder = "{{" + entry.getKey() + "}}";
            result = result.replace(placeholder, entry.getValue() != null ? entry.getValue() : "");
        }

        return result;
    }

    private static final java.util.regex.Pattern UNRESOLVED_SECTION = java.util.regex.Pattern.compile(
            "\\{\\{#([A-Za-z0-9_.-]+)}}.*?\\{\\{/\\1}}", java.util.regex.Pattern.DOTALL);
    private static final java.util.regex.Pattern UNRESOLVED_TAG = java.util.regex.Pattern.compile(
            "\\{\\{[#/]?[A-Za-z0-9_.-]+}}");

    /**
     * Finishes a rendered mail (#1235): a {@code {{#name}}…{{/name}}} section whose variable was not
     * in the map is removed (absent counts as false, as in Mustache), and any {@code {{name}}} tag
     * left over is blanked. {@link #replaceVariables} keeps leftovers on purpose, because staged
     * renders fill them in a second pass and the composer reports them to the organizer; call this
     * only on a mail that gets no further pass.
     *
     * @param rendered output of {@link #replaceVariables}, or null
     * @return the text without unresolved sections and tags, or null for null
     */
    public static String removeUnresolved(String rendered) {
        if (rendered == null) {
            return null;
        }
        String withoutSections = UNRESOLVED_SECTION.matcher(rendered).replaceAll("");
        return UNRESOLVED_TAG.matcher(withoutSections).replaceAll("");
    }

    /**
     * Email attachment record.
     *
     * @param filename Attachment filename (e.g., "event.ics", "ticket.pdf")
     * @param content  Attachment content as byte array
     * @param mimeType MIME type (e.g., "text/calendar", "application/pdf")
     * @param inline   When true the part is sent with Content-Disposition: inline so that
     *                 calendar clients (Apple Mail, Outlook, Gmail) render accept/decline
     *                 buttons instead of treating the file as a plain attachment.
     *                 Use true for text/calendar parts, false (default) for everything else.
     */
    public record EmailAttachment(String filename, byte[] content, String mimeType, boolean inline) {
        /** Backward-compatible constructor — inline defaults to false. */
        public EmailAttachment(String filename, byte[] content, String mimeType) {
            this(filename, content, mimeType, false);
        }
    }

    /**
     * Refuse outbound mail to RFC 2606 / RFC 6761 reserved domains (example.com,
     * *.test, *.invalid, …). Throwing before the SES call protects SES quota and
     * reputation from test fixtures, fuzzers, and security scanners.
     *
     * Async callers (RegistrationEmailService, NewsletterEmailService, etc.) wrap
     * outbound sends in a broad try/catch, so this propagates cleanly without
     * surfacing an error to the end user.
     */
    private static void assertSendable(String recipient) {
        if (ReservedEmailDomain.isReserved(recipient)) {
            log.warn("Refusing SES send to reserved-domain recipient: {}",
                    LoggingUtils.maskEmail(recipient));
            throw new ReservedEmailRecipientException(recipient);
        }
    }

    /**
     * Second wall of defence (incident 2026-07-01): refuse to hand a <b>test-marked</b> email to
     * a live SES client. The test suites stamp canonical markers ({@link EmailContentTestMarker})
     * into the entity names/titles that flow into email subjects/bodies, so a match here means the
     * email was generated from test data and must never reach real recipients.
     *
     * <p>Called ONLY from the SES branch (non-null {@code sesClient}) of each send method, so local
     * dev (where {@code LocalEmailCapture} or logging handles the mail) is unaffected — dev-only send
     * specs still run end-to-end. Integration tests take the SES branch (the {@code SesRecorder}),
     * so the wall applies there as in production. Logs at ERROR so a CloudWatch log-metric alarm can
     * fire if a test ever leaks a send onto production, then throws to abort the SES call.
     */
    private static void assertNotTestMarked(String subject, String htmlBody) {
        if (EmailContentTestMarker.containsMarker(subject, htmlBody)) {
            log.error("BLOCKED test-marked email from live SES send (markers: {}). subject={}",
                    EmailContentTestMarker.describe(), subject);
            throw new TestMarkedEmailException("subject=" + subject);
        }
    }

    /**
     * Story 10.32 — central CC normalisation used by every CC-aware overload.
     * Applies, in order:
     * <ol>
     *   <li>Null-coerce the input list to empty.</li>
     *   <li>Drop null / blank entries; trim whitespace.</li>
     *   <li>Drop entries that match {@code to} case-insensitively (avoids the
     *       primary recipient being CC'd to themselves).</li>
     *   <li>Drop entries whose domain is reserved (example.com, *.test,
     *       *.invalid) — same protection {@link #assertSendable} gives the
     *       {@code to} address. Without this, a user who registers
     *       {@code me@example.com} as an additional email causes every
     *       outbound email to ship a CC to SES → quota + reputation hit.
     *       Found in review 2026-05-22 finding P1-6.</li>
     * </ol>
     */
    private static List<String> normaliseCc(List<String> cc, List<String> to) {
        if (cc == null || cc.isEmpty()) {
            return java.util.Collections.emptyList();
        }
        return cc.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .filter(s -> to.stream().noneMatch(s::equalsIgnoreCase))
                .filter(s -> {
                    if (ReservedEmailDomain.isReserved(s)) {
                        log.warn("Dropping reserved-domain CC entry: {}", LoggingUtils.maskEmail(s));
                        return false;
                    }
                    return true;
                })
                .toList();
    }

    /**
     * Each address in the form SES needs ({@link EmailAddresses#toDeliveryForm}), one address per
     * entry: an entry holding several addresses ({@code "a@x.ch,b@y.ch"}) is not an address and
     * throws {@link IllegalArgumentException}, with or without an SES client (#1176).
     */
    private static List<String> deliveryForms(List<String> addresses) {
        return addresses.stream().map(EmailAddresses::toDeliveryForm).toList();
    }

    /** Header addresses built one by one; never re-parsed from a joined string. */
    private static InternetAddress[] internetAddresses(List<String> deliveryForms) throws MessagingException {
        InternetAddress[] result = new InternetAddress[deliveryForms.size()];
        for (int i = 0; i < deliveryForms.size(); i++) {
            result[i] = new InternetAddress(deliveryForms.get(i), true);
        }
        return result;
    }

    /**
     * The To: list of a group mail: trimmed, de-duplicated, and without addresses SES cannot
     * deliver to or that are on a reserved domain. A single bad address is dropped with a
     * warning instead of failing the whole mail, so the other recipients still get it.
     *
     * @throws IllegalArgumentException when no deliverable address is left
     */
    static List<String> deliverableRecipients(List<String> to) {
        Objects.requireNonNull(to, "to recipients must not be null");
        java.util.Map<String, String> unique = new java.util.LinkedHashMap<>();
        for (String raw : to) {
            String address = raw == null ? "" : raw.trim();
            if (address.isEmpty()) {
                continue;
            }
            if (ReservedEmailDomain.isReserved(address)) {
                log.warn("Dropping reserved-domain recipient from group mail: {}", LoggingUtils.maskEmail(address));
            } else if (!EmailAddresses.isAcceptable(address)) {
                log.warn("Dropping undeliverable recipient from group mail: {}", LoggingUtils.maskEmail(address));
            } else {
                unique.putIfAbsent(address.toLowerCase(java.util.Locale.ROOT), address);
            }
        }
        if (unique.isEmpty()) {
            throw new IllegalArgumentException(
                    "Cannot send group mail: none of the " + to.size() + " recipients is deliverable");
        }
        return List.copyOf(unique.values());
    }

    /**
     * #1102: SES has no SMTPUTF8, so addresses go out with the domain in Punycode
     * ({@link EmailAddresses#toDeliveryForm}). A CC that cannot be delivered at all (Unicode local
     * part, several addresses in one entry) is dropped with a warning so the primary recipient
     * still gets the mail. The entries kept, in their original spelling, are what the capture and
     * the mail record list, so neither shows a Cc recipient SES never got (17.0b3).
     */
    private static List<String> deliverableCc(List<String> cc) {
        return cc.stream()
                .filter(s -> {
                    if (!EmailAddresses.isAcceptable(s)) {
                        log.warn("Dropping undeliverable CC entry: {}", LoggingUtils.maskEmail(s));
                        return false;
                    }
                    return true;
                })
                .toList();
    }

    /**
     * Exception thrown when email sending fails.
     */
    public static class EmailSendException extends RuntimeException {
        public EmailSendException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
