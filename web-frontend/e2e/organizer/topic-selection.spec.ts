/**
 * E2E: Topic Selection / Backlog — slice 4 / topics (plan §C)
 * docs/plans/playwright-staging-hardening.md
 *
 * Rewritten 2026-05-30 to reality + the quality bar (testid-only locators, factory data,
 * mandatory cleanup, no empty tests). Story 5.2 (TopicBacklogManager).
 *
 * Reality check (verified in TopicBacklogManager.tsx / CreateTopicModal.tsx / TopicList.tsx):
 *   • Topic creation is a MODAL (`new-topic-button` → `create-topic-modal`) with
 *     `topic-title-input`, `topic-description-input`, a MUI category Select
 *     (`topic-category-select-input` → `category-option-<cat>`), and `submit-topic-button`.
 *     On success the mutation resolves and the modal closes; on failure a `topic-form-error`
 *     Alert renders and the modal stays open — so the modal closing is an exact success signal
 *     (mirrors the company/user create `@smoke`). No fixed sleeps.
 *   • The topic page already carries rich testids — `topic-backlog-manager`, `topic-list`,
 *     `topic-card-<code>`, `staleness-score-<code>`, `view-mode-list`/`view-mode-heatmap`,
 *     `filter-category`. Slice 4 therefore needed ZERO new topic testids (the plan's
 *     "zero testids / heavy" estimate was wrong for topics; the heavy testid work is
 *     event-types). All non-testid locators in the old spec are gone.
 *
 * Cleanup contract (verified in event-management-service):
 *   • `topic_code` is SLUGIFIED from the title (`Topic.generateTopicCode`: lowercase, strip
 *     to [a-z0-9 -], spaces→`-`). A title of `factory.topicCode()` (`bruno-test-topic-<ts>`,
 *     already a valid slug) therefore yields `topic_code === that string` — reachable by the
 *     `ems/topics` prefix sweep (`topic_code LIKE 'bruno-test-topic-%'`) AND known up-front,
 *     so the spec deletes by the exact captured code. A topic with no usage history (never
 *     assigned to an event, as here) is deletable via `DELETE /topics/{code}` (→204), so
 *     `cleanupById(token,'topics',code)` is the per-test path; the global-teardown
 *     `bruno-test-topic-%` sweep is the backstop.
 *
 * Tests DELETED in the rewrite-to-reality (asserted non-existent DOM or duplicated Bruno):
 *   • The entire "Topics API Contract Tests" describe (GET/POST/assign/DELETE) — a
 *     Playwright re-implementation of Bruno's `event-topics-api` collection (`30-list`,
 *     `31-create`, `32-get`, `33-select-topic-for-event`, `34-verify-workflow`,
 *     `99a-posttest-cleanup`), same `bruno-test-topic-` prefix. Two of them sent
 *     `Bearer ${E2E_TEST_TOKEN}` (an env var nothing sets → `Bearer undefined`), so they
 *     never passed. Bruno owns the topic API contract.
 *   • Heat-map tests ("usage heat map", "quarterly frequency", hover tooltip): the detail
 *     `TopicHeatMap` renders ONLY when `usageHistory.length > 0`; a freshly-created topic has
 *     none, so these asserted DOM that cannot exist without a full event-assignment workflow.
 *     Heat-map cells also carry no testids. Not gate-worthy. (Backlog: cover via a seeded
 *     used-topic fixture if heat-map ever needs gating.)
 *   • "freshness green" / staleness-badge-on-fresh-topic standalone tests folded into the
 *     create `@smoke` is NOT done — staleness assertion dropped to avoid the pagination/sort
 *     timing flake of locating the new card on a populated prod list (same reasoning the
 *     company `@smoke` used to skip the search-result assertion). Modal-close + the
 *     cleanupById 204 are the persistence proof.
 *   • Topic→event selection / workflow-transition tests: the success surface the old spec
 *     asserted (`success-message`) does NOT exist (selection swaps the panel to speaker
 *     brainstorming), the flow mutates a real event's workflow state, and Bruno
 *     `33-select-topic-for-event` + `34-verify-topic-selection-workflow` already cover it at
 *     the API. Dropped to keep the slice reliability-first + prod-safe.
 *   • Category-filter interaction test: the filter MenuItems (TopicFilterPanel) carry no
 *     testids and API-level category filtering is Bruno-covered; not worth new testids here.
 *
 * Prod-safety (plan risk #1): the `@smoke` creates a real EMS `topics` row on staging (=prod);
 * teardown is explicit `cleanupById(token,'topics',code)` in afterEach with the canonical
 * `bruno-test-topic-%` global-teardown sweep as backstop.
 */

import { test, expect } from '@playwright/test';
import * as factory from '../helpers/test-data-factory';
import { readOrganizerToken } from '../helpers/event-fixture';
import { cleanupById } from '../helpers/test-fixtures-cleanup';

test.describe('Topic Backlog & Creation (Story 5.2)', { tag: '@gate' }, () => {
  let token: string;
  // topic_codes created via the UI in this run — explicit-deleted in afterEach (sweep backstop).
  const createdCodes: string[] = [];

  test.beforeAll(() => {
    token = readOrganizerToken();
  });

  test.beforeEach(async ({ page }) => {
    await page.goto('/organizer/topics');
    await expect(page.getByTestId('topic-backlog-manager')).toBeVisible();
  });

  test.afterEach(async () => {
    while (createdCodes.length > 0) {
      const code = createdCodes.pop();
      if (code) {
        await cleanupById(token, 'topics', code);
      }
    }
  });

  test('should_displayTopicBacklog_when_loaded', async ({ page }) => {
    await expect(page.getByTestId('new-topic-button')).toBeVisible();
    await expect(page.getByTestId('view-mode-list')).toBeVisible();
    await expect(page.getByTestId('view-mode-heatmap')).toBeVisible();

    // The list renders in list view (default view may be the heat map).
    await page.getByTestId('view-mode-list').click();
    await expect(page.getByTestId('topic-list')).toBeVisible();
  });

  test('should_openCreateModal_when_newTopicClicked', async ({ page }) => {
    await page.getByTestId('new-topic-button').click();

    await expect(page.getByTestId('create-topic-modal')).toBeVisible();
    await expect(page.getByTestId('topic-title-input')).toBeVisible();
    await expect(page.getByTestId('topic-description-input')).toBeVisible();
    await expect(page.getByTestId('topic-category-select-input')).toBeVisible();
    await expect(page.getByTestId('submit-topic-button')).toBeVisible();

    // Cancel closes the modal — no mutation.
    await page.getByTestId('cancel-topic-button').click();
    await expect(page.getByTestId('create-topic-modal')).toBeHidden();
  });

  test(
    'should_createTopic_when_validDataProvided',
    { tag: ['@smoke', '@gate'] },
    async ({ page }) => {
      // `factory.topicCode()` is already a valid slug, so topic_code === this string and the
      // `bruno-test-topic-%` sweep / explicit delete both reach it.
      const code = factory.topicCode(); // bruno-test-topic-<ts>
      createdCodes.push(code); // register for cleanup before the network call

      await page.getByTestId('new-topic-button').click();
      await expect(page.getByTestId('create-topic-modal')).toBeVisible();

      await page.getByTestId('topic-title-input').fill(code);
      await page.getByTestId('topic-description-input').fill('Playwright slice-4 fixture topic.');

      // MUI Select: open the combobox, then pick the option by testid.
      await page.getByTestId('topic-category-select-input').click();
      await page.getByTestId('category-option-technical').click();

      await page.getByTestId('submit-topic-button').click();

      // Success ⇔ the create mutation resolved and CreateTopicModal closed (a failed create
      // keeps the modal open with a `topic-form-error` Alert). Modal-close IS the persistence
      // proof; afterEach then explicit-deletes the real row (cleanupById → 204 confirms it
      // existed). No card-in-list assertion: a new topic's position on a populated, paginated
      // prod list is sort-dependent and would add flake for no extra signal (company `@smoke`
      // precedent).
      await expect(page.getByTestId('create-topic-modal')).toBeHidden({ timeout: 15_000 });
    }
  );
});
