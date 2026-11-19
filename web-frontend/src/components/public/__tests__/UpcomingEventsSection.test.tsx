/**
 * UpcomingEventsSection Component Tests
 *
 * One section "Nächste BAT-Termine" (owner, 2026-11-07, replaces the separate dates section of
 * #1110): the next three events after the one in the hero, from the planned calendar. An event
 * with a published phase gets the full card (link, topic, lineup, registration chip); an event
 * without one shows date and title only, with no link (history 2026-06-11: unpublished details
 * must not leak).
 *
 * Network at the wire (MSW, `listPublicEvents`): the planned calendar is the request without
 * `page` (`presentationService.getUpcomingEvents`), the published list the one with it
 * (`eventApiClient.getPublicEvents`). Real i18next (English), real auth context (signed out).
 */

import { describe, test, expect } from 'vitest';
import { HttpResponse } from 'msw';
import { screen, waitFor, within } from '@testing-library/react';
import i18n from '@/i18n/config';
import { api } from '@/test/msw/api';
import { renderWithAuth, signedOut } from '@/test/auth';
import { anEvent, type Event } from '@/test/fixtures/events';
import { UpcomingEventsSection } from '../UpcomingEventsSection';

const topic = (name: string): NonNullable<Event['topic']> => ({
  code: name.toLowerCase().replace(/\s+/g, '-'),
  name,
  description: null,
  category: 'technical',
});

const planned = (eventCode: string, date: string, title: string): Event =>
  anEvent({ eventCode, date, title, topic: topic('internal draft topic') });

const published = (eventCode: string, date: string): Event =>
  anEvent({
    eventCode,
    title: `${eventCode} published title`,
    date,
    topic: topic('Architecture'),
    sessions: [],
    speakers: [
      {
        username: 'jane.doe',
        firstName: 'Jane',
        lastName: 'Doe',
        company: null,
        companyDisplayName: null,
        companyLogoUrl: null,
        profilePictureUrl: null,
        bio: null,
        speakerRole: null,
        talkTitle: null,
        sessionSlug: null,
      },
    ],
  });

const page = (data: Event[]) => ({
  data,
  pagination: {
    page: 1,
    limit: 20,
    totalItems: data.length,
    totalPages: 1,
    hasNext: false,
    hasPrev: false,
  },
});

/** Answers the calendar (no `page`) and the published list (`page`) of `GET /public/events`. */
function serve(calendar: Event[], publishedList: Event[] | 'fail') {
  api.on('listPublicEvents', ({ query }) => {
    if (!query.has('page')) return page(calendar);
    if (publishedList === 'fail') return HttpResponse.json({ message: 'boom' }, { status: 500 });
    return page(publishedList);
  });
}

const title = () => i18n.t('events:public.upcomingDates.title');

const render = (ui: React.ReactElement) => renderWithAuth(ui, { auth: signedOut() });

describe('UpcomingEventsSection', () => {
  test('should show the next three events after the hero, nearest first, under one title', async () => {
    serve(
      [
        planned('BATbern60', '2027-01-15T00:00:00Z', 'The hero event'),
        planned('BATbern61', '2027-03-05T00:00:00Z', 'FinOps'),
        planned('BATbern62', '2027-06-25T00:00:00Z', 'AI Governance'),
        planned('BATbern63', '2027-11-05T00:00:00Z', 'Tbd Herbst 27'),
      ],
      []
    );

    render(<UpcomingEventsSection currentEventCode="BATbern60" />);

    const cards = await screen.findAllByTestId(/^upcoming-event-(card|date-card)$/);
    expect(cards).toHaveLength(3);
    expect(within(cards[0]).getByText('FinOps')).toBeInTheDocument();
    expect(within(cards[2]).getByText('Tbd Herbst 27')).toBeInTheDocument();
    expect(screen.getByText(title())).toBeInTheDocument();
    // The event in the hero is not repeated.
    expect(screen.queryByText('The hero event')).not.toBeInTheDocument();
    expect(screen.getAllByRole('heading', { level: 2 })).toHaveLength(1);
  });

  test('should show the full linked card for a published event and date + title only otherwise', async () => {
    serve(
      [
        planned('BATbern61', '2027-03-05T00:00:00Z', 'FinOps'),
        planned('BATbern62', '2027-06-25T00:00:00Z', 'AI Governance'),
      ],
      [published('BATbern61', '2027-03-05T00:00:00Z')]
    );

    render(<UpcomingEventsSection currentEventCode="BATbern60" />);

    // Published: the full EventCard with its link, topic and lineup.
    const full = await screen.findByTestId('event-card-BATbern61');
    expect(full.closest('a')).toHaveAttribute('href', '/events/BATbern61');
    expect(within(full).getByText('Architecture')).toBeInTheDocument();

    // Not published: date and title in the same card style, no link, no topic.
    const dateCard = screen.getByTestId('upcoming-event-date-card');
    expect(within(dateCard).getByText('AI Governance')).toBeInTheDocument();
    // In the UI language (#1222 item 6): English here, no fixed German date.
    expect(within(dateCard).getByText('June 25, 2027')).toBeInTheDocument();
    expect(dateCard.closest('a')).toBeNull();
    expect(within(dateCard).queryByText('internal draft topic')).not.toBeInTheDocument();
  });

  test('should render nothing when no event follows the hero', async () => {
    serve([], []);

    const { container } = render(<UpcomingEventsSection currentEventCode="BATbern60" />);

    await waitFor(() => expect(api.calls('listPublicEvents').length).toBeGreaterThan(0));
    await waitFor(() => expect(container).toBeEmptyDOMElement());
  });

  test('should fall back to date + title cards when the published list fails', async () => {
    serve([planned('BATbern61', '2027-03-05T00:00:00Z', 'FinOps')], 'fail');

    render(<UpcomingEventsSection currentEventCode="BATbern60" />);

    const dateCard = await screen.findByTestId('upcoming-event-date-card');
    expect(within(dateCard).getByText('FinOps')).toBeInTheDocument();
  });
});
