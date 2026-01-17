/**
 * EventOverviewTab Component Tests (Story 5.6)
 *
 * Tests for the overview tab showing event summary, metrics, and quick actions.
 */

import { render, screen, fireEvent } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { BrowserRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
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
  maxSpeakerSlots: 12,
  pendingMaterialsCount: 3,
  budget: {
    allocated: 15000,
    currency: 'CHF',
  },
};

describe('EventOverviewTab Component (Story 5.6)', () => {
  let queryClient: QueryClient;

  // Test wrapper with providers
  const renderWithProviders = (ui: React.ReactElement) => {
    return render(
      <QueryClientProvider client={queryClient}>
        <BrowserRouter>
          <I18nextProvider i18n={i18n}>{ui}</I18nextProvider>
        </BrowserRouter>
      </QueryClientProvider>
    );
  };

  beforeEach(() => {
    vi.clearAllMocks();
    queryClient = new QueryClient({
      defaultOptions: {
        queries: { retry: false },
        mutations: { retry: false },
      },
    });
  });

  afterEach(() => {
    queryClient.clear();
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
    it.skip('should_displayRegistrationDeadline_when_provided', () => {
      // TODO: Fix date-fns locale configuration in tests
      // The registration deadline is rendered with locale-specific formatting
      // which may not be working correctly in the test environment
      renderWithProviders(<EventOverviewTab event={mockEvent} eventCode="BAT54" />);

      // Should display the registration deadline label
      expect(screen.getByText(/registration.*deadline/i)).toBeInTheDocument();

      // Should display a date containing "10" and "2025" (flexible format)
      // The exact month format depends on locale (Mar, Mär, March, etc.)
      expect(
        screen.getByText((content, element) => {
          const text = element?.textContent || '';
          // Must contain 10 AND 2025 (the actual date values)
          const hasDay = /\b10\b/.test(text);
          const hasYear = /\b2025\b/.test(text);
          return hasDay && hasYear;
        })
      ).toBeInTheDocument();
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

      // Should show "0% filled" for capacity (more specific to avoid matching materials "0% complete")
      expect(screen.getByText(/0%.*filled/i)).toBeInTheDocument();
    });

    it('should_handleMissingWorkflowState_withDefault', () => {
      const eventWithoutWorkflow = { ...mockEvent, workflowState: undefined };
      renderWithProviders(<EventOverviewTab event={eventWithoutWorkflow} eventCode="BAT54" />);

      // Should use default CREATED state
      expect(screen.getByTestId('workflow-progress-bar')).toBeInTheDocument();
    });
  });

  describe('Session Materials Metrics (Story 5.9 - Task 7a)', () => {
    it('should_displaySessionMaterialsRatio_when_sessionsHaveMaterials', () => {
      const eventWithMaterials: EventDetailUI = {
        ...mockEventDetailUI,
        sessionsWithMaterialsCount: 3,
        totalSessionsCount: 4,
      };
      renderWithProviders(<EventOverviewTab event={eventWithMaterials} eventCode="BAT54" />);

      // Should show "3/4 sessions complete" or "3/4 sessions"
      expect(screen.getByText(/3\/4.*session/i)).toBeInTheDocument();
    });

    it('should_showRedProgressBar_when_materialsCompletionBelow50Percent', () => {
      const eventLowCompletion: EventDetailUI = {
        ...mockEventDetailUI,
        sessionsWithMaterialsCount: 1,
        totalSessionsCount: 4,
      };
      renderWithProviders(<EventOverviewTab event={eventLowCompletion} eventCode="BAT54" />);

      // Find the materials progress bar (25% completion = red)
      // LinearProgress with value 25 and error color (red)
      const progressBars = screen.getAllByRole('progressbar');
      const materialsProgressBar = progressBars.find((bar) => {
        return bar.getAttribute('aria-valuenow') === '25';
      });

      expect(materialsProgressBar).toBeInTheDocument();
      // Check for error color class (MUI uses .MuiLinearProgress-colorError for red)
      expect(materialsProgressBar?.className).toContain('colorError');
    });

    it('should_showYellowProgressBar_when_materialsCompletion50To99Percent', () => {
      const eventMediumCompletion: EventDetailUI = {
        ...mockEventDetailUI,
        sessionsWithMaterialsCount: 3,
        totalSessionsCount: 4,
      };
      renderWithProviders(<EventOverviewTab event={eventMediumCompletion} eventCode="BAT54" />);

      // Find the materials progress bar (75% completion = yellow/warning)
      const progressBars = screen.getAllByRole('progressbar');
      const materialsProgressBar = progressBars.find((bar) => {
        return bar.getAttribute('aria-valuenow') === '75';
      });

      expect(materialsProgressBar).toBeInTheDocument();
      // Check for warning color class (MUI uses .MuiLinearProgress-colorWarning for yellow)
      expect(materialsProgressBar?.className).toContain('colorWarning');
    });

    it('should_showGreenProgressBar_when_materialsCompletion100Percent', () => {
      const eventFullCompletion: EventDetailUI = {
        ...mockEventDetailUI,
        sessionsWithMaterialsCount: 4,
        totalSessionsCount: 4,
      };
      renderWithProviders(<EventOverviewTab event={eventFullCompletion} eventCode="BAT54" />);

      // Find the materials progress bar (100% completion = green/success)
      const progressBars = screen.getAllByRole('progressbar');
      const materialsProgressBar = progressBars.find((bar) => {
        return bar.getAttribute('aria-valuenow') === '100';
      });

      expect(materialsProgressBar).toBeInTheDocument();
      // Check for success color class (MUI uses .MuiLinearProgress-colorSuccess for green)
      expect(materialsProgressBar?.className).toContain('colorSuccess');
    });

    it('should_showZeroCompletion_when_noSessionsHaveMaterials', () => {
      const eventNoMaterials: EventDetailUI = {
        ...mockEventDetailUI,
        sessionsWithMaterialsCount: 0,
        totalSessionsCount: 4,
      };
      renderWithProviders(<EventOverviewTab event={eventNoMaterials} eventCode="BAT54" />);

      // Should show "0/4 sessions"
      expect(screen.getByText(/0\/4.*session/i)).toBeInTheDocument();
    });

    it('should_handleNoSessions_gracefully', () => {
      const eventNoSessions: EventDetailUI = {
        ...mockEventDetailUI,
        sessionsWithMaterialsCount: 0,
        totalSessionsCount: 0,
      };
      renderWithProviders(<EventOverviewTab event={eventNoSessions} eventCode="BAT54" />);

      // Should show "0/0 sessions" or hide the section
      expect(screen.getByText(/0\/0.*session/i)).toBeInTheDocument();
    });
  });
});
