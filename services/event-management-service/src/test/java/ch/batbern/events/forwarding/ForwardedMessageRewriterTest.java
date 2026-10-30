package ch.batbern.events.forwarding;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

/** Ports T9 of the Lambda's email-forwarder.test.ts. */
@DisplayName("ForwardedMessageRewriter")
class ForwardedMessageRewriterTest {

    private static final String RAW = "Return-Path: <bounce@example.org>\r\n"
            + "DKIM-Signature: v=1; a=rsa-sha256; d=example.org; s=x;\r\n"
            + "\tb=abc\r\n"
            + "Sender: list@example.org\r\n"
            + "From: \"Anna Muster\" <anna@example.org>\r\n"
            + "To: info@batbern.ch\r\n"
            + "Cc: other@example.org\r\n"
            + "Subject: Frage zur Anmeldung\r\n"
            + "Message-ID: <original-123@example.org>\r\n"
            + "MIME-Version: 1.0\r\n"
            + "Content-Type: text/plain; charset=UTF-8\r\n"
            + "Content-Transfer-Encoding: 8bit\r\n"
            + "\r\n"
            + "Grüezi mitenand\r\n"
            + "Zeile zwei\r\n";

    private final ForwardedMessageRewriter rewriter = new ForwardedMessageRewriter();

    @Test
    @DisplayName("should_rewriteFromAndReplyTo_when_forwarding")
    void should_rewriteFromAndReplyTo_when_forwarding() throws Exception {
        MimeMessage out = parse(rewrite(null, null));

        assertThat(out.getHeader("From", null)).contains("Anna Muster via BATbern").contains("<noreply@batbern.ch>");
        assertThat(out.getHeader("Reply-To", null)).isEqualTo("anna@example.org");
    }

    @Test
    @DisplayName("should_preserveSubjectMessageIdAndBodyBytes")
    void should_preserveSubjectMessageIdAndBodyBytes() throws Exception {
        byte[] rewritten = rewrite(null, null);
        MimeMessage out = parse(rewritten);

        assertThat(out.getSubject()).isEqualTo("Frage zur Anmeldung");
        assertThat(out.getMessageID()).isEqualTo("<original-123@example.org>");
        String text = new String(rewritten, StandardCharsets.UTF_8);
        assertThat(text).endsWith("\r\n\r\nGrüezi mitenand\r\nZeile zwei\r\n");
    }

    @Test
    @DisplayName("should_stripReturnPathSenderAndDkim")
    void should_stripReturnPathSenderAndDkim() throws Exception {
        MimeMessage out = parse(rewrite(null, null));

        assertThat(out.getHeader("Return-Path")).isNull();
        assertThat(out.getHeader("Sender")).isNull();
        assertThat(out.getHeader("DKIM-Signature")).isNull();
    }

    @Test
    @DisplayName("should_markAsForwarded")
    void should_markAsForwarded() throws Exception {
        assertThat(parse(rewrite(null, null)).getHeader(MailClassifier.FORWARDED_MARKER_HEADER)).containsExactly("1");
    }

    @Test
    @DisplayName("should_leaveToAndCcUnchanged_when_noOverride")
    void should_leaveToAndCcUnchanged_when_noOverride() throws Exception {
        MimeMessage out = parse(rewrite(null, null));

        assertThat(out.getHeader("To", null)).isEqualTo("info@batbern.ch");
        assertThat(out.getHeader("Cc", null)).isEqualTo("other@example.org");
    }

    @Test
    @DisplayName("should_overrideToAndCc_when_recipientsProvided")
    void should_overrideToAndCc_when_recipientsProvided() throws Exception {
        MimeMessage out = parse(rewrite(List.of("s1@example.org", "s2@example.org"), List.of("mod@example.org")));

        assertThat(out.getHeader("To", null)).isEqualTo("s1@example.org, s2@example.org");
        assertThat(out.getHeader("Cc", null)).isEqualTo("mod@example.org");
    }

    @Test
    @DisplayName("should_removeCc_when_emptyCcOverride")
    void should_removeCc_when_emptyCcOverride() throws Exception {
        assertThat(parse(rewrite(List.of("s1@example.org"), List.of())).getHeader("Cc")).isNull();
    }

    @Test
    @DisplayName("should_useLocalPart_when_senderHasNoDisplayName")
    void should_useLocalPart_when_senderHasNoDisplayName() throws Exception {
        byte[] out = rewriter.rewrite(RAW.getBytes(StandardCharsets.UTF_8), null, "anna@example.org",
                "noreply@batbern.ch", null, null);

        assertThat(parse(out).getHeader("From", null)).contains("anna via BATbern");
    }

    private byte[] rewrite(List<String> to, List<String> cc) {
        return rewriter.rewrite(RAW.getBytes(StandardCharsets.UTF_8), "Anna Muster", "anna@example.org",
                "noreply@batbern.ch", to, cc);
    }

    private static MimeMessage parse(byte[] raw) throws Exception {
        return new MimeMessage(Session.getInstance(new Properties()), new ByteArrayInputStream(raw));
    }

    @Test
    @DisplayName("should_setReplyToOverride_when_provided")
    void should_setReplyToOverride_when_provided() throws Exception {
        byte[] out = rewriter.rewrite(RAW.getBytes(StandardCharsets.UTF_8), "Anna Muster", "anna@example.org",
                "noreply@batbern.ch", "batbern60-moderator@batbern.ch", List.of("s1@example.org"), List.of());

        assertThat(parse(out).getHeader("Reply-To", null)).isEqualTo("batbern60-moderator@batbern.ch");
    }
}
