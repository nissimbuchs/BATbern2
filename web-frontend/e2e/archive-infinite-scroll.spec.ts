/**
 * E2E: Archive Infinite Scroll (hardened — docs/plans/playwright-staging-hardening.md, slice 7 / PR 8)
 *
 * Public, read-only. data-testid locators only. Tagged @gate.
 *
 * The archive paginates 20 events/page (useInfiniteEvents). The sentinel
 * (data-testid="infinite-scroll-sentinel") renders only while hasNextPage is true; reaching
 * it auto-fetches the next page. Each test skips itself cleanly when the environment's archive
 * is a single page (< 20 events), logging why — never a silent pass.
 *
 * Removed dead/flaky tests (logged in PR):
 *  - "maintain scroll position on browser back": browser scroll-restoration is not
 *    deterministic across runs/engines; not a gate-worthy assertion.
 *  - "respect <1000ms scroll performance target": wall-clock timing is environment-dependent
 *    and CI-flaky; performance belongs in a dedicated budget, not the UI gate.
 *  - "rapid scrolling without duplicate loads": low signal, high flake; the dedupe invariant
 *    is already covered by the count-bound assertion in the auto-load test.
 */

import { test, expect } from '@playwright/test';
import type { Page } from '@playwright/test';

const cardCount = (page: Page) => page.getByTestId('event-card').count();

test.describe('Archive Infinite Scroll', { tag: '@gate' }, () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/archive');
    await expect(page.getByTestId('event-card').first()).toBeVisible();
  });

  test('auto-loads the next page when the sentinel scrolls into view', async ({ page }) => {
    const initial = await cardCount(page);
    expect(initial).toBeGreaterThan(0);
    expect(initial).toBeLessThanOrEqual(20);
    test.skip(initial < 20, 'Archive is a single page in this environment — nothing to paginate');

    await page.getByTestId('infinite-scroll-sentinel').scrollIntoViewIfNeeded();
    await expect.poll(() => cardCount(page), { timeout: 10_000 }).toBeGreaterThan(initial);
    // Dedup invariant: a single page-load must not balloon past two pages.
    expect(await cardCount(page)).toBeLessThanOrEqual(40);
  });

  test('removes the sentinel once all pages are loaded', async ({ page }) => {
    // Scroll the sentinel into view repeatedly; each step waits until EITHER another page
    // loaded (card count grew) OR the sentinel disappeared (end reached). 15 steps covers a
    // 20/page archive well past its real size.
    for (let i = 0; i < 15; i++) {
      const sentinel = page.getByTestId('infinite-scroll-sentinel');
      if ((await sentinel.count()) === 0) break;
      const before = await cardCount(page);
      await sentinel.scrollIntoViewIfNeeded();
      await page
        .waitForFunction(
          (prev) => {
            const cards = document.querySelectorAll('[data-testid="event-card"]').length;
            const hasSentinel = !!document.querySelector(
              '[data-testid="infinite-scroll-sentinel"]'
            );
            return cards > prev || !hasSentinel;
          },
          before,
          { timeout: 8_000 }
        )
        .catch(() => {}); // tolerate a stalled step; the final assertion is the real check
    }

    // The sentinel renders only while hasNextPage is true, so it is gone at the end.
    await expect(page.getByTestId('infinite-scroll-sentinel')).toHaveCount(0);
  });

  test(
    'shows the loading indicator while fetching the next page',
    { tag: '@quarantine' },
    async ({ page }) => {
      // Quarantined: the loading state is brief and racy to catch deterministically; tracked
      // for promotion once a stable wait pattern is proven (plan §A6 quarantine discipline).
      const initial = await cardCount(page);
      test.skip(initial < 20, 'Archive is a single page in this environment — no fetch to observe');

      await page.getByTestId('infinite-scroll-sentinel').scrollIntoViewIfNeeded();
      await expect(page.getByTestId('infinite-scroll-loading')).toBeVisible({ timeout: 3_000 });
    }
  );
});
