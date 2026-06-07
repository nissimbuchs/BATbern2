/**
 * E2E — Speaker Kanban column triage + time-in-state colour coding (Story 11.D.3)
 * slice 8 / speaker pool (plan §C). docs/plans/playwright-staging-hardening.md
 *
 * Hardened 2026-05-31 to the gate quality bar: API seeding now uses an explicit organizer
 * token (`readOrganizerToken()`) instead of the unset `process.env.E2E_TEST_TOKEN`, the event
 * is created via the API fixture (`createRegistrationEvent`, captured BATbern{N}) instead of a
 * UI form with `Date.now()` titles, and every run cleans up its events (`cleanupByCode` →
 * speaker_pool cascade). testid-only locators.
 *
 * Covers AC5 Playwright cases #31-32 (negative-case regression smoke). The slot-capacity
 * "Slot capacity reached" case was dropped: it requires a small maxSlots (EVENING defaults to 4,
 * not settable via the public API → the sub-line never renders, so the test only ever skipped)
 * AND multiple promote-to-READY calls (a flaky out-of-band Cognito/CUMS write). Both the
 * capacity gate and the stale-data sub-line are asserted deterministically in the Vitest suite
 * `SpeakerStatusLanes.test.tsx`.
 */
import { test, expect } from '@playwright/test';
import { readOrganizerToken, createRegistrationEvent } from '../helpers/event-fixture';
import { cleanupByCode } from '../helpers/test-fixtures-cleanup';
import { seedSpeaker, setSpeakerStatus } from '../helpers/speaker-pool-fixture';

test.describe(
  'Speaker kanban — column triage + chip colour coding (Story 11.D.3)',
  { tag: '@gate' },
  () => {
    let token: string;
    // Per-worker list of throwaway events created by this file's tests; cleaned after each test.
    // Playwright workers are separate processes, so this module-level array is per-worker — a
    // test and its afterEach share the worker, so no cross-test race even under fullyParallel.
    const createdEvents: string[] = [];

    test.beforeAll(() => {
      token = readOrganizerToken();
    });

    test.afterEach(async () => {
      while (createdEvents.length) {
        await cleanupByCode(token, createdEvents.pop()!); // cascade removes speaker_pool
      }
    });

    async function freshEvent(): Promise<string> {
      const ev = await createRegistrationEvent(token);
      createdEvents.push(ev.eventCode);
      return ev.eventCode;
    }

    test('should not render CONTACTED sub-line when no cards are stale (negative-case regression)', async ({
      page,
    }) => {
      const eventCode = await freshEvent();
      const speakerId = await seedSpeaker(token, eventCode, 'Fresh CONTACTED Speaker');
      await setSpeakerStatus(token, eventCode, speakerId, 'CONTACTED');

      await page.goto(`/organizer/events/${eventCode}?tab=speakers&view=kanban`);
      await expect(page.getByTestId(`speaker-card-${speakerId}`)).toBeVisible();

      // The CONTACTED sub-line only appears when at least one card is "stale" (>14 days since
      // state entry). Freshly-seeded data → sub-line absent; the lane heading still renders.
      await expect(page.getByTestId('status-lane-subline-contacted')).toHaveCount(0);
      await expect(page.getByTestId('status-lane-heading-contacted')).toBeVisible();
    });

    test('should render time-in-state chip with data-severity attribute (Story 11.D.3 wire-up)', async ({
      page,
    }) => {
      const eventCode = await freshEvent();
      const speakerId = await seedSpeaker(token, eventCode, 'Time Chip Speaker');
      await setSpeakerStatus(token, eventCode, speakerId, 'CONTACTED');

      await page.goto(`/organizer/events/${eventCode}?tab=speakers&view=kanban`);

      const chip = page.getByTestId(`time-in-state-chip-${speakerId}`);
      await expect(chip).toBeVisible();
      // A fresh card is at 0 days in state → `normal` (the public test contract added in 11.D.3).
      // Vitest covers warning + error transitions (they require backdating `updated_at`).
      await expect(chip).toHaveAttribute('data-severity', 'normal');
      await expect(chip).toHaveClass(/MuiChip-colorDefault/);
    });
  }
);
