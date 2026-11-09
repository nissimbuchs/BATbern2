/**
 * ConflictDetectionAlert Component Tests (Story 5.7 - Task 4a RED Phase)
 *
 * Tests for conflict detection and resolution modal
 * Following TDD: These tests MUST fail until implementation (Task 4b)
 *
 * Coverage:
 * - AC9: Warn if speaker has conflicting commitment at same time
 * - Conflict types: room_overlap, speaker_double_booked, speaker_unavailable
 * - Fixtures carry what EMS sends (TimingConflictError: type + conflictingSessionSlug)
 */

import { render, screen } from '@testing-library/react';
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
      },
    ],
  };

  const mockSpeakerUnavailableConflict: TimingConflictError = {
    error: 'TIMING_CONFLICT',
    message: 'Speaker unavailable during requested time',
    conflicts: [
      {
        type: 'speaker_unavailable',
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

      // Then: Modal is displayed (getAllByRole since multiple dialogs may exist)
      const dialogs = screen.getAllByRole('dialog');
      expect(dialogs.length).toBeGreaterThan(0);
      // Check for conflict message instead
      expect(screen.getByText(/session timing conflicts/i)).toBeInTheDocument();
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

    it('should_showMessage_when_409BodyHasNoConflictList', () => {
      // Given: the agenda-full 409 of the slot endpoint. EMS answers it with its generic error
      // body (AgendaFullException), which has a message but no `conflicts` list; the hook passes
      // that body on as the conflict.
      const agendaFull: TimingConflictError = {
        error: 'ERR_CONFLICT',
        message: "Agenda is full for event 'BATbern99'",
      };

      renderWithProviders(
        <ConflictDetectionAlert
          conflict={agendaFull}
          isOpen={true}
          onClose={() => {}}
          onResolve={() => {}}
        />
      );

      // Then: the dialog shows the server's message instead of crashing the page
      expect(screen.getByText(/agenda is full/i)).toBeInTheDocument();
      expect(screen.queryByTestId('conflict-type-badge')).not.toBeInTheDocument();
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

      // Then: Shows the server's message and the room overlap badge
      expect(screen.getByText(/session timing conflicts/i)).toBeInTheDocument();
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

      // Then: Shows speaker double-booked message (may appear multiple times)
      const messages = screen.getAllByText(/speaker.*already scheduled/i);
      expect(messages.length).toBeGreaterThan(0);
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

      // Then: Shows the server's message and the speaker unavailable badge
      expect(screen.getByText(/speaker unavailable during requested time/i)).toBeInTheDocument();
      expect(screen.getByTestId('conflict-type-badge')).toHaveTextContent('Speaker Unavailable');
      expect(screen.getByTestId('conflict-severity')).toHaveClass('severity-warning');
    });
  });

  describe('Conflicting Slot', () => {
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

      // Then: Conflicting slot is highlighted (testid is just 'conflicting-slot')
      const conflictingSlot = screen.getByTestId('conflicting-slot');
      expect(conflictingSlot).toHaveClass('conflicting-slot-highlight');
      // Component uses MUI sx prop for bgcolor, not inline styles
      expect(conflictingSlot).toBeInTheDocument();
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

      // Then: Shows error severity badge (translation outputs "ERROR" in all caps)
      expect(screen.getByTestId('conflict-severity')).toHaveClass('severity-error');
      expect(screen.getByTestId('conflict-severity')).toHaveTextContent(/error/i);
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

      // Then: Shows warning severity badge (translation outputs "WARNING" in all caps)
      expect(screen.getByTestId('conflict-severity')).toHaveClass('severity-warning');
      expect(screen.getByTestId('conflict-severity')).toHaveTextContent(/warning/i);
    });
  });
});
