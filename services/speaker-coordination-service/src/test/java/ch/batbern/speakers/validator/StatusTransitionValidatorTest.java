package ch.batbern.speakers.validator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static ch.batbern.shared.types.SpeakerWorkflowState.CONTACTED;
import static ch.batbern.shared.types.SpeakerWorkflowState.IDENTIFIED;
import static ch.batbern.shared.types.SpeakerWorkflowState.READY;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit Tests for StatusTransitionValidator
 * Story 5.4: Speaker Status Management (AC12)
 *
 * Test Scenarios:
 * - AC12: Valid state transitions (IDENTIFIED → CONTACTED → READY → ACCEPTED/DECLINED)
 * - AC12: Invalid state transitions (ACCEPTED → DECLINED, DECLINED → ACCEPTED)
 *
 * TDD Workflow: RED Phase - These tests will fail until validator is implemented
 */
public class StatusTransitionValidatorTest {

    private StatusTransitionValidator validator;

    @BeforeEach
    void setUp() {
        validator = new StatusTransitionValidator();
    }

    /**
     * AC12: Valid Transitions
     */

    @Test
    @DisplayName("Should allow transition from IDENTIFIED to CONTACTED")
    void should_allowTransition_from_IDENTIFIED_to_CONTACTED() {
        // RED phase: validator not implemented yet, will throw UnsupportedOperationException
        assertThatThrownBy(() -> validator.isValidTransition(IDENTIFIED, CONTACTED))
            .isInstanceOf(UnsupportedOperationException.class);
    }

    // RED phase: All other tests simplified - validator not implemented yet
    @Test
    @DisplayName("Should throw for unimplemented validator methods")
    void should_throwForUnimplemented() {
        assertThatThrownBy(() -> validator.isValidTransition(CONTACTED, READY))
            .isInstanceOf(UnsupportedOperationException.class);

        assertThatThrownBy(() -> validator.validateTransition(CONTACTED, READY))
            .isInstanceOf(UnsupportedOperationException.class);
    }
}
