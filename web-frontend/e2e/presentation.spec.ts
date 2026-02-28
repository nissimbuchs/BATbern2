/**
 * E2E Test: Moderator Presentation Page
 * Story 10.8a: Moderator Presentation Page — Functional
 *
 * ACs: #1 (loads without auth), #18 (sidebar appears after ArrowRight x4), #41 (no horizontal scroll)
 *
 * NOTE: Uses 'BATbern57' as the test event code. Tests run against local dev server.
 * They will pass only if the event exists and CUMS presentation settings endpoint is available.
 * In CI the test is skipped when the dev server is not running.
 */

import { test, expect } from '@playwright/test';

const EVENT_CODE = process.env.PRESENTATION_TEST_EVENT_CODE ?? 'BATbern57';
const PRESENTATION_URL = `/present/${EVENT_CODE}`;

test.describe('Moderator Presentation Page', () => {
  test.use({ viewport: { width: 1920, height: 1080 } });

  test('loads without authentication and renders Welcome section (AC #1)', async ({ page }) => {
    // Navigate without any auth token in headers
    await page.goto(PRESENTATION_URL);

    // Should NOT redirect to login
    await page.waitForURL(`**${PRESENTATION_URL}`, { timeout: 10_000 }).catch(() => {
      // Page may still be loading; that's OK for this check
    });

    // The page should NOT contain the login form
    const loginForm = page.locator('[data-testid="login-form"], input[type="password"]');
    await expect(loginForm).toHaveCount(0);

    // Should render something from WelcomeSlide (BATbern branding)
    await expect(page.locator('text=BATbern').first()).toBeVisible({ timeout: 15_000 });
  });

  test('pressing ArrowRight 4 times shows sidebar (AC #18)', async ({ page }) => {
    await page.goto(PRESENTATION_URL);

    // Wait for initial render
    await expect(page.locator('text=BATbern').first()).toBeVisible({ timeout: 15_000 });

    // Navigate 4 sections forward (Welcome → About → Committee → Topic Reveal → Agenda Preview)
    for (let i = 0; i < 4; i++) {
      await page.keyboard.press('ArrowRight');
      await page.waitForTimeout(100);
    }

    // After 4 presses we're on Agenda Preview (§5); sidebar should NOT yet appear
    // (sidebar appears from §6 onward — session slides)
    // Press once more to enter first session slide
    await page.keyboard.press('ArrowRight');
    // Wait for FLIP spring animation to complete (~350ms at stiffness 100, damping 22)
    await page.waitForTimeout(500);

    // Story 10.8b: verify FLIP animation completed — agenda container is in sidebar layout
    const agendaContainer = page.locator('[data-testid="agenda-flip-container"]');
    await expect(agendaContainer).toBeVisible({ timeout: 5_000 });
    await expect(agendaContainer).toHaveAttribute('data-layout', 'sidebar');
  });

  test('pressing ArrowLeft from session slide returns agenda to center (AC #4)', async ({
    page,
  }) => {
    await page.goto(PRESENTATION_URL);
    await expect(page.locator('text=BATbern').first()).toBeVisible({ timeout: 15_000 });

    // Navigate forward into the first session slide (5× ArrowRight)
    for (let i = 0; i < 5; i++) {
      await page.keyboard.press('ArrowRight');
      await page.waitForTimeout(100);
    }
    // Wait for sidebar FLIP to complete
    await page.waitForTimeout(500);
    const agendaContainer = page.locator('[data-testid="agenda-flip-container"]');
    await expect(agendaContainer).toHaveAttribute('data-layout', 'sidebar');

    // Navigate back to Agenda Preview — sidebar should FLIP back to center
    await page.keyboard.press('ArrowLeft');
    await page.waitForTimeout(500);
    await expect(agendaContainer).toHaveAttribute('data-layout', 'center');
  });

  test('no horizontal scrollbar at 1920×1080 (AC #41)', async ({ page }) => {
    await page.goto(PRESENTATION_URL);
    await expect(page.locator('text=BATbern').first()).toBeVisible({ timeout: 15_000 });

    const hasHorizontalScroll = await page.evaluate(() => {
      return document.documentElement.scrollWidth > document.documentElement.clientWidth;
    });

    expect(hasHorizontalScroll).toBe(false);
  });
});
