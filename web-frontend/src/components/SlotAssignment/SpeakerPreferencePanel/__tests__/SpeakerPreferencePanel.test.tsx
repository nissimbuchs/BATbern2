/**
 * SpeakerPreferencePanel Component Tests (Story 5.7 - Task 4a RED Phase)
 *
 * Tests for speaker preference display drawer
 * Following TDD: These tests MUST fail until implementation (Task 4b)
 *
 * Coverage:
 * - AC7: Display speaker time preferences (morning/afternoon, conflicts)
 * - AC8: Track A/V needs and room setup requirements per speaker
 * - AC11: Highlight when slot matches speaker preference
 */

import { render, screen, fireEvent } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { BrowserRouter } from 'react-router-dom';
import { I18nextProvider } from 'react-i18next';
import i18n from '@/i18n/config';
import { SpeakerPreferencePanel } from '../SpeakerPreferencePanel';

const renderWithProviders = (ui: React.ReactElement) => {
  return render(
    <BrowserRouter>
      <I18nextProvider i18n={i18n}>{ui}</I18nextProvider>
    </BrowserRouter>
  );
};

describe('SpeakerPreferencePanel Component (Story 5.7 - Task 4a RED Phase)', () => {
  const mockSpeaker = {
    username: 'john.doe',
    displayName: 'John Doe',
    companyName: 'Acme Corp',
    preferences: {
      preferredTimeOfDay: 'morning',
      avoidTimes: [{ start: '2025-05-15T12:00:00Z', end: '2025-05-15T13:00:00Z' }],
      avRequirements: {
        microphone: true,
        projector: true,
        recording: false,
        whiteboard: true,
      },
      roomSetupNotes: 'Prefer standing desk and natural light',
    },
  };

  beforeEach(() => {
    vi.clearAllMocks();
  });

  describe('Panel Rendering', () => {
    it('should_renderDrawer_when_opened', () => {
      // AC7: Right drawer (400px) sliding on [View Preferences] click
      // Given: Panel is opened for a speaker
      renderWithProviders(
        <SpeakerPreferencePanel speaker={mockSpeaker} isOpen={true} onClose={() => {}} />
      );

      // Then: Drawer is visible
      expect(screen.getByRole('dialog')).toBeInTheDocument();
      expect(screen.getByText('John Doe - Preferences')).toBeInTheDocument();
    });

    it('should_hideDrawer_when_closed', () => {
      // Given: Panel is closed
      renderWithProviders(
        <SpeakerPreferencePanel speaker={mockSpeaker} isOpen={false} onClose={() => {}} />
      );

      // Then: Drawer is not visible
      expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    });
  });

  describe('Time Preferences Display', () => {
    it('should_displayTimeOfDayPreference_when_set', () => {
      // AC7: Time preferences - Morning/Afternoon/Evening
      // Given: Speaker prefers morning slots
      renderWithProviders(
        <SpeakerPreferencePanel speaker={mockSpeaker} isOpen={true} onClose={() => {}} />
      );

      // Then: Shows morning preference with icon
      expect(screen.getByText(/morning/i)).toBeInTheDocument();
      expect(screen.getByTestId('preferred-icon-morning')).toHaveClass('icon-preferred');
    });

    it('should_displayAvoidTimes_when_specified', () => {
      // AC7: Specific avoid times - List of date-time ranges
      // Given: Speaker wants to avoid 12:00-13:00
      renderWithProviders(
        <SpeakerPreferencePanel speaker={mockSpeaker} isOpen={true} onClose={() => {}} />
      );

      // Then: Shows avoid time range
      expect(screen.getByText(/12:00.*13:00/)).toBeInTheDocument();
      expect(screen.getByTestId('avoid-time-0')).toHaveClass('avoid-time-range');
    });

    it('should_showIconsForTimePreferences_when_displayed', () => {
      // Given: Morning (preferred), Afternoon (neutral), Evening (avoid)
      const speakerWithMultiplePreferences = {
        ...mockSpeaker,
        preferences: {
          ...mockSpeaker.preferences,
          preferredTimeOfDay: 'morning',
          neutralTimeOfDay: ['afternoon'],
          avoidTimeOfDay: ['evening'],
        },
      };

      renderWithProviders(
        <SpeakerPreferencePanel
          speaker={speakerWithMultiplePreferences}
          isOpen={true}
          onClose={() => {}}
        />
      );

      // Then: Shows different icons for each preference level
      expect(screen.getByTestId('preferred-icon-morning')).toHaveClass('icon-preferred');
      expect(screen.getByTestId('neutral-icon-afternoon')).toHaveClass('icon-neutral');
      expect(screen.getByTestId('avoid-icon-evening')).toHaveClass('icon-avoid');
    });
  });

  describe('A/V Requirements Display', () => {
    it('should_displayAVRequirements_when_specified', () => {
      // AC8: A/V requirements - Checkboxes for microphone, projector, recording
      // Given: Speaker needs microphone and projector
      renderWithProviders(
        <SpeakerPreferencePanel speaker={mockSpeaker} isOpen={true} onClose={() => {}} />
      );

      // Then: Shows required A/V equipment
      expect(screen.getByText(/microphone/i)).toBeInTheDocument();
      expect(screen.getByText(/projector/i)).toBeInTheDocument();
      expect(screen.getByTestId('av-requirement-microphone')).toHaveClass('requirement-needed');
      expect(screen.getByTestId('av-requirement-projector')).toHaveClass('requirement-needed');
    });

    it('should_showUncheckedIcon_when_notRequired', () => {
      // Given: Recording is not required
      renderWithProviders(
        <SpeakerPreferencePanel speaker={mockSpeaker} isOpen={true} onClose={() => {}} />
      );

      // Then: Recording shows as not needed
      expect(screen.getByTestId('av-requirement-recording')).toHaveClass('requirement-not-needed');
    });
  });

  describe('Room Setup Display', () => {
    it('should_displayRoomSetupNotes_when_provided', () => {
      // AC8: Room setup - Standing desk, natural light, flip chart, notes field
      // Given: Speaker has room setup preferences
      renderWithProviders(
        <SpeakerPreferencePanel speaker={mockSpeaker} isOpen={true} onClose={() => {}} />
      );

      // Then: Shows room setup notes
      expect(screen.getByText(/prefer standing desk and natural light/i)).toBeInTheDocument();
    });
  });

  describe('Dynamic Match Scoring', () => {
    it('should_displayMatchScore_when_hoveringOverSlot', () => {
      // AC11: Dynamic match score - Shows % match when hovering over session slot
      // Given: Panel is open and user hovers over a time slot
      renderWithProviders(
        <SpeakerPreferencePanel
          speaker={mockSpeaker}
          isOpen={true}
          onClose={() => {}}
          hoveredSlot={{ time: '09:00', room: 'Main Hall' }}
        />
      );

      // Then: Shows match percentage
      expect(screen.getByText(/90%.*match/i)).toBeInTheDocument();
      expect(screen.getByTestId('match-score-indicator')).toHaveClass('match-high');
    });

    it('should_showGreenIndicator_when_highMatch', () => {
      // AC11: Color-coded match indicators (Green 80-100%)
      // Given: Slot matches speaker preference 90%
      renderWithProviders(
        <SpeakerPreferencePanel
          speaker={mockSpeaker}
          isOpen={true}
          onClose={() => {}}
          hoveredSlot={{ time: '09:00', room: 'Main Hall' }}
          matchScore={90}
        />
      );

      // Then: Shows green indicator
      expect(screen.getByTestId('match-score-indicator')).toHaveClass('match-high');
      expect(screen.getByTestId('match-score-indicator')).toHaveStyle({ backgroundColor: 'green' });
    });

    it('should_showYellowIndicator_when_mediumMatch', () => {
      // AC11: Color-coded match indicators (Yellow 50-79%)
      // Given: Slot partially matches speaker preference 65%
      renderWithProviders(
        <SpeakerPreferencePanel
          speaker={mockSpeaker}
          isOpen={true}
          onClose={() => {}}
          hoveredSlot={{ time: '13:00', room: 'Main Hall' }}
          matchScore={65}
        />
      );

      // Then: Shows yellow indicator
      expect(screen.getByTestId('match-score-indicator')).toHaveClass('match-medium');
      expect(screen.getByTestId('match-score-indicator')).toHaveStyle({
        backgroundColor: 'orange',
      });
    });

    it('should_showRedIndicator_when_lowMatch', () => {
      // AC11: Color-coded match indicators (Red <50%)
      // Given: Slot poorly matches speaker preference 20%
      renderWithProviders(
        <SpeakerPreferencePanel
          speaker={mockSpeaker}
          isOpen={true}
          onClose={() => {}}
          hoveredSlot={{ time: '19:00', room: 'Conference Room B' }}
          matchScore={20}
        />
      );

      // Then: Shows red indicator
      expect(screen.getByTestId('match-score-indicator')).toHaveClass('match-low');
      expect(screen.getByTestId('match-score-indicator')).toHaveStyle({ backgroundColor: 'red' });
    });
  });

  describe('Action Buttons', () => {
    it('should_assignToCurrentSlot_when_buttonClicked', () => {
      // Given: [Assign to Current Slot] button exists
      const onAssignToSlot = vi.fn();

      renderWithProviders(
        <SpeakerPreferencePanel
          speaker={mockSpeaker}
          isOpen={true}
          onClose={() => {}}
          hoveredSlot={{ time: '09:00', room: 'Main Hall' }}
          onAssignToSlot={onAssignToSlot}
        />
      );

      const assignButton = screen.getByRole('button', { name: /assign to current slot/i });

      // When: Button is clicked
      fireEvent.click(assignButton);

      // Then: Assignment callback is triggered
      expect(onAssignToSlot).toHaveBeenCalledWith({ time: '09:00', room: 'Main Hall' });
    });

    it('should_findBestMatch_when_buttonClicked', () => {
      // Given: [Find Best Match] button exists
      const onFindBestMatch = vi.fn();

      renderWithProviders(
        <SpeakerPreferencePanel
          speaker={mockSpeaker}
          isOpen={true}
          onClose={() => {}}
          onFindBestMatch={onFindBestMatch}
        />
      );

      const findMatchButton = screen.getByRole('button', { name: /find best match/i });

      // When: Button is clicked
      fireEvent.click(findMatchButton);

      // Then: Find best match callback is triggered
      expect(onFindBestMatch).toHaveBeenCalledWith(mockSpeaker.username);
    });
  });

  describe('Panel Closure', () => {
    it('should_closePan el_when_closeButtonClicked', () => {
      // Given: Panel is open
      const onClose = vi.fn();

      renderWithProviders(
        <SpeakerPreferencePanel speaker={mockSpeaker} isOpen={true} onClose={onClose} />
      );

      const closeButton = screen.getByRole('button', { name: /close/i });

      // When: Close button is clicked
      fireEvent.click(closeButton);

      // Then: Close callback is triggered
      expect(onClose).toHaveBeenCalled();
    });

    it('should_closePanel_when_clickedOutside', () => {
      // Given: Panel is open
      const onClose = vi.fn();

      renderWithProviders(
        <SpeakerPreferencePanel speaker={mockSpeaker} isOpen={true} onClose={onClose} />
      );

      // When: User clicks outside panel
      fireEvent.click(document.body);

      // Then: Panel closes
      expect(onClose).toHaveBeenCalled();
    });
  });
});
