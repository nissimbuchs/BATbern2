package ch.batbern.events.service;

import ch.batbern.events.dto.generated.users.AdditionalEmail;
import ch.batbern.events.dto.generated.users.UserResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * #1244 (owner decision 2026-11-07): event mails go to the primary address; a copy only to a
 * confirmed additional address whose owner switched the copy on. Before, every additional
 * address got a CC, confirmed or not.
 */
class EventMailCopiesTest {

    private static AdditionalEmail address(String email, boolean confirmed, Boolean receive) {
        AdditionalEmail a = new AdditionalEmail();
        a.setEmail(email);
        a.setVerifiedAt(confirmed ? OffsetDateTime.now() : null);
        a.setReceiveEventMail(receive);
        return a;
    }

    private static UserResponse user(AdditionalEmail... addresses) {
        UserResponse u = new UserResponse();
        u.setEmail("primary@example.com");
        u.setAdditionalEmails(List.of(addresses));
        return u;
    }

    @Test
    @DisplayName("only confirmed addresses with the copy switched on get one")
    void should_copyOnlyConfirmedOptedInAddresses() {
        UserResponse u = user(
                address("opted-in@example.com", true, true),
                address("confirmed-off@example.com", true, false),
                address("unconfirmed-on@example.com", false, true),
                address("legacy-null@example.com", true, null));

        assertThat(EventMailCopies.of(u)).containsExactly("opted-in@example.com");
    }

    @Test
    @DisplayName("no user, no additional addresses or a blank address give no copy")
    void should_giveNoCopy_when_nothingQualifies() {
        assertThat(EventMailCopies.of(null)).isEmpty();
        UserResponse none = new UserResponse();
        assertThat(EventMailCopies.of(none)).isEmpty();
        assertThat(EventMailCopies.of(user(address(" ", true, true)))).isEmpty();
    }
}
