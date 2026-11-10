/**
 * EventSpeakersTab Component Tests (Story 5.6 · Epic 14 Phase C 14.C.1/14.C.4/14.C.5)
 *
 * The real tree runs against the MSW harness (17.0c2): `GET /events/{code}/speakers/status-summary`,
 * `GET /events/{code}/speakers/pool`, `GET /events/{code}?include=sessions`, and in the Slots
 * sub-view `GET /events/{code}/sessions/unassigned` + `GET /events/{code}/timetable`. Every child
 * (kanban lanes, sessions table, slot assignment, drawers) is the real one, with en i18n. Covers the
 * Pool/Agenda/Slots sub-view contract, the agenda "needs a slot" summary + jump, and the in-tab
 * Slots sub-view (focusSpeakerId carried from ?speakerId=).
 */

import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, beforeEach } from 'vitest';
import { api } from '@/test/msw/api';
import type { ResponseBody } from '@/test/msw/api';
import { renderWithAuth, signedInAs } from '@/test/auth';
import {
  aSessionResponse,
  aSessionSpeaker,
  aSpeakerPoolEntry,
  anEvent,
  daysFromNow,
  shapes,
} from '@/test/fixtures';
import { EventSpeakersTab } from '../EventSpeakersTab';

type EventDetail = ResponseBody<'getEvent', 200>;
type SessionResponse = NonNullable<EventDetail['sessions']>[number];
type PoolEntry = ResponseBody<'getSpeakerPool', 200>[number];

const EVENT_CODE = 'BATbern54';
const EVENT_DATE = daysFromNow(60);

const speaker = shapes.bruennimann; // Matthias Brünnimann, username matthias.bruennimann
const otherSpeaker = shapes.vietnamese; // Hông Ân Meier, username hongan.meier

/** A session as `GET /events/{code}?include=sessions` and `/sessions/unassigned` return it. */
const aSession = (overrides: Partial<SessionResponse>): SessionResponse => ({
  ...aSessionResponse(),
  sessionSlug: 'session',
  eventCode: EVENT_CODE,
  title: 'Session',
  sessionType: 'presentation',
  language: 'de',
  ...overrides,
});

/** Slotted: has a start time and a room. */
const slottedSession = aSession({
  sessionSlug: 'hongan-meier-plattform-teams',
  title: 'Plattform-Teams in der Praxis',
  startTime: '2026-09-01T16:15:00Z',
  endTime: '2026-09-01T17:00:00Z',
  room: 'Saal',
  speakers: [
    {
      ...aSessionSpeaker(),
      username: otherSpeaker.id,
      firstName: otherSpeaker.firstName,
      lastName: otherSpeaker.lastName,
      speakerRole: 'PRIMARY_SPEAKER',
      isConfirmed: true,
    },
  ],
});

/** Needs a slot: `startTime: null`. */
const unslottedSession = aSession({
  sessionSlug: 'matthias-bruennimann-ki-architektur',
  title: 'KI in der Architekturarbeit',
  startTime: null,
  endTime: null,
  speakers: [
    {
      ...aSessionSpeaker(),
      username: speaker.id,
      firstName: speaker.firstName,
      lastName: speaker.lastName,
      speakerRole: 'PRIMARY_SPEAKER',
      isConfirmed: true,
    },
  ],
});

const anEventDetail = (sessions: SessionResponse[]): EventDetail => ({
  ...anEvent({
    eventCode: EVENT_CODE,
    eventNumber: 54,
    date: EVENT_DATE,
    workflowState: 'SPEAKER_IDENTIFICATION',
  }),
  sessions,
});

const poolEntry: PoolEntry = {
  ...aSpeakerPoolEntry(),
  id: '3f6c1e2a-8b4d-4c7e-9a1f-5d2b7e8c0a11',
  eventId: '9a1b2c3d-4e5f-4a6b-8c7d-0e1f2a3b4c5d',
  speakerName: `${speaker.firstName} ${speaker.lastName}`,
  username: speaker.id,
  company: 'buecherstube',
  status: 'QUALITY_REVIEWED',
  sessionSlug: unslottedSession.sessionSlug,
  isSlotAssigned: false,
  createdAt: daysFromNow(-20),
  updatedAt: daysFromNow(-2),
};

const renderAt = (search = '') =>
  renderWithAuth(<EventSpeakersTab eventCode={EVENT_CODE} />, {
    auth: signedInAs('organizer'),
    initialEntries: [`/organizer/events/${EVENT_CODE}?tab=speakers${search}`],
  });

describe('EventSpeakersTab — Pool/Agenda/Slots sub-views (Epic 14 Phase C)', () => {
  beforeEach(() => {
    localStorage.clear();
    api.on('getStatusSummary', () => ({
      eventCode: EVENT_CODE,
      statusCounts: { ACCEPTED: 8, INVITED: 4 },
      totalSpeakers: 12,
      acceptedCount: 8,
      declinedCount: 0,
      pendingCount: 4,
      acceptanceRate: 66.7,
      minSlotsRequired: 12,
      maxSlotsAllowed: 12,
      thresholdMet: false,
      overflowDetected: false,
    }));
    api.on('getSpeakerPool', () => [poolEntry]);
    api.on('getEvent', () => anEventDetail([slottedSession, unslottedSession]));
    api.on('getUnassignedSessions', () => [unslottedSession]);
  });

  it('should_renderThreeWayToggle_when_loaded', async () => {
    renderAt();
    expect(await screen.findByTestId('pool-view-toggle')).toBeInTheDocument();
    expect(screen.getByTestId('agenda-view-toggle')).toBeInTheDocument();
    expect(screen.getByTestId('slots-view-toggle')).toBeInTheDocument();
    // The summary bar reads the status summary from the wire.
    expect(screen.getByText('8/12')).toBeInTheDocument();
    expect(api.calls('getStatusSummary')[0].params.eventCode).toBe(EVENT_CODE);
    expect(api.calls('getSpeakerPool')[0].params.eventCode).toBe(EVENT_CODE);
  });

  it('should_defaultToPoolKanban_when_noViewParam', async () => {
    renderAt();
    const card = await screen.findByTestId(`speaker-card-${poolEntry.id}`);
    expect(within(card).getByText('Matthias Brünnimann')).toBeInTheDocument();
    expect(screen.queryByTestId('needs-slot-summary')).not.toBeInTheDocument();
  });

  it('should_askForSessions_when_loadingTheEvent', async () => {
    renderAt('&view=agenda');
    await screen.findByTestId('needs-slot-summary');
    const call = api.calls('getEvent')[0];
    expect(call.params.eventCode).toBe(EVENT_CODE);
    expect(call.query.get('include')).toContain('sessions');
  });

  it('should_renderAgenda_when_viewAgenda', async () => {
    renderAt('&view=agenda');
    expect(await screen.findByText('Plattform-Teams in der Praxis')).toBeInTheDocument();
    expect(screen.getByText('KI in der Architekturarbeit')).toBeInTheDocument();
    expect(screen.queryByTestId(`speaker-card-${poolEntry.id}`)).not.toBeInTheDocument();
  });

  it('should_renderSlots_when_viewSlots', async () => {
    renderAt('&view=slots');
    expect(
      await screen.findByTestId(`tray-session-${unslottedSession.sessionSlug}`)
    ).toBeInTheDocument();
    expect(api.calls('getUnassignedSessions')[0].params.eventCode).toBe(EVENT_CODE);
    expect(screen.queryByTestId('needs-slot-summary')).not.toBeInTheDocument();
  });

  it('should_loadUnassignedSessionsOnce_when_slotsOpened', async () => {
    renderAt('&view=slots');
    await screen.findByTestId('slot-action-bar');
    // Let any follow-up render settle, then the tray must still be there and fetched once:
    // a refetch would flash the loading skeleton and drop the tray in between.
    await waitFor(() =>
      expect(screen.getByTestId(`tray-session-${unslottedSession.sessionSlug}`)).toBeInTheDocument()
    );
    await new Promise((resolve) => setTimeout(resolve, 100));
    expect(screen.queryByTestId('loading-skeleton')).not.toBeInTheDocument();
    expect(api.calls('getUnassignedSessions')).toHaveLength(1);
  });

  it('should_mapLegacyKanbanToPool_and_sessionsToAgenda', async () => {
    const kanban = renderAt('&view=kanban');
    expect(await screen.findByTestId(`speaker-card-${poolEntry.id}`)).toBeInTheDocument();
    kanban.unmount();

    renderAt('&view=sessions');
    expect(await screen.findByTestId('needs-slot-summary')).toBeInTheDocument();
    expect(screen.getByText('KI in der Architekturarbeit')).toBeInTheDocument();
  });

  it('should_carryFocusSpeakerId_into_slots', async () => {
    renderAt(`&view=slots&speakerId=${speaker.id}`);
    // The tray highlights the session of the speaker named in ?speakerId=.
    expect(
      await screen.findByTestId(`focused-session-${unslottedSession.sessionSlug}`)
    ).toBeInTheDocument();
    expect(
      screen.queryByTestId(`tray-session-${unslottedSession.sessionSlug}`)
    ).not.toBeInTheDocument();
  });

  it('should_showNeedsSlotSummary_when_agenda', async () => {
    renderAt('&view=agenda');
    // one of the two sessions has a null startTime → "1 of 2 sessions need a slot"
    const summary = await screen.findByTestId('needs-slot-summary');
    await waitFor(() => expect(summary).toHaveTextContent(/1 of 2/));
  });

  it('should_showNoSessionsYet_when_agendaWithZeroSessions', async () => {
    api.on('getEvent', () => anEventDetail([]));
    renderAt('&view=agenda');
    const summary = await screen.findByTestId('needs-slot-summary');
    // Zero sessions must NOT read as "all slotted" — it's "no sessions yet".
    await waitFor(() => expect(api.calls('getEvent')).toHaveLength(1));
    expect(summary).toHaveTextContent(/no sessions yet/i);
    expect(summary).not.toHaveTextContent(/all sessions slotted/i);
  });

  it('should_jumpToSlots_when_arrangeSlotsClicked', async () => {
    const user = userEvent.setup();
    renderAt('&view=agenda');
    const jump = await screen.findByTestId('arrange-slots-button');
    await user.click(jump);
    expect(
      await screen.findByTestId(`tray-session-${unslottedSession.sessionSlug}`)
    ).toBeInTheDocument();
    expect(screen.queryByTestId('needs-slot-summary')).not.toBeInTheDocument();
    expect(screen.getByTestId('slots-view-toggle')).toHaveAttribute('aria-pressed', 'true');
  });
});
