/**
 * EventPage Component Tests
 *
 * Story 5.6 — unified tab-based event page.
 * Epic 14 Phase A — lifecycle-aware 8-tab IA: relevance-driven dim/lock,
 * count-driven attention badges, recomposed tab slots.
 */

import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { I18nextProvider } from 'react-i18next';
import i18n from '@/i18n/config';
import { EventPage } from '../EventPage';

// A valid early-stage event (Wrap-up is locked at SPEAKER_IDENTIFICATION).
const baseEvent = {
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
  workflowState: 'SPEAKER_IDENTIFICATION',
  organizerUsername: 'john.doe',
  currentAttendeeCount: 87,
  createdAt: '2024-12-01T10:00:00Z',
  updatedAt: '2025-01-15T14:30:00Z',
};

vi.mock('@/hooks/useEvents', () => ({
  useEvent: vi.fn(),
}));

// Mock useNavigate
const mockNavigate = vi.fn();
vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual('react-router-dom');
  return {
    ...actual,
    useNavigate: () => mockNavigate,
  };
});

// Control the attention badges directly (the hook's data wiring is covered by
// tabBadges.test.ts).
vi.mock('../useTabBadges', () => ({
  useTabBadges: vi.fn(),
}));

// Mock child tab components / containers
vi.mock('../EventOverviewTab', () => ({
  EventOverviewTab: ({ eventCode }: { eventCode: string }) => (
    <div data-testid="event-overview-tab">Overview Tab - {eventCode}</div>
  ),
}));
// Phase B: the Cockpit tab mounts CockpitTab (its internals are covered by the
// cockpit/* test suite). Mock it here to keep EventPage tests focused on the shell.
vi.mock('../cockpit/CockpitTab', () => ({
  CockpitTab: ({ eventCode }: { eventCode: string }) => (
    <div data-testid="cockpit-tab">Cockpit Tab - {eventCode}</div>
  ),
}));
vi.mock('../EventSpeakersTab', () => ({
  EventSpeakersTab: ({ eventCode }: { eventCode: string }) => (
    <div data-testid="event-speakers-tab">Speakers Tab - {eventCode}</div>
  ),
}));
vi.mock('../EventParticipantsTab', () => ({
  default: ({ event }: { event: { eventCode: string } }) => (
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
vi.mock('../EventCommunicationsContainer', () => ({
  EventCommunicationsContainer: ({ eventCode }: { eventCode: string }) => (
    <div data-testid="event-communications-tab">Communications - {eventCode}</div>
  ),
}));
vi.mock('../EventWrapupContainer', () => ({
  EventWrapupContainer: ({ eventCode }: { eventCode: string }) => (
    <div data-testid="event-wrapup-tab">Wrap-up - {eventCode}</div>
  ),
}));
vi.mock('../EventDetailsContainer', () => ({
  EventDetailsContainer: () => (
    <div data-testid="event-details-tab">
      <div data-testid="details-subtab-info" />
      <div data-testid="details-subtab-tasks" />
      <div data-testid="details-subtab-settings" />
    </div>
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

// Mock useMediaQuery (desktop by default)
vi.mock('@mui/material', async () => {
  const actual = await vi.importActual('@mui/material');
  return {
    ...actual,
    useMediaQuery: vi.fn().mockReturnValue(false),
  };
});

const setEvent = async (overrides: Record<string, unknown> = {}) => {
  const { useEvent } = await import('@/hooks/useEvents');
  (useEvent as ReturnType<typeof vi.fn>).mockReturnValue({
    data: { ...baseEvent, ...overrides },
    isLoading: false,
    error: null,
  });
};

const setBadges = async (badges: {
  speakers?: number;
  publishingReady?: boolean;
  commsOverdue?: boolean;
}) => {
  const { useTabBadges } = await import('../useTabBadges');
  (useTabBadges as ReturnType<typeof vi.fn>).mockReturnValue({
    speakers: badges.speakers ?? 0,
    publishingReady: badges.publishingReady ?? false,
    commsOverdue: badges.commsOverdue ?? false,
  });
};

const renderWithProviders = (initialRoute = '/organizer/events/BAT54') => {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
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

describe('EventPage — 8-tab lifecycle shell (Epic 14 Phase A)', () => {
  beforeEach(async () => {
    vi.clearAllMocks();
    await setEvent();
    await setBadges({});
  });

  describe('Header', () => {
    it('keeps the event title in the header (FR2)', () => {
      renderWithProviders();
      expect(screen.getByRole('heading', { name: 'Spring Conference 2025' })).toBeInTheDocument();
    });

    it('renders breadcrumbs', () => {
      renderWithProviders();
      expect(screen.getByTestId('breadcrumbs')).toBeInTheDocument();
      expect(screen.getByText(/Events/i)).toBeInTheDocument();
    });
  });

  describe('7-tab IA (FR1/FR3 — config cluster merged into Details)', () => {
    it('renders exactly 7 tabs', () => {
      renderWithProviders();
      expect(screen.getAllByRole('tab')).toHaveLength(7);
    });

    it('renders the 7 lifecycle tabs (Settings folded into Details)', () => {
      renderWithProviders();
      expect(screen.getByRole('tab', { name: /cockpit/i })).toBeInTheDocument();
      expect(screen.getByRole('tab', { name: /speakers/i })).toBeInTheDocument();
      expect(screen.getByRole('tab', { name: /registrations/i })).toBeInTheDocument();
      expect(screen.getByRole('tab', { name: /communications/i })).toBeInTheDocument();
      expect(screen.getByRole('tab', { name: /publishing/i })).toBeInTheDocument();
      expect(screen.getByRole('tab', { name: /wrap-?up/i })).toBeInTheDocument();
      expect(screen.getByRole('tab', { name: /details/i })).toBeInTheDocument();
      // Settings is no longer a top-level tab — it's a sub-tab of Details now.
      expect(screen.queryByRole('tab', { name: /^settings$/i })).not.toBeInTheDocument();
    });

    it('no longer shows the old consolidated standalone tabs', () => {
      renderWithProviders();
      expect(screen.queryByRole('tab', { name: /newsletter/i })).not.toBeInTheDocument();
      expect(screen.queryByRole('tab', { name: /registrant notices/i })).not.toBeInTheDocument();
      expect(screen.queryByRole('tab', { name: /photos/i })).not.toBeInTheDocument();
      expect(screen.queryByRole('tab', { name: /appreciation/i })).not.toBeInTheDocument();
      expect(screen.queryByRole('tab', { name: /participants/i })).not.toBeInTheDocument();
      expect(screen.queryByRole('tab', { name: /^overview$/i })).not.toBeInTheDocument();
    });

    it('lands on the Cockpit by default', () => {
      renderWithProviders();
      expect(screen.getByTestId('cockpit-tab')).toBeInTheDocument();
    });
  });

  describe('Tab navigation', () => {
    it('switches to Speakers & Agenda', async () => {
      renderWithProviders();
      fireEvent.click(screen.getByRole('tab', { name: /speakers/i }));
      await waitFor(() => expect(screen.getByTestId('event-speakers-tab')).toBeInTheDocument());
    });

    it('switches to Registrations', async () => {
      renderWithProviders();
      fireEvent.click(screen.getByRole('tab', { name: /registrations/i }));
      await waitFor(() => expect(screen.getByTestId('event-participants-tab')).toBeInTheDocument());
    });

    it('switches to Communications', async () => {
      renderWithProviders();
      fireEvent.click(screen.getByRole('tab', { name: /communications/i }));
      await waitFor(() =>
        expect(screen.getByTestId('event-communications-tab')).toBeInTheDocument()
      );
    });

    it('switches to Details (Info/Tasks/Settings sub-tabs)', async () => {
      renderWithProviders();
      fireEvent.click(screen.getByRole('tab', { name: /details/i }));
      await waitFor(() => expect(screen.getByTestId('details-subtab-info')).toBeInTheDocument());
      expect(screen.getByTestId('details-subtab-tasks')).toBeInTheDocument();
      expect(screen.getByTestId('details-subtab-settings')).toBeInTheDocument();
    });
  });

  describe('URL-based tab selection', () => {
    it('selects a tab from the ?tab= param', () => {
      renderWithProviders('/organizer/events/BAT54?tab=communications');
      expect(screen.getByTestId('event-communications-tab')).toBeInTheDocument();
    });

    it('defaults to Cockpit for an invalid ?tab=', () => {
      renderWithProviders('/organizer/events/BAT54?tab=bogus');
      expect(screen.getByTestId('cockpit-tab')).toBeInTheDocument();
    });
  });

  describe('Lifecycle dim/lock (FR5)', () => {
    it('locks Wrap-up before EVENT_LIVE (disabled tab)', () => {
      renderWithProviders();
      expect(screen.getByRole('tab', { name: /wrap-?up/i })).toBeDisabled();
    });

    it('falls back to Cockpit when the URL targets a locked tab', () => {
      renderWithProviders('/organizer/events/BAT54?tab=wrapup');
      expect(screen.getByTestId('cockpit-tab')).toBeInTheDocument();
      expect(screen.queryByTestId('event-wrapup-tab')).not.toBeInTheDocument();
    });

    it('unlocks Wrap-up from EVENT_LIVE onward', async () => {
      await setEvent({ workflowState: 'EVENT_LIVE' });
      renderWithProviders('/organizer/events/BAT54?tab=wrapup');
      expect(screen.getByRole('tab', { name: /wrap-?up/i })).not.toBeDisabled();
      expect(screen.getByTestId('event-wrapup-tab')).toBeInTheDocument();
    });

    it('keeps Cockpit and Details active in every state', async () => {
      await setEvent({ workflowState: 'CREATED' });
      renderWithProviders();
      expect(screen.getByRole('tab', { name: /cockpit/i })).not.toBeDisabled();
      expect(screen.getByRole('tab', { name: /details/i })).not.toBeDisabled();
    });
  });

  describe('Attention badges (FR6)', () => {
    it('shows the Speakers count badge when work is waiting', async () => {
      await setBadges({ speakers: 3 });
      renderWithProviders();
      expect(screen.getByTestId('event-tab-badge-speakers')).toBeInTheDocument();
    });

    it('shows the Publishing ready dot', async () => {
      await setBadges({ publishingReady: true });
      renderWithProviders();
      expect(screen.getByTestId('event-tab-badge-publishing')).toBeInTheDocument();
    });

    it('shows the Communications overdue dot', async () => {
      await setBadges({ commsOverdue: true });
      renderWithProviders();
      expect(screen.getByTestId('event-tab-badge-communications')).toBeInTheDocument();
    });

    it('shows no badges when all counts are zero', () => {
      renderWithProviders();
      expect(screen.queryByTestId('event-tab-badge-speakers')).not.toBeInTheDocument();
      expect(screen.queryByTestId('event-tab-badge-publishing')).not.toBeInTheDocument();
      expect(screen.queryByTestId('event-tab-badge-communications')).not.toBeInTheDocument();
    });
  });

  describe('Loading / error / not-found states', () => {
    it('shows the loading spinner', async () => {
      const { useEvent } = await import('@/hooks/useEvents');
      (useEvent as ReturnType<typeof vi.fn>).mockReturnValue({
        data: null,
        isLoading: true,
        error: null,
      });
      renderWithProviders();
      expect(screen.getByRole('progressbar')).toBeInTheDocument();
    });

    it('shows an error alert + back button', async () => {
      const { useEvent } = await import('@/hooks/useEvents');
      (useEvent as ReturnType<typeof vi.fn>).mockReturnValue({
        data: null,
        isLoading: false,
        error: { message: 'Failed to load event' },
      });
      renderWithProviders();
      expect(screen.getByText(/Failed to load event/i)).toBeInTheDocument();
      expect(screen.getByRole('button', { name: /back/i })).toBeInTheDocument();
    });

    it('shows the not-found alert', async () => {
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
    it('exposes a tablist with an aria-label', () => {
      renderWithProviders();
      const tablist = screen.getByRole('tablist');
      expect(tablist).toBeInTheDocument();
      expect(tablist).toHaveAttribute('aria-label');
    });
  });
});
