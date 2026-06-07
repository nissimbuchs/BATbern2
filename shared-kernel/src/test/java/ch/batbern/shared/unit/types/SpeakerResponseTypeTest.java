package ch.batbern.shared.unit.types;

import ch.batbern.shared.types.SpeakerResponseType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Test for SpeakerResponseType enum (ACCEPT / DECLINE only per ADR-009 §0.6).
 *
 * Story 11.B.1: Remove TENTATIVE response. Speakers respond ACCEPT or DECLINE only;
 * speakers who are unsure simply do not respond yet (reminder/escalation handles delays).
 */
class SpeakerResponseTypeTest {

    @Test
    @DisplayName("should_haveExactly2Values_when_enumInspected")
    void should_haveExactly2Values_when_enumInspected() {
        assertThat(SpeakerResponseType.values()).hasSize(2);
    }

    @Test
    @DisplayName("should_containAcceptAndDecline_when_enumInspected")
    void should_containAcceptAndDecline_when_enumInspected() {
        assertThat(SpeakerResponseType.values())
            .containsExactlyInAnyOrder(
                SpeakerResponseType.ACCEPT,
                SpeakerResponseType.DECLINE
            );
    }

    @Test
    @DisplayName("should_throwIllegalArgumentException_when_valueOfCalled_withTentative")
    void should_throwIllegalArgumentException_when_valueOfCalled_withTentative() {
        assertThatThrownBy(() -> SpeakerResponseType.valueOf("TENTATIVE"))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
