package ch.batbern.shared.service;

import ch.batbern.shared.mail.MailContext;
import ch.batbern.shared.mail.OutboundMail;
import ch.batbern.shared.mail.OutboundMailRecorder;
import ch.batbern.shared.util.EmailContentTestMarker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.awscore.exception.AwsErrorDetails;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.ses.model.MessageTag;
import software.amazon.awssdk.services.ses.model.SendEmailRequest;
import software.amazon.awssdk.services.ses.model.SendEmailResponse;
import software.amazon.awssdk.services.ses.model.SendRawEmailRequest;
import software.amazon.awssdk.services.ses.model.SendRawEmailResponse;
import software.amazon.awssdk.services.ses.model.SesException;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Story 16.1a: a send that carries a {@link MailContext} is handed to the {@link OutboundMailRecorder}
 * after SES answers, with the SES Message-ID, and tagged {@code mail_group} for the SES events.
 */
@DisplayName("EmailService records outbound mail (Story 16.1a)")
class EmailServiceRecordingTest {

    private static final String TO = "speaker@batbern-test.ch";
    private static final String CC = "speaker.private@batbern-test.ch";

    private EmailService emailService;
    private SesClient sesClient;
    private OutboundMailRecorder recorder;
    private final MailContext context =
            MailContext.individual("BATbern60", MailContext.Audience.SPEAKERS, "speaker-invitation")
                    .withSecrets("Temp-Pa55!");

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
    }

    private OutboundMail recorded() {
        ArgumentCaptor<OutboundMail> mail = ArgumentCaptor.forClass(OutboundMail.class);
        verify(recorder).record(org.mockito.ArgumentMatchers.eq(context), mail.capture());
        return mail.getValue();
    }

    @Test
    @DisplayName("a successful send is recorded with the Message-ID, redacted, and tagged mail_group")
    void success_recordedAndTagged() {
        when(sesClient.sendEmail(any(SendEmailRequest.class)))
                .thenReturn(SendEmailResponse.builder().messageId("ses-msg-1").build());

        emailService.sendHtmlEmailSync(context, TO, List.of(CC), "Invitation BATbern60",
                "<p>Password Temp-Pa55!</p>", null, "batbern60-moderator@batbern.ch");

        OutboundMail mail = recorded();
        assertThat(mail.to()).isEqualTo(TO);
        assertThat(mail.cc()).containsExactly(CC);
        assertThat(mail.subject()).isEqualTo("Invitation BATbern60");
        assertThat(mail.htmlBody()).isEqualTo("<p>Password [redacted]</p>");
        assertThat(mail.sesMessageId()).isEqualTo("ses-msg-1");
        assertThat(mail.failureReason()).isNull();

        ArgumentCaptor<SendEmailRequest> request = ArgumentCaptor.forClass(SendEmailRequest.class);
        verify(sesClient).sendEmail(request.capture());
        assertThat(request.getValue().tags())
                .containsExactly(MessageTag.builder().name("mail_group").value(context.sendGroupId()).build());
    }

    @Test
    @DisplayName("an SES error is recorded as a failure with the error code, then rethrown")
    void sesError_recordedAsFailure() {
        when(sesClient.sendEmail(any(SendEmailRequest.class))).thenThrow((SesException) SesException.builder()
                .awsErrorDetails(AwsErrorDetails.builder().errorCode("MessageRejected")
                        .errorMessage("Email address is not verified.").build())
                .build());

        assertThatThrownBy(() -> emailService.sendHtmlEmailSync(context, TO, List.of(), "S", "<p>b</p>", null, null))
                .isInstanceOf(EmailService.EmailSendException.class);

        OutboundMail mail = recorded();
        assertThat(mail.sesMessageId()).isNull();
        assertThat(mail.failureReason()).isEqualTo("MessageRejected");
    }

    @Test
    @DisplayName("ADR-015: a test-marked send is blocked before SES and writes no record")
    void testMarked_notRecorded() {
        assertThatThrownBy(() -> emailService.sendHtmlEmailSync(context, TO, List.of(),
                "Hello " + EmailContentTestMarker.PLAYWRIGHT_MARKER, "<p>b</p>", null, null))
                .isInstanceOf(TestMarkedEmailException.class);

        verifyNoInteractions(recorder);
    }

    @Test
    @DisplayName("a send without context is neither recorded nor tagged")
    void noContext_notRecorded() {
        when(sesClient.sendEmail(any(SendEmailRequest.class)))
                .thenReturn(SendEmailResponse.builder().messageId("ses-msg-2").build());

        emailService.sendHtmlEmailSync(TO, List.of(), "S", "<p>b</p>", null, null);

        verifyNoInteractions(recorder);
        ArgumentCaptor<SendEmailRequest> request = ArgumentCaptor.forClass(SendEmailRequest.class);
        verify(sesClient).sendEmail(request.capture());
        assertThat(request.getValue().tags()).isEmpty();
    }

    @Test
    @DisplayName("a failing recorder never fails the send")
    void recorderFailure_swallowed() {
        when(sesClient.sendEmail(any(SendEmailRequest.class)))
                .thenReturn(SendEmailResponse.builder().messageId("ses-msg-3").build());
        doThrow(new IllegalStateException("db down")).when(recorder).record(any(), any());

        emailService.sendHtmlEmailSync(context, TO, List.of(), "S", "<p>b</p>", null, null);

        verify(sesClient).sendEmail(any(SendEmailRequest.class));
    }

    @Test
    @DisplayName("sends with attachments are recorded and tagged too")
    void attachments_recorded() {
        when(sesClient.sendRawEmail(any(SendRawEmailRequest.class)))
                .thenReturn(SendRawEmailResponse.builder().messageId("ses-raw-1").build());
        List<EmailService.EmailAttachment> ics =
                List.of(new EmailService.EmailAttachment("event.ics", new byte[] {1}, "text/calendar", true));

        emailService.sendHtmlEmailWithAttachments(context, TO, List.of(), "S", "<p>b</p>", ics);
        emailService.sendHtmlEmailSyncWithAttachments(context, TO, "S", "<p>b</p>", ics, "newsletter-set");

        ArgumentCaptor<OutboundMail> mail = ArgumentCaptor.forClass(OutboundMail.class);
        verify(recorder, org.mockito.Mockito.times(2)).record(org.mockito.ArgumentMatchers.eq(context), mail.capture());
        assertThat(mail.getAllValues()).extracting(OutboundMail::sesMessageId).containsOnly("ses-raw-1");
        ArgumentCaptor<SendRawEmailRequest> request = ArgumentCaptor.forClass(SendRawEmailRequest.class);
        verify(sesClient, org.mockito.Mockito.times(2)).sendRawEmail(request.capture());
        assertThat(request.getAllValues()).allSatisfy(r -> assertThat(r.tags()).extracting(MessageTag::name)
                .containsExactly("mail_group"));
    }

    @Test
    @DisplayName("local dev: the captured mail is recorded with a local- id, so the record links to /dev/emails")
    void localCapture_recordedWithLocalId() {
        ReflectionTestUtils.setField(emailService, "sesClient", null);
        LocalEmailCapture capture = mock(LocalEmailCapture.class);
        UUID captured = UUID.randomUUID();
        when(capture.capture(anyString(), anyList(), anyString(), anyString(), anyString(), anyString(), anyList()))
                .thenReturn(captured);
        ReflectionTestUtils.setField(emailService, "localEmailCapture", capture);

        emailService.sendHtmlEmailSync(context, TO, List.of(), "S", "<p>b</p>", null, null);

        assertThat(recorded().sesMessageId()).isEqualTo("local-" + captured);
        verify(sesClient, never()).sendEmail(any(SendEmailRequest.class));
    }
}
