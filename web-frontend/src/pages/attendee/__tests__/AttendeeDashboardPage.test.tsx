/**
 * AttendeeDashboardPage Tests (Story 7.6)
 */
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter } from 'react-router-dom';
import AttendeeDashboardPage from '../AttendeeDashboardPage';

vi.mock('@/services/attendeeDashboardService', () => ({
  getDashboard: vi.fn(),
}));

vi.mock('@/hooks/useAuth', () => ({
  useAuth: () => ({ user: { username: 'jane.attendee' } }),
}));

// Keep the page's secondary panel out of the test (it has its own config/network deps).
vi.mock('@/components/attendee/CommunityTopicSuggestPanel', () => ({
  CommunityTopicSuggestPanel: () => <div data-testid="topic-panel" />,
}));

vi.mock('@/components/public/PublicLayout', () => ({
  PublicLayout: ({ children }: { children: React.ReactNode }) => <div>{children}</div>,
}));

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string, opts?: Record<string, unknown> | string) => {
      const map: Record<string, string> = {
        'attendee.dashboard.title': 'Your events',
        'attendee.dashboard.titleWithName': 'Welcome back, {{name}}',
        'attendee.dashboard.upcoming': 'Upcoming events',
        'attendee.dashboard.past': 'Past events',
        'attendee.dashboard.emptyUpcoming': 'No upcoming events',
        'attendee.dashboard.emptyPast': 'No past events yet.',
        'attendee.dashboard.error': 'Could not load your events.',
        'attendee.dashboard.status.confirmed': 'Confirmed',
        'attendee.dashboard.status.attended': 'Attended',
      };
      const val = map[key] ?? (typeof opts === 'string' ? opts : key);
      return typeof opts === 'object' && opts
        ? val.replace(/\{\{(\w+)\}\}/g, (_, k: string) =>
            String((opts as Record<string, unknown>)[k] ?? '')
          )
        : val;
    },
  }),
}));

import * as attendeeDashboardService from '@/services/attendeeDashboardService';

function renderPage() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>
        <AttendeeDashboardPage />
      </MemoryRouter>
    </QueryClientProvider>
  );
}

describe('AttendeeDashboardPage', () => {
  beforeEach(() => vi.clearAllMocks());

  it('renders upcoming + past events, each card linking to the right detail route', async () => {
    vi.mocked(attendeeDashboardService.getDashboard).mockResolvedValue({
      attendeeName: 'Jane',
      upcomingEvents: [
        {
          eventCode: 'BATbern99',
          eventTitle: 'Future Event',
          eventDate: '2099-01-01T09:00:00Z',
          eventLocation: 'Bern',
          workflowState: 'AGENDA_PUBLISHED',
          registrationStatus: 'confirmed',
        },
      ],
      pastEvents: [
        {
          eventCode: 'BATbern50',
          eventTitle: 'Old Event',
          eventDate: '2020-01-01T09:00:00Z',
          eventLocation: 'Bern',
          workflowState: 'ARCHIVED',
          registrationStatus: 'attended',
        },
      ],
    });

    renderPage();

    await waitFor(() => expect(screen.getByText('Future Event')).toBeInTheDocument());
    expect(screen.getByText('Old Event')).toBeInTheDocument();

    // Upcoming → /events/, past → /archive/
    const links = screen.getAllByTestId('attendee-event-card');
    const hrefs = links.map((l) => l.getAttribute('href'));
    expect(hrefs).toContain('/events/BATbern99');
    expect(hrefs).toContain('/archive/BATbern50');
  });

  it('shows empty states when there are no events', async () => {
    vi.mocked(attendeeDashboardService.getDashboard).mockResolvedValue({
      attendeeName: 'Jane',
      upcomingEvents: [],
      pastEvents: [],
    });

    renderPage();

    await waitFor(() => expect(screen.getByTestId('attendee-upcoming-empty')).toBeInTheDocument());
    expect(screen.getByTestId('attendee-past-empty')).toBeInTheDocument();
  });
});
