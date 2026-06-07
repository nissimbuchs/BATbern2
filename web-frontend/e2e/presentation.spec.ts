/**
 * E2E: Moderator Presentation Page — slice 7 (plan §C)
 * docs/plans/playwright-staging-hardening.md
 *
 * Story 10.8a/b — ACs: #1 (loads without auth), #18 (sidebar appears on session slide),
 * #4 (agenda FLIPs back to center), #41 (no horizontal scroll @1920×1080).
 *
 * Read-only / public (no mutation → @gate only, no @smoke). Hardened 2026-05-30 to the
 * quality bar: the brittle `text=BATbern` "page loaded" match is replaced by the
 * `presentation-welcome-slide` testid on WelcomeSlide. Runs in a forced-anonymous context
 * so AC #1 ("loads without authentication") is genuinely exercised (the chromium project
 * otherwise carries an organizer session).
 *
 * Deck shape (verified against BATbern57): Welcome → 4 intro slides → Agenda Preview
 * (the agenda mounts CENTERED) → session slides (the agenda FLIPs to a SIDEBAR). There are
 * two `agenda-flip-container` elements — a `data-layout="center"` one (agenda-preview /
 * recap) and a `data-layout="sidebar"` one (session slides), conditionally mounted; during
 * the FLIP both briefly unmount. So we anchor on the stable centered agenda-preview slide,
 * press once into the first session slide, and assert via a layout-scoped locator (which
 * auto-retries through the FLIP animation instead of racing it).
 *
 * Uses BATbern57 (a real archived event on staging/prod, also mirrored locally) as source.
 */

import { test, expect, type Page } from '@playwright/test';

const EVENT_CODE = process.env.PRESENTATION_TEST_EVENT_CODE ?? 'BATbern57';
const PRESENTATION_URL = `/present/${EVENT_CODE}`;

/** The agenda FLIP container in a specific layout state (testid + state qualifier). */
const flip = (page: Page, layout: 'center' | 'sidebar') =>
  page.locator(`[data-testid="agenda-flip-container"][data-layout="${layout}"]`);

/**
 * Advance the deck with ArrowRight until the centered agenda-preview slide is reached
 * (the first slide on which the agenda container mounts). Bounded; the short settle paces
 * input so each intro-slide transition registers. Stops on the stable centered state so the
 * caller can step once into the session slide without racing the FLIP unmount transient.
 */
async function advanceToAgendaPreview(page: Page, maxPresses = 10): Promise<void> {
  for (let i = 0; i < maxPresses; i++) {
    if ((await flip(page, 'center').count()) > 0) return;
    await page.keyboard.press('ArrowRight');
    await page.waitForTimeout(400); // intro-slide transition settle
  }
}

test.describe('Moderator Presentation Page', { tag: '@gate' }, () => {
  // AC #1 demands no auth — force a fresh anonymous context regardless of project.
  test.use({ storageState: { cookies: [], origins: [] }, viewport: { width: 1920, height: 1080 } });

  test('loads without authentication and renders the Welcome slide (AC #1)', async ({ page }) => {
    await page.goto(PRESENTATION_URL);

    // Must NOT redirect to a login form.
    await expect(page.locator('[data-testid="login-form"], input[type="password"]')).toHaveCount(0);

    // WelcomeSlide renders (public, no auth).
    await expect(page.getByTestId('presentation-welcome-slide')).toBeVisible({ timeout: 15_000 });
  });

  test('advancing into a session slide shows the agenda sidebar (AC #18)', async ({ page }) => {
    await page.goto(PRESENTATION_URL);
    await expect(page.getByTestId('presentation-welcome-slide')).toBeVisible({ timeout: 15_000 });

    await advanceToAgendaPreview(page);
    await expect(flip(page, 'center')).toBeVisible(); // agenda-preview: agenda is centered
    await page.keyboard.press('ArrowRight'); // → first session slide

    // The agenda FLIPs into the sidebar layout.
    await expect(flip(page, 'sidebar')).toBeVisible({ timeout: 5_000 });
  });

  test('returning from a session slide FLIPs the agenda back to center (AC #4)', async ({
    page,
  }) => {
    await page.goto(PRESENTATION_URL);
    await expect(page.getByTestId('presentation-welcome-slide')).toBeVisible({ timeout: 15_000 });

    await advanceToAgendaPreview(page);
    await page.keyboard.press('ArrowRight'); // → first session slide (sidebar)
    await expect(flip(page, 'sidebar')).toBeVisible({ timeout: 5_000 });

    await page.keyboard.press('ArrowLeft'); // back to Agenda Preview (center)
    await expect(flip(page, 'center')).toBeVisible({ timeout: 5_000 });
  });

  test('has no horizontal scrollbar at 1920×1080 (AC #41)', async ({ page }) => {
    await page.goto(PRESENTATION_URL);
    await expect(page.getByTestId('presentation-welcome-slide')).toBeVisible({ timeout: 15_000 });

    const hasHorizontalScroll = await page.evaluate(
      () => document.documentElement.scrollWidth > document.documentElement.clientWidth
    );
    expect(hasHorizontalScroll).toBe(false);
  });
});
