/**
 * UpcomingEventsSection Component Tests
 *
 * One section "Nächste BAT-Termine" (owner, 2026-11-07, replaces the separate dates section of
 * #1110): the next three events after the one in the hero, from the planned calendar. An event
 * with a published phase gets the full card (link, topic, lineup, registration chip); an event
 * without one shows date and title only, with no link (history 2026-06-11: unpublished details
 * must not leak).
 */

import { describe, test, expect, vi, beforeEach } from 'vitest';
import { screen, waitFor, within } from '@testing-library/react';
import { render } from '@/test/test-utils';
import { UpcomingEventsSection } from '../UpcomingEventsSection';
import { eventApiClient } from '@/services/eventApiClient';
import { presentationService } from '@/services/presentationService';
import type { EventDetailUI } from '@/types/event.types';

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string) => key,
  }),
}));

// useMyRegistration is called per published card; stub it to "no registration".
vi.mock('@/hooks/useMyRegistration', () => ({
  useMyRegistration: () => ({ data: undefined }),
}));

// A TOPIC-phase card mounts SpeakerSelfNominatePanel, which calls useAuth; stub it.
vi.mock('@/hooks/useAuth/useAuth', () => ({
  useAuth: () => ({ isAuthenticated: false }),
}));

vi.mock('@/services/eventApiClient', () => ({
  eventApiClient: { getPublicEvents: vi.fn() },
}));

vi.mock('@/services/presentationService', () => ({
  presentationService: { getUpcomingEvents: vi.fn() },
}));

const mockPublished = vi.mocked(eventApiClient.getPublicEvents);
const mockCalendar = vi.mocked(presentationService.getUpcomingEvents);

type CalendarEvent = Awaited<ReturnType<typeof presentationService.getUpcomingEvents>>[number];

const planned = (eventCode: string, date: string, title: string) =>
  ({ eventCode, date, title, topic: { name: 'internal draft topic' } }) as unknown as CalendarEvent;

function published(eventCode: string, date: string): EventDetailUI {
  return {
    eventCode,
    title: `${eventCode} published title`,
    date,
    currentPublishedPhase: 'SPEAKERS',
    topic: { name: 'Architecture' },
    sessions: [],
    speakers: [{ username: 'jane.doe', firstName: 'Jane', lastName: 'Doe' }],
  } as unknown as EventDetailUI;
}

const page = (data: EventDetailUI[]) =>
  ({
    data,
    pagination: {
      page: 1,
      limit: 5,
      totalItems: data.length,
      totalPages: 1,
      hasNext: false,
      hasPrev: false,
    },
  }) as never;

describe('UpcomingEventsSection', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  test('should show the next three events after the hero, nearest first, under one title', async () => {
    mockCalendar.mockResolvedValue([
      planned('BATbern61', '2027-03-05T00:00:00Z', 'FinOps'),
      planned('BATbern62', '2027-06-25T00:00:00Z', 'AI Governance'),
      planned('BATbern63', '2027-11-05T00:00:00Z', 'Tbd Herbst 27'),
    ]);
    mockPublished.mockResolvedValue(page([]));

    render(<UpcomingEventsSection currentEventCode="BATbern60" />);

    const cards = await screen.findAllByTestId(/^upcoming-event-(card|date-card)$/);
    expect(cards).toHaveLength(3);
    expect(within(cards[0]).getByText('FinOps')).toBeInTheDocument();
    expect(within(cards[2]).getByText('Tbd Herbst 27')).toBeInTheDocument();
    expect(screen.getByText('public.upcomingDates.title')).toBeInTheDocument();
    expect(mockCalendar).toHaveBeenCalledWith('BATbern60');
    expect(screen.getAllByRole('heading', { level: 2 })).toHaveLength(1);
  });

  test('should show the full linked card for a published event and date + title only otherwise', async () => {
    mockCalendar.mockResolvedValue([
      planned('BATbern61', '2027-03-05T00:00:00Z', 'FinOps'),
      planned('BATbern62', '2027-06-25T00:00:00Z', 'AI Governance'),
    ]);
    mockPublished.mockResolvedValue(page([published('BATbern61', '2027-03-05T00:00:00Z')]));

    render(<UpcomingEventsSection currentEventCode="BATbern60" />);

    // Published: the full EventCard with its link, topic and lineup.
    const full = await screen.findByTestId('event-card-BATbern61');
    expect(full.closest('a')).toHaveAttribute('href', '/events/BATbern61');
    expect(within(full).getByText('Architecture')).toBeInTheDocument();

    // Not published: date and title in the same card style, no link, no topic.
    const dateCard = screen.getByTestId('upcoming-event-date-card');
    expect(within(dateCard).getByText('AI Governance')).toBeInTheDocument();
    expect(within(dateCard).getByText(/25\. Juni 2027/)).toBeInTheDocument();
    expect(dateCard.closest('a')).toBeNull();
    expect(within(dateCard).queryByText('internal draft topic')).not.toBeInTheDocument();
  });

  test('should render nothing when no event follows the hero', async () => {
    mockCalendar.mockResolvedValue([]);
    mockPublished.mockResolvedValue(page([]));

    const { container } = render(<UpcomingEventsSection currentEventCode="BATbern60" />);

    await waitFor(() => expect(mockCalendar).toHaveBeenCalled());
    await waitFor(() => expect(container).toBeEmptyDOMElement());
  });

  test('should fall back to date + title cards when the published list fails', async () => {
    mockCalendar.mockResolvedValue([planned('BATbern61', '2027-03-05T00:00:00Z', 'FinOps')]);
    mockPublished.mockRejectedValue(new Error('boom'));

    render(<UpcomingEventsSection currentEventCode="BATbern60" />);

    const dateCard = await screen.findByTestId('upcoming-event-date-card');
    expect(within(dateCard).getByText('FinOps')).toBeInTheDocument();
  });
});
