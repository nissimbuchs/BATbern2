package ch.batbern.shared.service;

import ch.batbern.shared.mail.MailContext;
import ch.batbern.shared.mail.OutboundMail;
import ch.batbern.shared.mail.OutboundMailRecorder;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.ses.model.SendRawEmailRequest;
import software.amazon.awssdk.services.ses.model.SendRawEmailResponse;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Story 16.2b AC7: a raw send with a {@link MailContext} (registration mail with ICS, newsletter)
 * carries our own {@code Message-ID: <{uuid}@{domain}>}, and the recorder gets the same value, so a
 * reply can be matched whatever SES does with the header.
 */
@DisplayName("EmailService: explicit Message-ID on raw sends (Story 16.2b)")
class EmailServiceRawMessageIdTest {

    private static final EmailService.EmailAttachment ICS = new EmailService.EmailAttachment("event.ics",
            "BEGIN:VCALENDAR\r\nEND:VCALENDAR\r\n".getBytes(StandardCharsets.UTF_8), "text/calendar", true);

    private EmailService emailService;
    private SesClient sesClient;
    private OutboundMailRecorder recorder;
    private final MailContext context =
            MailContext.individual("BATbern60", MailContext.Audience.REGISTRANTS, "registration-confirmation");

    @BeforeEach
    void setUp() {
        emailService = new EmailService();
        sesClient = mock(SesClient.class);
        recorder = mock(OutboundMailRecorder.class);
        ReflectionTestUtils.setField(emailService, "sesClient", sesClient);
        ReflectionTestUtils.setField(emailService, "outboundMailRecorder", recorder);
        ReflectionTestUtils.setField(emailService, "fromEmail", "noreply@batbern.ch");
        ReflectionTestUtils.setField(emailService, "fromName", "BATbern");
        ReflectionTestUtils.setField(emailService, "replyToEmail", "replies@batbern.ch");
        when(sesClient.sendRawEmail(any(SendRawEmailRequest.class)))
                .thenReturn(SendRawEmailResponse.builder().messageId("ses-raw-1").build());
    }

    private String sentMessageId() throws Exception {
        ArgumentCaptor<SendRawEmailRequest> request = ArgumentCaptor.forClass(SendRawEmailRequest.class);
        verify(sesClient).sendRawEmail(request.capture());
        return new MimeMessage(Session.getInstance(new Properties()),
                new ByteArrayInputStream(request.getValue().rawMessage().data().asByteArray()))
                .getHeader("Message-ID", null);
    }

    private OutboundMail recorded() {
        ArgumentCaptor<OutboundMail> mail = ArgumentCaptor.forClass(OutboundMail.class);
        verify(recorder).record(eq(context), mail.capture());
        return mail.getValue();
    }

    @Test
    @DisplayName("registration path: the header is ours and equals the recorded value")
    void withAttachments() throws Exception {
        emailService.sendHtmlEmailWithAttachments(context, "success+reg@simulator.amazonses.com", List.of(),
                "Anmeldung", "<p>Danke</p>", List.of(ICS));

        String header = sentMessageId();
        assertThat(header).matches("<[0-9a-f-]{36}@batbern\\.ch>");
        assertThat(recorded().messageIdHeader()).isEqualTo(header);
    }

    @Test
    @DisplayName("newsletter path: the header is ours and equals the recorded value")
    void syncWithAttachments() throws Exception {
        emailService.sendHtmlEmailSyncWithAttachments(context, "success+nl@simulator.amazonses.com", "Newsletter",
                "<p>News</p>", List.of(), null);

        String header = sentMessageId();
        assertThat(header).matches("<[0-9a-f-]{36}@batbern\\.ch>");
        assertThat(recorded().messageIdHeader()).isEqualTo(header);
    }

    @Test
    @DisplayName("no context: unchanged, JavaMail's own id, nothing recorded")
    void noContext() throws Exception {
        emailService.sendHtmlEmailSyncWithAttachments("success+nl@simulator.amazonses.com", "Newsletter",
                "<p>News</p>", List.of(), null);

        assertThat(sentMessageId()).doesNotMatch("<[0-9a-f-]{36}@batbern\\.ch>");
        verify(recorder, org.mockito.Mockito.never()).record(any(), any());
    }
}
