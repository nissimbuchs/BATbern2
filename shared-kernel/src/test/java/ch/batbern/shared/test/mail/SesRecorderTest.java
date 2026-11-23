package ch.batbern.shared.test.mail;

import ch.batbern.shared.mail.MailContext;
import ch.batbern.shared.mail.OutboundMail;
import ch.batbern.shared.mail.OutboundMailRecorder;
import ch.batbern.shared.service.EmailService;
import ch.batbern.shared.service.EmailService.EmailAttachment;
import ch.batbern.shared.test.mail.SesRecorder.RecordedMail;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.services.sesv2.SesV2Client;
import software.amazon.awssdk.services.sesv2.model.SendEmailRequest;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The recorder parses what the real {@link EmailService} hands to SES, on every send path. */
@DisplayName("SesRecorder")
class SesRecorderTest {

    private static final String ANNA = "success+anna@simulator.amazonses.com";
    private static final String BEAT = "success+beat@simulator.amazonses.com";
    private static final MailContext CONTEXT =
            MailContext.individual("BATbern60", MailContext.Audience.PARTNERS, "partner-meeting-invite");

    private SesRecorder recorder;
    private EmailService emailService;

    @BeforeEach
    void setUp() {
        recorder = new SesRecorder();
        emailService = new EmailService();
        ReflectionTestUtils.setField(emailService, "sesClient", recorder.client());
        ReflectionTestUtils.setField(emailService, "fromEmail", "noreply@batbern.ch");
        ReflectionTestUtils.setField(emailService, "fromName", "BATbern");
        ReflectionTestUtils.setField(emailService, "replyToEmail", "replies@batbern.ch");
    }

    @Test
    @DisplayName("records a SendEmail request: To, Cc, Reply-To, subject, html, mail_group tag")
    void should_recordSendEmail() {
        emailService.sendHtmlEmailSync(CONTEXT, ANNA, List.of(BEAT), "Einladung", "<p>Grüezi</p>", null,
                "events@batbern.ch");

        RecordedMail mail = recorder.single();
        assertThat(mail.raw()).isFalse();
        assertThat(mail.to()).containsExactly(ANNA);
        assertThat(mail.cc()).containsExactly(BEAT);
        assertThat(mail.replyTo()).containsExactly("events@batbern.ch");
        assertThat(mail.subject()).isEqualTo("Einladung");
        assertThat(mail.html()).isEqualTo("<p>Grüezi</p>");
        assertThat(mail.attachmentNames()).isEmpty();
        assertThat(mail.tags()).containsKey("mail_group");
        assertThat(mail.sesMessageId()).startsWith("ses-recorder-");
        assertThat(recorder.mailsTo(BEAT.toUpperCase())).containsExactly(mail);
    }

    @Test
    @DisplayName("parses a raw MIME send: every group recipient in To:, html part, attachment names")
    void should_parseRawMimeSend() {
        emailService.sendGroupHtmlEmailWithAttachments(CONTEXT, List.of(ANNA, BEAT), List.of(), "Partner-Meeting",
                "<p>Agenda</p>", List.of(new EmailAttachment("invite.ics", "BEGIN:VCALENDAR".getBytes(
                        StandardCharsets.UTF_8), "text/calendar", true)));

        RecordedMail mail = recorder.single();
        assertThat(mail.raw()).isTrue();
        assertThat(mail.to()).containsExactly(ANNA, BEAT);
        assertThat(mail.cc()).isEmpty();
        assertThat(mail.replyTo()).containsExactly("replies@batbern.ch");
        assertThat(mail.subject()).isEqualTo("Partner-Meeting");
        assertThat(mail.html()).isEqualTo("<p>Agenda</p>");
        assertThat(mail.attachmentNames()).containsExactly("invite.ics");
        assertThat(mail.messageIdHeader()).endsWith("@batbern.ch>");
    }

    @Test
    @DisplayName("keeps the envelope of a forwarded raw message apart from its headers")
    void should_keepEnvelopeOfRawForward() {
        byte[] mime = ("From: someone@batbern.ch\r\nTo: info@batbern.ch\r\nSubject: Frage\r\n"
                + "Content-Type: text/plain; charset=UTF-8\r\n\r\nHallo").getBytes(StandardCharsets.UTF_8);

        emailService.sendRawMessage(mime, List.of(ANNA), null);

        RecordedMail mail = recorder.single();
        assertThat(mail.to()).containsExactly("info@batbern.ch");
        assertThat(mail.envelope()).containsExactly(ANNA);
        assertThat(mail.text()).isEqualTo("Hallo");
    }

    /**
     * Production 2026-11-22: a 10.9 MB mail to ok@ failed for all 12 recipients, because SES API v1
     * refuses a message over 10 MB. SES API v2 takes up to 40 MB; the recorder enforces that cap.
     */
    @Test
    @DisplayName("a 15 MB raw forward goes through SES v2 SendEmail with raw content and is delivered")
    void should_forward15MbRawMessage_throughSesV2() {
        OutboundMailRecorder outbound = Mockito.mock(OutboundMailRecorder.class);
        ReflectionTestUtils.setField(emailService, "outboundMailRecorder", outbound);
        byte[] mime = rawMessageOfSize(15 * 1024 * 1024);

        emailService.sendRawMessage(CONTEXT, mime, List.of(ANNA, BEAT), List.of(), "Grosse Beilage", "<p>b</p>", null);

        assertThat(recorder.client()).isInstanceOf(SesV2Client.class);
        ArgumentCaptor<SendEmailRequest> request = ArgumentCaptor.forClass(SendEmailRequest.class);
        Mockito.verify(recorder.client()).sendEmail(request.capture());
        assertThat(request.getValue().content().raw().data().asByteArray()).hasSize(mime.length);
        assertThat(request.getValue().content().simple()).isNull();
        RecordedMail mail = recorder.single();
        assertThat(mail.raw()).isTrue();
        assertThat(mail.envelope()).containsExactly(ANNA, BEAT);
        ArgumentCaptor<OutboundMail> record = ArgumentCaptor.forClass(OutboundMail.class);
        Mockito.verify(outbound).record(Mockito.eq(CONTEXT), record.capture());
        assertThat(record.getValue().failureReason()).isNull();
        assertThat(record.getValue().sesMessageId()).isEqualTo(mail.sesMessageId());
    }

    @Test
    @DisplayName("a 41 MB raw message is refused as SES v2 refuses it, and recorded FAILED")
    void should_refuseRawMessageOver40Mb() {
        OutboundMailRecorder outbound = Mockito.mock(OutboundMailRecorder.class);
        ReflectionTestUtils.setField(emailService, "outboundMailRecorder", outbound);
        byte[] mime = rawMessageOfSize(41 * 1024 * 1024);

        assertThatThrownBy(() -> emailService.sendRawMessage(CONTEXT, mime, List.of(ANNA), List.of(), "Zu gross",
                "<p>b</p>", null)).isInstanceOf(EmailService.EmailSendException.class);

        assertThat(recorder.mails()).isEmpty();
        ArgumentCaptor<OutboundMail> record = ArgumentCaptor.forClass(OutboundMail.class);
        Mockito.verify(outbound).record(Mockito.eq(CONTEXT), record.capture());
        assertThat(record.getValue().sesMessageId()).isNull();
        assertThat(record.getValue().failureReason()).isEqualTo(SesRecorder.MESSAGE_TOO_LARGE);
    }

    /** A forwarded MIME message of exactly {@code size} bytes: headers plus a base64-like body. */
    private static byte[] rawMessageOfSize(int size) {
        byte[] head = ("From: someone@example.com\r\nTo: ok@batbern.ch\r\nSubject: Grosse Beilage\r\n"
                + "Content-Type: text/plain; charset=US-ASCII\r\n\r\n").getBytes(StandardCharsets.US_ASCII);
        byte[] mime = new byte[size];
        System.arraycopy(head, 0, mime, 0, head.length);
        Arrays.fill(mime, head.length, size, (byte) 'A');
        for (int i = head.length + 76; i < size - 1; i += 78) {
            mime[i] = '\r';
            mime[i + 1] = '\n';
        }
        return mime;
    }

    @Test
    @DisplayName("failWith makes sends throw until reset; reset forgets the mails")
    void should_failAndReset() {
        emailService.sendHtmlEmailSync(ANNA, "Erste", "<p>1</p>");
        recorder.failWith(() -> SesRecorder.sesError("Throttling", "throttled"));

        assertThatThrownBy(() -> emailService.sendHtmlEmailSync(ANNA, "Zweite", "<p>2</p>"))
                .isInstanceOf(EmailService.EmailSendException.class);
        assertThat(recorder.mails()).hasSize(1);

        recorder.reset();
        assertThat(recorder.mails()).isEmpty();
        emailService.sendHtmlEmailSync(ANNA, "Dritte", "<p>3</p>");
        assertThat(recorder.single().subject()).isEqualTo("Dritte");
    }

    @Test
    @DisplayName("failFor refuses one address only; the others are recorded")
    void should_failOneAddress_only() {
        recorder.failFor(BEAT, () -> SesRecorder.sesError("MessageRejected", "rejected"));

        emailService.sendHtmlEmailSync(ANNA, "An Anna", "<p>1</p>");
        assertThatThrownBy(() -> emailService.sendHtmlEmailSync(BEAT.toUpperCase(), "An Beat", "<p>2</p>"))
                .isInstanceOf(EmailService.EmailSendException.class);

        assertThat(recorder.single().to()).containsExactly(ANNA);
    }

    @Test
    @DisplayName("single fails when nothing was sent")
    void should_failSingle_when_noMail() {
        assertThatThrownBy(recorder::single).isInstanceOf(AssertionError.class).hasMessageContaining("got 0");
    }

    @Test
    @DisplayName("reset first drains the async sends, so a late mail of one test cannot reach the next")
    void should_drainAsyncSendsBeforeForgetting_when_reset() {
        List<String> order = new java.util.ArrayList<>();
        SesRecorder draining = new SesRecorder(() -> {
            order.add("drain");
            emailService.sendHtmlEmailSync(ANNA, "late", "<p>late</p>");
        });
        ReflectionTestUtils.setField(emailService, "sesClient", draining.client());

        draining.reset();

        assertThat(order).containsExactly("drain");
        assertThat(draining.mails()).as("the late mail was drained, then forgotten").isEmpty();
    }

    @Test
    @DisplayName("awaitAsyncSends drains without forgetting, for a test that asserts an async send")
    void should_keepMails_when_awaitAsyncSends() {
        SesRecorder draining = new SesRecorder(() -> emailService.sendHtmlEmailSync(ANNA, "async", "<p>a</p>"));
        ReflectionTestUtils.setField(emailService, "sesClient", draining.client());

        draining.awaitAsyncSends();

        assertThat(draining.single().subject()).isEqualTo("async");
    }
}
