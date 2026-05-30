/**
 * E2E — Speaker Kanban primary-action button (Story 11.D.2)
 * slice 8 / speaker pool (plan §C). docs/plans/playwright-staging-hardening.md
 *
 * Hardened 2026-05-31 to the gate quality bar: explicit organizer token (was the unset
 * `process.env.E2E_TEST_TOKEN`), API event fixture (was a UI form with `Date.now()` titles),
 * and per-test cleanup (`cleanupByCode` → speaker_pool cascade). testid-only locators.
 *
 * Covers the state-aware primary-action button surfaces that are deterministic from the public
 * API:
 *   - IDENTIFIED → opens MarkContactedModal.
 *   - CONTACTED  → opens the drawer's Promote sub-view (post-Epic-11 the CONTACTED action opens
 *                  the in-drawer promote view, NOT the legacy PromoteSpeakerDialog — so the real
 *                  testid is `promote-submit-button`, not the dead `promote-email-field`).
 *
 * Dropped vs the old spec (reliability + reality): the READY slot-capacity "disabled" case
 * (assumed maxSlots=1; EVENING defaults to 4, not settable via the public API — covered by the
 * column-triage capacity test + Vitest), the QUALITY_REVIEWED `assign-session-slot` case (a
 * 6-transition walk; asserted in SpeakerStatusLanes.test.tsx), and the INVITED → drawer case
 * (reaching INVITED requires promote-to-READY, an intermittently-flaky out-of-band Cognito/CUMS
 * write — kept out of the gate; the @smoke covers the primary-action → modal flow instead).
 */
import { test, expect } from '@playwright/test';
import { readOrganizerToken, createRegistrationEvent } from '../helpers/event-fixture';
import { cleanupByCode } from '../helpers/test-fixtures-cleanup';
import { seedSpeaker, setSpeakerStatus } from '../helpers/speaker-pool-fixture';

test.describe('Speaker kanban — primary-action button (Story 11.D.2)', { tag: '@gate' }, () => {
  let token: string;
  const createdEvents: string[] = [];

  test.beforeAll(() => {
    token = readOrganizerToken();
  });

  test.afterEach(async () => {
    while (createdEvents.length) {
      await cleanupByCode(token, createdEvents.pop()!);
    }
  });

  async function freshEvent(): Promise<string> {
    const ev = await createRegistrationEvent(token);
    createdEvents.push(ev.eventCode);
    return ev.eventCode;
  }

  test('should open MarkContactedModal when Log-outreach button is clicked on IDENTIFIED card', async ({
    page,
  }) => {
    const eventCode = await freshEvent();
    const speakerId = await seedSpeaker(token, eventCode, 'Identified E2E Speaker');

    await page.goto(`/organizer/events/${eventCode}?tab=speakers&view=kanban`);
    await page.getByTestId(`primary-action-button-${speakerId}`).click();

    await expect(page.getByTestId('mark-contacted-modal')).toBeVisible();
  });

  test('should open the drawer Promote sub-view when Promote button is clicked on CONTACTED card', async ({
    page,
  }) => {
    const eventCode = await freshEvent();
    const speakerId = await seedSpeaker(token, eventCode, 'Contacted E2E Speaker');
    await setSpeakerStatus(token, eventCode, speakerId, 'CONTACTED');

    await page.goto(`/organizer/events/${eventCode}?tab=speakers&view=kanban`);
    await page.getByTestId(`primary-action-button-${speakerId}`).click();

    // Epic 11: CONTACTED promote opens the in-drawer Promote sub-view (UserAutocomplete +
    // Create-New-Speaker), whose submit control carries `promote-submit-button`.
    await expect(page.getByTestId('promote-submit-button')).toBeVisible();
  });
});
