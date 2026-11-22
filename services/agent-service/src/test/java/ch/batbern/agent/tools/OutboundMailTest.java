package ch.batbern.agent.tools;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The mail contract's values (story 7): one address per recipient, counted once per person. */
@DisplayName("OutboundMail and MailRecipient")
class OutboundMailTest {

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "anna", "@example.com", "anna@", "a@b@example.com", "anna @example.com"})
    @DisplayName("anything but one address is refused, without the value in the message")
    void should_refuse_when_notOneAddress(String address) {
        assertThatThrownBy(() -> MailRecipient.external(address))
                .isInstanceOf(IllegalArgumentException.class)
                .satisfies(e -> assertThat(e.getMessage()).doesNotContain("example.com"));
    }

    @Test
    @DisplayName("addresses are stripped and lowercased; counts are distinct; both ways counts as non-organizer")
    void should_countDistinct_when_addressesRepeat() {
        OutboundMail mail = new OutboundMail(List.of(
                MailRecipient.external(" Anna@Example.com "),
                MailRecipient.external("anna@example.com"),
                MailRecipient.organizer("ben@example.com"),
                MailRecipient.external("ben@example.com"),
                MailRecipient.organizer("organizers@example.com")));

        assertThat(mail.nonOrganizerAddresses()).containsExactly("anna@example.com", "ben@example.com");
        assertThat(mail.organizerCount()).isEqualTo(1);
        assertThat(mail.distinctAddressCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("a mail has a recipient")
    void should_refuse_when_noRecipient() {
        assertThatThrownBy(() -> new OutboundMail(List.of())).isInstanceOf(IllegalArgumentException.class);
    }
}
