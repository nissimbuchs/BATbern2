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
import userEvent from '@testing-library/user-event';
import type { QueryClient } from '@tanstack/react-query';
import { HttpResponse } from 'msw';
import { api } from '@/test/msw/api';
import { aClaimCandidate, aUserSettings, futureEvent, pastEvent } from '@/test/fixtures';
import i18n from '@/i18n/config';
import { MAX_ENTRIES, WHATS_NEW_ENTRIES, selectEntries } from '@/features/whatsNew/catalogue';
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

  // Owner 2026-11-14: three cards, one row, even with more registrations (the next three).
  it('should_showOnlyTheNextThreeRegisteredEvents_when_userIsRegisteredForFour', async () => {
    given(CALENDAR.slice(0, 4).map((e) => registered(e)));

    renderPage();

    const cards = await upcomingCards();
    expect(cards).toHaveLength(3);
    expect(within(cards[0]).getByText('FinOps')).toBeInTheDocument();
    expect(screen.queryByText('Tbd Herbst 28')).not.toBeInTheDocument();
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
    expect(within(cards[0]).queryByTestId('attendee-event-status')).toBeNull();
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
    // Owner 2026-11-14: no participation status on the local card (it overflowed the card).
    expect(within(card).queryByTestId('attendee-event-status')).toBeNull();
  });
});

describe('AttendeeDashboardPage, What’s new card', () => {
  /** The stored "seen" time, as the server keeps it across browsers. */
  let storedSeenAt: string | undefined;

  /** What the card must show for a stored time, from the live list (it grows with every PR). */
  const expected = (seenAt: string | undefined, hasClaimCandidates = false) =>
    selectEntries(WHATS_NEW_ENTRIES, seenAt, { hasClaimCandidates }).map((e) => e.key);

  /** The day before the newest live entry: at least that entry is new, older ones are not. */
  const newestReleaseDay = () =>
    WHATS_NEW_ENTRIES.filter((e) => !e.condition)
      .map((e) => e.releasedOn)
      .sort()
      .at(-1)!;
  const dayBefore = (day: string) =>
    new Date(Date.parse(`${day}T00:00:00Z`) - 12 * 60 * 60 * 1000).toISOString();

  beforeEach(() => {
    storedSeenAt = undefined;
    api.on('listPublicEvents', () => page(CALENDAR));
    given([]);
    api.on('getUserSettings', () =>
      aUserSettings(storedSeenAt ? { whatsNewSeenAt: storedSeenAt } : {})
    );
    api.on('updateUserSettings', ({ body }) => {
      storedSeenAt = body?.whatsNewSeenAt;
      return aUserSettings({ whatsNewSeenAt: storedSeenAt });
    });
  });

  /** The settings have answered, one way or the other. */
  const settingsSettled = (queryClient: QueryClient) =>
    waitFor(() =>
      expect(['success', 'error']).toContain(
        queryClient.getQueryState(['users', 'me', 'settings', 'whatsNew'])?.status
      )
    );

  const entryKeys = async () => {
    const card = await screen.findByTestId('whats-new-card');
    return within(card)
      .getAllByTestId(/^whats-new-entry-/)
      .map((el) => el.getAttribute('data-testid')!.replace('whats-new-entry-', ''));
  };

  it('should_showTheMostImportantAtMostThree_when_neverSeen', async () => {
    renderPage();

    const shown = await entryKeys();
    expect(shown).toEqual(expected(undefined));
    expect(shown.length).toBeGreaterThan(0);
    expect(shown.length).toBeLessThanOrEqual(MAX_ENTRIES);
    const card = screen.getByTestId('whats-new-card');
    expect(card).toHaveAttribute('id', 'whats-new');
    // Each entry: its title, linking to where the feature is.
    const first = WHATS_NEW_ENTRIES.find((e) => e.key === shown[0])!;
    const title = within(card).getByText(i18n.t(`events:whatsNew.entries.${first.key}.title`));
    expect(title.closest('a')).toHaveAttribute('href', first.link);
    // The card sits above the dashboard's own content.
    const heading = screen.getByRole('heading', { level: 1 });
    expect(heading.compareDocumentPosition(card) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
  });

  it('should_showOnlyTheNewerEntries_when_seenBefore', async () => {
    storedSeenAt = dayBefore(newestReleaseDay());
    renderPage();

    const shown = await entryKeys();
    expect(shown).toEqual(expected(storedSeenAt));
    // Only entries released after the stored time.
    for (const key of shown) {
      const entry = WHATS_NEW_ENTRIES.find((e) => e.key === key)!;
      expect(Date.parse(`${entry.releasedOn}T00:00:00Z`)).toBeGreaterThan(Date.parse(storedSeenAt));
    }
  });

  it('should_showNoCard_when_upToDate', async () => {
    storedSeenAt = new Date().toISOString();
    const { queryClient } = renderPage();

    await screen.findByTestId('attendee-past');
    await settingsSettled(queryClient);
    expect(screen.queryByTestId('whats-new-card')).not.toBeInTheDocument();
  });

  it('should_hideTheCardAndStoreNow_when_closed_andShowNoneInAnotherBrowser', async () => {
    const user = userEvent.setup();
    const first = renderPage();

    await screen.findByTestId('whats-new-card');
    const before = Date.now();
    await user.click(screen.getByTestId('whats-new-close'));

    await waitFor(() => expect(screen.queryByTestId('whats-new-card')).not.toBeInTheDocument());
    const sent = api.calls('updateUserSettings')[0].body?.whatsNewSeenAt;
    expect(sent).toBeDefined();
    expect(Date.parse(sent!)).toBeGreaterThanOrEqual(before - 1000);
    expect(Object.keys(api.calls('updateUserSettings')[0].body ?? {})).toEqual(['whatsNewSeenAt']);

    // Another browser: a fresh query cache reads the stored time from the server.
    first.unmount();
    const second = renderPage();
    await screen.findByTestId('attendee-past');
    await settingsSettled(second.queryClient);
    expect(api.calls('getUserSettings').length).toBeGreaterThanOrEqual(2);
    expect(screen.queryByTestId('whats-new-card')).not.toBeInTheDocument();
  });

  it('should_linkToTheFullList_when_theCardShows', async () => {
    renderPage();

    const card = await screen.findByTestId('whats-new-card');
    expect(within(card).getByTestId('whats-new-all-link')).toHaveAttribute(
      'href',
      '/attendee/whats-new'
    );
  });

  it('should_keepTheCardWithoutAPageError_when_storingFails', async () => {
    api.on('updateUserSettings', () => HttpResponse.json({ message: 'boom' }, { status: 500 }));
    const user = userEvent.setup();
    renderPage();

    await screen.findByTestId('whats-new-card');
    await user.click(screen.getByTestId('whats-new-close'));

    await waitFor(() => expect(api.calls('updateUserSettings')).toHaveLength(1));
    await waitFor(() => expect(screen.getByTestId('whats-new-close')).toBeEnabled());
    expect(screen.getByTestId('whats-new-card')).toBeInTheDocument();
    expect(screen.queryByTestId('attendee-dashboard-error')).not.toBeInTheDocument();
  });

  it('should_putTheMergeFirst_when_theUserHasAnEarlierProfile', async () => {
    api.on('listProfileClaimCandidates', () => [aClaimCandidate()]);
    renderPage();

    await waitFor(async () => expect(await entryKeys()).toEqual(expected(undefined, true)));
    const merge = WHATS_NEW_ENTRIES.find((e) => e.condition === 'hasClaimCandidates')!;
    expect((await entryKeys())[0]).toBe(merge.key);
    expect(
      within(screen.getByTestId(`whats-new-entry-${merge.key}`)).getByRole('link')
    ).toHaveAttribute('href', '/attendee/dashboard#earlier-profiles');
    // The offer it links to is on the page, with the link target.
    expect(await screen.findByTestId('earlier-profiles-section')).toHaveAttribute(
      'id',
      'earlier-profiles'
    );
  });

  it('should_leaveOutTheMerge_when_theCandidatesCannotBeRead', async () => {
    api.on('listProfileClaimCandidates', () =>
      HttpResponse.json({ message: 'boom' }, { status: 401 })
    );
    renderPage();

    expect(await entryKeys()).toEqual(expected(undefined, false));
  });

  it('should_showNoCard_when_theSettingsCannotBeRead', async () => {
    api.on('getUserSettings', () => HttpResponse.json({ message: 'boom' }, { status: 401 }));
    const { queryClient } = renderPage();

    await screen.findByTestId('attendee-past');
    await settingsSettled(queryClient);
    expect(screen.queryByTestId('whats-new-card')).not.toBeInTheDocument();
  });
});
