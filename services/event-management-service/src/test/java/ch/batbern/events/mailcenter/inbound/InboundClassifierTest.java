package ch.batbern.events.mailcenter.inbound;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Story 16.2a: InboundClassifier")
class InboundClassifierTest {

    private static final String DOMAIN = "batbern.ch";

    private static InboundClassifier.Kind classify(String headers, String body) {
        byte[] raw = (headers + "\r\n" + body + "\r\n").getBytes(StandardCharsets.UTF_8);
        return InboundClassifier.classify(InboundMailParser.parse(raw), raw, DOMAIN);
    }

    private static String base(String subject) {
        return "From: \"A Speaker\" <speaker@example.com>\r\nTo: batbern60-moderator@batbern.ch\r\n"
                + "Subject: " + subject + "\r\nMessage-ID: <x@example.com>\r\n"
                + "Content-Type: text/plain; charset=UTF-8\r\n";
    }

    @Test
    void should_beHuman_when_plainQuestion() {
        assertThat(classify(base("Re: Ihr Vortrag"), "Wann ist mein Slot?")).isEqualTo(InboundClassifier.Kind.HUMAN);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "Out of Office: Ihr Vortrag", "Automatic reply: talk", "Auto-Reply: x", "AutoReply x",
        "Abwesenheitsnotiz: Re: Vortrag", "Automatische Antwort: Vortrag", "Abwesend bis 3.11.",
        "Réponse automatique : conférence", "Absence: talk", "Risposta automatica: talk", "OUT OF OFFICE"})
    void should_beOutOfOffice_when_subjectStartsWithPattern(String subject) {
        assertThat(classify(base(subject), "text")).isEqualTo(InboundClassifier.Kind.OUT_OF_OFFICE);
    }

    @Test
    void should_beHuman_when_patternOnlyInsideSubject() {
        assertThat(classify(base("Re: my absence on Friday"), "text")).isEqualTo(InboundClassifier.Kind.HUMAN);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "Auto-Submitted: auto-replied", "Precedence: auto_reply", "X-Autoreply: yes", "X-Autorespond: yes"})
    void should_beOutOfOffice_when_autoReplyHeader(String header) {
        assertThat(classify(base("Re: Vortrag") + header + "\r\n", "text"))
                .isEqualTo(InboundClassifier.Kind.OUT_OF_OFFICE);
    }

    @Test
    void should_beHuman_when_autoSubmittedNo() {
        assertThat(classify(base("Re: Vortrag") + "Auto-Submitted: no\r\n", "text"))
                .isEqualTo(InboundClassifier.Kind.HUMAN);
    }

    @Test
    void should_beBounce_when_mailerDaemon() {
        String headers = "From: MAILER-DAEMON@example.com\r\nTo: batbern60-moderator@batbern.ch\r\n"
                + "Subject: Undelivered Mail\r\nContent-Type: text/plain\r\n";
        assertThat(classify(headers, "failed")).isEqualTo(InboundClassifier.Kind.BOUNCE);
    }

    @Test
    void should_beLoop_when_forwardedMarkerOrOwnDomain() {
        assertThat(classify(base("Re: x") + "X-BATbern-Forwarded: 1\r\n", "t")).isEqualTo(InboundClassifier.Kind.LOOP);
        String own = "From: noreply@batbern.ch\r\nTo: batbern60-moderator@batbern.ch\r\nSubject: x\r\n"
                + "Content-Type: text/plain\r\n";
        assertThat(classify(own, "t")).isEqualTo(InboundClassifier.Kind.LOOP);
    }

    @Test
    void should_beCalendarReply_when_imipReply() {
        String headers = "From: speaker@example.com\r\nTo: calendar@batbern.ch\r\nSubject: Accepted: BATbern60\r\n"
                + "Content-Type: text/calendar; method=REPLY; charset=UTF-8\r\n";
        assertThat(classify(headers, "BEGIN:VCALENDAR\r\nMETHOD:REPLY\r\nEND:VCALENDAR"))
                .isEqualTo(InboundClassifier.Kind.CALENDAR_REPLY);
    }

    // ── Story 16.7 AC3/AC4 ───────────────────────────────────────────────────────────────────

    @Test
    void should_beVirus_when_virusVerdictFails_evenForCalendarReply() {
        String headers = base("Accepted: Vortrag") + "X-SES-Virus-Verdict: FAIL\r\n"
                + "Content-Type: text/calendar; method=REPLY\r\n";
        assertThat(classify(headers, "BEGIN:VCALENDAR")).isEqualTo(InboundClassifier.Kind.VIRUS);
    }

    @Test
    void should_beSpam_when_spamVerdictFails() {
        assertThat(classify(base("You won") + "X-SES-Spam-Verdict: FAIL\r\n", "Click here"))
                .isEqualTo(InboundClassifier.Kind.SPAM);
    }

    @Test
    void should_countOutOfOffice_when_spamVerdictFailsOnAutoReply() {
        assertThat(classify(base("Out of Office: x") + "X-SES-Spam-Verdict: FAIL\r\n", "away"))
                .isEqualTo(InboundClassifier.Kind.OUT_OF_OFFICE);
    }

    @Test
    void should_beHuman_when_verdictGray() {
        assertThat(classify(base("Frage") + "X-SES-Spam-Verdict: GRAY\r\n", "text"))
                .isEqualTo(InboundClassifier.Kind.HUMAN);
    }
}
