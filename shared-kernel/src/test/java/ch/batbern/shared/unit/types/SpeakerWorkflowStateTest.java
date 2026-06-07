package ch.batbern.shared.unit.types;

import ch.batbern.shared.types.SpeakerWorkflowState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Test for SpeakerWorkflowState enum (8-state model per ADR-009 §0.1).
 *
 * Story 11.B.1: Reduce SpeakerWorkflowState enum to 8 states.
 * Removed: SLOT_ASSIGNED, CONFIRMED, OVERFLOW, WITHDREW (per ADR-009 §0.1, §0.7).
 */
class SpeakerWorkflowStateTest {

    @Test
    @DisplayName("should_haveExactly8Values_when_enumInspected")
    void should_haveExactly8Values_when_enumInspected() {
        assertThat(SpeakerWorkflowState.values()).hasSize(8);
    }

    @Test
    @DisplayName("should_containAll8ExpectedStates_when_enumInspected")
    void should_containAll8ExpectedStates_when_enumInspected() {
        assertThat(SpeakerWorkflowState.values())
            .containsExactlyInAnyOrder(
                SpeakerWorkflowState.IDENTIFIED,
                SpeakerWorkflowState.CONTACTED,
                SpeakerWorkflowState.READY,
                SpeakerWorkflowState.INVITED,
                SpeakerWorkflowState.ACCEPTED,
                SpeakerWorkflowState.CONTENT_SUBMITTED,
                SpeakerWorkflowState.QUALITY_REVIEWED,
                SpeakerWorkflowState.DECLINED
            );
    }

    @Test
    @DisplayName("should_throwIllegalArgumentException_when_valueOfCalled_withSlotAssigned")
    void should_throwIllegalArgumentException_when_valueOfCalled_withSlotAssigned() {
        assertThatThrownBy(() -> SpeakerWorkflowState.valueOf("SLOT_ASSIGNED"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("should_throwIllegalArgumentException_when_valueOfCalled_withConfirmed")
    void should_throwIllegalArgumentException_when_valueOfCalled_withConfirmed() {
        assertThatThrownBy(() -> SpeakerWorkflowState.valueOf("CONFIRMED"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("should_throwIllegalArgumentException_when_valueOfCalled_withOverflow")
    void should_throwIllegalArgumentException_when_valueOfCalled_withOverflow() {
        assertThatThrownBy(() -> SpeakerWorkflowState.valueOf("OVERFLOW"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("should_throwIllegalArgumentException_when_valueOfCalled_withWithdrew")
    void should_throwIllegalArgumentException_when_valueOfCalled_withWithdrew() {
        assertThatThrownBy(() -> SpeakerWorkflowState.valueOf("WITHDREW"))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
