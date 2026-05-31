/**
 * E2E: Event Lifecycle workflow walk — slice 10 / event-workflow (plan §C, §C-bis OQ-3)
 * docs/plans/playwright-staging-hardening.md
 *
 * Rewritten 2026-05-31 to reality + the quality bar (testid-only locators, API/factory fixture
 * data, mandatory cleanup, no empty tests). Story 5.1a: Workflow State Machine.
 *
 * What this replaces (rewrite-to-reality):
 *   • The old spec was a 6-phase monolithic UI walk (Phase A create-via-UI + heatmap topic +
 *     speaker brainstorming; Phase B native-MOUSE drag-drop kanban CONTACTED→READY→ACCEPTED;
 *     Phase B.5 content submission; Phase C quality review; Phase D slot-assign + publish;
 *     Phase E archive-via-edit-modal). It:
 *       – created its event through the UI with a `Date.now()`-salted number and NEVER cleaned
 *         up the event (leaked one event + its tasks/speakers/sessions per run);
 *       – drove the kanban with raw `page.mouse` drag-drop (the exact flaky native-DnD the plan
 *         keeps out of any gate) and HARDCODED organizer/speaker display names
 *         ('Nissim Buchs'/'Daniel Kühni'/'N Nissim ELCA AI'), environment-specific + fragile;
 *       – depended on the speaker workflow + content flows that belong to slice 8 (speaker pool),
 *         and on automatic/cron transitions a UI walk can't drive deterministically.
 *
 * Reality (verified in EventWorkflowController / EventWorkflowStateMachine / EventOverviewTab):
 *   • The 8-state lifecycle is CREATED → TOPIC_SELECTION → SPEAKER_IDENTIFICATION →
 *     SLOT_ASSIGNMENT → AGENDA_PUBLISHED → EVENT_LIVE → EVENT_COMPLETED → ARCHIVED. Most
 *     transitions are AUTOMATIC (event listeners + cron at 00:01/23:59 Bern), so a pure-UI walk
 *     stalls on cron and adds flake.
 *   • `PUT /events/{code}/workflow/transition` with `overrideValidation:true` is the test-only
 *     override the organizer UI itself exposes (the "override workflow validation" checkbox).
 *     `transitionToState` honours it by SKIPPING ALL validation — so the gate can force-advance
 *     to ANY state, including the cron-only EVENT_LIVE/EVENT_COMPLETED, deterministically.
 *   • The event overview tab (default tab at `/organizer/events/:code`) renders a
 *     `workflow-status-badge` Chip whose visible label is TRANSLATED; slice 10 added a
 *     locale-independent `data-workflow-state` attribute carrying the raw state for assertions.
 *
 * Approach (OQ-3 resolved: hybrid — API force-advances, UI asserts each screen):
 *   The walk drives state via the override transition API and, after each step, reloads the
 *   overview and asserts the badge's `data-workflow-state` reflects the new state — plus an
 *   authoritative `GET /workflow/status` cross-check. No cron, no DnD, no content/speaker flows.
 *
 * Cleanup contract: ONE throwaway event per file (serial, shared), created via the API fixture
 * (captured `BATbern{N}`). afterAll force-advances it to ARCHIVED (events are only DELETE-able
 * once ARCHIVED — DELETE returns 409 otherwise) and then `cleanupByCode` deletes it; the
 * force-archive makes teardown robust even if the walk failed mid-sequence. The `BATPW-E2E`
 * title token is the orphan backstop. Never touches a real event.
 */

import { test, expect, type Page } from '@playwright/test';
import {
  readOrganizerToken,
  createRegistrationEvent,
  transitionWorkflow,
  getWorkflowState,
  WORKFLOW_FORWARD_STATES,
  type RegistrationEvent,
} from '../helpers/event-fixture';
import { cleanupByCode } from '../helpers/test-fixtures-cleanup';

test.describe('Event Lifecycle workflow walk (Story 5.1a)', { tag: '@gate' }, () => {
  // One throwaway event for the file (serial) — created once, torn down once. The read-only
  // @gate asserts the initial CREATED render first; the @smoke then force-advances the SAME
  // event through every forward state. Serial so the @smoke's mutation can't race the @gate.
  test.describe.configure({ mode: 'serial' });

  let token: string;
  let fixtureEvent: RegistrationEvent;

  test.beforeAll(async () => {
    token = readOrganizerToken();
    // A CREATED event with a captured BATbern{N} code — the walk's starting state.
    fixtureEvent = await createRegistrationEvent(token);
  });

  test.afterAll(async () => {
    if (fixtureEvent?.eventCode) {
      // Events are only deletable once ARCHIVED (DELETE → 409 otherwise). Force-archive first
      // (override, tolerate any error if the walk already archived it) so teardown is robust
      // regardless of where the walk stopped, then delete.
      await transitionWorkflow(token, fixtureEvent.eventCode, 'ARCHIVED').catch(() => {});
      await cleanupByCode(token, fixtureEvent.eventCode);
    }
  });

  /** Navigate to the event overview tab (default) and return the workflow badge locator. */
  async function openOverview(page: Page) {
    await page.goto(`/organizer/events/${fixtureEvent.eventCode}`);
    const badge = page.getByTestId('workflow-status-badge');
    await expect(badge).toBeVisible();
    return badge;
  }

  test('should_renderOverviewWithCreatedBadge_when_eventOpened', async ({ page }) => {
    // A freshly created event opens on the overview tab in CREATED — proves the event detail
    // screen renders and the badge reflects the persisted workflow state.
    const badge = await openOverview(page);
    await expect(badge).toHaveAttribute('data-workflow-state', 'CREATED');
    expect(await getWorkflowState(token, fixtureEvent.eventCode)).toBe('CREATED');
  });

  test(
    'should_advanceThroughEveryWorkflowState_when_forceTransitioned',
    { tag: ['@smoke', '@gate'] },
    async ({ page }) => {
      // Walk the full forward lifecycle: force-advance via the override API, then reload the
      // overview and assert the badge reflects the new state, with an authoritative status
      // cross-check. Each iteration is one mutating step of the one happy path for this entity.
      for (const state of WORKFLOW_FORWARD_STATES) {
        const confirmed = await transitionWorkflow(token, fixtureEvent.eventCode, state);
        expect(confirmed).toBe(state);

        // Authoritative server-side verification the transition persisted.
        expect(await getWorkflowState(token, fixtureEvent.eventCode)).toBe(state);

        // UI assertion: the overview badge reflects the new state (toHaveAttribute auto-retries
        // through the fresh-page refetch).
        const badge = await openOverview(page);
        await expect(badge).toHaveAttribute('data-workflow-state', state);
      }

      // Ended in ARCHIVED — the only DELETE-able state, so afterAll's cleanup will succeed (204).
      expect(await getWorkflowState(token, fixtureEvent.eventCode)).toBe('ARCHIVED');
    }
  );
});
