package ch.batbern.shared.service;

import ch.batbern.shared.mail.ComposedMail;
import ch.batbern.shared.mail.MailContext;
import ch.batbern.shared.mail.OutboundMail;
import ch.batbern.shared.mail.OutboundMailRecorder;
import ch.batbern.shared.util.EmailContentTestMarker;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
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
import java.util.List;
import java.util.Properties;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Story 16.6 AC1/AC2/AC6: an organizer's answer to a conversation is composed with its own headers
 * (display name, Reply-To with the conversation token, In-Reply-To, References, our Message-ID),
 * sent through {@code SendRawEmail} and recorded once.
 */
@DisplayName("EmailService.sendComposed (Story 16.6)")
class EmailServiceComposedMailTest {

    private static final String MESSAGE_ID = "<0f4c2a52-1111-4222-8333-944455556666@batbern.ch>";

    private EmailService emailService;
    private SesClient sesClient;
    private OutboundMailRecorder recorder;
    private final MailContext context = MailContext.individual("BATbern60", MailContext.Audience.SPEAKERS, null);

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

    private static ComposedMail mail(String subject) {
        return mail(subject, null);
    }

    private static ComposedMail mail(String subject, String fromAddress) {
        return new ComposedMail("Anna Muster via BATbern", fromAddress, "speaker@batbern-test.ch", subject,
                "<p>Thanks, see you.</p>", "Thanks, see you.", "replies+abcdefghijklmnop@batbern.ch",
                MESSAGE_ID, "<in-1@mail.batbern-test.ch>", List.of("<root@mail.batbern-test.ch>",
                        "<in-1@mail.batbern-test.ch>"));
    }

    private MimeMessage sentMime() throws Exception {
        ArgumentCaptor<SendRawEmailRequest> request = ArgumentCaptor.forClass(SendRawEmailRequest.class);
        verify(sesClient).sendRawEmail(request.capture());
        return new MimeMessage(Session.getInstance(new Properties()),
                new ByteArrayInputStream(request.getValue().rawMessage().data().asByteArray()));
    }

    @Test
    @DisplayName("headers: display name, Reply-To, In-Reply-To, References and the given Message-ID survive")
    void headers() throws Exception {
        when(sesClient.sendRawEmail(any(SendRawEmailRequest.class)))
                .thenReturn(SendRawEmailResponse.builder().messageId("ses-1").build());

        emailService.sendComposed(context, mail("Re: Slides"));

        MimeMessage sent = sentMime();
        InternetAddress from = (InternetAddress) sent.getFrom()[0];
        assertThat(from.getPersonal()).isEqualTo("Anna Muster via BATbern");
        assertThat(from.getAddress()).isEqualTo("noreply@batbern.ch");
        assertThat(((InternetAddress) sent.getReplyTo()[0]).getAddress())
                .isEqualTo("replies+abcdefghijklmnop@batbern.ch");
        assertThat(sent.getHeader("Message-ID", null)).isEqualTo(MESSAGE_ID);
        assertThat(sent.getHeader("In-Reply-To", null)).isEqualTo("<in-1@mail.batbern-test.ch>");
        assertThat(sent.getHeader("References", null))
                .isEqualTo("<root@mail.batbern-test.ch> <in-1@mail.batbern-test.ch>");
        assertThat(sent.getSubject()).isEqualTo("Re: Slides");
        assertThat(sent.getContentType()).startsWith("multipart/alternative");
    }

    @Test
    @DisplayName("From: the given address when set, with the display name")
    void fromAddress() throws Exception {
        when(sesClient.sendRawEmail(any(SendRawEmailRequest.class)))
                .thenReturn(SendRawEmailResponse.builder().messageId("ses-1").build());

        emailService.sendComposed(context, mail("Re: Slides", "info@batbern.ch"));

        InternetAddress from = (InternetAddress) sentMime().getFrom()[0];
        assertThat(from.getPersonal()).isEqualTo("Anna Muster via BATbern");
        assertThat(from.getAddress()).isEqualTo("info@batbern.ch");
    }

    @Test
    @DisplayName("recorded once with the SES id")
    void recorded() {
        when(sesClient.sendRawEmail(any(SendRawEmailRequest.class)))
                .thenReturn(SendRawEmailResponse.builder().messageId("ses-1").build());

        emailService.sendComposed(context, mail("Re: Slides"));

        ArgumentCaptor<OutboundMail> mail = ArgumentCaptor.forClass(OutboundMail.class);
        verify(recorder).record(eq(context), mail.capture());
        assertThat(mail.getValue().to()).isEqualTo("speaker@batbern-test.ch");
        assertThat(mail.getValue().sesMessageId()).isEqualTo("ses-1");
        assertThat(mail.getValue().htmlBody()).isEqualTo("<p>Thanks, see you.</p>");
        assertThat(mail.getValue().messageIdHeader()).isEqualTo(MESSAGE_ID);
    }

    @Test
    @DisplayName("ADR-015: a test-marked subject is refused before SES, nothing recorded")
    void testMarked() {
        assertThatThrownBy(() -> emailService.sendComposed(context,
                mail("Re: " + EmailContentTestMarker.PLAYWRIGHT_MARKER)))
                .isInstanceOf(TestMarkedEmailException.class);
        verifyNoInteractions(recorder);
    }

    @Test
    @DisplayName("ADR-015: a reserved-domain recipient is refused")
    void reserved() {
        ComposedMail toReserved = new ComposedMail("A via BATbern", null, "someone@example.com", "Re: x", "<p>x</p>",
                "x", "replies+abcdefghijklmnop@batbern.ch", MESSAGE_ID, null, List.of());
        assertThatThrownBy(() -> emailService.sendComposed(context, toReserved))
                .isInstanceOf(ReservedEmailRecipientException.class);
        verifyNoInteractions(sesClient);
    }

    @Test
    @DisplayName("local dev: captured to the dev inbox and recorded as local-{captureId}")
    void local() {
        ReflectionTestUtils.setField(emailService, "sesClient", null);
        LocalEmailCapture capture = mock(LocalEmailCapture.class);
        UUID captured = UUID.randomUUID();
        when(capture.capture(anyString(), anyList(), anyString(), anyString(), anyString(), anyString(), anyList()))
                .thenReturn(captured);
        ReflectionTestUtils.setField(emailService, "localEmailCapture", capture);

        emailService.sendComposed(context, mail("Re: Slides"));

        verify(capture).capture(eq("speaker@batbern-test.ch"), eq(List.of()), eq("Re: Slides"),
                eq("<p>Thanks, see you.</p>"), eq("noreply@batbern.ch"), eq("Anna Muster via BATbern"), eq(List.of()));
        ArgumentCaptor<OutboundMail> mail = ArgumentCaptor.forClass(OutboundMail.class);
        verify(recorder).record(eq(context), mail.capture());
        assertThat(mail.getValue().sesMessageId()).isEqualTo("local-" + captured);
    }
}
