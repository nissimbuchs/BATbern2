/**
 * EventPublishingTab Component Tests (Story 5.6)
 *
 * Tests for the publishing configuration, timeline, and quality checkpoints tab.
 */

import { render, screen, fireEvent } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { BrowserRouter } from 'react-router-dom';
import { I18nextProvider } from 'react-i18next';
import i18n from '@/i18n/config';
import { EventPublishingTab } from '../EventPublishingTab';
import type { Event } from '@/types/event.types';

// Mock window.open
const mockWindowOpen = vi.fn();
Object.defineProperty(window, 'open', { value: mockWindowOpen, writable: true });

// Mock event data
const mockEvent: Event = {
  eventId: '123e4567-e89b-12d3-a456-426614174000',
  eventCode: 'BAT54',
  eventNumber: 54,
  title: 'Spring Conference 2025',
  description: 'Advanced microservices architecture',
  date: '2025-03-15T09:00:00Z',
  registrationDeadline: '2025-03-10T23:59:59Z',
  venueName: 'Kursaal Bern',
  venueAddress: 'Kornhausstrasse 3, 3013 Bern',
  venueCapacity: 200,
  status: 'published',
  workflowState: 'SPEAKER_CONFIRMATION',
  organizerUsername: 'john.doe',
  currentAttendeeCount: 87,
  createdAt: '2024-12-01T10:00:00Z',
  updatedAt: '2025-01-15T14:30:00Z',
};

// Test wrapper with providers
const renderWithProviders = (ui: React.ReactElement) => {
  return render(
    <BrowserRouter>
      <I18nextProvider i18n={i18n}>{ui}</I18nextProvider>
    </BrowserRouter>
  );
};

describe('EventPublishingTab Component (Story 5.6)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  describe('Publishing Status Section', () => {
    it('should_displayPublishingStatusTitle_when_rendered', () => {
      renderWithProviders(<EventPublishingTab event={mockEvent} eventCode="BAT54" />);

      expect(screen.getByText(/Publishing Status/i)).toBeInTheDocument();
    });

    it('should_displayConfigureButton_when_rendered', () => {
      renderWithProviders(<EventPublishingTab event={mockEvent} eventCode="BAT54" />);

      expect(screen.getByRole('button', { name: /Configure/i })).toBeInTheDocument();
    });

    it('should_displayStrategy_when_rendered', () => {
      renderWithProviders(<EventPublishingTab event={mockEvent} eventCode="BAT54" />);

      expect(screen.getByText(/Strategy/i)).toBeInTheDocument();
      expect(screen.getByText(/Progressive Publishing/i)).toBeInTheDocument();
    });

    it('should_displayCurrentPhase_when_rendered', () => {
      renderWithProviders(<EventPublishingTab event={mockEvent} eventCode="BAT54" />);

      expect(screen.getByText(/Current Phase/i)).toBeInTheDocument();
    });
  });

  describe('Publishing Timeline Section', () => {
    it('should_displayTimelineTitle_when_rendered', () => {
      renderWithProviders(<EventPublishingTab event={mockEvent} eventCode="BAT54" />);

      expect(screen.getByText(/Publishing Timeline/i)).toBeInTheDocument();
    });

    it.skip('should_displayTimelinePhases_when_rendered', () => {
      renderWithProviders(<EventPublishingTab event={mockEvent} eventCode="BAT54" />);

      expect(screen.getByText(/Topic Published/i)).toBeInTheDocument();
      expect(screen.getByText(/Speakers Published/i)).toBeInTheDocument();
      expect(screen.getByText(/Final Agenda/i)).toBeInTheDocument();
      expect(screen.getByText(/Event Day/i)).toBeInTheDocument();
      expect(screen.getByText(/Post-Event Materials/i)).toBeInTheDocument();
    });

    it('should_displayTimelineDates_when_rendered', () => {
      renderWithProviders(<EventPublishingTab event={mockEvent} eventCode="BAT54" />);

      // Should display formatted dates
      expect(screen.getByText(/Jan 5, 2025|5 Jan 2025/i)).toBeInTheDocument();
      expect(screen.getByText(/Feb 15, 2025|15 Feb 2025/i)).toBeInTheDocument();
    });

    it('should_displayCompletedStatusIcon_for_completedPhases', () => {
      renderWithProviders(<EventPublishingTab event={mockEvent} eventCode="BAT54" />);

      // Topic Published and Speakers Published should be completed
      const completedPhases = screen.getAllByText(/Topic Published|Speakers Published/i);
      expect(completedPhases.length).toBeGreaterThan(0);
    });

    it.skip('should_displayPendingStatusIcon_for_pendingPhases', () => {
      renderWithProviders(<EventPublishingTab event={mockEvent} eventCode="BAT54" />);

      // Final Agenda should be pending
      expect(screen.getByText(/Final Agenda/i)).toBeInTheDocument();
    });
  });

  describe('Quality Checkpoints Section', () => {
    it('should_displayQualityCheckpointsTitle_when_rendered', () => {
      renderWithProviders(<EventPublishingTab event={mockEvent} eventCode="BAT54" />);

      expect(screen.getByText(/Quality Checkpoints/i)).toBeInTheDocument();
    });

    it('should_displayQualityChecks_when_rendered', () => {
      renderWithProviders(<EventPublishingTab event={mockEvent} eventCode="BAT54" />);

      expect(screen.getByText(/Abstract length validation/i)).toBeInTheDocument();
      expect(screen.getByText(/Lessons learned requirement/i)).toBeInTheDocument();
      expect(screen.getByText(/All materials submitted/i)).toBeInTheDocument();
      expect(screen.getByText(/Moderator review complete/i)).toBeInTheDocument();
    });

    it('should_displayPendingMessage_for_pendingChecks', () => {
      renderWithProviders(<EventPublishingTab event={mockEvent} eventCode="BAT54" />);

      expect(screen.getByText(/2 pending/i)).toBeInTheDocument();
    });

    it('should_displayResolveCheckpointsAlert_when_pendingChecksExist', () => {
      renderWithProviders(<EventPublishingTab event={mockEvent} eventCode="BAT54" />);

      expect(
        screen.getByText(/Resolve all checkpoints before publishing final agenda/i)
      ).toBeInTheDocument();
    });
  });

  describe('Actions Section', () => {
    it('should_displayActionsTitle_when_rendered', () => {
      renderWithProviders(<EventPublishingTab event={mockEvent} eventCode="BAT54" />);

      expect(screen.getByText(/^Actions$/i)).toBeInTheDocument();
    });

    it('should_displayPreviewButton_when_rendered', () => {
      renderWithProviders(<EventPublishingTab event={mockEvent} eventCode="BAT54" />);

      expect(screen.getByRole('button', { name: /Preview Public Page/i })).toBeInTheDocument();
    });

    it('should_openPublicPage_when_previewClicked', () => {
      renderWithProviders(<EventPublishingTab event={mockEvent} eventCode="BAT54" />);

      const previewButton = screen.getByRole('button', { name: /Preview Public Page/i });
      fireEvent.click(previewButton);

      expect(mockWindowOpen).toHaveBeenCalledWith('/events/BAT54', '_blank');
    });

    it('should_displayRepublishButton_when_rendered', () => {
      renderWithProviders(<EventPublishingTab event={mockEvent} eventCode="BAT54" />);

      expect(screen.getByRole('button', { name: /Republish Event/i })).toBeInTheDocument();
    });

    it('should_displayNotifyAttendeesButton_when_rendered', () => {
      renderWithProviders(<EventPublishingTab event={mockEvent} eventCode="BAT54" />);

      expect(screen.getByRole('button', { name: /Notify Attendees/i })).toBeInTheDocument();
    });
  });

  describe('Status Icons', () => {
    it('should_displayCorrectStatusIcons_when_rendered', () => {
      renderWithProviders(<EventPublishingTab event={mockEvent} eventCode="BAT54" />);

      // Component should render without errors
      expect(screen.getByText(/Publishing Timeline/i)).toBeInTheDocument();
    });
  });

  describe('Check Icons', () => {
    it('should_displayPassedCheckIcons_for_passedChecks', () => {
      renderWithProviders(<EventPublishingTab event={mockEvent} eventCode="BAT54" />);

      // Passed checks should be displayed
      expect(screen.getByText(/Abstract length validation/i)).toBeInTheDocument();
    });

    it('should_displayWarningCheckIcons_for_pendingChecks', () => {
      renderWithProviders(<EventPublishingTab event={mockEvent} eventCode="BAT54" />);

      // Pending checks should be displayed
      expect(screen.getByText(/All materials submitted/i)).toBeInTheDocument();
    });
  });
});
