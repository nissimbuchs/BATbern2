/**
 * E2E: Organizer admin tabs — RENDER coverage (slice 12 / admin tabs, plan §C)
 * docs/plans/playwright-staging-hardening.md
 *
 * The read/render dimension for EVERY admin tab + the two organizer pages. Full
 * Create/Read/Update/Delete for the three freely-reversible tabs lives in the sibling
 * `admin-{task-templates,email-templates,global-images}-crud.spec.ts` files (PO decision
 * 2026-05-31: real CRUD only where rows are creatable+deletable; the global-singleton tabs —
 * event-types, presentation, ai-prompts, settings/email-forwarding, venue/catering — stay
 * render-only here because their mutations are shared prod config with no safe restore path).
 *
 * Rewritten 2026-05-31 — supersedes `admin-settings.spec.ts`, which covered only the Settings
 * tab, navigated to the WRONG index (`?tab=7`; Settings is tab 6), SAVED a global-singleton
 * setting (`email-forwarding.support-contacts`) every run with no restore, and set a dead
 * `.playwright-auth-chromium.json` storageState (ENOENT — the chromium project inherits
 * `.playwright-auth-state.json`). All testid-only, read-only → `@gate`.
 */

import { test, expect } from '@playwright/test';

const ADMIN_URL = '/organizer/admin';

// One render assertion per admin tab — navigate by URL (?tab=N), assert the loaded content
// root. The content root testid appears only AFTER the tab's react-query load resolves, so a
// generous timeout absorbs cold dev compiles / API latency without a fixed sleep.
const ADMIN_TABS: { tab: number; testid: string; name: string }[] = [
  { tab: 0, testid: 'event-types-tab', name: 'Event Types' },
  { tab: 1, testid: 'import-data-tab', name: 'Import Data' },
  { tab: 2, testid: 'task-templates-tab', name: 'Task Templates' },
  { tab: 3, testid: 'email-templates-tab', name: 'Email Templates' },
  { tab: 4, testid: 'presentation-settings-tab', name: 'Presentation Settings' },
  { tab: 5, testid: 'ai-prompts-tab', name: 'AI Prompts' },
  { tab: 6, testid: 'admin-settings-tab', name: 'Settings' },
  { tab: 7, testid: 'global-images-tab', name: 'Global Images' },
  { tab: 8, testid: 'venue-catering-contacts-tab', name: 'Venue & Catering' },
];

test.describe('AdminTabs · render (Story 10.1)', { tag: '@gate' }, () => {
  for (const { tab, testid, name } of ADMIN_TABS) {
    test(`should_render_when_adminTabOpened [${name}]`, async ({ page }) => {
      await page.goto(`${ADMIN_URL}?tab=${tab}`);
      // The tab strip itself is the page-loaded signal; then the selected tab's content root.
      await expect(page.getByTestId('admin-tabs')).toBeVisible();
      await expect(page.getByTestId(testid)).toBeVisible({ timeout: 20_000 });
    });
  }

  test('should_renderAnalyticsDashboard_when_pageOpened', async ({ page }) => {
    await page.goto('/organizer/analytics');
    await expect(page.getByTestId('organizer-analytics-page')).toBeVisible({ timeout: 20_000 });
  });

  test('should_renderNotificationsPage_when_pageOpened', async ({ page }) => {
    await page.goto('/organizer/notifications');
    await expect(page.getByTestId('notifications-page')).toBeVisible({ timeout: 20_000 });
  });
});
