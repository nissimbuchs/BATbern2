/**
 * E2E: Archive Filtering (hardened — docs/plans/playwright-staging-hardening.md, slice 7 / PR 8)
 *
 * Public, read-only. data-testid locators only. Tagged @gate.
 *
 * Rewritten against the REAL FilterSidebar/FilterSheet + ArchivePage URL contract:
 *   topics=<comma-separated codes>, q=<search>, sort=<-date|date|-attendance>.
 *
 * Removed dead tests (logged in PR — features never implemented):
 *  - time-period filter ("Last 5 Years" / "2020-2024"): ArchiveFilters is { topics, search }
 *    only. No time-period control or URL param exists in the app.
 *  - "Most Sessions" sort option: the sort-select offers newest / oldest / most-attended only.
 *  - active-filter-chip: no such element is rendered.
 * Also: search uses the `q` param (not `search`), sort is a native <select data-testid=
 * "sort-select"> (not a click-to-open dropdown), and clear is `clear-filters` (not
 * `clear-filters-button`) — all corrected here.
 */

import { test, expect } from '@playwright/test';

const TOPIC_CHECKBOX = '[data-testid^="topic-checkbox-"]';

test.describe('Archive Filtering', { tag: '@gate' }, () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/archive');
    await expect(page.getByTestId('filter-sidebar')).toBeVisible();
    // The sidebar shell renders before its topic data loads; interacting (topic click,
    // sort change) before the topic list + URL-sync handlers are wired drops the update
    // and the URL never gains the param (flaky `toHaveURL`/`toBeChecked`). Wait for the
    // first topic checkbox — its presence proves the topic data loaded and the controls
    // are interactive.
    await expect(page.getByTestId('filter-sidebar').locator(TOPIC_CHECKBOX).first()).toBeVisible();
  });

  test('shows all filter controls in the desktop sidebar', async ({ page }) => {
    const sidebar = page.getByTestId('filter-sidebar');
    await expect(sidebar.getByTestId('search-input')).toBeVisible();
    await expect(sidebar.getByTestId('topic-filter')).toBeVisible();
    await expect(sidebar.getByTestId('sort-select')).toBeVisible();
    await expect(sidebar.getByTestId('clear-filters')).toBeVisible();
  });

  test('filters by topic and reflects it in the URL', async ({ page }) => {
    const firstTopic = page.getByTestId('filter-sidebar').locator(TOPIC_CHECKBOX).first();
    await expect(firstTopic).toBeVisible();

    // .click() (not .check()) — the checkbox is controlled via a URL round-trip, so its
    // checked state updates asynchronously; a retrying toBeChecked() is the reliable wait.
    await firstTopic.click();
    await expect(firstTopic).toBeChecked();
    await expect(page).toHaveURL(/[?&]topics=/);
  });

  test('searches via the q query parameter', async ({ page }) => {
    const search = page.getByTestId('filter-sidebar').getByTestId('search-input');

    await search.fill('Cloud');
    await expect(page).toHaveURL(/[?&]q=Cloud/);

    await search.fill('');
    await expect(page).not.toHaveURL(/[?&]q=/);
  });

  test('sorts via the sort-select control', async ({ page }) => {
    const sort = page.getByTestId('filter-sidebar').getByTestId('sort-select');
    await sort.selectOption('date'); // oldest-first
    await expect(page).toHaveURL(/[?&]sort=date/);
  });

  test('persists topic + search filters across reload', async ({ page }) => {
    const sidebar = page.getByTestId('filter-sidebar');
    const firstTopic = sidebar.locator(TOPIC_CHECKBOX).first();
    await firstTopic.click();
    await expect(firstTopic).toBeChecked();
    // Wait for the topic param to land in the URL BEFORE filling search — the two filter
    // updates each do an async URL round-trip and can clobber each other if interleaved.
    await expect(page).toHaveURL(/[?&]topics=/);

    await sidebar.getByTestId('search-input').fill('Architecture');
    await expect(page).toHaveURL(/[?&]q=Architecture/);
    // Both params must be present together before the reload restores them.
    await expect(page).toHaveURL(/[?&]topics=/);

    await page.reload();

    await expect(page.getByTestId('filter-sidebar').getByTestId('search-input')).toHaveValue(
      'Architecture'
    );
    // The first topic was the one we checked; it stays checked after reload (sort order stable).
    await expect(page.getByTestId('filter-sidebar').locator(TOPIC_CHECKBOX).first()).toBeChecked();
  });

  test('clears all filters', async ({ page }) => {
    const sidebar = page.getByTestId('filter-sidebar');
    const firstTopic = sidebar.locator(TOPIC_CHECKBOX).first();
    await firstTopic.click();
    await expect(firstTopic).toBeChecked();
    await sidebar.getByTestId('search-input').fill('Test');
    await expect(page).toHaveURL(/[?&]q=Test/);

    await sidebar.getByTestId('clear-filters').click();

    await expect(page.getByTestId('filter-sidebar').getByTestId('search-input')).toHaveValue('');
    await expect(
      page.getByTestId('filter-sidebar').locator(TOPIC_CHECKBOX).first()
    ).not.toBeChecked();
    await expect(page).not.toHaveURL(/[?&](topics|q)=/);
  });

  test('opens the filter sheet on a mobile viewport', async ({ page }) => {
    await page.setViewportSize({ width: 375, height: 667 });
    await page.goto('/archive');

    const sheet = page.getByTestId('filter-sheet');
    await sheet.getByTestId('filter-sheet-trigger').click();

    // When open, the sheet renders a FilterSidebar; scope to the sheet to avoid matching the
    // (display:none) desktop sidebar that is still in the DOM.
    await expect(sheet.getByTestId('search-input')).toBeVisible();
    await expect(sheet.getByTestId('topic-filter')).toBeVisible();
  });
});
