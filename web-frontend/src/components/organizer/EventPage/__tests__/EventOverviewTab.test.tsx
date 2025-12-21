/**
 * EventOverviewTab Component Tests (Story 5.6)
 *
 * Tests for the overview tab showing event summary, metrics, and quick actions.
 */

import { render, screen, fireEvent } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { BrowserRouter } from 'react-router-dom';
import { I18nextProvider } from 'react-i18next';
import i18n from '@/i18n/config';
import { EventOverviewTab } from '../EventOverviewTab';
import type { Event, EventDetailUI } from '@/types/event.types';

// Mock window.open
const mockWindowOpen = vi.fn();
Object.defineProperty(window, 'open', { value: mockWindowOpen, writable: true });

// Mock useNavigate
const mockNavigate = vi.fn();
vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual('react-router-dom');
  return {
    ...actual,
    useNavigate: () => mockNavigate,
  };
});

// Mock WorkflowProgressBar
vi.mock('@/components/organizer/EventManagement', () => ({
  WorkflowProgressBar: ({ eventCode }: { eventCode: string }) => (
    <div data-testid="workflow-progress-bar">{eventCode}</div>
  ),
}));

// Mock event data
const mockEvent: Event = {
  eventId: '123e4567-e89b-12d3-a456-426614174000',
  eventCode: 'BAT54',
  eventNumber: 54,
  title: 'Spring Conference 2025',
  description: 'Advanced microservices architecture patterns and best practices',
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

const mockEventDetailUI: EventDetailUI = {
  ...mockEvent,
  eventDate: '2025-03-15T09:00:00Z',
  eventType: 'full-day',
  workflowStep: 5,
  confirmedSpeakersCount: 8,
  pendingMaterialsCount: 3,
  budget: {
    allocated: 15000,
    currency: 'CHF',
  },
};

// Test wrapper with providers
const renderWithProviders = (ui: React.ReactElement) => {
  return render(
    <BrowserRouter>
      <I18nextProvider i18n={i18n}>{ui}</I18nextProvider>
    </BrowserRouter>
  );
};

describe('EventOverviewTab Component (Story 5.6)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  describe('Rendering', () => {
    it('should_displayEventTitle_when_rendered', () => {
      renderWithProviders(<EventOverviewTab event={mockEvent} eventCode="BAT54" />);

      expect(screen.getByText('Spring Conference 2025')).toBeInTheDocument();
    });

    it('should_displayEventDescription_when_provided', () => {
      renderWithProviders(<EventOverviewTab event={mockEvent} eventCode="BAT54" />);

      expect(
        screen.getByText('Advanced microservices architecture patterns and best practices')
      ).toBeInTheDocument();
    });

    it('should_displayVenueName_when_provided', () => {
      renderWithProviders(<EventOverviewTab event={mockEvent} eventCode="BAT54" />);

      expect(screen.getByText('Kursaal Bern')).toBeInTheDocument();
    });

    it('should_displayVenueAddress_when_provided', () => {
      renderWithProviders(<EventOverviewTab event={mockEvent} eventCode="BAT54" />);

      expect(screen.getByText('Kornhausstrasse 3, 3013 Bern')).toBeInTheDocument();
    });

    it('should_displayWorkflowProgressBar_when_rendered', () => {
      renderWithProviders(<EventOverviewTab event={mockEvent} eventCode="BAT54" />);

      expect(screen.getByTestId('workflow-progress-bar')).toBeInTheDocument();
    });

    it.skip('should_displayWorkflowStateChip_when_workflowStateProvided', () => {
      renderWithProviders(<EventOverviewTab event={mockEvent} eventCode="BAT54" />);

      // The chip should show the workflow state label
      expect(screen.getByRole('status')).toBeInTheDocument();
    });
  });

  describe('Key Metrics', () => {
    it('should_displayCapacityMetrics_when_eventHasCapacity', () => {
      renderWithProviders(<EventOverviewTab event={mockEvent} eventCode="BAT54" />);

      // Should show registered count and total capacity
      expect(screen.getByText(/87/)).toBeInTheDocument();
      expect(screen.getByText(/200/)).toBeInTheDocument();
    });

    it('should_displayCapacityPercentage_when_hasAttendees', () => {
      renderWithProviders(<EventOverviewTab event={mockEvent} eventCode="BAT54" />);

      // 87/200 = 43.5%
      expect(screen.getByText(/43%|44%/)).toBeInTheDocument();
    });

    it('should_displaySpeakerProgress_when_eventDetailUIProvided', () => {
      renderWithProviders(<EventOverviewTab event={mockEventDetailUI} eventCode="BAT54" />);

      // Should show confirmed speakers
      expect(screen.getByText(/8\/12/)).toBeInTheDocument();
    });

    it.skip('should_displayPendingMaterials_when_eventDetailUIProvided', () => {
      renderWithProviders(<EventOverviewTab event={mockEventDetailUI} eventCode="BAT54" />);

      // Should show pending materials count
      expect(screen.getByText('3')).toBeInTheDocument();
    });

    it('should_displayBudget_when_budgetProvided', () => {
      renderWithProviders(<EventOverviewTab event={mockEventDetailUI} eventCode="BAT54" />);

      // Should show budget
      expect(screen.getByText(/CHF.*15000|15000/)).toBeInTheDocument();
    });
  });

  describe('Quick Actions', () => {
    it('should_displayEditButton_when_rendered', () => {
      renderWithProviders(<EventOverviewTab event={mockEvent} eventCode="BAT54" />);

      expect(screen.getByRole('button', { name: /edit/i })).toBeInTheDocument();
    });

    it('should_navigateToEditMode_when_editButtonClicked', () => {
      renderWithProviders(<EventOverviewTab event={mockEvent} eventCode="BAT54" />);

      const editButton = screen.getByRole('button', { name: /edit/i });
      fireEvent.click(editButton);

      expect(mockNavigate).toHaveBeenCalledWith('/organizer/events/BAT54?tab=overview&edit=true');
    });

    it('should_displayPreviewButton_when_rendered', () => {
      renderWithProviders(<EventOverviewTab event={mockEvent} eventCode="BAT54" />);

      expect(screen.getByRole('button', { name: /preview/i })).toBeInTheDocument();
    });

    it('should_openPublicPage_when_previewButtonClicked', () => {
      renderWithProviders(<EventOverviewTab event={mockEvent} eventCode="BAT54" />);

      const previewButton = screen.getByRole('button', { name: /preview/i });
      fireEvent.click(previewButton);

      expect(mockWindowOpen).toHaveBeenCalledWith('/events/BAT54', '_blank');
    });

    it('should_displaySendNotificationButton_when_rendered', () => {
      renderWithProviders(<EventOverviewTab event={mockEvent} eventCode="BAT54" />);

      expect(screen.getByRole('button', { name: /notification/i })).toBeInTheDocument();
    });

    it('should_displayTimelineButton_when_rendered', () => {
      renderWithProviders(<EventOverviewTab event={mockEvent} eventCode="BAT54" />);

      expect(screen.getByRole('button', { name: /timeline/i })).toBeInTheDocument();
    });

    it('should_navigateToTimeline_when_timelineButtonClicked', () => {
      renderWithProviders(<EventOverviewTab event={mockEvent} eventCode="BAT54" />);

      const timelineButton = screen.getByRole('button', { name: /timeline/i });
      fireEvent.click(timelineButton);

      expect(mockNavigate).toHaveBeenCalledWith('/organizer/events/timeline?highlight=BAT54');
    });
  });

  describe('Theme Image', () => {
    it('should_displayThemeImage_when_themeImageUrlProvided', () => {
      const eventWithImage = {
        ...mockEvent,
        themeImageUrl: 'https://example.com/image.jpg',
      };
      renderWithProviders(<EventOverviewTab event={eventWithImage} eventCode="BAT54" />);

      const image = screen.getByRole('img');
      expect(image).toHaveAttribute('src', 'https://example.com/image.jpg');
    });

    it('should_notDisplayThemeImage_when_noThemeImageUrl', () => {
      renderWithProviders(<EventOverviewTab event={mockEvent} eventCode="BAT54" />);

      expect(screen.queryByRole('img')).not.toBeInTheDocument();
    });
  });

  describe('Workflow State', () => {
    it('should_displayAdvanceWorkflowButton_when_earlyStage', () => {
      const earlyStageEvent = { ...mockEvent, workflowState: 'CREATED' as const };
      renderWithProviders(<EventOverviewTab event={earlyStageEvent} eventCode="BAT54" />);

      expect(screen.getByRole('button', { name: /advance/i })).toBeInTheDocument();
    });

    it('should_notDisplayAdvanceButton_when_lateStage', () => {
      const lateStageEvent = { ...mockEvent, workflowState: 'EVENT_READY' as const };
      renderWithProviders(<EventOverviewTab event={lateStageEvent} eventCode="BAT54" />);

      expect(screen.queryByRole('button', { name: /advance/i })).not.toBeInTheDocument();
    });
  });

  describe('Registration Deadline', () => {
    it('should_displayRegistrationDeadline_when_provided', () => {
      renderWithProviders(<EventOverviewTab event={mockEvent} eventCode="BAT54" />);

      // Should display the formatted registration deadline
      expect(screen.getByText(/10 Mar 2025|Mar 10, 2025/i)).toBeInTheDocument();
    });
  });

  describe('Edge Cases', () => {
    it('should_handleMissingVenueName_gracefully', () => {
      const eventWithoutVenue = { ...mockEvent, venueName: undefined };
      renderWithProviders(<EventOverviewTab event={eventWithoutVenue} eventCode="BAT54" />);

      expect(screen.getByText('-')).toBeInTheDocument();
    });

    it('should_handleZeroCapacity_gracefully', () => {
      const eventWithZeroCapacity = { ...mockEvent, venueCapacity: 0, currentAttendeeCount: 0 };
      renderWithProviders(<EventOverviewTab event={eventWithZeroCapacity} eventCode="BAT54" />);

      expect(screen.getByText(/0%/)).toBeInTheDocument();
    });

    it('should_handleMissingWorkflowState_withDefault', () => {
      const eventWithoutWorkflow = { ...mockEvent, workflowState: undefined };
      renderWithProviders(
        <EventOverviewTab event={eventWithoutWorkflow} eventCode="BAT54" />
      );

      // Should use default CREATED state
      expect(screen.getByTestId('workflow-progress-bar')).toBeInTheDocument();
    });
  });
});
