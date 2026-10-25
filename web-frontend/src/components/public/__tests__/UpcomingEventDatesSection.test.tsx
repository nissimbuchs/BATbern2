/**
 * UpcomingEventDatesSection Component Tests (#1110)
 *
 * One card per upcoming event, date and title only. Unlike UpcomingEventsSection (published
 * events, full cards), this lists the planned calendar, including events without a published
 * phase. It reuses the list the presenter's "Upcoming events" slide already shows.
 */

import { describe, test, expect, vi, beforeEach } from 'vitest';
import { screen, waitFor, within } from '@testing-library/react';
import { render } from '@/test/test-utils';
import { UpcomingEventDatesSection } from '../UpcomingEventDatesSection';
import { presentationService } from '@/services/presentationService';

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string) => key,
  }),
}));

vi.mock('@/services/presentationService', () => ({
  presentationService: { getUpcomingEvents: vi.fn() },
}));

const mockGetUpcomingEvents = vi.mocked(presentationService.getUpcomingEvents);

const event = (eventCode: string, date: string, title: string) =>
  ({ eventCode, date, title }) as Awaited<
    ReturnType<typeof presentationService.getUpcomingEvents>
  >[number];

describe('UpcomingEventDatesSection', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  test('should show one card per upcoming event with date and title when events exist', async () => {
    mockGetUpcomingEvents.mockResolvedValue([
      event('BATbern61', '2027-03-05T00:00:00Z', 'TBD Frühling 27'),
      event('BATbern62', '2027-06-25T00:00:00Z', 'Tbd Sommer 27'),
      event('BATbern63', '2027-11-05T00:00:00Z', 'Tbd Herbst 27'),
    ]);

    render(<UpcomingEventDatesSection currentEventCode="BATbern60" />);

    const cards = await screen.findAllByTestId('upcoming-event-date-card');
    expect(cards).toHaveLength(3);
    expect(within(cards[0]).getByText('TBD Frühling 27')).toBeInTheDocument();
    expect(within(cards[0]).getByText(/5\. März 2027/)).toBeInTheDocument();
    expect(screen.getByText('public.upcomingDates.title')).toBeInTheDocument();
  });

  test('should exclude the event featured in the hero when fetching', async () => {
    mockGetUpcomingEvents.mockResolvedValue([]);

    render(<UpcomingEventDatesSection currentEventCode="BATbern60" />);

    await waitFor(() => expect(mockGetUpcomingEvents).toHaveBeenCalledWith('BATbern60'));
  });

  test('should render nothing when no upcoming events exist', async () => {
    mockGetUpcomingEvents.mockResolvedValue([]);

    const { container } = render(<UpcomingEventDatesSection currentEventCode="BATbern60" />);

    await waitFor(() => expect(mockGetUpcomingEvents).toHaveBeenCalled());
    expect(container).toBeEmptyDOMElement();
  });

  test('should show at most three cards when the service returns more', async () => {
    mockGetUpcomingEvents.mockResolvedValue([
      event('BATbern61', '2027-03-05T00:00:00Z', 'A'),
      event('BATbern62', '2027-06-25T00:00:00Z', 'B'),
      event('BATbern63', '2027-11-05T00:00:00Z', 'C'),
      event('BATbern64', '2028-03-03T00:00:00Z', 'D'),
    ]);

    render(<UpcomingEventDatesSection currentEventCode="BATbern60" />);

    expect(await screen.findAllByTestId('upcoming-event-date-card')).toHaveLength(3);
    expect(screen.queryByText('D')).not.toBeInTheDocument();
  });
});
