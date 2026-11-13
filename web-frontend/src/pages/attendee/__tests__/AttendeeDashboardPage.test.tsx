/**
 * AttendeeDashboardPage tests (Story 7.6; story 3 of 2026-11-13).
 *
 * On the MSW harness: the real services, hooks and child components run; only the network is
 * answered, checked against the specs.
 *
 * Story 3: the "upcoming" list shows three cards, every upcoming event the user is registered for
 * first, then the next events they are not registered for. The cards are the homepage's: a
 * published event links to its event page, an unpublished one shows code, title and date only.
 */

import { describe, it, expect, beforeEach } from 'vitest';
import { screen, waitFor, within } from '@testing-library/react';
import { HttpResponse } from 'msw';
import { api } from '@/test/msw/api';
import { futureEvent, pastEvent } from '@/test/fixtures';
import type { Event } from '@/test/fixtures';
import { anAuthState, anAuthUser, renderWithAuth } from '@/test/auth';
import type { components } from '@/types/generated/events-core-api.types';
import AttendeeDashboardPage from '../AttendeeDashboardPage';

type AttendeeEventCard = components['schemas']['AttendeeEventCardResponse'];

/** Five events ahead, nearest first; BATbern63 to 65 are not in the published list. */
const CALENDAR: Event[] = [
  futureEvent(10, { eventCode: 'BATbern61', title: 'FinOps' }),
  futureEvent(100, { eventCode: 'BATbern62', title: 'AI Governance' }),
  futureEvent(200, { eventCode: 'BATbern63', title: 'Plattformen' }),
  futureEvent(300, { eventCode: 'BATbern64', title: 'Tbd Herbst 28' }),
  futureEvent(400, { eventCode: 'BATbern65', title: 'Tbd Frühling 29' }),
];
const UNPUBLISHED = new Set(['BATbern63', 'BATbern64', 'BATbern65']);

const page = (data: Event[]) => ({
  data,
  pagination: {
    page: 1,
    limit: 10,
    totalItems: data.length,
    totalPages: 1,
    hasNext: false,
    hasPrev: false,
  },
});

function registered(event: Event, status = 'confirmed'): AttendeeEventCard {
  return {
    eventCode: event.eventCode,
    eventTitle: event.title,
    eventDate: event.date,
    eventLocation: 'Kursaal Bern',
    workflowState: 'AGENDA_PUBLISHED',
    registrationStatus: status,
  };
}

/** The dashboard answer and the user's registrations behind `getMyRegistration`. */
function given(upcoming: AttendeeEventCard[]) {
  api.on('getAttendeeDashboard', () => ({
    attendeeName: 'Matthias Keller',
    upcomingEvents: upcoming,
    pastEvents: [
      {
        eventCode: 'BATbern50',
        eventTitle: 'Old Event',
        eventDate: pastEvent(400).date,
        eventLocation: 'Bern',
        workflowState: 'ARCHIVED',
        registrationStatus: 'attended',
      },
    ],
  }));
  const codes = new Set(upcoming.map((e) => e.eventCode));
  api.on('getMyRegistration', ({ params }) =>
    codes.has(params.eventCode)
      ? {
          registered: true,
          eventCode: params.eventCode,
          registrationCode: `${params.eventCode}-reg-ABC123`,
          status: 'CONFIRMED' as const,
          registrationDate: '2026-11-01T10:30:00Z',
          waitlistPosition: null,
        }
      : {
          registered: false,
          eventCode: null,
          registrationCode: null,
          registrationDate: null,
          status: null,
          waitlistPosition: null,
        }
  );
}

function renderPage() {
  return renderWithAuth(<AttendeeDashboardPage />, {
    auth: anAuthState(anAuthUser({ role: 'attendee', username: 'matthias.keller.2' })),
    initialEntries: ['/dashboard'],
  });
}

/** The cards of the upcoming list, in order. */
async function upcomingCards() {
  const section = await screen.findByTestId('attendee-upcoming');
  await waitFor(() =>
    expect(
      within(section).queryAllByTestId(/^(upcoming-event-(card|date-card)|attendee-event-card)$/)
        .length
    ).toBeGreaterThan(0)
  );
  return within(section).getAllByTestId(/^(upcoming-event-(card|date-card)|attendee-event-card)$/);
}

describe('AttendeeDashboardPage', () => {
  beforeEach(() => {
    // The calendar asks for 10 (presentationService), the published list for 5: the read model
    // answers both; the "calendar" holds events without a published phase too.
    api.on('listPublicEvents', ({ query }) =>
      query.get('limit') === '10'
        ? page(CALENDAR)
        : page(CALENDAR.filter((e) => !UNPUBLISHED.has(e.eventCode)))
    );
  });

  it('should_showTheNextThreeEvents_when_userHasNoRegistration', async () => {
    given([]);

    renderPage();

    const cards = await upcomingCards();
    expect(cards).toHaveLength(3);
    expect(within(cards[0]).getByText('FinOps')).toBeInTheDocument();
    expect(within(cards[1]).getByText('AI Governance')).toBeInTheDocument();
    expect(within(cards[2]).getByText('Plattformen')).toBeInTheDocument();
    // Published: linked to the event page, where the user registers. No register button.
    expect(within(cards[0]).getByTestId('event-card')).toHaveAttribute('href', '/events/BATbern61');
    expect(within(cards[0]).queryByRole('button', { name: /register/i })).toBeNull();
    expect(within(cards[0]).queryByTestId('event-card-status-chip')).toBeNull();
    // Not published: code, title and date only, no link, nothing else of the event.
    const planned = cards[2];
    expect(planned).toHaveAttribute('data-testid', 'upcoming-event-date-card');
    expect(within(planned).getByText('BATbern63')).toBeInTheDocument();
    expect(planned.closest('a')).toBeNull();
    expect(within(planned).queryByText('Kursaal Bern')).not.toBeInTheDocument();
    expect(screen.queryByTestId('attendee-upcoming-empty')).not.toBeInTheDocument();
  });

  it('should_showTheRegisteredEventFirst_when_userIsRegisteredForOne', async () => {
    given([registered(CALENDAR[1])]);

    renderPage();

    const cards = await upcomingCards();
    expect(cards).toHaveLength(3);
    expect(within(cards[0]).getByText('AI Governance')).toBeInTheDocument();
    expect(await within(cards[0]).findByTestId('event-card-status-chip')).toHaveTextContent(
      'Confirmed'
    );
    expect(within(cards[1]).getByText('FinOps')).toBeInTheDocument();
    expect(within(cards[2]).getByText('Plattformen')).toBeInTheDocument();
    expect(within(cards[1]).queryByTestId('event-card-status-chip')).toBeNull();
  });

  it('should_showAllRegisteredEventsAndNoOthers_when_userIsRegisteredForFour', async () => {
    given(CALENDAR.slice(0, 4).map((e) => registered(e)));

    renderPage();

    const cards = await upcomingCards();
    expect(cards).toHaveLength(4);
    expect(within(cards[3]).getByText('Tbd Herbst 28')).toBeInTheDocument();
    expect(screen.queryByText('Tbd Frühling 29')).not.toBeInTheDocument();
  });

  it('should_stillShowRegisteredEvents_when_thePublicListFails', async () => {
    api.on('listPublicEvents', () => HttpResponse.json({ message: 'boom' }, { status: 500 }));
    given([registered(CALENDAR[0])]);

    renderPage();

    const cards = await upcomingCards();
    expect(cards).toHaveLength(1);
    expect(cards[0]).toHaveAttribute('data-testid', 'attendee-event-card');
    expect(cards[0]).toHaveAttribute('href', '/events/BATbern61');
    expect(within(cards[0]).getByText('FinOps')).toBeInTheDocument();
    expect(within(cards[0]).getByTestId('attendee-event-status')).toHaveTextContent('Confirmed');
  });

  it('should_showTheEmptyText_when_thereIsNoUpcomingEventAtAll', async () => {
    api.on('listPublicEvents', () => page([]));
    given([]);

    renderPage();

    expect(await screen.findByTestId('attendee-upcoming-empty')).toBeInTheDocument();
  });

  it('should_keepThePastList_linkingToTheArchive', async () => {
    given([]);

    renderPage();

    const past = await screen.findByTestId('attendee-past');
    const card = await within(past).findByTestId('attendee-event-card');
    expect(card).toHaveAttribute('href', '/archive/BATbern50');
    expect(within(card).getByText('Old Event')).toBeInTheDocument();
  });
});
