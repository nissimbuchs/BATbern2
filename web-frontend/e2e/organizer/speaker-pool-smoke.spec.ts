/**
 * E2E: Speaker pool — slice 8 / speaker pool (plan §C) — the slice's mutating @smoke.
 * docs/plans/playwright-staging-hardening.md
 *
 * The canonical mutating+cleanup happy path for the organizer speaker-pool kanban: log outreach
 * on an IDENTIFIED speaker via the kanban primary-action button → MarkContactedModal → save,
 * driving the IDENTIFIED → CONTACTED transition, and verify it persisted server-side.
 *
 * Why this path (reliability, plan risk #3): it is the kanban's simplest deterministic mutation.
 * It deliberately does NOT promote-to-READY — promote provisions a Cognito/CUMS user out-of-band,
 * an external write that is intermittently flaky on dev (observed 500s) and would make a
 * per-deploy BLOCKING gate spurious. It also avoids native drag-drop. The setup (seed an
 * IDENTIFIED speaker) is API-driven; the mutation + assertion are UI + API.
 *
 * Prod-safety / cleanup: one throwaway EVENING event per run (createRegistrationEvent, captured
 * BATbern{N}) deleted in afterAll (cleanupByCode) — `speaker_pool.event_id` is ON DELETE CASCADE.
 * IDENTIFIED → CONTACTED records an outreach row (child of the speaker_pool row) and provisions
 * NO user, so the event-delete cascade is the complete teardown — no residue.
 */

import { test, expect } from '@playwright/test';
import {
  readOrganizerToken,
  createRegistrationEvent,
  type RegistrationEvent,
} from '../helpers/event-fixture';
import { cleanupByCode } from '../helpers/test-fixtures-cleanup';
import { seedSpeaker, getSpeakerStatus } from '../helpers/speaker-pool-fixture';

test.describe('Speaker pool — log outreach (Story 11.D)', { tag: '@gate' }, () => {
  test.describe.configure({ mode: 'serial' });

  let token: string;
  let fixtureEvent: RegistrationEvent;

  test.beforeAll(async () => {
    token = readOrganizerToken();
    fixtureEvent = await createRegistrationEvent(token);
  });

  test.afterAll(async () => {
    if (fixtureEvent?.eventCode) {
      await cleanupByCode(token, fixtureEvent.eventCode); // cascade removes speaker_pool + outreach
    }
  });

  test(
    'should_transitionToContacted_when_outreachLogged',
    { tag: ['@smoke', '@gate'] },
    async ({ page }) => {
      const eventCode = fixtureEvent.eventCode;

      // Seed an IDENTIFIED speaker via the API (no user provisioned).
      const speakerId = await seedSpeaker(token, eventCode, 'BATPW Outreach Speaker');
      expect(await getSpeakerStatus(token, eventCode, speakerId)).toBe('IDENTIFIED');

      await page.goto(`/organizer/events/${eventCode}?tab=speakers&view=kanban`);

      // IDENTIFIED card primary-action = "Log outreach" → opens MarkContactedModal.
      await page.getByTestId(`primary-action-button-${speakerId}`).click();
      await expect(page.getByTestId('mark-contacted-modal')).toBeVisible();

      // Contact date defaults to now; select a contact method (required) and save.
      await page.getByTestId('contact-method-select').click();
      await page.getByTestId('contact-method-option-email').click();
      await page.getByTestId('save-button').click();

      // Modal closes on success → the card's exact-state chip flips to CONTACTED.
      // Epic 14 (14.C.2): the kanban is 4 PHASE columns (Sourcing/Inviting/Content/Confirmed),
      // not per-state lanes — IDENTIFIED and CONTACTED both live in "Sourcing", and each card
      // carries its exact state on a `state-chip-{id}` chip (data-state). Assert that, not a lane.
      await expect(page.getByTestId('mark-contacted-modal')).toBeHidden({ timeout: 15_000 });
      await expect(page.getByTestId(`state-chip-${speakerId}`)).toHaveAttribute(
        'data-state',
        'CONTACTED'
      );

      // Authoritative verification: the server recorded the transition.
      await expect
        .poll(async () => getSpeakerStatus(token, eventCode, speakerId), { timeout: 15_000 })
        .toBe('CONTACTED');
    }
  );
});
