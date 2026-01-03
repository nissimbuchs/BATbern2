/**
 * EventPage Component Tests (Story 5.6)
 *
 * Tests for the unified event page with tab-based navigation.
 */

import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { I18nextProvider } from 'react-i18next';
import i18n from '@/i18n/config';
import { EventPage } from '../EventPage';
import type { Event } from '@/types/event.types';

// Mock useEvent hook - inline data to avoid hoisting issues
vi.mock('@/hooks/useEvents', () => ({
  useEvent: vi.fn().mockReturnValue({
    data: {
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
    },
    isLoading: false,
    error: null,
  }),
}));

// Mock event data for test assertions
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

// Mock useNavigate
const mockNavigate = vi.fn();
vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual('react-router-dom');
  return {
    ...actual,
    useNavigate: () => mockNavigate,
  };
});

// Mock child tab components
vi.mock('../EventOverviewTab', () => ({
  EventOverviewTab: ({ eventCode }: { eventCode: string }) => (
    <div data-testid="event-overview-tab">Overview Tab - {eventCode}</div>
  ),
}));

vi.mock('../EventSpeakersTab', () => ({
  EventSpeakersTab: ({ eventCode }: { eventCode: string }) => (
    <div data-testid="event-speakers-tab">Speakers Tab - {eventCode}</div>
  ),
}));

vi.mock('../EventVenueTab', () => ({
  EventVenueTab: () => <div data-testid="event-venue-tab">Venue Tab</div>,
}));

vi.mock('../EventParticipantsTab', () => ({
  default: ({ event }: { event: any }) => (
    <div data-testid="event-participants-tab">Participants Tab - {event.eventCode}</div>
  ),
}));

vi.mock('../EventPublishingTab', () => ({
  EventPublishingTab: ({ eventCode }: { eventCode: string }) => (
    <div data-testid="event-publishing-tab">Publishing Tab - {eventCode}</div>
  ),
}));

vi.mock('../EventSettingsTab', () => ({
  EventSettingsTab: ({ eventCode }: { eventCode: string }) => (
    <div data-testid="event-settings-tab">Settings Tab - {eventCode}</div>
  ),
}));

// Mock Breadcrumbs component
vi.mock('@/components/shared/Breadcrumbs', () => ({
  Breadcrumbs: ({ items }: { items: Array<{ label: string }> }) => (
    <nav data-testid="breadcrumbs">
      {items.map((item, i) => (
        <span key={i}>{item.label}</span>
      ))}
    </nav>
  ),
}));

// Mock getWorkflowStateLabel
vi.mock('@/utils/workflow/workflowState', () => ({
  getWorkflowStateLabel: (state: string) => state,
}));

// Mock useMediaQuery
vi.mock('@mui/material', async () => {
  const actual = await vi.importActual('@mui/material');
  return {
    ...actual,
    useMediaQuery: vi.fn().mockReturnValue(false), // Desktop by default
  };
});

// Test wrapper with providers
const renderWithProviders = (initialRoute = '/organizer/events/BAT54') => {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
    },
  });

  return render(
    <MemoryRouter initialEntries={[initialRoute]}>
      <QueryClientProvider client={queryClient}>
        <I18nextProvider i18n={i18n}>
          <Routes>
            <Route path="/organizer/events/:eventCode" element={<EventPage />} />
          </Routes>
        </I18nextProvider>
      </QueryClientProvider>
    </MemoryRouter>
  );
};

describe('EventPage Component (Story 5.6)', () => {
  beforeEach(async () => {
    vi.clearAllMocks();
    // Reset the mock to return valid event data for each test
    const { useEvent } = await import('@/hooks/useEvents');
    (useEvent as ReturnType<typeof vi.fn>).mockReturnValue({
      data: {
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
      },
      isLoading: false,
      error: null,
    });
  });

  describe('Header', () => {
    it.skip('should_displayEventTitle_when_rendered', () => {
      renderWithProviders();

      expect(screen.getByText('Spring Conference 2025')).toBeInTheDocument();
    });

    it('should_displayEventCode_when_rendered', () => {
      renderWithProviders();

      expect(screen.getByText('BAT54')).toBeInTheDocument();
    });

    it('should_displayWorkflowStateChip_when_rendered', () => {
      renderWithProviders();

      expect(screen.getByText('SPEAKER_CONFIRMATION')).toBeInTheDocument();
    });

    it('should_displayBreadcrumbs_when_rendered', () => {
      renderWithProviders();

      expect(screen.getByTestId('breadcrumbs')).toBeInTheDocument();
      expect(screen.getByText(/Events/i)).toBeInTheDocument();
    });
  });

  describe('Tab Navigation', () => {
    it('should_displayAllTabs_when_rendered', () => {
      renderWithProviders();

      expect(screen.getByRole('tab', { name: /overview/i })).toBeInTheDocument();
      expect(screen.getByRole('tab', { name: /speakers/i })).toBeInTheDocument();
      expect(screen.getByRole('tab', { name: /venue/i })).toBeInTheDocument();
      expect(screen.getByRole('tab', { name: /participants/i })).toBeInTheDocument();
      expect(screen.getByRole('tab', { name: /publishing/i })).toBeInTheDocument();
      expect(screen.getByRole('tab', { name: /settings/i })).toBeInTheDocument();
    });

    it('should_selectOverviewTab_byDefault', () => {
      renderWithProviders();

      expect(screen.getByTestId('event-overview-tab')).toBeInTheDocument();
    });

    it('should_switchToSpeakersTab_when_clicked', async () => {
      renderWithProviders();

      const speakersTab = screen.getByRole('tab', { name: /speakers/i });
      fireEvent.click(speakersTab);

      await waitFor(() => {
        expect(screen.getByTestId('event-speakers-tab')).toBeInTheDocument();
      });
    });

    it('should_switchToVenueTab_when_clicked', async () => {
      renderWithProviders();

      const venueTab = screen.getByRole('tab', { name: /venue/i });
      fireEvent.click(venueTab);

      await waitFor(() => {
        expect(screen.getByTestId('event-venue-tab')).toBeInTheDocument();
      });
    });

    it('should_switchToParticipantsTab_when_clicked', async () => {
      renderWithProviders();

      const participantsTab = screen.getByRole('tab', { name: /participants/i });
      fireEvent.click(participantsTab);

      await waitFor(() => {
        expect(screen.getByTestId('event-participants-tab')).toBeInTheDocument();
      });
    });

    it('should_switchToPublishingTab_when_clicked', async () => {
      renderWithProviders();

      const publishingTab = screen.getByRole('tab', { name: /publishing/i });
      fireEvent.click(publishingTab);

      await waitFor(() => {
        expect(screen.getByTestId('event-publishing-tab')).toBeInTheDocument();
      });
    });

    it('should_switchToSettingsTab_when_clicked', async () => {
      renderWithProviders();

      const settingsTab = screen.getByRole('tab', { name: /settings/i });
      fireEvent.click(settingsTab);

      await waitFor(() => {
        expect(screen.getByTestId('event-settings-tab')).toBeInTheDocument();
      });
    });
  });

  describe('URL-based Tab Selection', () => {
    it('should_selectTabFromUrl_when_tabParamProvided', () => {
      renderWithProviders('/organizer/events/BAT54?tab=speakers');

      expect(screen.getByTestId('event-speakers-tab')).toBeInTheDocument();
    });

    it('should_selectVenueTab_when_urlHasVenueParam', () => {
      renderWithProviders('/organizer/events/BAT54?tab=venue');

      expect(screen.getByTestId('event-venue-tab')).toBeInTheDocument();
    });

    it('should_selectParticipantsTab_when_urlHasParticipantsParam', () => {
      renderWithProviders('/organizer/events/BAT54?tab=participants');

      expect(screen.getByTestId('event-participants-tab')).toBeInTheDocument();
    });

    it('should_selectPublishingTab_when_urlHasPublishingParam', () => {
      renderWithProviders('/organizer/events/BAT54?tab=publishing');

      expect(screen.getByTestId('event-publishing-tab')).toBeInTheDocument();
    });

    it('should_selectSettingsTab_when_urlHasSettingsParam', () => {
      renderWithProviders('/organizer/events/BAT54?tab=settings');

      expect(screen.getByTestId('event-settings-tab')).toBeInTheDocument();
    });

    it('should_defaultToOverview_when_invalidTabParam', () => {
      renderWithProviders('/organizer/events/BAT54?tab=invalid');

      expect(screen.getByTestId('event-overview-tab')).toBeInTheDocument();
    });
  });

  describe('Tab Content', () => {
    it('should_passEventCodeToOverviewTab', () => {
      renderWithProviders();

      expect(screen.getByText(/Overview Tab - BAT54/)).toBeInTheDocument();
    });

    it('should_passEventCodeToSpeakersTab', () => {
      renderWithProviders('/organizer/events/BAT54?tab=speakers');

      expect(screen.getByText(/Speakers Tab - BAT54/)).toBeInTheDocument();
    });

    it('should_passEventCodeToParticipantsTab', () => {
      renderWithProviders('/organizer/events/BAT54?tab=participants');

      expect(screen.getByText(/Participants Tab - BAT54/)).toBeInTheDocument();
    });
  });

  describe('Loading State', () => {
    it('should_displayLoadingSpinner_when_loading', async () => {
      const { useEvent } = await import('@/hooks/useEvents');
      (useEvent as ReturnType<typeof vi.fn>).mockReturnValue({
        data: null,
        isLoading: true,
        error: null,
      });

      renderWithProviders();

      expect(screen.getByRole('progressbar')).toBeInTheDocument();
    });
  });

  describe('Error State', () => {
    it('should_displayErrorAlert_when_errorOccurs', async () => {
      const { useEvent } = await import('@/hooks/useEvents');
      (useEvent as ReturnType<typeof vi.fn>).mockReturnValue({
        data: null,
        isLoading: false,
        error: { message: 'Failed to load event' },
      });

      renderWithProviders();

      expect(screen.getByText(/Failed to load event/i)).toBeInTheDocument();
    });

    it('should_displayBackButton_when_errorOccurs', async () => {
      const { useEvent } = await import('@/hooks/useEvents');
      (useEvent as ReturnType<typeof vi.fn>).mockReturnValue({
        data: null,
        isLoading: false,
        error: { message: 'Failed to load event' },
      });

      renderWithProviders();

      expect(screen.getByRole('button', { name: /back/i })).toBeInTheDocument();
    });
  });

  describe('Not Found State', () => {
    it('should_displayNotFoundAlert_when_eventNotFound', async () => {
      const { useEvent } = await import('@/hooks/useEvents');
      (useEvent as ReturnType<typeof vi.fn>).mockReturnValue({
        data: null,
        isLoading: false,
        error: null,
      });

      renderWithProviders();

      expect(screen.getByText(/Event not found/i)).toBeInTheDocument();
    });
  });

  describe('Accessibility', () => {
    it('should_haveProperTabListRole', () => {
      renderWithProviders();

      expect(screen.getByRole('tablist')).toBeInTheDocument();
    });

    it('should_haveProperTabRoles', () => {
      renderWithProviders();

      const tabs = screen.getAllByRole('tab');
      // 6 tabs: Overview, Speakers, Venue, Participants, Publishing, Settings
      expect(tabs.length).toBe(6);
    });

    it('should_haveAriaLabel_forTabNavigation', () => {
      renderWithProviders();

      const tablist = screen.getByRole('tablist');
      expect(tablist).toHaveAttribute('aria-label');
    });
  });
});
