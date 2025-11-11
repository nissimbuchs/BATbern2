package ch.batbern.shared.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.ses.model.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
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
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final SesClient sesClient;

    @Value("${app.email.from:noreply@batbern.ch}")
    private String fromEmail;

    @Value("${app.email.from-name:BATbern Platform}")
    private String fromName;

    /**
     * Send a simple HTML email asynchronously.
     *
     * @param to Recipient email address
     * @param subject Email subject
     * @param htmlBody HTML content
     */
    @Async
    public void sendHtmlEmail(String to, String subject, String htmlBody) {
        // In test/local environments without SES, just log the email
        if (sesClient == null) {
            log.warn("SES client not configured - skipping email send (local/test mode)");
            log.info("Would send email to: {}, subject: {}", to, subject);
            return;
        }

        try {
            log.debug("Sending HTML email to: {}, subject: {}", to, subject);

            SendEmailRequest request = SendEmailRequest.builder()
                    .source(String.format("%s <%s>", fromName, fromEmail))
                    .destination(Destination.builder().toAddresses(to).build())
                    .message(software.amazon.awssdk.services.ses.model.Message.builder()
                            .subject(Content.builder().data(subject).charset("UTF-8").build())
                            .body(Body.builder()
                                    .html(Content.builder().data(htmlBody).charset("UTF-8").build())
                                    .build())
                            .build())
                    .build();

            SendEmailResponse response = sesClient.sendEmail(request);
            log.info("Email sent successfully to: {}, MessageId: {}", to, response.messageId());

        } catch (SesException e) {
            log.error("Failed to send email to: {}, Error: {}", to, e.awsErrorDetails().errorMessage(), e);
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
        // In test/local environments without SES, just log the email
        if (sesClient == null) {
            log.warn("SES client not configured - skipping email send (local/test mode)");
            log.info("Would send email with {} attachment(s) to: {}, subject: {}",
                    attachments.size(), to, subject);
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
                attachmentPart.setFileName(attachment.filename());
                multipart.addBodyPart(attachmentPart);
            }

            message.setContent(multipart);

            // Convert MimeMessage to raw bytes
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            message.writeTo(outputStream);
            ByteBuffer rawMessage = ByteBuffer.wrap(outputStream.toByteArray());

            // Send raw email via SES
            SendRawEmailRequest rawRequest = SendRawEmailRequest.builder()
                    .rawMessage(RawMessage.builder()
                            .data(SdkBytes.fromByteBuffer(rawMessage))
                            .build())
                    .build();

            SendRawEmailResponse response = sesClient.sendRawEmail(rawRequest);
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
     * Replace template variables in email content.
     * Variables are in format {{variableName}}.
     *
     * Example:
     * replaceVariables("Hello {{firstName}}", Map.of("firstName", "John"))
     * returns "Hello John"
     *
     * @param template Email template with {{variable}} placeholders
     * @param variables Map of variable names to values
     * @return Email content with variables replaced
     */
    public String replaceVariables(String template, java.util.Map<String, String> variables) {
        String result = template;
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
     * @param content Attachment content as byte array
     * @param mimeType MIME type (e.g., "text/calendar", "application/pdf")
     */
    public record EmailAttachment(String filename, byte[] content, String mimeType) {}

    /**
     * Exception thrown when email sending fails.
     */
    public static class EmailSendException extends RuntimeException {
        public EmailSendException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
