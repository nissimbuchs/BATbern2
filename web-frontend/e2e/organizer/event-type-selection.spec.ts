/**
 * E2E: Event Type Selection & Configuration — slice 4 / event-types (plan §C)
 * docs/plans/playwright-staging-hardening.md
 *
 * Rewritten 2026-05-30 to reality + the quality bar (testid-only locators, factory data,
 * no empty tests). Story 5.1 (EventTypeSelector) + Story 10.1 (EventTypesTab).
 *
 * Reality check (verified in EventTypeSelector / EventForm / EventTypesTab / App.tsx):
 *   • The New-Event modal opens via `new-event-button` (NOT `quick-action-new-event`) on
 *     /organizer/events → `create-event-modal`. The EventTypeSelector (`event-type-selector`,
 *     options `event-type-option-{full_day,afternoon,evening}`, loading
 *     `event-type-selector-loading`) lives on the default Info tab. The selector shows slot
 *     metadata INLINE per option; there is NO `SlotTemplatePreview` in the event form — that
 *     component renders only in the EventTypesTab admin cards, so the old "slot preview on
 *     select" test (expecting `slot-template-preview` in the modal) asserted non-existent DOM
 *     → DELETED.
 *   • The Event-Types admin surface is the `event-types-button` → `/organizer/admin?tab=0`
 *     (EventTypesTab). The standalone `/organizer/event-types` route now `<Navigate>`-redirects
 *     to the admin tab and the standalone `EventTypeConfigurationAdmin` page was DEAD (only its
 *     own unit test referenced it) → deleted in this PR. EventTypesTab has NO `<h1>`, no
 *     "ADMIN ONLY" text, and no "Back to Dashboard" button (breadcrumbs only) — the old spec's
 *     assertions on all three were stale → DELETED.
 *   • testids added this PR (same commit as this spec): `event-types-tab`,
 *     `event-type-card-{TYPE}`, `edit-event-type-{TYPE}` (EventTypesTab), `edit-event-type-modal`
 *     (its Dialog), `event-type-config-{save,cancel}` (EventTypeConfigurationForm),
 *     `slot-template-preview` (SlotTemplatePreview root). The edit-modal field inputs are
 *     asserted via the modal + save/cancel buttons (testid-only) rather than `input[name=…]`.
 *
 * Prod-safety (plan risk #1): event-type config is a GLOBAL singleton that governs every
 * future event of that type — mutating it on staging (=prod) has a wide blast radius and no
 * clean restore. So ALL mutating paths are excluded:
 *   • The UI edit flow is opened and CANCELLED (never saved) — read-only.
 *   • The old `PUT /events/types/{type}` "update (ORGANIZER only)" and "400 invalid" API tests
 *     used `Bearer ${E2E_TEST_TOKEN}` (unset → never passed) AND mutate global config → DELETED.
 *   • The old `403 without role` PUT test is UNTESTABLE here: playwright.config injects a global
 *     `Authorization` header when AUTH_TOKEN is set, so a header-less `request.put` still runs
 *     AS ORGANIZER → it would 200 and MUTATE prod config, not 403 → DELETED. (Same reasoning
 *     PR 4 used to drop the authenticated reconcile-POST.)
 * Only read-only GET contract checks remain on the API side. Nothing in this spec mutates, so
 * there is no `@smoke` here — slice 4's single mutating+cleanup `@smoke` is the topic create
 * (topic-selection.spec.ts). All tests are `@gate`.
 */

import { test, expect } from '@playwright/test';
import { API_URL } from '../../playwright.config';

test.describe('Event Type Selection (Story 5.1)', { tag: '@gate' }, () => {
  test.describe('Selector in the New-Event form', () => {
    test('should_displayEventTypeSelector_when_creatingEvent', async ({ page }) => {
      await page.goto('/organizer/events');
      await page.getByTestId('new-event-button').click();

      await expect(page.getByTestId('create-event-modal')).toBeVisible();
      await expect(page.getByTestId('event-type-selector')).toBeVisible();
    });

    test('should_showThreeEventTypeOptions_when_selectorOpened', async ({ page }) => {
      await page.goto('/organizer/events');
      await page.getByTestId('new-event-button').click();
      await expect(page.getByTestId('event-type-selector')).toBeVisible();

      // MUI Select: open the combobox; options render in a portal (still testid-addressable).
      await page.getByTestId('event-type-selector').click();
      await expect(page.getByTestId('event-type-option-full_day')).toBeVisible();
      await expect(page.getByTestId('event-type-option-afternoon')).toBeVisible();
      await expect(page.getByTestId('event-type-option-evening')).toBeVisible();
    });

    test('should_showLoadingState_while_fetchingEventTypes', async ({ page }) => {
      // Delay the event-types fetch so the loading variant of the selector is observable.
      await page.route('**/api/v1/events/types', async (route) => {
        await new Promise((resolve) => setTimeout(resolve, 1000));
        await route.continue();
      });

      await page.goto('/organizer/events');
      await page.getByTestId('new-event-button').click();

      await expect(page.getByTestId('event-type-selector-loading')).toBeVisible();
    });
  });

  test.describe('Event Types admin (EventTypesTab)', () => {
    test('should_renderEventTypeCards_when_navigatedFromQuickActions', async ({ page }) => {
      await page.goto('/organizer/events');
      await page.getByTestId('event-types-button').click();

      await expect(page).toHaveURL(/\/organizer\/admin/);
      await expect(page.getByTestId('event-types-tab')).toBeVisible();
      await expect(page.getByTestId('event-type-card-FULL_DAY')).toBeVisible();
      await expect(page.getByTestId('event-type-card-AFTERNOON')).toBeVisible();
      await expect(page.getByTestId('event-type-card-EVENING')).toBeVisible();
      // Each card renders a slot-config preview.
      await expect(page.getByTestId('slot-template-preview').first()).toBeVisible();
    });

    test('should_openAndCancelEditModal_when_editingAConfig', async ({ page }) => {
      await page.goto('/organizer/admin?tab=0');
      await expect(page.getByTestId('event-types-tab')).toBeVisible();

      await page.getByTestId('edit-event-type-FULL_DAY').click();

      // Edit modal opens with its save/cancel actions — assert via testids, then CANCEL
      // (never save; saving would mutate the global config — see prod-safety note above).
      await expect(page.getByTestId('event-type-config-save')).toBeVisible();
      await expect(page.getByTestId('event-type-config-cancel')).toBeVisible();

      await page.getByTestId('event-type-config-cancel').click();
      await expect(page.getByTestId('event-type-config-save')).toBeHidden();
    });
  });

  test.describe('GET /events/types contract (read-only)', () => {
    test('should_returnThreeEventTypes_when_listingTypes', async ({ request }) => {
      const response = await request.get(`${API_URL}/api/v1/events/types`);
      expect(response.status()).toBe(200);

      const eventTypes = (await response.json()) as Array<{
        type: string;
        minSlots: number;
        maxSlots: number;
        slotDuration: number;
      }>;
      expect(eventTypes).toHaveLength(3);

      const fullDay = eventTypes.find((et) => et.type === 'FULL_DAY');
      expect(fullDay).toBeDefined();
      // Values are user-configurable, so assert structural invariants, not specific numbers.
      expect(fullDay!.minSlots).toBeGreaterThan(0);
      expect(fullDay!.maxSlots).toBeGreaterThanOrEqual(fullDay!.minSlots);
      expect(fullDay!.slotDuration).toBeGreaterThanOrEqual(15);
    });

    test('should_return404_when_eventTypeDoesNotExist', async ({ request }) => {
      const response = await request.get(`${API_URL}/api/v1/events/types/INVALID_TYPE`);
      expect(response.status()).toBe(404);
    });
  });
});
