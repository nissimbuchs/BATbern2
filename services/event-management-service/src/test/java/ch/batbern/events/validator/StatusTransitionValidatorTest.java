package ch.batbern.events.validator;

import ch.batbern.shared.exception.InvalidStateTransitionException;
import ch.batbern.shared.types.SpeakerWorkflowState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit Tests for StatusTransitionValidator
 *
 * Validates that DECLINED is reachable from every non-terminal state
 * (including CONFIRMED), and that DECLINED itself is terminal.
 */
class StatusTransitionValidatorTest {

    private final StatusTransitionValidator validator = new StatusTransitionValidator();

    @ParameterizedTest
    @EnumSource(value = SpeakerWorkflowState.class, names = {
        "IDENTIFIED", "INVITED", "CONTACTED", "READY", "ACCEPTED",
        "SLOT_ASSIGNED", "CONTENT_SUBMITTED", "QUALITY_REVIEWED", "CONFIRMED"
    })
    @DisplayName("Should allow transition to DECLINED from every active state")
    void should_allowTransitionToDeclined_from_everyActiveState(SpeakerWorkflowState fromState) {
        assertThat(validator.isValidTransition(fromState, SpeakerWorkflowState.DECLINED)).isTrue();
    }

    @ParameterizedTest
    @EnumSource(value = SpeakerWorkflowState.class, names = {"DECLINED"}, mode = EnumSource.Mode.EXCLUDE)
    @DisplayName("Should block all transitions out of DECLINED")
    void should_blockTransitionFromDeclined_toAnyOtherState(SpeakerWorkflowState toState) {
        assertThat(validator.isValidTransition(SpeakerWorkflowState.DECLINED, toState)).isFalse();
    }

    @Test
    @DisplayName("Should allow idempotent DECLINED to DECLINED transition")
    void should_allowIdempotentDeclinedTransition() {
        assertThat(validator.isValidTransition(SpeakerWorkflowState.DECLINED, SpeakerWorkflowState.DECLINED)).isTrue();
    }

    @Test
    @DisplayName("Should throw InvalidStateTransitionException for invalid transition")
    void should_throwException_when_invalidTransition() {
        assertThatThrownBy(() ->
            validator.validateTransition(SpeakerWorkflowState.DECLINED, SpeakerWorkflowState.ACCEPTED)
        ).isInstanceOf(InvalidStateTransitionException.class);
    }

    @Test
    @DisplayName("Should return false for null from state")
    void should_returnFalse_when_fromStateIsNull() {
        assertThat(validator.isValidTransition(null, SpeakerWorkflowState.DECLINED)).isFalse();
    }

    @Test
    @DisplayName("Should return false for null to state")
    void should_returnFalse_when_toStateIsNull() {
        assertThat(validator.isValidTransition(SpeakerWorkflowState.IDENTIFIED, null)).isFalse();
    }
}
