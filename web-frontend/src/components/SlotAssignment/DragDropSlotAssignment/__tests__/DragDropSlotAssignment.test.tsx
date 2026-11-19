/**
 * DragDropSlotAssignment Component Tests
 *
 * Story 5.7 — original drag-and-drop slot assignment behaviour.
 * Epic 14 Phase C (14.C.5) — reworked to a 2-column in-tab layout with a top
 * action bar (no viewport lock) plus an optional `focusSpeakerId` highlight.
 * #1223 — the counters count every speaker session of the event (they read 0 when all were
 * slotted), and the pool filter labels fit a phone.
 *
 * Runs the real hooks (`useSlotAssignment`, `useEvent`, `useTimetable`, `useBreakpoints`) and
 * services against the MSW harness; only the viewport (`window.matchMedia`) is set per test.
 *
 * Coverage:
 * - AC5: Drag-and-drop UI to drag speaker cards to time slots
 * - AC6: Visual timeline showing all slots and assignments
 * - AC11: Highlight when slot matches speaker preference
 * - AC12: Show unassigned speakers list with real-time updates
 * - 14.C.5: 2-column layout, top action bar, no 100vh lock, focusSpeakerId
 */

import { screen, fireEvent, waitFor, within } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { HttpResponse } from 'msw';
import { DragDropSlotAssignment } from '../DragDropSlotAssignment';
import { aSessionResponse, aSessionSpeaker, anEvent, daysFromNow } from '@/test/fixtures';
import { api } from '@/test/msw/api';
import type { ResponseBody } from '@/test/msw/api';
import { renderWithAuth } from '@/test/auth';

type EventDetail = ResponseBody<'getEvent', 200>;
type SessionResponse = NonNullable<EventDetail['sessions']>[number];
type Timetable = ResponseBody<'getEventTimetable', 200>;

const EVENT_CODE = 'BATbern142';
/** The event day as YYYY-MM-DD, relative to the clock (no fixed calendar date). */
const DAY = daysFromNow(30).slice(0, 10);

/** Local wall-clock instant on the event day (no 'Z': the grid labels are local HH:MM). */
const at = (hour: number) => `${DAY}T${String(hour).padStart(2, '0')}:00:00`;

/**
 * A timetable with hourly SPEAKER_SLOTs from 09:00 to 19:00 on the event day. `occupants` maps a
 * slot index (0-based) to the slug of the session that holds it.
 */
const aTimetable = (occupants: Record<number, string> = {}): Timetable => ({
  slots: Array.from({ length: 11 }, (_, i) => ({
    type: 'SPEAKER_SLOT' as const,
    slotKey: `SPEAKER_SLOT-${i + 1}`,
    startTime: at(9 + i),
    endTime: at(10 + i),
    title: null,
    slotIndex: i + 1,
    sessionSlug: null,
    assignedSessionSlug: occupants[i] ?? null,
  })),
  unassignedSessions: [],
});

const speakerOf = (username: string, firstName: string, lastName: string, company: string) => ({
  ...aSessionSpeaker(),
  username,
  firstName,
  lastName,
  company,
  speakerRole: 'PRIMARY_SPEAKER' as const,
  isConfirmed: true,
});

const unslotted = (slug: string, title: string, speaker = speakerOf('x', 'X', 'Y', 'Z')) =>
  aSessionResponse({
    sessionSlug: slug,
    eventCode: EVENT_CODE,
    language: 'de',
    title,
    startTime: null,
    endTime: null,
    room: null,
    speakers: [speaker],
  });

const slotted = (
  slug: string,
  title: string,
  hour: number,
  speakers: SessionResponse['speakers'] = []
) =>
  aSessionResponse({
    sessionSlug: slug,
    eventCode: EVENT_CODE,
    language: 'de',
    title,
    startTime: at(hour),
    endTime: at(hour + 1),
    room: 'Main Hall',
    speakers,
  });

const john = unslotted(
  'session-1',
  'John Doe - Acme Corp',
  speakerOf('john.doe', 'John', 'Doe', 'Acme Corp')
);
const jane = unslotted(
  'session-2',
  'Jane Smith - Tech Inc',
  speakerOf('jane.smith', 'Jane', 'Smith', 'Tech Inc')
);

/** A structural session: on the agenda, but no speaker slot to fill. */
const moderation = aSessionResponse({
  sessionSlug: 'moderation-start',
  eventCode: EVENT_CODE,
  title: 'Moderation',
  sessionType: 'moderation',
  startTime: at(8),
  endTime: at(9),
  room: 'Main Hall',
});

const anEventDetail = (sessions: SessionResponse[]): EventDetail => ({
  ...anEvent({ eventCode: EVENT_CODE, eventNumber: 142, date: `${DAY}T16:00:00.000Z` }),
  eventType: 'FULL_DAY',
  sessions,
});

/** Answers the three reads the view makes: the event with its sessions, the grid, the tray. */
const serve = ({
  sessions = [john, jane] as SessionResponse[],
  timetable = aTimetable(),
}: { sessions?: SessionResponse[]; timetable?: Timetable } = {}) => {
  api.on('getEvent', () => anEventDetail(sessions));
  api.on('getEventTimetable', () => timetable);
  api.on('getUnassignedSessions', () =>
    sessions.filter((s) => !s.startTime && s.sessionType !== 'moderation')
  );
};

const problem = (status: number, message: string) =>
  HttpResponse.json({ timestamp: daysFromNow(0), status, error: 'Error', message }, { status });

const renderView = (focusSpeakerId?: string) =>
  renderWithAuth(<DragDropSlotAssignment eventCode={EVENT_CODE} focusSpeakerId={focusSpeakerId} />);

/** Renders and waits until the reads have landed (the action bar replaces the skeleton). */
const renderLoaded = async (focusSpeakerId?: string) => {
  const result = renderView(focusSpeakerId);
  await screen.findByTestId('slot-action-bar');
  await screen.findByTestId('timeline-grid');
  return result;
};

/** Viewport: `width` px wide. MUI's `useMediaQuery` reads `window.matchMedia`. */
const setViewportWidth = (width: number) => {
  window.matchMedia = vi.fn((query: string) => {
    const max = /max-width:\s*([\d.]+)px/.exec(query);
    const min = /min-width:\s*([\d.]+)px/.exec(query);
    const matches = (!max || width <= Number(max[1])) && (!min || width >= Number(min[1]));
    return {
      matches,
      media: query,
      onchange: null,
      addListener: vi.fn(),
      removeListener: vi.fn(),
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
      dispatchEvent: vi.fn(),
    };
  }) as unknown as typeof window.matchMedia;
};

/** The injected emotion CSS for an element's css-* class (jsdom evaluates no media query). */
const cssOf = (el: HTMLElement): string => {
  const cls = Array.from(el.classList).find((c) => c.startsWith('css-'));
  if (!cls) return '';
  let combined = '';
  document.querySelectorAll('style').forEach((styleEl) => {
    const css = styleEl.textContent ?? '';
    if (css.includes(`.${cls}`)) combined += css + '\n';
  });
  return combined;
};

const originalMatchMedia = window.matchMedia;

describe('DragDropSlotAssignment Component', () => {
  beforeEach(() => {
    setViewportWidth(1280);
    serve();
  });

  afterEach(() => {
    window.matchMedia = originalMatchMedia;
  });

  describe('Component Rendering (14.C.5 two-column in-tab layout)', () => {
    it('should_renderTwoColumnLayoutWithTopActionBar_when_initialized', async () => {
      await renderLoaded();

      expect(screen.getByTestId('speaker-pool-sidebar')).toBeInTheDocument();
      expect(screen.getByTestId('session-timeline-grid')).toBeInTheDocument();
      expect(screen.getByTestId('slot-action-bar')).toBeInTheDocument();
      expect(api.calls('getUnassignedSessions')[0].params.eventCode).toBe(EVENT_CODE);
      expect(api.calls('getEventTimetable')[0].params.eventCode).toBe(EVENT_CODE);
    });

    it('should_notRenderSeparateQuickActionsPanel_when_initialized', async () => {
      await renderLoaded();

      expect(screen.queryByTestId('quick-actions-panel')).not.toBeInTheDocument();
    });

    it('should_notLockToViewportHeight_when_renderedInTab', async () => {
      const { container } = await renderLoaded();

      const css = Array.from(container.ownerDocument.querySelectorAll('style'))
        .map((s) => s.textContent ?? '')
        .join('\n');
      expect(/height:\s*100vh/.test(css)).toBe(false);
    });

    it('should_showLoadingState_when_fetchingData', () => {
      renderView();

      // Before the reads answer, the skeleton stands in for the view.
      expect(screen.getByTestId('loading-skeleton')).toBeInTheDocument();
    });
  });

  describe('Session counters (#1223)', () => {
    it('should_countEverySpeakerSession_when_allAreSlotted', async () => {
      // BATbern60's case: every session has a slot, the tray is empty.
      const a = slotted('sess-a', 'Talk A', 9);
      const b = slotted('sess-b', 'Talk B', 10);
      const c = slotted('sess-c', 'Talk C', 11);
      serve({
        sessions: [a, b, c, moderation],
        timetable: aTimetable({ 0: 'sess-a', 1: 'sess-b', 2: 'sess-c' }),
      });

      await renderLoaded();

      expect(screen.getByText('3 Total Sessions')).toBeInTheDocument();
      expect(screen.getByText('3 Assigned')).toBeInTheDocument();
      expect(screen.getByText('0 Pending')).toBeInTheDocument();
      expect(screen.getByText('3 of 3 assigned (100%)')).toBeInTheDocument();
      expect(screen.getByText('All timings assigned!')).toBeInTheDocument();
    });

    it('should_countSlottedAndPending_when_someAreSlotted', async () => {
      const a = slotted('sess-a', 'Talk A', 9);
      serve({ sessions: [a, john, jane, moderation], timetable: aTimetable({ 0: 'sess-a' }) });

      await renderLoaded();

      expect(screen.getByText('3 Total Sessions')).toBeInTheDocument();
      expect(screen.getByText('1 Assigned')).toBeInTheDocument();
      expect(screen.getByText('2 Pending')).toBeInTheDocument();
      expect(screen.getByText('1 of 3 assigned (33%)')).toBeInTheDocument();
      expect(screen.queryByText('All timings assigned!')).not.toBeInTheDocument();
    });

    it('should_countOnlySpeakerSessions_when_anUntimedBreakIsUnassigned', async () => {
      // A structural session without a time has no speaker slot: it is neither pending nor in
      // the tray, so Total = Assigned + Pending holds.
      const a = slotted('sess-a', 'Talk A', 9);
      const untimedBreak = aSessionResponse({
        sessionSlug: 'coffee-break',
        eventCode: EVENT_CODE,
        title: 'Coffee break',
        sessionType: 'break',
        startTime: null,
        endTime: null,
      });
      serve({ sessions: [a, john, untimedBreak], timetable: aTimetable({ 0: 'sess-a' }) });
      api.on('getUnassignedSessions', () => [john, untimedBreak]);

      await renderLoaded();

      expect(screen.getByText('2 Total Sessions')).toBeInTheDocument();
      expect(screen.getByText('1 Assigned')).toBeInTheDocument();
      expect(screen.getByText('1 Pending')).toBeInTheDocument();
      expect(screen.getByText('1 of 2 assigned (50%)')).toBeInTheDocument();
      const pool = screen.getByTestId('speaker-pool-sidebar');
      expect(within(pool).queryByText('Coffee break')).not.toBeInTheDocument();
    });

    it('should_sayOneSession_when_thereIsOne', async () => {
      const a = slotted('sess-a', 'Talk A', 9);
      serve({ sessions: [a], timetable: aTimetable({ 0: 'sess-a' }) });

      await renderLoaded();

      expect(screen.getByText('1 Total Session')).toBeInTheDocument();
      expect(screen.getByRole('status', { hidden: true })).toHaveTextContent(
        '1 session assigned successfully'
      );
    });

    it('should_keepTheProgress_when_poolFilterChanges', async () => {
      const a = slotted('sess-a', 'Talk A', 9);
      serve({ sessions: [a, john, jane], timetable: aTimetable({ 0: 'sess-a' }) });
      await renderLoaded();

      fireEvent.click(screen.getByTestId('filter-all'));

      // The filter changes which cards show, not how many sessions are assigned.
      expect(screen.getByText('1 of 3 assigned (33%)')).toBeInTheDocument();
      expect(screen.getByTestId('unassigned-badge')).toHaveTextContent('2 Remaining');
    });
  });

  describe('Unassigned Speakers List', () => {
    it('should_displayUnassignedSessions_when_loaded', async () => {
      await renderLoaded();

      expect(screen.getByText('John Doe - Acme Corp')).toBeInTheDocument();
      expect(screen.getByText('Jane Smith - Tech Inc')).toBeInTheDocument();
    });

    it('should_showDraggableIndicator_when_hoveringOverSpeakerCard', async () => {
      await renderLoaded();

      const speakerCard = screen.getByText('John Doe - Acme Corp').closest('[draggable]');
      fireEvent.mouseEnter(speakerCard!);

      await waitFor(() => {
        expect(speakerCard).toHaveStyle({ cursor: 'grab' });
      });
    });

    it('should_filterUnassignedSessions_when_filterApplied', async () => {
      const a = slotted('sess-a', 'Talk A', 9);
      serve({ sessions: [a, john, jane], timetable: aTimetable({ 0: 'sess-a' }) });
      await renderLoaded();

      fireEvent.click(screen.getByTestId('filter-unassigned'));

      const pool = screen.getByTestId('speaker-pool-sidebar');
      expect(within(pool).getByText('John Doe - Acme Corp')).toBeInTheDocument();
      expect(within(pool).getByText('Jane Smith - Tech Inc')).toBeInTheDocument();
      expect(within(pool).queryByText('Talk A')).not.toBeInTheDocument();
    });

    it('should_keepFilterLabelsVisible_when_phoneWidth', async () => {
      // #1223 item 9: at 375px the three equal-width filter buttons are ~90px wide, and the
      // upper-case "UNASSIGNED" (longer in de/fi) did not fit. jsdom measures nothing, so the
      // test asserts the styles that let a label fit: no upper-casing, no min width, wrapping.
      setViewportWidth(375);
      await renderLoaded();

      for (const id of ['filter-all', 'filter-assigned', 'filter-unassigned']) {
        const css = cssOf(screen.getByTestId(id));
        expect(css).toMatch(/text-transform:\s*none/);
        expect(css).toMatch(/white-space:\s*normal/);
        expect(css).toMatch(/overflow-wrap:\s*anywhere/);
        expect(css).toMatch(/min-width:\s*0/);
      }
    });
  });

  describe('Focus speaker (14.C.5 carry speaker context)', () => {
    it('should_highlightMatchingUnassignedSession_when_focusSpeakerIdMatches', async () => {
      await renderLoaded('jane.smith');

      await waitFor(() => {
        expect(screen.getByTestId('focused-session-session-2')).toBeInTheDocument();
      });
      expect(screen.queryByTestId('focused-session-session-1')).not.toBeInTheDocument();
    });

    it('should_renderNormally_when_focusSpeakerIdHasNoMatch', async () => {
      await renderLoaded('nobody.here');

      expect(screen.queryByTestId('focused-session-session-1')).not.toBeInTheDocument();
      expect(screen.queryByTestId('focused-session-session-2')).not.toBeInTheDocument();
      expect(screen.getByText('John Doe - Acme Corp')).toBeInTheDocument();
      expect(screen.getByText('Jane Smith - Tech Inc')).toBeInTheDocument();
    });
  });

  describe('Session Timeline Grid', () => {
    it('should_displayTimelineGrid_when_initialized', async () => {
      await renderLoaded();

      expect(screen.getByTestId('timeline-grid')).toBeInTheDocument();
      expect(screen.getByText(/09:00/)).toBeInTheDocument();
      expect(screen.getByText(/14:00/)).toBeInTheDocument();
    });

    it('should_showDropZones_when_draggingSession', async () => {
      const { container } = await renderLoaded();

      const speakerCard = screen.getByText('John Doe - Acme Corp').closest('[draggable]');
      fireEvent.dragStart(speakerCard!);
      fireEvent.dragOver(screen.getByTestId('slot-09:00-Main-Hall'));

      await waitFor(() => {
        expect(container.querySelectorAll('.drop-zone-active').length).toBeGreaterThan(0);
      });
    });

    it.each([
      ['09:00', 'preference-match-high', /90% match/],
      ['13:00', 'preference-match-medium', /65% match/],
      ['19:00', 'preference-match-low', /20% match/],
    ])('should_highlightPreference_when_draggingOver %s', async (time, cls, label) => {
      // AC11: green 80-100%, yellow 50-79%, red below 50%
      await renderLoaded();

      const speakerCard = screen.getByText('John Doe - Acme Corp').closest('[draggable]');
      fireEvent.dragStart(speakerCard!);
      const slot = screen.getByTestId(`slot-${time}-Main-Hall`);
      fireEvent.dragOver(slot);

      await waitFor(() => {
        expect(slot).toHaveClass(cls);
        expect(screen.getByText(label)).toBeInTheDocument();
      });
    });
  });

  describe('Drag and Drop Interaction', () => {
    it('should_assignToSlotByKey_when_sessionDroppedOnEmptySlot', async () => {
      api.on('assignSessionToSlot', () => aTimetable({ 0: 'session-1' }));
      await renderLoaded();

      const speakerCard = screen.getByText('John Doe - Acme Corp').closest('[draggable]');
      const targetSlot = screen.getByTestId('slot-09:00-Main-Hall');
      fireEvent.dragStart(speakerCard!);
      fireEvent.dragOver(targetSlot);
      fireEvent.drop(targetSlot);

      await waitFor(() => expect(api.calls('assignSessionToSlot')).toHaveLength(1));
      const [call] = api.calls('assignSessionToSlot');
      expect(call.params).toEqual({ eventCode: EVENT_CODE, sessionSlug: 'session-1' });
      expect(call.body).toEqual({ targetSlotKey: 'SPEAKER_SLOT-1', mode: 'ASSIGN' });
    });

    it('should_updatePendingCount_when_sessionAssigned', async () => {
      api.on('assignSessionToSlot', () => aTimetable({ 0: 'session-1' }));
      await renderLoaded();
      expect(screen.getByText('2 Pending')).toBeInTheDocument();

      const speakerCard = screen.getByText('John Doe - Acme Corp').closest('[draggable]');
      const targetSlot = screen.getByTestId('slot-09:00-Main-Hall');
      fireEvent.dragStart(speakerCard!);
      fireEvent.dragOver(targetSlot);
      fireEvent.drop(targetSlot);

      expect(await screen.findByText('1 Pending')).toBeInTheDocument();
      expect(screen.getByText('1 Assigned')).toBeInTheDocument();
      expect(screen.getByText('2 Total Sessions')).toBeInTheDocument();
    });

    it('should_rollbackOnError_when_assignmentFails', async () => {
      api.on('assignSessionToSlot', () => problem(500, 'Database unavailable'));
      await renderLoaded();

      const speakerCard = screen.getByText('John Doe - Acme Corp').closest('[draggable]');
      const targetSlot = screen.getByTestId('slot-09:00-Main-Hall');
      fireEvent.dragStart(speakerCard!);
      fireEvent.drop(targetSlot);

      await waitFor(() => expect(api.calls('assignSessionToSlot')).toHaveLength(1));
      // The optimistic removal is undone: the session is back in the tray.
      await waitFor(() => {
        expect(screen.getByTestId('speaker-pool-sidebar')).toContainElement(
          screen.getByText('John Doe - Acme Corp')
        );
      });
      expect(screen.getByText('2 Pending')).toBeInTheDocument();
    });

    it('should_insert_when_poolSpeakerDroppedOnOccupiedSlot', async () => {
      const occupant = slotted('occupant-session', 'Occupant Talk', 9);
      serve({
        sessions: [john, jane, occupant],
        timetable: aTimetable({ 0: 'occupant-session' }),
      });
      api.on('assignSessionToSlot', () => aTimetable({ 0: 'session-1' }));
      await renderLoaded();

      const speakerCard = screen.getByText('John Doe - Acme Corp').closest('[draggable]');
      fireEvent.dragStart(speakerCard!);
      fireEvent.drop(screen.getByTestId('slot-09:00-Main-Hall'));

      await waitFor(() => expect(api.calls('assignSessionToSlot')).toHaveLength(1));
      expect(api.calls('assignSessionToSlot')[0].body).toEqual({
        targetSlotKey: 'SPEAKER_SLOT-1',
        mode: 'INSERT',
      });
    });

    it('should_previewReflow_when_draggingPoolSpeakerOverOccupiedSlot', async () => {
      // 15.3: dragging over an occupied slot previews the insert before committing.
      const occupant = slotted('occupant-session', 'Occupant Talk', 9);
      serve({
        sessions: [john, jane, occupant],
        timetable: aTimetable({ 0: 'occupant-session' }),
      });
      await renderLoaded();

      const speakerCard = screen.getByText('John Doe - Acme Corp').closest('[draggable]');
      const targetSlot = screen.getByTestId('slot-09:00-Main-Hall');
      fireEvent.dragStart(speakerCard!);
      fireEvent.dragOver(targetSlot);

      await waitFor(() => {
        expect(screen.getByTestId('drop-hint')).toBeInTheDocument();
        expect(targetSlot).toHaveTextContent('John Doe - Acme Corp');
        expect(document.querySelector('[data-preview-moved="true"]')).toBeTruthy();
      });
    });

    it('should_unassign_when_removeButtonClickedOnAssignedSlot', async () => {
      const occupant = slotted('occupant-session', 'Occupant Talk', 9);
      serve({
        sessions: [john, jane, occupant],
        timetable: aTimetable({ 0: 'occupant-session' }),
      });
      api.on('unassignTiming', () => ({ ...occupant, startTime: null, endTime: null }));
      await renderLoaded();

      fireEvent.click(screen.getByTestId('remove-slot-occupant-session'));

      await waitFor(() => expect(api.calls('unassignTiming')).toHaveLength(1));
      expect(api.calls('unassignTiming')[0].params).toEqual({
        eventCode: EVENT_CODE,
        sessionSlug: 'occupant-session',
      });
    });

    it('should_swap_when_assignedSpeakerDroppedOnOccupiedSlot', async () => {
      const a = slotted('sess-a', 'A', 9);
      const b = slotted('sess-b', 'B', 10);
      serve({ sessions: [a, b], timetable: aTimetable({ 0: 'sess-a', 1: 'sess-b' }) });
      api.on('assignSessionToSlot', () => aTimetable({ 0: 'sess-b', 1: 'sess-a' }));
      await renderLoaded();

      // Drag the assigned 'A' cell (09:00) onto the occupied 10:00 slot → SWAP
      fireEvent.dragStart(screen.getByTestId('slot-09:00-Main-Hall'));
      fireEvent.drop(screen.getByTestId('slot-10:00-Main-Hall'));

      await waitFor(() => expect(api.calls('assignSessionToSlot')).toHaveLength(1));
      const [call] = api.calls('assignSessionToSlot');
      expect(call.params.sessionSlug).toBe('sess-a');
      expect(call.body).toEqual({ targetSlotKey: 'SPEAKER_SLOT-2', mode: 'SWAP' });
    });
  });

  describe('Top Action Bar (14.C.5)', () => {
    it('should_renderAllThreeActionButtons_when_rendered', async () => {
      await renderLoaded();

      expect(screen.getByTestId('generate-structural-button')).toBeInTheDocument();
      expect(screen.getByTestId('auto-assign-button')).toBeInTheDocument();
      expect(screen.getByRole('button', { name: /clear all/i })).toBeInTheDocument();
    });

    it('should_openAutoAssignModal_when_autoAssignClicked', async () => {
      // AC13: Bulk auto-assignment feature
      await renderLoaded();

      fireEvent.click(screen.getByRole('button', { name: /auto.*assign/i }));

      expect(screen.getByRole('dialog')).toBeInTheDocument();
    });

    it('should_clearAllAssignments_when_clearButtonClicked', async () => {
      await renderLoaded();

      fireEvent.click(screen.getByRole('button', { name: /clear all/i }));

      expect(screen.getByRole('dialog')).toBeInTheDocument();
      expect(screen.getByText(/are you sure/i)).toBeInTheDocument();
    });

    it('should_offerOverwrite_when_structuralSessionsAlreadyExist', async () => {
      // The service's 409 reaches the component as the AxiosError, which switches the dialog to
      // the overwrite question.
      api.on('generateStructuralSessions', () =>
        HttpResponse.json(
          { status: 409, error: 'Conflict', message: 'Structural sessions already exist' },
          { status: 409 }
        )
      );
      await renderLoaded();

      fireEvent.click(screen.getByTestId('generate-structural-button'));
      fireEvent.click(screen.getByTestId('generate-structural-confirm'));

      expect(
        await screen.findByText(/Moderation, break and lunch blocks already exist/)
      ).toBeInTheDocument();
      expect(screen.getByTestId('generate-structural-overwrite-confirm')).toBeInTheDocument();
      expect(api.calls('generateStructuralSessions')[0].query.get('overwrite')).toBe('false');
    });

    it('should_showServerMessage_when_structuralGenerationFails', async () => {
      api.on('generateStructuralSessions', () =>
        HttpResponse.json(
          { status: 500, error: 'Internal Server Error', message: 'Event type not configured' },
          { status: 500 }
        )
      );
      await renderLoaded();

      fireEvent.click(screen.getByTestId('generate-structural-button'));
      fireEvent.click(screen.getByTestId('generate-structural-confirm'));

      expect(await screen.findByText('Event type not configured')).toBeInTheDocument();
      expect(screen.queryByTestId('generate-structural-overwrite-confirm')).not.toBeInTheDocument();
    });

    it('should_labelActionBarForScreenReaders_when_rendered', async () => {
      // NFR7: the top action bar is a labelled toolbar region.
      await renderLoaded();

      expect(screen.getByTestId('slot-action-bar')).toHaveAttribute('aria-label');
    });
  });

  describe('Responsive timeline (organizer mobile)', () => {
    it('should_notSetFixedMinWidthOnTimelineGrid_when_rendered', async () => {
      await renderLoaded();

      // The inner grid wrapper must no longer carry any fixed min-width rule.
      const inner = screen.getByTestId('timeline-grid').firstElementChild as HTMLElement;
      expect(inner).toBeTruthy();
      const css = cssOf(inner);
      expect(/min-width:\s*560px/.test(css)).toBe(false);
      expect(/min-width:\s*800px/.test(css)).toBe(false);
    });

    it('should_wrapSessionTitle_whiteSpaceNormal_atXs', async () => {
      const long = slotted(
        'assigned-session',
        'A Very Long Session Title That Must Wrap On Phones',
        9,
        [speakerOf('john.doe', 'John', 'Doe', 'Acme Corp')]
      );
      serve({
        sessions: [john, jane, long],
        timetable: aTimetable({ 0: 'assigned-session' }),
      });
      await renderLoaded();

      const titleEl = screen.getByText('A Very Long Session Title That Must Wrap On Phones');
      // xs base (min-width:0px) -> white-space:normal (wraps on phones)
      expect(
        /@media\s*\(min-width:\s*0px\)\s*\{[^}]*white-space:\s*normal[^}]*\}/.test(cssOf(titleEl))
      ).toBe(true);
    });
  });

  describe('Accessibility', () => {
    it('should_supportKeyboardNavigation_when_tabPressed', async () => {
      await renderLoaded();

      const speakerCard = screen
        .getByText('John Doe - Acme Corp')
        .closest<HTMLElement>('[role="article"]');
      speakerCard?.focus();

      expect(speakerCard).toHaveAttribute('aria-label');
      expect(speakerCard).toHaveAttribute('tabindex', '0');
    });

    it('should_announceAssignment_when_screenReaderEnabled', async () => {
      const a = slotted('sess-a', 'Talk A', 9);
      const b = slotted('sess-b', 'Talk B', 10);
      serve({ sessions: [a, b, john], timetable: aTimetable({ 0: 'sess-a', 1: 'sess-b' }) });
      await renderLoaded();

      // ARIA live region announces the current assignment count
      expect(screen.getByRole('status', { hidden: true })).toHaveTextContent(
        '2 sessions assigned successfully'
      );
    });
  });

  describe('Mobile tap-to-assign (14.G.3)', () => {
    it('arms empty slots after a tray session is tapped, then assigns on slot tap', async () => {
      setViewportWidth(375);
      api.on('assignSessionToSlot', () => aTimetable({ 0: 'session-1' }));
      await renderLoaded();

      expect(document.querySelector('[data-armed="true"]')).toBeNull();

      fireEvent.click(screen.getByTestId('tray-session-session-1'));
      await waitFor(() => {
        expect(document.querySelector('[data-armed="true"]')).toBeTruthy();
      });

      fireEvent.click(document.querySelector('[data-armed="true"]') as HTMLElement);
      await waitFor(() => expect(api.calls('assignSessionToSlot')).toHaveLength(1));
      expect(api.calls('assignSessionToSlot')[0].params.sessionSlug).toBe('session-1');
      expect(api.calls('assignSessionToSlot')[0].body).toMatchObject({ mode: 'ASSIGN' });
    });

    it('does not arm slots on desktop (drag-drop unchanged)', async () => {
      await renderLoaded();

      fireEvent.click(screen.getByTestId('tray-session-session-1'));

      expect(document.querySelector('[data-armed="true"]')).toBeNull();
    });
  });
});
