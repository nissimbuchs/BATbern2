package ch.batbern.shared.service;

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
import software.amazon.awssdk.services.ses.model.RawMessage;
import software.amazon.awssdk.services.ses.model.SendEmailRequest;
import software.amazon.awssdk.services.ses.model.SendEmailResponse;
import software.amazon.awssdk.services.ses.model.SendRawEmailRequest;
import software.amazon.awssdk.services.ses.model.SendRawEmailResponse;
import software.amazon.awssdk.services.ses.model.SesException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Properties;

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
     * Optional — present only in production/staging (bean defined in AwsSesConfig).
     * Null in local and test environments: EmailService falls back to LocalEmailCapture or logging.
     */
    @Autowired(required = false)
    private SesClient sesClient;

    /** Optional — present only on 'local' Spring profile. Null in test/production. */
    @Autowired(required = false)
    private LocalEmailCapture localEmailCapture;

    @Value("${app.email.from:noreply@batbern.ch}")
    private String fromEmail;

    @Value("${app.email.from-name:BATbern Platform}")
    private String fromName;

    @Value("${app.email.reply-to:replies@batbern.ch}")
    private String replyToEmail;

    /**
     * Default SES Configuration Set applied to every send so BOUNCE/COMPLAINT events
     * route to SNS → SQS → BounceProcessingService for ALL email kinds (transactional
     * and newsletter). Null in local/test or when not configured. Explicit overrides
     * via the *Sync(... configurationSetName) overloads still work.
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
        sendHtmlEmailSync(to, subject, htmlBody, configurationSetName);
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
        sendHtmlEmailSync(to, subject, htmlBody, configurationSetName);
    }

    /**
     * Story 10.29 AC4: Synchronous variant with optional SES Configuration Set.
     * When configurationSetName is non-null, BOUNCE/COMPLAINT events are routed
     * through the Configuration Set's event destinations.
     */
    public void sendHtmlEmailSync(String to, String subject, String htmlBody, String configurationSetName) {
        if (sesClient == null) {
            log.warn("SES client not configured - skipping email send (local/test mode)");
            if (localEmailCapture != null) {
                localEmailCapture.capture(to, subject, htmlBody, fromEmail, fromName, List.of());
            } else {
                log.info("Would send email to: {}, subject: {}", to, subject);
            }
            return;
        }

        try {
            log.debug("Sending HTML email (sync) to: {}, subject: {}, configSet: {}",
                    to, subject, configurationSetName);

            SendEmailRequest.Builder requestBuilder = SendEmailRequest.builder()
                    .source(String.format("%s <%s>", fromName, fromEmail))
                    .replyToAddresses(replyToEmail)
                    .destination(Destination.builder().toAddresses(to).build())
                    .message(software.amazon.awssdk.services.ses.model.Message.builder()
                            .subject(Content.builder().data(subject).charset("UTF-8").build())
                            .body(Body.builder()
                                    .html(Content.builder().data(htmlBody).charset("UTF-8").build())
                                    .build())
                            .build());

            if (configurationSetName != null && !configurationSetName.isBlank()) {
                requestBuilder.configurationSetName(configurationSetName);
            }

            SendEmailResponse response = sesClient.sendEmail(requestBuilder.build());
            log.info("Email sent (sync) to: {}, MessageId: {}", to, response.messageId());

        } catch (SesException e) {
            log.error("Failed to send email (sync) to: {}, Error: {}", to, e.awsErrorDetails().errorMessage(), e);
            throw new EmailSendException("Failed to send email to: " + to, e);
        }
    }

    /**
     * Send an HTML email with attachments asynchronously (e.g., .ics calendar file).
     *
     * @param to Recipient email address
     * @param subject Email subject
     * @param htmlBody HTML content
     * @param attachments List of email attachments
     */
    @Async
    public void sendHtmlEmailWithAttachments(
            String to,
            String subject,
            String htmlBody,
            List<EmailAttachment> attachments
    ) {
        // In test/local environments without SES, capture or log the email
        if (sesClient == null) {
            log.warn("SES client not configured - skipping email send (local/test mode)");
            if (localEmailCapture != null) {
                List<CapturedEmail.AttachmentInfo> attachmentInfos = attachments.stream()
                    .map(a -> new CapturedEmail.AttachmentInfo(a.filename(), a.mimeType(), a.content().length))
                    .toList();
                java.util.UUID emailId = localEmailCapture.capture(
                        to, subject, htmlBody, fromEmail, fromName, attachmentInfos);
                for (EmailAttachment attachment : attachments) {
                    localEmailCapture.storeAttachmentBytes(emailId, attachment.filename(), attachment.content());
                }
            } else {
                log.info("Would send email with {} attachment(s) to: {}, subject: {}",
                        attachments.size(), to, subject);
            }
            return;
        }

        try {
            log.debug("Sending HTML email with {} attachment(s) to: {}", attachments.size(), to);

            // Build raw MIME message using JavaMail API
            Session session = Session.getInstance(new Properties());
            MimeMessage message = new MimeMessage(session);

            // Set headers
            message.setFrom(new InternetAddress(fromEmail, fromName));
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(to));
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

            SendRawEmailResponse response = sesClient.sendRawEmail(rawRequestBuilder.build());
            log.info("Email with attachments sent successfully to: {}, MessageId: {}", to, response.messageId());

        } catch (MessagingException | IOException e) {
            log.error("Failed to create MIME message for: {}", to, e);
            throw new EmailSendException("Failed to create email message", e);
        } catch (SesException e) {
            log.error("Failed to send email to: {}, Error: {}", to, e.awsErrorDetails().errorMessage(), e);
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
        if (sesClient == null) {
            log.warn("SES client not configured - skipping email send (local/test mode)");
            if (localEmailCapture != null) {
                List<CapturedEmail.AttachmentInfo> attachmentInfos = attachments.stream()
                    .map(a -> new CapturedEmail.AttachmentInfo(a.filename(), a.mimeType(), a.content().length))
                    .toList();
                java.util.UUID emailId = localEmailCapture.capture(
                        to, subject, htmlBody, fromEmail, fromName, attachmentInfos);
                for (EmailAttachment attachment : attachments) {
                    localEmailCapture.storeAttachmentBytes(emailId, attachment.filename(), attachment.content());
                }
            }
            return;
        }

        try {
            log.debug("Sending HTML email (sync) with {} attachment(s) to: {}", attachments.size(), to);

            Session session = Session.getInstance(new Properties());
            MimeMessage message = new MimeMessage(session);
            message.setFrom(new InternetAddress(fromEmail, fromName));
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(to));
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

            SendRawEmailResponse response = sesClient.sendRawEmail(rawRequestBuilder.build());
            log.info("Email with attachments sent (sync) to: {}, MessageId: {}", to, response.messageId());

        } catch (MessagingException | IOException e) {
            log.error("Failed to create MIME message for: {}", to, e);
            throw new EmailSendException("Failed to create email message", e);
        } catch (SesException e) {
            log.error("Failed to send email to: {}, Error: {}", to, e.awsErrorDetails().errorMessage(), e);
            throw new EmailSendException("Failed to send email to: " + to, e);
        }
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
     * Exception thrown when email sending fails.
     */
    public static class EmailSendException extends RuntimeException {
        public EmailSendException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
