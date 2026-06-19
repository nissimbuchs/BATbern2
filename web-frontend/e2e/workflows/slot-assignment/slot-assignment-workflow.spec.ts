/**
 * E2E: Slot Assignment — slice 6 / sessions+slot-assignment (plan §C)
 * docs/plans/playwright-staging-hardening.md
 *
 * Rewritten 2026-05-30 to reality + the quality bar (testid-only locators, API/factory fixture
 * data, mandatory cleanup, no empty tests). Story 5.7 (BAT-11): Slot Assignment & Progressive
 * Publishing.
 *
 * What this replaces (rewrite-to-reality):
 *   • The old spec was a 5-test `test.describe.skip(...)` RED-PHASE TDD block asserting an
 *     IDEALIZED DOM that was never built: `assignment-progress` ("0 of 3 assigned"),
 *     `speaker-card`, `slot-dropzone[data-time]`, `assignment-success-toast`,
 *     `conflict-detection-modal` + room-change resolution, `speaker-preference-panel`,
 *     `auto-assign-all-button` + a 3-step `bulk-auto-assignment-modal` wizard with
 *     `algorithm-balanced`/`assignment-preview-list`/`match-score`, an
 *     `assignment-complete-banner` and a `/publishing` tab walk. NONE of those testids exist.
 *   • Its helpers POSTed placeholder sessions to a hardcoded `BATbern997` and created events
 *     through the UI with hardcoded speakers ('john.doe'/'jane.smith') — no cleanup, fictional flow.
 *
 * Reality (verified in SlotAssignmentPage / DragDropSlotAssignment / SlotAssignmentController):
 *   • The page is routed in production at `/organizer/events/:eventCode/slot-assignment` and is
 *     a three-column layout: `speaker-pool-sidebar` (unassigned sessions), `session-timeline-grid`
 *     + `timeline-grid` (drop cells `slot-<HH:MM>-Main-Hall`, driven by the BACKEND timetable),
 *     `quick-actions-panel` (`generate-structural-button`, `auto-assign-button`, clear-all).
 *   • The slot GRID is computed from the event-type config (EVENING is seeded in Flyway V10:
 *     4 speaker slots + a break), so it renders rows for ANY EVENING event — no sessions needed.
 *   • Assignment uses native HTML5 drag-and-drop, which is too flaky to gate. The deterministic
 *     mutating path is the **auto-assign** button → modal → confirm (`POST /sessions/auto-assign`),
 *     which assigns every unassigned session into a free slot. That is this slice's `@smoke`.
 *   • "Unassigned" = a non-structural session with `startTime IS NULL`. The REST create endpoint
 *     requires timing (`CreateSessionRequest @NotNull`), so the fixture creates timed sessions then
 *     clears all timings to reach the unassigned state (`addUnassignedSessions`, event-fixture.ts).
 *
 * Cleanup contract: each run creates ONE throwaway EVENING event via the API fixture (captured
 * `BATbern{N}` code) and deletes it in afterAll (`cleanupByCode`) — `sessions.event_id` (V2) and
 * `session_timing_history.session_id` (V28) are `ON DELETE CASCADE`, so the event delete removes
 * every session + timing row. Server-generated `sessionSlug`s aren't prefix-sweepable, so the
 * parent-event delete is the only teardown (plan §A5 server-generated-code caveat). Never touches
 * a real event.
 *
 * Prod-safety (plan risk #1 + #2): the `@smoke` mutates ONLY its own throwaway event; the
 * server-generated code is captured and explicit-deleted. No drag-drop, no cron/workflow walk.
 */

import { test, expect, type Page } from '@playwright/test';
import {
  readOrganizerToken,
  createRegistrationEvent,
  addUnassignedSessions,
  getUnassignedSessionCount,
  type RegistrationEvent,
} from '../../helpers/event-fixture';
import { cleanupByCode } from '../../helpers/test-fixtures-cleanup';

// EVENING-event slot cells are `slot-<HH:MM>-Main-Hall`. The <HH:MM> is rendered in the
// BROWSER's timezone (toTimeStr on the backend-computed slot start), so it varies by env —
// match the testid by shape, never a hardcoded time.
const SLOT_CELL = /^slot-\d{1,2}:\d{2}-Main-Hall$/;

test.describe('Slot Assignment (Story 5.7)', { tag: '@gate' }, () => {
  // One throwaway EVENING event for the file (serial) — created once, torn down once. The
  // read-only @gate runs first on the bare event; the @smoke then adds + auto-assigns its own
  // sessions. Serial so the @smoke's mutation can't race the @gate's render.
  test.describe.configure({ mode: 'serial' });

  let token: string;
  let fixtureEvent: RegistrationEvent;

  test.beforeAll(async () => {
    token = readOrganizerToken();
    // createRegistrationEvent makes a CREATED, EVENING-type event with a captured BATbern{N} —
    // EVENING's seeded slot template is what makes the timetable/auto-assign deterministic.
    fixtureEvent = await createRegistrationEvent(token);
  });

  test.afterAll(async () => {
    if (fixtureEvent?.eventCode) {
      await cleanupByCode(token, fixtureEvent.eventCode); // cascade removes sessions + timing
    }
  });

  /**
   * Open the in-tab Slots sub-view and wait for its layout to render.
   * Epic 14 (14.C.5): the separate `/slot-assignment` route was retired in favour of the
   * Speakers & Agenda ▸ Slots sub-view (2-column tray + timeline with a top action bar);
   * the old 3-column `quick-actions-panel` is gone — bulk actions live in `slot-action-bar`.
   */
  async function openSlotAssignment(page: Page) {
    await page.goto(`/organizer/events/${fixtureEvent.eventCode}?tab=speakers&view=slots`);
    // The in-tab Slots sub-view loads event + sessions + timetable before the action bar shows
    // (`slot-action-bar` renders only when !isLoading) — allow for a cold local backend.
    await expect(page.getByTestId('slot-action-bar')).toBeVisible({ timeout: 15_000 });
  }

  test('should_renderSlotAssignmentLayout_when_pageOpened', async ({ page }) => {
    await openSlotAssignment(page);

    // 2-column in-tab layout (tray + timeline) under the top action bar.
    await expect(page.getByTestId('speaker-pool-sidebar')).toBeVisible();
    await expect(page.getByTestId('session-timeline-grid')).toBeVisible();
    await expect(page.getByTestId('timeline-grid')).toBeVisible();
    await expect(page.getByTestId('slot-action-bar')).toBeVisible();

    // Bulk actions present (moved from the retired quick-actions column into the top bar).
    await expect(page.getByTestId('generate-structural-button')).toBeVisible();
    await expect(page.getByTestId('auto-assign-button')).toBeVisible();

    // The backend timetable produced at least one droppable speaker slot (EVENING config) —
    // proves the grid is wired to the event-type slot template, not an empty shell.
    await expect(page.getByTestId(SLOT_CELL).first()).toBeVisible();
  });

  test(
    'should_assignAllSessions_when_autoAssignConfirmed',
    { tag: ['@smoke', '@gate'] },
    async ({ page }) => {
      // Seed two unassigned (placeholder) sessions on the fixture event via the API.
      await addUnassignedSessions(token, fixtureEvent.eventCode, 2);
      expect(await getUnassignedSessionCount(token, fixtureEvent.eventCode)).toBe(2);

      await openSlotAssignment(page);

      // The sidebar shows the two unassigned session cards (default filter = unassigned).
      await expect(page.getByTestId('drag-handle')).toHaveCount(2);

      // Auto-assign: button → modal → confirm. Deterministic (no native drag-drop). The
      // component awaits the POST + refetch before closing the modal, so modal-hidden is the
      // completion signal.
      await page.getByTestId('auto-assign-button').click();
      await expect(page.getByTestId('auto-assign-modal')).toBeVisible();
      await page.getByTestId('auto-assign-confirm').click();
      await expect(page.getByTestId('auto-assign-modal')).toBeHidden({ timeout: 15_000 });

      // UI confirmation: the unassigned list is now empty (all sessions got a slot).
      await expect(page.getByTestId('empty-state')).toBeVisible();

      // Authoritative verification: the server has zero unassigned sessions left — the
      // assignment actually persisted (stronger than the UI signal alone).
      expect(await getUnassignedSessionCount(token, fixtureEvent.eventCode)).toBe(0);
    }
  );
});
