package ch.batbern.events.forwarding;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("MailAuthentication")
class MailAuthenticationTest {

    /** Shape SES writes, captured (addresses replaced) from a stored inbound mail on 2026-10-26. */
    private static final String SES_PASS = "amazonses.com;\r\n"
            + " spf=pass (spfCheck: domain of mail.example.ch designates 192.0.2.7 as permitted sender)"
            + " client-ip=192.0.2.7; envelope-from=bounce@mail.example.ch; helo=mail.example.ch;\r\n"
            + " dkim=pass header.i=@example.ch;\r\n"
            + " dmarc=pass header.from=example.ch;";

    @Test
    @DisplayName("should_verifySender_when_dmarcPasses")
    void should_verifySender_when_dmarcPasses() {
        MailAuthentication auth = MailAuthentication.fromHeaders(new String[] {SES_PASS});

        assertThat(auth.verifiesSender("anna@example.ch")).isTrue();
        assertThat(auth.dmarc()).isEqualTo("pass");
    }

    @Test
    @DisplayName("should_reject_when_dmarcFails_evenWithAlignedDkim")
    void should_reject_when_dmarcFails_evenWithAlignedDkim() {
        String header = "amazonses.com; spf=fail; dkim=pass header.i=@example.ch; dmarc=fail header.from=example.ch;";

        assertThat(MailAuthentication.fromHeaders(new String[] {header}).verifiesSender("anna@example.ch")).isFalse();
    }

    @Test
    @DisplayName("should_verifySender_when_noDmarcPolicyButAlignedDkim")
    void should_verifySender_when_noDmarcPolicyButAlignedDkim() {
        String header = "amazonses.com; spf=pass; dkim=pass header.i=@mail.example.ch; dmarc=none header.from=example.ch;";

        assertThat(MailAuthentication.fromHeaders(new String[] {header}).verifiesSender("anna@example.ch")).isTrue();
    }

    @Test
    @DisplayName("should_reject_when_noDmarcAndDkimFromOtherDomain")
    void should_reject_when_noDmarcAndDkimFromOtherDomain() {
        String header = "amazonses.com; spf=pass; dkim=pass header.d=attacker.example; dmarc=none;";

        assertThat(MailAuthentication.fromHeaders(new String[] {header}).verifiesSender("anna@example.ch")).isFalse();
    }

    @Test
    @DisplayName("should_reject_when_onlySpfPasses")
    void should_reject_when_onlySpfPasses() {
        // SPF authenticates the envelope sender, not the From an organizer check relies on.
        String header = "amazonses.com; spf=pass; dkim=none;";

        assertThat(MailAuthentication.fromHeaders(new String[] {header}).verifiesSender("anna@example.ch")).isFalse();
    }

    @Test
    @DisplayName("should_trustOnlyTopmostHeader_when_senderAddedForgedPassBelow")
    void should_trustOnlyTopmostHeader_when_senderAddedForgedPassBelow() {
        String sesVerdict = "amazonses.com; spf=fail; dkim=none; dmarc=fail header.from=example.ch;";
        String forged = "amazonses.com; spf=pass; dkim=pass header.i=@example.ch; dmarc=pass;";

        MailAuthentication auth = MailAuthentication.fromHeaders(new String[] {sesVerdict, forged});

        assertThat(auth.verifiesSender("anna@example.ch")).isFalse();
    }

    @Test
    @DisplayName("should_reject_when_topmostHeaderIsNotFromSes")
    void should_reject_when_topmostHeaderIsNotFromSes() {
        String foreign = "mx.example.org; dmarc=pass header.from=example.ch;";

        assertThat(MailAuthentication.fromHeaders(new String[] {foreign}).verifiesSender("anna@example.ch")).isFalse();
    }

    @Test
    @DisplayName("should_reject_when_noHeader")
    void should_reject_when_noHeader() {
        assertThat(MailAuthentication.fromHeaders(null).verifiesSender("anna@example.ch")).isFalse();
        assertThat(MailAuthentication.fromHeaders(new String[0]).verifiesSender("anna@example.ch")).isFalse();
    }
}
