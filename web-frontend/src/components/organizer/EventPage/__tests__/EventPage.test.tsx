/**
 * EventPage Component Tests
 *
 * Story 5.6 — unified tab-based event page.
 * Epic 14 Phase A — lifecycle-aware tab IA: relevance-driven dim/lock,
 * count-driven attention badges, recomposed tab slots.
 *
 * The real tree runs against the MSW harness (17.0c2): `useEvent` → `GET /events/{eventCode}`,
 * `useTabBadges` → `GET /events/{eventCode}/tasks` + `GET /events/{eventCode}/publish/status`,
 * every tab (Cockpit, Speakers & Agenda, Registrations, Communications, Publishing, Wrap-up,
 * Details), the breadcrumbs, the router and en i18n. Navigation is asserted with a route sentinel;
 * the mobile layout comes from a `window.matchMedia` stub (jsdom has none).
 */

import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { HttpResponse, delay } from 'msw';
import { Routes, Route } from 'react-router-dom';
import i18n from '@/i18n/config';
import { api } from '@/test/msw/api';
import type { ResponseBody } from '@/test/msw/api';
import { renderWithAuth, signedInAs } from '@/test/auth';
import { anEvent, daysFromNow } from '@/test/fixtures';
import { EventPage } from '../EventPage';

type EventDetail = ResponseBody<'getEvent', 200>;
type PublishingStatus = ResponseBody<'getPublishingStatus', 200>;
type EventTask = ResponseBody<'listEventTasks', 200>[number];

const EVENT_CODE = 'BATbern54';
const TITLE = 'Architektur im Wandel: Zürich trifft Bern';

/**
 * An early-stage event (Wrap-up is locked at SPEAKER_IDENTIFICATION), as `GET /events/{code}`
 * answers the page's `include=...sessions...`: no sessions planned yet.
 */
const anEventDetail = (overrides: Partial<EventDetail> = {}): EventDetail => ({
  ...anEvent({
    eventCode: EVENT_CODE,
    eventNumber: 54,
    title: TITLE,
    workflowState: 'SPEAKER_IDENTIFICATION',
  }),
  sessions: [],
  ...overrides,
});

const serveEvent = (overrides: Partial<EventDetail> = {}) =>
  api.on('getEvent', () => anEventDetail(overrides));

const nothingPublishable: PublishingStatus = {
  currentPhase: null,
  publishedPhases: [],
  topic: { isValid: false, errors: ['Event topic must be defined'] },
  speakers: { isValid: false, errors: ['At least 3 sessions required'] },
  sessions: {
    isValid: false,
    errors: ['No sessions found for this event'],
    assignedCount: 0,
    totalCount: 0,
    unassignedSessions: [],
  },
};

const aTask = (overrides: Partial<EventTask> = {}): EventTask => ({
  id: '7d1f0c2a-5b3e-4c1d-9a8f-000000000001',
  eventCode: EVENT_CODE,
  taskName: 'Book the venue',
  triggerState: 'CREATED',
  status: 'todo',
  createdAt: daysFromNow(-30),
  updatedAt: daysFromNow(-30),
  ...overrides,
});

/** A `window.matchMedia` that evaluates min-/max-width queries for a viewport `width` px wide. */
const viewport = (width: number) => {
  window.matchMedia = ((query: string) => {
    const min = /min-width:\s*([\d.]+)px/.exec(query);
    const max = /max-width:\s*([\d.]+)px/.exec(query);
    const matches = (!min || width >= Number(min[1])) && (!max || width <= Number(max[1]));
    return {
      matches,
      media: query,
      onchange: null,
      addListener: () => {},
      removeListener: () => {},
      addEventListener: () => {},
      removeEventListener: () => {},
      dispatchEvent: () => false,
    };
  }) as typeof window.matchMedia;
};

const originalMatchMedia = window.matchMedia;

const renderPage = (initialRoute = `/organizer/events/${EVENT_CODE}`) =>
  renderWithAuth(
    <Routes>
      <Route path="/organizer/events/:eventCode" element={<EventPage />} />
      <Route path="/organizer/events" element={<div data-testid="events-list-route" />} />
      <Route path="/organizer/event-without-code" element={<EventPage />} />
    </Routes>,
    { auth: signedInAs('organizer'), initialEntries: [initialRoute] }
  );

/** Renders the page and waits for the event header. */
const renderLoaded = async (initialRoute?: string) => {
  const result = renderPage(initialRoute);
  await screen.findByRole('heading', { name: TITLE });
  return result;
};

describe('EventPage — lifecycle tab shell (Epic 14)', () => {
  beforeEach(() => {
    localStorage.clear();
    viewport(1280);
    serveEvent();
    api.on('listEventTasks', () => []);
    api.on('getPublishingStatus', () => nothingPublishable);
  });

  afterEach(async () => {
    window.matchMedia = originalMatchMedia;
    await i18n.changeLanguage('en');
  });

  describe('Header', () => {
    it('keeps the event title in the header (FR2)', async () => {
      await renderLoaded();
      expect(screen.getByRole('heading', { name: TITLE })).toBeInTheDocument();
    });

    it('asks for the event with the expansions the page needs', async () => {
      await renderLoaded();
      const call = api.calls('getEvent')[0];
      expect(call.params.eventCode).toBe(EVENT_CODE);
      expect(call.query.get('include')).toBe(
        'venue,topics,sessions,workflow,metrics,registrations'
      );
    });

    it('renders breadcrumbs that lead back to the event list', async () => {
      const user = userEvent.setup();
      await renderLoaded();
      const breadcrumbs = screen.getByRole('navigation', { name: 'breadcrumb' });
      expect(within(breadcrumbs).getByText(TITLE)).toBeInTheDocument();

      await user.click(within(breadcrumbs).getByText('Events'));

      expect(await screen.findByTestId('events-list-route')).toBeInTheDocument();
    });

    it('does NOT show Start Presentation / Live Control in the header (14.B.4 — they are Cockpit cards)', async () => {
      await renderLoaded();
      expect(screen.getByRole('heading', { name: TITLE })).toBeInTheDocument();
      expect(screen.queryByRole('link', { name: /start presentation/i })).not.toBeInTheDocument();
      expect(screen.queryByRole('button', { name: /live control/i })).not.toBeInTheDocument();
    });
  });

  describe('7-tab IA (FR1/FR3 — config cluster merged into Details)', () => {
    const pageTabs = () => within(screen.getByRole('tablist', { name: 'Event page navigation' }));

    it('renders exactly 7 tabs', async () => {
      await renderLoaded();
      expect(pageTabs().getAllByRole('tab')).toHaveLength(7);
    });

    it('renders the 7 lifecycle tabs (Settings folded into Details)', async () => {
      await renderLoaded();
      const tabs = pageTabs();
      expect(tabs.getByRole('tab', { name: 'Cockpit' })).toBeInTheDocument();
      expect(tabs.getByRole('tab', { name: 'Speakers & Agenda' })).toBeInTheDocument();
      expect(tabs.getByRole('tab', { name: 'Registrations' })).toBeInTheDocument();
      expect(tabs.getByRole('tab', { name: 'Communications' })).toBeInTheDocument();
      expect(tabs.getByRole('tab', { name: 'Publishing' })).toBeInTheDocument();
      expect(tabs.getByRole('tab', { name: /wrap-?up/i })).toBeInTheDocument();
      expect(tabs.getByRole('tab', { name: 'Details' })).toBeInTheDocument();
      // Settings is no longer a top-level tab — it's a sub-tab of Details now.
      expect(tabs.queryByRole('tab', { name: /^settings$/i })).not.toBeInTheDocument();
    });

    it('no longer shows the old consolidated standalone tabs', async () => {
      await renderLoaded();
      const tabs = pageTabs();
      expect(tabs.getAllByRole('tab')).toHaveLength(7);
      expect(tabs.queryByRole('tab', { name: /newsletter/i })).not.toBeInTheDocument();
      expect(tabs.queryByRole('tab', { name: /registrant notices/i })).not.toBeInTheDocument();
      expect(tabs.queryByRole('tab', { name: /photos/i })).not.toBeInTheDocument();
      expect(tabs.queryByRole('tab', { name: /appreciation/i })).not.toBeInTheDocument();
      expect(tabs.queryByRole('tab', { name: /participants/i })).not.toBeInTheDocument();
      expect(tabs.queryByRole('tab', { name: /^overview$/i })).not.toBeInTheDocument();
    });

    it('lands on the Cockpit by default', async () => {
      await renderLoaded();
      expect(pageTabs().getByRole('tab', { name: 'Cockpit' })).toHaveAttribute(
        'aria-selected',
        'true'
      );
      expect(screen.getByTestId('cockpit-tab')).toBeInTheDocument();
      expect(screen.getByText('At a glance')).toBeInTheDocument();
    });
  });

  describe('Tab navigation', () => {
    const clickTab = async (name: string | RegExp) => {
      const user = userEvent.setup();
      await renderLoaded();
      await user.click(
        within(screen.getByRole('tablist', { name: 'Event page navigation' })).getByRole('tab', {
          name,
        })
      );
    };

    it('switches to Speakers & Agenda', async () => {
      await clickTab('Speakers & Agenda');
      expect(await screen.findByTestId('add-speakers-button')).toBeInTheDocument();
      expect(screen.queryByTestId('cockpit-tab')).not.toBeInTheDocument();
    });

    it('switches to Registrations', async () => {
      await clickTab('Registrations');
      expect(await screen.findByTestId('add-participant-button')).toBeInTheDocument();
      expect(screen.queryByTestId('cockpit-tab')).not.toBeInTheDocument();
    });

    it('switches to Communications', async () => {
      await clickTab('Communications');
      expect(await screen.findByTestId('new-mail-button')).toBeInTheDocument();
      expect(screen.queryByTestId('cockpit-tab')).not.toBeInTheDocument();
    });

    it('switches to Details (Info/Tasks/Settings sub-tabs)', async () => {
      await clickTab('Details');
      const subTabs = within(await screen.findByRole('tablist', { name: 'Details' }));
      expect(subTabs.getByRole('tab', { name: 'Info' })).toBeInTheDocument();
      expect(subTabs.getByRole('tab', { name: 'Tasks' })).toBeInTheDocument();
      expect(subTabs.getByRole('tab', { name: 'Settings' })).toBeInTheDocument();
    });
  });

  describe('URL-based tab selection', () => {
    it('selects a tab from the ?tab= param', async () => {
      await renderLoaded(`/organizer/events/${EVENT_CODE}?tab=communications`);
      expect(await screen.findByTestId('new-mail-button')).toBeInTheDocument();
      expect(screen.queryByTestId('cockpit-tab')).not.toBeInTheDocument();
    });

    it('defaults to Cockpit for an invalid ?tab=', async () => {
      await renderLoaded(`/organizer/events/${EVENT_CODE}?tab=bogus`);
      expect(screen.getByTestId('cockpit-tab')).toBeInTheDocument();
    });
  });

  describe('Lifecycle dim/lock (FR5)', () => {
    it('locks Wrap-up before EVENT_LIVE (disabled tab)', async () => {
      await renderLoaded();
      expect(screen.getByRole('tab', { name: /wrap-?up/i })).toBeDisabled();
      expect(screen.getByRole('tab', { name: 'Cockpit' })).not.toBeDisabled();
    });

    it('falls back to Cockpit when the URL targets a locked tab', async () => {
      await renderLoaded(`/organizer/events/${EVENT_CODE}?tab=wrapup`);
      expect(screen.getByTestId('cockpit-tab')).toBeInTheDocument();
      expect(screen.queryByTestId('wrapup-panel')).not.toBeInTheDocument();
    });

    it('unlocks Wrap-up from EVENT_LIVE onward', async () => {
      serveEvent({ workflowState: 'EVENT_LIVE', date: daysFromNow(0) });
      await renderLoaded(`/organizer/events/${EVENT_CODE}?tab=wrapup`);
      expect(screen.getByRole('tab', { name: /wrap-?up/i })).not.toBeDisabled();
      expect(await screen.findByTestId('wrapup-panel')).toBeInTheDocument();
      expect(screen.queryByTestId('cockpit-tab')).not.toBeInTheDocument();
    });

    it('keeps Cockpit and Details active in every state', async () => {
      serveEvent({ workflowState: 'CREATED' });
      await renderLoaded();
      expect(screen.getByRole('tab', { name: 'Cockpit' })).not.toBeDisabled();
      expect(screen.getByRole('tab', { name: 'Details' })).not.toBeDisabled();
    });
  });

  describe('Attention badges (FR6)', () => {
    it('shows the Speakers count badge when work is waiting', async () => {
      // Two sessions still need a slot + one speaker's materials need a review = 3.
      serveEvent({
        pendingMaterialsCount: 1,
        sessions: [
          { sessionSlug: 'keynote', eventCode: EVENT_CODE, title: 'Keynote', language: 'de' },
          {
            sessionSlug: 'panel',
            eventCode: EVENT_CODE,
            title: 'Panel',
            language: 'de',
            startTime: null,
          },
          {
            sessionSlug: 'welcome',
            eventCode: EVENT_CODE,
            title: 'Begrüssung',
            language: 'de',
            startTime: daysFromNow(40),
          },
        ],
      });
      await renderLoaded();
      const badge = screen.getByTestId('event-tab-badge-speakers');
      expect(within(badge).getByText('3')).toBeInTheDocument();
    });

    it('shows the Publishing ready dot', async () => {
      api.on('getPublishingStatus', () => ({
        ...nothingPublishable,
        topic: { isValid: true, errors: [] },
      }));
      await renderLoaded();
      expect(await screen.findByTestId('event-tab-badge-publishing')).toBeInTheDocument();
      expect(api.calls('getPublishingStatus')[0].params.eventCode).toBe(EVENT_CODE);
    });

    it('shows the Communications overdue dot', async () => {
      api.on('listEventTasks', () => [
        aTask({ taskName: 'Newsletter versenden', status: 'todo', dueDate: daysFromNow(-2) }),
      ]);
      await renderLoaded();
      expect(await screen.findByTestId('event-tab-badge-communications')).toBeInTheDocument();
      expect(api.calls('listEventTasks')[0].params.eventCode).toBe(EVENT_CODE);
    });

    it('shows no badges when all counts are zero', async () => {
      await renderLoaded();
      await waitFor(() => {
        expect(api.calls('listEventTasks')).toHaveLength(1);
        expect(api.calls('getPublishingStatus')).toHaveLength(1);
      });
      expect(screen.getByRole('tab', { name: 'Speakers & Agenda' })).toBeInTheDocument();
      expect(screen.queryByTestId('event-tab-badge-speakers')).not.toBeInTheDocument();
      expect(screen.queryByTestId('event-tab-badge-publishing')).not.toBeInTheDocument();
      expect(screen.queryByTestId('event-tab-badge-communications')).not.toBeInTheDocument();
    });
  });

  describe('Loading / error / not-found states', () => {
    it('shows the loading spinner', async () => {
      api.on('getEvent', async () => {
        await delay('infinite');
        return anEventDetail();
      });
      renderPage();
      expect(screen.getByRole('progressbar')).toBeInTheDocument();
      expect(screen.queryByRole('heading', { name: TITLE })).not.toBeInTheDocument();
    });

    it('shows an error alert + a back button that returns to the event list', async () => {
      const user = userEvent.setup();
      api.on('getEvent', () =>
        HttpResponse.json({ error: 'INTERNAL_ERROR', message: 'boom' }, { status: 500 })
      );
      renderPage();

      expect(await screen.findByText('Server Error: Please try again later')).toBeInTheDocument();
      await user.click(screen.getByRole('button', { name: 'Back' }));

      expect(await screen.findByTestId('events-list-route')).toBeInTheDocument();
    });

    it('shows the not-found alert, translated, when the event does not exist (404)', async () => {
      await i18n.changeLanguage('de');
      api.on('getEvent', () =>
        HttpResponse.json(
          { error: 'NOT_FOUND', message: 'Event not found: BATbern999', status: 404 },
          { status: 404 }
        )
      );
      renderPage();

      expect(await screen.findByText('Veranstaltung nicht gefunden')).toBeInTheDocument();
      // The client's English error text is not what the organizer sees.
      expect(screen.queryByText(/requested event was not found/i)).not.toBeInTheDocument();
      expect(screen.getByRole('button', { name: 'Zurück' })).toBeInTheDocument();
    });

    it('shows the not-found alert when there is no event code to load', async () => {
      renderPage('/organizer/event-without-code');
      expect(await screen.findByText('Event not found')).toBeInTheDocument();
      expect(screen.getByRole('button', { name: 'Back' })).toBeInTheDocument();
      expect(api.calls('getEvent')).toHaveLength(0);
    });
  });

  describe('Accessibility', () => {
    it('exposes a tablist with an aria-label', async () => {
      await renderLoaded();
      expect(screen.getByRole('tablist', { name: 'Event page navigation' })).toHaveAttribute(
        'aria-label',
        'Event page navigation'
      );
    });
  });

  describe('Mobile navigation (Phase G — 14.G.1)', () => {
    beforeEach(() => {
      viewport(375);
    });

    it('shows the four day-to-day destinations + a ⋯ More entry in the bottom bar', async () => {
      await renderLoaded();
      expect(await screen.findByTestId('event-more-button')).toBeInTheDocument();
      expect(screen.getByTestId('event-tab-cockpit')).toBeInTheDocument();
      expect(screen.getByTestId('event-tab-speakers')).toBeInTheDocument();
      expect(screen.getByTestId('event-tab-registrations')).toBeInTheDocument();
      expect(screen.getByTestId('event-tab-communications')).toBeInTheDocument();
      // Occasional tabs are NOT in the bottom bar — they live in the More sheet.
      expect(screen.queryByTestId('event-tab-publishing')).not.toBeInTheDocument();
      // The desktop tab strip is gone.
      expect(
        screen.queryByRole('tablist', { name: 'Event page navigation' })
      ).not.toBeInTheDocument();
    });

    it('opens the More sheet listing Publishing · Wrap-up · Details', async () => {
      const user = userEvent.setup();
      await renderLoaded();
      await user.click(await screen.findByTestId('event-more-button'));
      expect(await screen.findByTestId('event-more-sheet')).toBeInTheDocument();
      expect(screen.getByTestId('event-more-tab-publishing')).toBeInTheDocument();
      expect(screen.getByTestId('event-more-tab-wrapup')).toBeInTheDocument();
      expect(screen.getByTestId('event-more-tab-details')).toBeInTheDocument();
    });

    it('locks Wrap-up in the More sheet before EVENT_LIVE', async () => {
      const user = userEvent.setup();
      await renderLoaded();
      await user.click(await screen.findByTestId('event-more-button'));
      expect(await screen.findByTestId('event-more-lock-wrapup')).toBeInTheDocument();
      expect(screen.getByTestId('event-more-tab-wrapup')).toHaveAttribute('aria-disabled', 'true');
    });

    it('unlocks Wrap-up in the More sheet once EVENT_LIVE', async () => {
      const user = userEvent.setup();
      serveEvent({ workflowState: 'EVENT_LIVE', date: daysFromNow(0) });
      await renderLoaded();
      await user.click(await screen.findByTestId('event-more-button'));
      expect(await screen.findByTestId('event-more-tab-wrapup')).toBeInTheDocument();
      expect(screen.queryByTestId('event-more-lock-wrapup')).not.toBeInTheDocument();

      await user.click(screen.getByTestId('event-more-tab-wrapup'));
      expect(await screen.findByTestId('wrapup-panel')).toBeInTheDocument();
    });
  });
});
