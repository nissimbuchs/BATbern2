package ch.batbern.shared.test.mail;

import ch.batbern.shared.mail.MailContext;
import ch.batbern.shared.service.EmailService;
import ch.batbern.shared.service.EmailService.EmailAttachment;
import ch.batbern.shared.test.mail.SesRecorder.RecordedMail;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
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
}
