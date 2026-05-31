/**
 * E2E — Speaker-pool kanban GOLDEN PATH (intensive, UI-driven) — slice 8 addendum (plan §C)
 * docs/plans/playwright-staging-hardening.md
 *
 * The product owner asked for the speaker-pool kanban — the deliberate, button-driven
 * replacement for drag-drop — to be exercised INTENSIVELY through the FRONTEND (not the API),
 * end to end, in one realistic scenario that also unblocks the event lifecycle:
 *
 *   1. Create an EVENING event + a topic (API setup).
 *   2. Add 5 speakers ONE BY ONE through the brainstorming UI → all IDENTIFIED.
 *   3. Push every speaker IDENTIFIED → CONTACTED → READY with the card's DEFAULT (primary) action
 *      (Log outreach → MarkContactedModal; Promote → drawer promote sub-view).
 *   4. READY has TWO exit paths — both exercised:
 *        • Path A (email → speaker portal): the Cognito test speaker (batbern.speaker) is
 *          promoted by selecting the EXISTING user, sent an invitation (READY → INVITED), then
 *          logs into the SPEAKER PORTAL and ACCEPTS (INVITED → ACCEPTED).
 *        • Path B (organizer direct): 3 fresh test speakers go READY → ACCEPTED via the drawer's
 *          "Accept on behalf" alternative action (reason required).
 *   5. A 5th test speaker goes READY → DECLINED via the drawer's "Decline" alternative action.
 *   6. The 4 accepted speakers go ACCEPTED → CONTENT_SUBMITTED → QUALITY_REVIEWED through the UI.
 *   7. Slots are auto-assigned through the slot-assignment UI; with 4 publishable speakers the
 *      event reaches AGENDA_PUBLISHED, then the lifecycle is driven to ARCHIVED.
 *
 * End state: 4 QUALITY_REVIEWED + 1 DECLINED speaker, EVENING slot cap (4 occupants) exactly
 * filled, event ARCHIVED — connecting the speaker-pool slice to the event-workflow slice.
 *
 * Determinism & tagging (plan risk #3 / OQ-1): the CONTACTED → READY *promote* provisions a
 * Cognito user out-of-band (Pattern N) — deterministic on staging, intermittently flaky on
 * local-dev. This walk does 4 fresh provisions, so it is tagged **@gate only** (nightly, with
 * retries) — NOT a blocking per-deploy @smoke (a Cognito flake must not roll back a deploy). The
 * per-deploy blocking @smoke stays the single IDENTIFIED → CONTACTED path (speaker-pool-smoke).
 * Path A's promote selects the PRE-EXISTING batbern.speaker user (idempotent → no fresh Cognito
 * create), which is both more deterministic and the only way the invited speaker can actually
 * log in and accept.
 *
 * UI-driven, not API: every workflow transition is driven by a real testid'd control. The API is
 * used ONLY for setup (event/topic creation) and for read-only verification/id-mapping + the
 * cron-only EVENT_LIVE/EVENT_COMPLETED tail (no UI or override path exists for cron states).
 *
 * Cleanup: afterAll force-archives the event (events delete only when ARCHIVED) then cleanupByCode
 * (cascade removes speaker_pool/sessions). The 4 fresh `bruno.test` users (cross-service CUMS, not
 * cascaded) are swept by global-teardown's `cums/users` LIKE `bruno.test%` sweep. batbern.speaker
 * is the persistent test user — never deleted; its pool row on the throwaway event cascades away.
 */

import { test, expect, type Page } from '@playwright/test';
import {
  readOrganizerToken,
  createRegistrationEvent,
  createAndSelectTopic,
  transitionWorkflow,
  getWorkflowState,
  getUnassignedSessionCount,
  type RegistrationEvent,
} from '../helpers/event-fixture';
import { cleanupByCode } from '../helpers/test-fixtures-cleanup';
import {
  listPool,
  getSpeakerStatus,
  readSpeakerIdentity,
  type SpeakerIdentity,
} from '../helpers/speaker-pool-fixture';
import { email as factoryEmail } from '../helpers/test-data-factory';

// Five speakers, by the role each plays in the walk. Index 0 = Path A (portal); 1-3 = Path B
// (accept-on-behalf); 4 = decline-from-READY. Names are the brainstorm-era speakerName typed in
// the UI; the pool id is captured from listPool after add.
const SPEAKER_NAMES = [
  'BATPW PortalAccept Speaker', // 0 — Path A: invited → accepts in portal
  'BATPW OnBehalf One', // 1 — Path B: accept-on-behalf
  'BATPW OnBehalf Two', // 2 — Path B
  'BATPW OnBehalf Three', // 3 — Path B
  'BATPW Declined Speaker', // 4 — READY → DECLINED
] as const;

const KANBAN = (code: string) => `/organizer/events/${code}?tab=speakers&view=kanban`;

test.describe('Speaker-pool kanban golden path (intensive, UI-driven)', { tag: '@gate' }, () => {
  test.describe.configure({ mode: 'serial' });
  // Generous per-test budget: each phase drives several speakers through multi-step UI flows
  // (modals, drawer sub-views) plus out-of-band Cognito provisions.
  test.setTimeout(5 * 60 * 1000);

  let token: string;
  let speaker: SpeakerIdentity | undefined;
  let fixtureEvent: RegistrationEvent;
  /** Captured pool ids, indexed parallel to SPEAKER_NAMES. */
  const ids: string[] = [];

  test.beforeAll(async () => {
    token = readOrganizerToken();
    speaker = readSpeakerIdentity();
    // Path A is integral to this walk (it fills 1 of the 4 slots and exercises the portal), so
    // the whole golden path requires the SPEAKER token — skip cleanly when it's absent (the
    // `speaker` Playwright project only activates with SPEAKER_AUTH_TOKEN anyway).
    test.skip(!speaker, 'Golden path needs the SPEAKER token (batbern.speaker) for Path A');
    fixtureEvent = await createRegistrationEvent(token); // EVENING, CREATED, captured BATbern{N}
    await createAndSelectTopic(token, fixtureEvent.eventCode);
  });

  test.afterAll(async () => {
    if (fixtureEvent?.eventCode) {
      // Events are deletable only once ARCHIVED → force-archive (tolerant) then delete; the
      // cascade removes speaker_pool + sessions. Fresh bruno.test users are swept by teardown.
      await transitionWorkflow(token, fixtureEvent.eventCode, 'ARCHIVED').catch(() => {});
      await cleanupByCode(token, fixtureEvent.eventCode);
    }
  });

  // ── helpers (UI) ─────────────────────────────────────────────────────────────────────────

  /** Open the kanban view for the fixture event. */
  async function openKanban(page: Page) {
    await page.goto(KANBAN(fixtureEvent.eventCode));
    await expect(page.getByTestId('status-lane-identified')).toBeVisible();
  }

  /** Drive an IDENTIFIED card → CONTACTED via the primary action + MarkContactedModal. */
  async function logOutreach(page: Page, id: string) {
    await page.getByTestId(`primary-action-button-${id}`).click();
    await expect(page.getByTestId('mark-contacted-modal')).toBeVisible();
    await page.getByTestId('contact-method-select').click();
    await page.getByTestId('contact-method-option-email').click();
    await page.getByTestId('save-button').click();
    await expect(page.getByTestId('mark-contacted-modal')).toBeHidden({ timeout: 15_000 });
  }

  /** Drive a CONTACTED card → READY via the primary action → drawer promote sub-view, creating a
   *  fresh SPEAKER user (provisions Cognito out-of-band). */
  async function promoteCreatingNewUser(page: Page, id: string) {
    await page.getByTestId(`primary-action-button-${id}`).click();
    await expect(page.getByTestId('promote-submit-button')).toBeVisible();
    await page.getByTestId('promote-create-new-speaker-button').click();
    await expect(page.getByTestId('user-create-dialog')).toBeVisible();
    await page.getByTestId('user-create-firstName').locator('input').fill('Bruno');
    await page.getByTestId('user-create-lastName').locator('input').fill('Test');
    await page.getByTestId('user-create-email').locator('input').fill(factoryEmail());
    await page.getByTestId('user-create-role-SPEAKER').click();
    await page.getByTestId('user-create-submit').click();
    await expect(page.getByTestId('user-create-dialog')).toBeHidden({ timeout: 15_000 });
    // Created user is now the selected speaker → promote.
    await page.getByTestId('promote-submit-button').click();
    await expect(page.getByTestId('promote-submit-button')).toBeHidden({ timeout: 20_000 });
  }

  /** Drive a CONTACTED card → READY by selecting an EXISTING user (idempotent provision). */
  async function promoteSelectingExistingUser(page: Page, id: string, username: string) {
    await page.getByTestId(`primary-action-button-${id}`).click();
    await expect(page.getByTestId('promote-submit-button')).toBeVisible();
    // UserAutocomplete: its testid is on the <input> itself, and searchUsers matches NAME tokens
    // (not username / not email) — so type the firstName fragment (the username's first dotted
    // segment, e.g. batbern.speaker → "batbern"), then pick the keyed option (id === username).
    await page.getByTestId('promote-speaker-search-field').fill(username.split('.')[0]);
    const option = page.getByTestId(`user-option-${username}`);
    await expect(option).toBeVisible({ timeout: 15_000 });
    await option.click();
    await page.getByTestId('promote-submit-button').click();
    await expect(page.getByTestId('promote-submit-button')).toBeHidden({ timeout: 20_000 });
  }

  /** Open the drawer for a card (card click ≠ primary-action button) and run a status-change
   *  dialog action (accept-on-behalf or decline), supplying the required reason. */
  async function drawerStatusChange(
    page: Page,
    id: string,
    action: 'accept-on-behalf' | 'decline'
  ) {
    await page.getByTestId(`speaker-card-${id}`).click();
    const actionBtn = page.getByTestId(`drawer-action-${action}`);
    await expect(actionBtn).toBeVisible();
    await actionBtn.click();
    await expect(page.getByTestId('status-change-dialog')).toBeVisible();
    await page
      .getByTestId('status-change-reason')
      .locator('textarea')
      .first()
      .fill(`E2E golden-path ${action}`);
    await page.getByTestId('status-change-confirm').click();
    await expect(page.getByTestId('status-change-dialog')).toBeHidden({ timeout: 15_000 });
  }

  // ── phases 1-3 ───────────────────────────────────────────────────────────────────────────

  test('phase 1 — add 5 speakers one-by-one via the brainstorming UI → IDENTIFIED', async ({
    page,
  }) => {
    await openKanban(page);
    await page.getByTestId('add-speakers-button').click();

    // `speaker-name-field`'s testid is on the <input> itself (via inputProps), unlike the
    // user-create-* fields whose testid is on the TextField root.
    const nameField = page.getByTestId('speaker-name-field');
    for (const name of SPEAKER_NAMES) {
      await expect(nameField).toBeVisible();
      await nameField.fill(name);
      await page.getByTestId('add-to-pool-button').click();
      // Form clears on success (the panel stays open for the next add).
      await expect(nameField).toHaveValue('', { timeout: 15_000 });
    }

    // Map the UI-typed names → server ids; assert all 5 are IDENTIFIED.
    const pool = await listPool(token, fixtureEvent.eventCode);
    for (const name of SPEAKER_NAMES) {
      const entry = pool.find((e) => e.speakerName === name);
      expect(entry, `pool entry for "${name}"`).toBeTruthy();
      expect(entry!.status).toBe('IDENTIFIED');
      ids.push(entry!.id);
    }
    expect(ids).toHaveLength(5);
  });

  test('phase 2 — push all 5 IDENTIFIED → CONTACTED via the card primary action', async ({
    page,
  }) => {
    await openKanban(page);
    for (const id of ids) {
      await logOutreach(page, id);
      await expect
        .poll(() => getSpeakerStatus(token, fixtureEvent.eventCode, id), { timeout: 15_000 })
        .toBe('CONTACTED');
    }
  });

  test('phase 3 — promote all 5 CONTACTED → READY via the drawer promote sub-view', async ({
    page,
  }) => {
    await openKanban(page);

    // Speaker 0 (Path A): promote by selecting the EXISTING batbern.speaker user so the invitation
    // links to a user who can actually log into the portal (idempotent provision — no fresh
    // Cognito create). `speaker` is guaranteed present (beforeAll skips the walk otherwise).
    await promoteSelectingExistingUser(page, ids[0], speaker!.username);
    await expect
      .poll(() => getSpeakerStatus(token, fixtureEvent.eventCode, ids[0]), { timeout: 20_000 })
      .toBe('READY');

    // Speakers 1-4: promote by creating fresh SPEAKER users (Path B + decline).
    for (const id of ids.slice(1)) {
      await openKanban(page);
      await promoteCreatingNewUser(page, id);
      await expect
        .poll(() => getSpeakerStatus(token, fixtureEvent.eventCode, id), { timeout: 20_000 })
        .toBe('READY');
    }
  });

  // ── phases 4-7: the two READY exit paths + decline ───────────────────────────────────────

  test('phase 4 — Path A: send invitation (READY → INVITED) via the card primary action', async ({
    page,
  }) => {
    await openKanban(page);
    // READY card primary action = "Send invitation" → fires the invitation (no confirm dialog).
    await page.getByTestId(`primary-action-button-${ids[0]}`).click();
    await expect
      .poll(() => getSpeakerStatus(token, fixtureEvent.eventCode, ids[0]), { timeout: 20_000 })
      .toBe('INVITED');
  });

  test('phase 5 — Path A: the invited speaker ACCEPTS in the speaker portal (INVITED → ACCEPTED)', async ({
    browser,
  }) => {
    // Switch identity: a fresh context with the SPEAKER storage state (batbern.speaker), who was
    // just invited above. CRUCIAL: playwright.config's global `extraHTTPHeaders` injects the
    // ORGANIZER bearer (AUTH_TOKEN), which otherwise wins over the storageState token and makes
    // every speaker-portal API call hit the backend AS ORGANIZER → 403 "insufficient permissions".
    // Override it with the speaker's own token so the portal calls are authed as the speaker.
    const speakerContext = await browser.newContext({
      storageState: '.playwright-auth-speaker.json',
      extraHTTPHeaders: { Authorization: `Bearer ${speaker!.token}` },
    });
    const speakerPage = await speakerContext.newPage();
    try {
      await speakerPage.goto(`/speaker-portal/respond/${fixtureEvent.eventCode}`);
      // The response form renders only while the pool row is INVITED.
      await speakerPage.getByTestId('invitation-response-accept-btn').click();
      await speakerPage.getByTestId('invitation-response-submit-btn').click();
      await expect(speakerPage.getByTestId('invitation-response-success')).toBeVisible({
        timeout: 20_000,
      });
    } finally {
      await speakerContext.close();
    }
    // Authoritative cross-check via the organizer API: the speaker accepted.
    await expect
      .poll(() => getSpeakerStatus(token, fixtureEvent.eventCode, ids[0]), { timeout: 20_000 })
      .toBe('ACCEPTED');
  });

  test('phase 6 — Path B: organizer accepts 3 speakers on behalf (READY → ACCEPTED) via the drawer', async ({
    page,
  }) => {
    // Speakers 1-3 via the drawer's "Accept on behalf" alternative action (reason required). This
    // fills the EVENING slot cap exactly: batbern.speaker (1, accepted above) + these 3 = 4.
    for (const id of ids.slice(1, 4)) {
      await openKanban(page);
      await drawerStatusChange(page, id, 'accept-on-behalf');
      await expect
        .poll(() => getSpeakerStatus(token, fixtureEvent.eventCode, id), { timeout: 20_000 })
        .toBe('ACCEPTED');
    }
  });

  test('phase 7 — the 5th speaker is DECLINED from READY via the drawer alternative action', async ({
    page,
  }) => {
    await openKanban(page);
    await drawerStatusChange(page, ids[4], 'decline');
    await expect
      .poll(() => getSpeakerStatus(token, fixtureEvent.eventCode, ids[4]), { timeout: 20_000 })
      .toBe('DECLINED');
  });

  // ── phases 8-11: content → quality → slots → event lifecycle to ARCHIVED ─────────────────

  /** The 4 ACCEPTED speakers (batbern.speaker + the 3 on-behalf). */
  const acceptedIds = () => ids.slice(0, 4);

  test('phase 8 — the 4 accepted speakers submit content (ACCEPTED → CONTENT_SUBMITTED)', async ({
    page,
  }) => {
    for (const id of acceptedIds()) {
      await openKanban(page);
      // ACCEPTED card primary action = "Enter content" → opens the drawer Content tab.
      await page.getByTestId(`primary-action-button-${id}`).click();
      await expect(page.getByTestId('presentation-title-field')).toBeVisible();
      // The speaker is auto-resolved from the promoted username (no picker selection needed).
      await page.getByTestId('presentation-title-field').fill(`BATPW Talk ${id.slice(0, 6)}`);
      await page
        .getByTestId('presentation-abstract-field')
        .fill('Golden-path E2E presentation abstract — auto-cleaned with the event.');
      await page.getByTestId('submit-speaker-content-button').click();
      await expect
        .poll(() => getSpeakerStatus(token, fixtureEvent.eventCode, id), { timeout: 20_000 })
        .toBe('CONTENT_SUBMITTED');
    }
  });

  test('phase 9 — the organizer approves all 4 (CONTENT_SUBMITTED → QUALITY_REVIEWED)', async ({
    page,
  }) => {
    for (const id of acceptedIds()) {
      await openKanban(page);
      // CONTENT_SUBMITTED card primary action = "Review content" → opens the quality-review view.
      await page.getByTestId(`primary-action-button-${id}`).click();
      await expect(page.getByTestId('approve-content-button')).toBeVisible();
      await page.getByTestId('approve-content-button').click();
      await expect
        .poll(() => getSpeakerStatus(token, fixtureEvent.eventCode, id), { timeout: 20_000 })
        .toBe('QUALITY_REVIEWED');
    }
  });

  test('phase 10 — auto-assign all sessions to slots via the slot-assignment UI', async ({
    page,
  }) => {
    await page.goto(`/organizer/events/${fixtureEvent.eventCode}/slot-assignment`);
    await expect(page.getByTestId('quick-actions-panel')).toBeVisible();
    // Each promoted speaker owns a placeholder (unassigned) session.
    const before = await getUnassignedSessionCount(token, fixtureEvent.eventCode);
    expect(before).toBeGreaterThan(0);

    await page.getByTestId('auto-assign-button').click();
    await expect(page.getByTestId('auto-assign-modal')).toBeVisible();
    await page.getByTestId('auto-assign-confirm').click();
    await expect(page.getByTestId('auto-assign-modal')).toBeHidden({ timeout: 20_000 });

    // Auto-assign places the accepted speakers into the EVENING slots. The count drops but need
    // not reach 0 — the DECLINED speaker still owns an orphan placeholder session that has no
    // accepted speaker and so is not auto-assigned. The authoritative proof that the 4 ACCEPTED
    // speakers got slots is phase 11 (the event can only reach AGENDA_PUBLISHED when every
    // accepted speaker is publishable = QUALITY_REVIEWED AND slot-assigned).
    await expect
      .poll(() => getUnassignedSessionCount(token, fixtureEvent.eventCode), { timeout: 20_000 })
      .toBeLessThan(before);
  });

  test('phase 11 — event auto-advanced to SLOT_ASSIGNMENT, then is driven to ARCHIVED', async ({
    page,
  }) => {
    // GENUINE checkpoint (no override): accepting speakers auto-advanced the event from
    // SPEAKER_IDENTIFICATION to SLOT_ASSIGNMENT (SpeakerAcceptedEventListener) — i.e. the
    // UI-driven speaker work really did move the event forward.
    await expect
      .poll(() => getWorkflowState(token, fixtureEvent.eventCode), { timeout: 15_000 })
      .toBe('SLOT_ASSIGNMENT');

    // The lifecycle tail is driven via the override transition API (slice 10's mechanism):
    // AGENDA_PUBLISHED's automatic advance is gated on "all sessions timed", which the DECLINED
    // speaker's orphan placeholder session holds open, and EVENT_LIVE/EVENT_COMPLETED are
    // cron-only (daily 00:01 / 23:59 Bern) with no UI control. Assert the overview badge reflects
    // each state — proving the lifecycle reaches ARCHIVED with the speaker pool fully worked.
    for (const state of [
      'AGENDA_PUBLISHED',
      'EVENT_LIVE',
      'EVENT_COMPLETED',
      'ARCHIVED',
    ] as const) {
      await transitionWorkflow(token, fixtureEvent.eventCode, state);
      await page.goto(`/organizer/events/${fixtureEvent.eventCode}`);
      await expect(page.getByTestId('workflow-status-badge')).toHaveAttribute(
        'data-workflow-state',
        state
      );
    }
  });
});
