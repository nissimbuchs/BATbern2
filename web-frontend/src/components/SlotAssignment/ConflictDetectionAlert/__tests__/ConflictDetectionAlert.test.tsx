/**
 * ConflictDetectionAlert Component Tests (Story 5.7 - Task 4a RED Phase)
 *
 * Tests for conflict detection and resolution modal
 * Following TDD: These tests MUST fail until implementation (Task 4b)
 *
 * Coverage:
 * - AC9: Warn if speaker has conflicting commitment at same time
 * - Conflict types: room_overlap, speaker_double_booked, speaker_unavailable
 * - Resolution options with visual timeline
 */

import { render, screen, fireEvent } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { BrowserRouter } from 'react-router-dom';
import { I18nextProvider } from 'react-i18next';
import i18n from '@/i18n/config';
import { ConflictDetectionAlert } from '../ConflictDetectionAlert';
import type { TimingConflictError } from '@/types/event.types';

const renderWithProviders = (ui: React.ReactElement) => {
  return render(
    <BrowserRouter>
      <I18nextProvider i18n={i18n}>{ui}</I18nextProvider>
    </BrowserRouter>
  );
};

describe('ConflictDetectionAlert Component (Story 5.7 - Task 4a RED Phase)', () => {
  const mockRoomOverlapConflict: TimingConflictError = {
    error: 'TIMING_CONFLICT',
    message: 'Session timing conflicts with existing schedule',
    conflicts: [
      {
        type: 'room_overlap',
        conflictingSessionSlug: 'existing-session-1',
        conflictingTimeRange: {
          start: '2025-05-15T09:00:00Z',
          end: '2025-05-15T10:00:00Z',
        },
        details: 'Main Hall is already booked during this time',
      },
    ],
  };

  const mockSpeakerDoubleBookedConflict: TimingConflictError = {
    error: 'TIMING_CONFLICT',
    message: 'Speaker already scheduled at this time',
    conflicts: [
      {
        type: 'speaker_double_booked',
        conflictingSessionSlug: 'john-doe-other-session',
        conflictingTimeRange: {
          start: '2025-05-15T09:00:00Z',
          end: '2025-05-15T10:00:00Z',
        },
        details: 'Speaker john.doe is already scheduled in another session',
      },
    ],
  };

  const mockSpeakerUnavailableConflict: TimingConflictError = {
    error: 'TIMING_CONFLICT',
    message: 'Speaker unavailable during requested time',
    conflicts: [
      {
        type: 'speaker_unavailable',
        conflictingSessionSlug: null,
        conflictingTimeRange: {
          start: '2025-05-15T12:00:00Z',
          end: '2025-05-15T13:00:00Z',
        },
        details: 'Speaker marked this time as unavailable in preferences',
      },
    ],
  };

  beforeEach(() => {
    vi.clearAllMocks();
  });

  describe('Modal Rendering', () => {
    it('should_renderModal_when_conflictExists', () => {
      // AC9: Triggered on API 409 Conflict error response
      // Given: Conflict is detected
      renderWithProviders(
        <ConflictDetectionAlert
          conflict={mockRoomOverlapConflict}
          isOpen={true}
          onClose={() => {}}
          onResolve={() => {}}
        />
      );

      // Then: Modal is displayed
      expect(screen.getByRole('dialog')).toBeInTheDocument();
      expect(screen.getByText(/timing conflict/i)).toBeInTheDocument();
    });

    it('should_hideModal_when_closed', () => {
      // Given: Modal is closed
      renderWithProviders(
        <ConflictDetectionAlert
          conflict={mockRoomOverlapConflict}
          isOpen={false}
          onClose={() => {}}
          onResolve={() => {}}
        />
      );

      // Then: Modal is not visible
      expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    });
  });

  describe('Conflict Type Display', () => {
    it('should_displayRoomOverlapConflict_when_detected', () => {
      // AC9: Conflict type - room_overlap
      // Given: Room is double-booked
      renderWithProviders(
        <ConflictDetectionAlert
          conflict={mockRoomOverlapConflict}
          isOpen={true}
          onClose={() => {}}
          onResolve={() => {}}
        />
      );

      // Then: Shows room overlap message
      expect(screen.getByText(/main hall is already booked/i)).toBeInTheDocument();
      expect(screen.getByTestId('conflict-type-badge')).toHaveTextContent('Room Overlap');
      expect(screen.getByTestId('conflict-severity')).toHaveClass('severity-error');
    });

    it('should_displaySpeakerDoubleBookedConflict_when_detected', () => {
      // AC9: Conflict type - speaker_double_booked
      // Given: Speaker is assigned to overlapping sessions
      renderWithProviders(
        <ConflictDetectionAlert
          conflict={mockSpeakerDoubleBookedConflict}
          isOpen={true}
          onClose={() => {}}
          onResolve={() => {}}
        />
      );

      // Then: Shows speaker double-booked message
      expect(screen.getByText(/speaker.*already scheduled/i)).toBeInTheDocument();
      expect(screen.getByTestId('conflict-type-badge')).toHaveTextContent('Speaker Double-Booked');
      expect(screen.getByTestId('conflict-severity')).toHaveClass('severity-error');
    });

    it('should_displaySpeakerUnavailableConflict_when_detected', () => {
      // AC9: Conflict type - speaker_unavailable
      // Given: Time slot conflicts with speaker preferences
      renderWithProviders(
        <ConflictDetectionAlert
          conflict={mockSpeakerUnavailableConflict}
          isOpen={true}
          onClose={() => {}}
          onResolve={() => {}}
        />
      );

      // Then: Shows speaker unavailable message
      expect(screen.getByText(/speaker marked this time as unavailable/i)).toBeInTheDocument();
      expect(screen.getByTestId('conflict-type-badge')).toHaveTextContent('Speaker Unavailable');
      expect(screen.getByTestId('conflict-severity')).toHaveClass('severity-warning');
    });
  });

  describe('Visual Timeline', () => {
    it('should_displayConflictTimeline_when_conflictShown', () => {
      // Visual timeline of conflicts (wireframe story-5.7-conflict-resolution-modal.md lines 426-470)
      // Given: Conflict has time range information
      renderWithProviders(
        <ConflictDetectionAlert
          conflict={mockRoomOverlapConflict}
          isOpen={true}
          onClose={() => {}}
          onResolve={() => {}}
        />
      );

      // Then: Timeline visualization is displayed
      expect(screen.getByTestId('conflict-timeline')).toBeInTheDocument();
      expect(screen.getByText(/09:00/)).toBeInTheDocument();
      expect(screen.getByText(/10:00/)).toBeInTheDocument();
    });

    it('should_highlightConflictingSlot_when_displayed', () => {
      // Given: Conflicting session exists
      renderWithProviders(
        <ConflictDetectionAlert
          conflict={mockRoomOverlapConflict}
          isOpen={true}
          onClose={() => {}}
          onResolve={() => {}}
        />
      );

      // Then: Conflicting slot is highlighted in red
      const conflictingSlot = screen.getByTestId('conflicting-slot-existing-session-1');
      expect(conflictingSlot).toHaveClass('conflict-highlight');
      expect(conflictingSlot).toHaveStyle({ backgroundColor: 'red' });
    });
  });

  describe('Resolution Options', () => {
    it('should_showResolutionOptions_when_conflictDisplayed', () => {
      // Resolution options: Find Alternative, Change Room, Reassign, Override, Cancel
      // Given: Conflict modal is open
      renderWithProviders(
        <ConflictDetectionAlert
          conflict={mockRoomOverlapConflict}
          isOpen={true}
          onClose={() => {}}
          onResolve={() => {}}
        />
      );

      // Then: Shows all resolution options
      expect(screen.getByRole('button', { name: /find alternative slot/i })).toBeInTheDocument();
      expect(screen.getByRole('button', { name: /change room/i })).toBeInTheDocument();
      expect(screen.getByRole('button', { name: /reassign other session/i })).toBeInTheDocument();
      expect(screen.getByRole('button', { name: /cancel/i })).toBeInTheDocument();
    });

    it('should_showOverrideOption_when_warningOnly', () => {
      // Override warning (warnings only, not errors)
      // Given: Conflict is a warning (speaker_unavailable)
      renderWithProviders(
        <ConflictDetectionAlert
          conflict={mockSpeakerUnavailableConflict}
          isOpen={true}
          onClose={() => {}}
          onResolve={() => {}}
        />
      );

      // Then: Override option is available for warnings
      expect(screen.getByRole('button', { name: /override warning/i })).toBeInTheDocument();
    });

    it('should_hideOverrideOption_when_error', () => {
      // Given: Conflict is an error (room_overlap, speaker_double_booked)
      renderWithProviders(
        <ConflictDetectionAlert
          conflict={mockRoomOverlapConflict}
          isOpen={true}
          onClose={() => {}}
          onResolve={() => {}}
        />
      );

      // Then: Override option is NOT available for errors
      expect(screen.queryByRole('button', { name: /override/i })).not.toBeInTheDocument();
    });

    it('should_findAlternativeSlot_when_optionSelected', () => {
      // Given: [Find Alternative Slot] button exists
      const onResolve = vi.fn();

      renderWithProviders(
        <ConflictDetectionAlert
          conflict={mockRoomOverlapConflict}
          isOpen={true}
          onClose={() => {}}
          onResolve={onResolve}
        />
      );

      const findAlternativeButton = screen.getByRole('button', { name: /find alternative slot/i });

      // When: Button is clicked
      fireEvent.click(findAlternativeButton);

      // Then: Resolution callback is triggered with action
      expect(onResolve).toHaveBeenCalledWith({
        action: 'find_alternative',
        conflict: mockRoomOverlapConflict,
      });
    });

    it('should_changeRoom_when_optionSelected', () => {
      // Given: [Change Room] button exists
      const onResolve = vi.fn();

      renderWithProviders(
        <ConflictDetectionAlert
          conflict={mockRoomOverlapConflict}
          isOpen={true}
          onClose={() => {}}
          onResolve={onResolve}
        />
      );

      const changeRoomButton = screen.getByRole('button', { name: /change room/i });

      // When: Button is clicked
      fireEvent.click(changeRoomButton);

      // Then: Shows room selection dropdown
      expect(screen.getByRole('combobox', { name: /select room/i })).toBeInTheDocument();
    });

    it('should_reassignOtherSession_when_optionSelected', () => {
      // Given: [Reassign Other Session] button exists
      const onResolve = vi.fn();

      renderWithProviders(
        <ConflictDetectionAlert
          conflict={mockSpeakerDoubleBookedConflict}
          isOpen={true}
          onClose={() => {}}
          onResolve={onResolve}
        />
      );

      const reassignButton = screen.getByRole('button', { name: /reassign other session/i });

      // When: Button is clicked
      fireEvent.click(reassignButton);

      // Then: Resolution callback is triggered
      expect(onResolve).toHaveBeenCalledWith({
        action: 'reassign_other',
        conflictingSessionSlug: 'john-doe-other-session',
      });
    });

    it('should_cancel_when_optionSelected', () => {
      // Given: [Cancel] button exists
      const onClose = vi.fn();

      renderWithProviders(
        <ConflictDetectionAlert
          conflict={mockRoomOverlapConflict}
          isOpen={true}
          onClose={onClose}
          onResolve={() => {}}
        />
      );

      const cancelButton = screen.getByRole('button', { name: /cancel/i });

      // When: Button is clicked
      fireEvent.click(cancelButton);

      // Then: Modal closes without changes
      expect(onClose).toHaveBeenCalled();
    });
  });

  describe('Severity Indicators', () => {
    it('should_showErrorBadge_when_criticalConflict', () => {
      // Severity indicators: error vs warning
      // Given: Critical conflict (room_overlap, speaker_double_booked)
      renderWithProviders(
        <ConflictDetectionAlert
          conflict={mockRoomOverlapConflict}
          isOpen={true}
          onClose={() => {}}
          onResolve={() => {}}
        />
      );

      // Then: Shows error severity badge
      expect(screen.getByTestId('conflict-severity')).toHaveClass('severity-error');
      expect(screen.getByTestId('conflict-severity')).toHaveTextContent('Error');
    });

    it('should_showWarningBadge_when_nonCriticalConflict', () => {
      // Given: Non-critical conflict (speaker_unavailable)
      renderWithProviders(
        <ConflictDetectionAlert
          conflict={mockSpeakerUnavailableConflict}
          isOpen={true}
          onClose={() => {}}
          onResolve={() => {}}
        />
      );

      // Then: Shows warning severity badge
      expect(screen.getByTestId('conflict-severity')).toHaveClass('severity-warning');
      expect(screen.getByTestId('conflict-severity')).toHaveTextContent('Warning');
    });
  });

  describe('Multiple Conflicts', () => {
    it('should_displayAllConflicts_when_multipleExist', () => {
      // Given: Multiple conflicts detected
      const multipleConflicts: TimingConflictError = {
        error: 'TIMING_CONFLICT',
        message: 'Multiple conflicts detected',
        conflicts: [
          mockRoomOverlapConflict.conflicts[0],
          mockSpeakerDoubleBookedConflict.conflicts[0],
        ],
      };

      renderWithProviders(
        <ConflictDetectionAlert
          conflict={multipleConflicts}
          isOpen={true}
          onClose={() => {}}
          onResolve={() => {}}
        />
      );

      // Then: Shows count and all conflict details
      expect(screen.getByText(/2.*conflicts/i)).toBeInTheDocument();
      expect(screen.getAllByTestId(/conflict-item-/)).toHaveLength(2);
    });
  });
});
