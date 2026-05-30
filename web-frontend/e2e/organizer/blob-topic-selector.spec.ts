/**
 * E2E: Blob Topic Selector — slice 4 / topics (plan §C)
 * docs/plans/playwright-staging-hardening.md
 *
 * Hardened 2026-05-30 to the quality bar (testid-only locators, no empty/conditional tests).
 * Story 10.4 (BlobTopicSelector entry point + canvas page).
 *
 * Reality check (verified in TopicBacklogManager.tsx + BlobTopicSelectorPage.tsx +
 * BlobTopicSelector.tsx):
 *   • Entry: `/organizer/topics?eventCode=<code>` renders `blob-selector-button` (shown only
 *     when an eventCode query param is present) → navigates to
 *     `/organizer/events/<code>/topic-blob`.
 *   • The canvas page renders `blob-canvas` (full-viewport SVG, no AuthLayout) + `back-to-topics`
 *     + (once session data loads) `fit-all-button` / `snap-to-active-button`.
 *   • The unsaved-changes dialog's confirm/cancel buttons had NO testids (text-only) — this PR
 *     adds `blob-unsaved-dialog`, `blob-back-confirm`, `blob-back-cancel` so the spec is
 *     testid-only (was `getByRole('dialog').getByRole('button', { name: /go back|confirm/i })`).
 *   • The old `fit-all` / `snap` test was conditional (`if (isLoaded) …`) → asserted nothing
 *     when the canvas hadn't loaded. Rewritten to wait for `blob-canvas` (deterministic on dev,
 *     where BATbern57 is mirrored with sessions) then assert both controls unconditionally.
 *
 * Read-only / public-organizer surface (no mutation) → all `@gate`. BATbern57 is a real
 * archived event mirrored locally (same fixture the presentation `@gate` uses).
 */

import { test, expect } from '@playwright/test';
import { BASE_URL } from '../../playwright.config';

const EVENT_CODE = 'BATbern57';

test.describe('Blob Topic Selector — Entry Point (Story 10.4)', { tag: '@gate' }, () => {
  test('should_showBlobSelectorButton_when_eventCodePresent', async ({ page }) => {
    await page.goto(`${BASE_URL}/organizer/topics?eventCode=${EVENT_CODE}`);
    await expect(page.getByTestId('blob-selector-button')).toBeVisible();
  });

  test('should_navigateToBlobRoute_when_buttonClicked', async ({ page }) => {
    await page.goto(`${BASE_URL}/organizer/topics?eventCode=${EVENT_CODE}`);
    await page.getByTestId('blob-selector-button').click();

    await expect(page).toHaveURL(new RegExp(`/organizer/events/${EVENT_CODE}/topic-blob`));
  });

  test('should_hideBlobSelectorButton_when_noEventCode', async ({ page }) => {
    await page.goto(`${BASE_URL}/organizer/topics`);
    await expect(page.getByTestId('topic-backlog-manager')).toBeVisible();
    await expect(page.getByTestId('blob-selector-button')).toBeHidden();
  });
});

test.describe('Blob Topic Selector — Canvas Page (Story 10.4)', { tag: '@gate' }, () => {
  test.beforeEach(async ({ page }) => {
    // Clear the onboarding flag so the overlay doesn't sit over the canvas controls.
    await page.addInitScript(() => {
      localStorage.removeItem('batbern_blob_onboarding_seen');
    });
    await page.goto(`${BASE_URL}/organizer/events/${EVENT_CODE}/topic-blob`);
  });

  test('should_renderCanvasAndControls_when_sessionDataLoaded', async ({ page }) => {
    // Canvas is the full-viewport SVG; fit-all / snap render once session data resolves.
    // Generous timeout: BlobTopicSelector is a heavy lazy D3 chunk that the Vite dev server
    // compiles on-demand on first navigation (>5s cold). On staging the bundle is prebuilt,
    // so this resolves immediately. The session-data fetch itself is ~0.5s.
    await expect(page.getByTestId('blob-canvas')).toBeVisible({ timeout: 30_000 });
    await expect(page.getByTestId('fit-all-button')).toBeVisible();
    await expect(page.getByTestId('snap-to-active-button')).toBeVisible();
  });

  test('should_showBackButton_topLeft', async ({ page }) => {
    await expect(page.getByTestId('back-to-topics')).toBeVisible();
  });

  test('should_showUnsavedDialog_when_backClicked', async ({ page }) => {
    await page.getByTestId('back-to-topics').click();
    await expect(page.getByTestId('blob-unsaved-dialog')).toBeVisible();
  });

  test('should_navigateToTopicList_when_backConfirmed', async ({ page }) => {
    await page.getByTestId('back-to-topics').click();
    await page.getByTestId('blob-back-confirm').click();

    await expect(page).toHaveURL(new RegExp(`/organizer/topics\\?eventCode=${EVENT_CODE}`));
  });

  test('should_stayOnBlobPage_when_backCancelled', async ({ page }) => {
    await page.getByTestId('back-to-topics').click();
    await page.getByTestId('blob-back-cancel').click();

    await expect(page.getByTestId('blob-unsaved-dialog')).toBeHidden();
    await expect(page).toHaveURL(new RegExp(`/organizer/events/${EVENT_CODE}/topic-blob`));
  });
});
