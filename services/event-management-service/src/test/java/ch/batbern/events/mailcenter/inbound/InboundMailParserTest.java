package ch.batbern.events.mailcenter.inbound;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Story 16.2a: InboundMailParser")
class InboundMailParserTest {

    private static byte[] fixture(String name) throws IOException {
        try (InputStream in = InboundMailParserTest.class.getResourceAsStream("/mail/" + name)) {
            assertThat(in).as(name).isNotNull();
            return in.readAllBytes();
        }
    }

    @Test
    @DisplayName("multipart/alternative: text part preferred, headers decoded, ids without brackets")
    void should_readHeadersAndPreferText_when_multipartAlternative() throws IOException {
        InboundMail mail = InboundMailParser.parse(fixture("speaker-multipart-alternative.eml"));

        assertThat(mail.messageId()).isEqualTo("speaker-alt-1@simulator.amazonses.com");
        assertThat(mail.inReplyTo())
                .isEqualTo("0107019a00000000-aaaa-bbbb-cccc-000000000001-000000@eu-central-1.amazonses.com");
        assertThat(mail.references()).containsExactly("first@example.com",
                "0107019a00000000-aaaa-bbbb-cccc-000000000001-000000@eu-central-1.amazonses.com");
        assertThat(mail.fromAddress()).isEqualTo("success+speaker@simulator.amazonses.com");
        assertThat(mail.fromName()).isEqualTo("Jürg Müller");
        assertThat(mail.replyTo()).isEqualTo("success+speaker-reply@simulator.amazonses.com");
        assertThat(mail.to()).containsExactly("batbern60-moderator@batbern.ch");
        assertThat(mail.cc()).containsExactly("someone@example.com");
        assertThat(mail.subject()).isEqualTo("Re: Für den Vortrag am 6. November");
        assertThat(mail.text()).contains("Grüezi, ich brauche für meine Folien einen Beamer mit HDMI.");
        assertThat(mail.html()).isNull();
        assertThat(mail.attachments()).isEmpty();
    }

    @Test
    @DisplayName("HTML only: sanitized, no image, style, script or event handler; link kept")
    void should_sanitizeHtml_when_noTextPart() throws IOException {
        InboundMail mail = InboundMailParser.parse(fixture("html-only.eml"));

        assertThat(mail.text()).contains("Wann beginnt mein Slot?");
        assertThat(mail.html()).contains("Wann beginnt <b>mein</b> Slot?")
                .contains("href=\"https://example.com/agenda\"")
                .doesNotContain("<img", "tracker.example.com", "<script", "alert(", "<style", "style=",
                        "onclick");
    }

    @Test
    @DisplayName("16.6: the attachment's bytes, in the order of the metadata")
    void should_returnAttachmentBytes_when_attachmentPresent() throws IOException {
        byte[] raw = fixture("attachment.eml");
        InboundMail mail = InboundMailParser.parse(raw);

        assertThat(InboundMailParser.attachmentContents(raw)).singleElement()
                .satisfies(bytes -> assertThat((long) bytes.length).isEqualTo(mail.attachments().get(0).size()));
    }

    @Test
    @DisplayName("attachment: metadata only, the text part is the body")
    void should_listAttachmentMetadata_when_attachmentPresent() throws IOException {
        InboundMail mail = InboundMailParser.parse(fixture("attachment.eml"));

        assertThat(mail.text()).contains("Anbei meine Folien.");
        assertThat(mail.attachments()).singleElement().satisfies(a -> {
            assertThat(a.name()).isEqualTo("slides.pdf");
            assertThat(a.contentType()).isEqualTo("application/pdf");
            assertThat(a.size()).isPositive();
        });
    }

    @Test
    @DisplayName("classification headers are exposed")
    void should_exposeClassificationHeaders_when_autoReply() throws IOException {
        InboundMail mail = InboundMailParser.parse(fixture("ooo-reply.eml"));

        assertThat(mail.autoSubmitted()).isEqualTo("auto-replied");
        assertThat(mail.returnPath()).isEqualTo("<success+registrant@simulator.amazonses.com>");
        assertThat(mail.subject()).startsWith("Abwesenheitsnotiz");
    }

    @Test
    @DisplayName("no Message-ID, no From: parsed without failing")
    void should_tolerateMissingHeaders_when_minimalMail() {
        byte[] raw = "Subject: hi\r\n\r\nhello\r\n".getBytes(StandardCharsets.UTF_8);
        InboundMail mail = InboundMailParser.parse(raw);

        assertThat(mail.messageId()).isNull();
        assertThat(mail.fromAddress()).isNull();
        assertThat(mail.references()).isEmpty();
        assertThat(mail.text()).contains("hello");
    }
}
