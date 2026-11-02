package ch.batbern.events.mailcenter.inbound;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Story 16.2b AC3: which event codes a subject names. */
@DisplayName("Story 16.2b: event codes in the subject")
class ConversationThreaderSubjectTest {

    @Test
    @DisplayName("one code behind reply prefixes")
    void oneCode() {
        assertThat(ConversationThreader.eventCodesIn("Re: AW: BATbern60 Anmeldung")).containsExactly("BATbern60");
    }

    @Test
    @DisplayName("the same code twice is one code; two codes are two")
    void severalCodes() {
        assertThat(ConversationThreader.eventCodesIn("BATbern60 / BATbern60")).containsExactly("BATbern60");
        assertThat(ConversationThreader.eventCodesIn("BATbern60 oder BATbern61")).hasSize(2);
    }

    @Test
    @DisplayName("no code, null subject")
    void none() {
        assertThat(ConversationThreader.eventCodesIn("Frage zum Parkplatz")).isEmpty();
        assertThat(ConversationThreader.eventCodesIn(null)).isEmpty();
    }
}
