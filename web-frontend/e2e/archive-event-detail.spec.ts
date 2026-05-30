/**
 * E2E: Archive Event Detail (hardened — docs/plans/playwright-staging-hardening.md, slice 7 / PR 8)
 *
 * Public, read-only. data-testid locators only. Tagged @gate.
 *
 * IMPORTANT — page identity: /archive/:eventCode renders <HomePage /> in ARCHIVE mode
 * (App.tsx). The standalone ArchiveEventDetailPage component is NOT routed in production
 * (only its own unit test mounts it), so this spec was fully rewritten against HomePage's
 * real archive rendering: HeroSection title (event-hero-title), back-to-archive link,
 * SessionCards (sessions-section + session-card), and SpeakerGrid (speaker-grid +
 * speaker-card via SpeakerDisplay's speaker-name).
 *
 * Removed dead assertions (logged in PR — they matched the unrouted ArchiveEventDetailPage):
 *  - event-header / event-topic / venue-info / registration-deadline: the archive header is
 *    the hero (title + date + location); there is no topic badge or logistics block here.
 *  - sessions-pagination "not visible": pagination was never implemented.
 *  - "back preserves filters": the event-card link is `/archive/<code>` with NO query string,
 *    so the archive filter params are dropped on drill-in and cannot be restored on back.
 *    Filter-preservation through card→detail→back is therefore not a supported behaviour.
 *  - materials-with-file-size: material presence is data-dependent (not every historical
 *    session has uploads); session-materials/material-download testids exist for a future
 *    seeded-data test.
 */

import { test, expect } from '@playwright/test';
import type { Page } from '@playwright/test';

async function openFirstArchivedEvent(page: Page) {
  await page.goto('/archive');
  const firstCard = page.getByTestId('event-card').first();
  await expect(firstCard).toBeVisible();
  await firstCard.click();
  await expect(page).toHaveURL(/\/archive\/BAT/);
  // HomePage refetches the event; the hero title is the readiness signal (avoid networkidle —
  // the homepage has long-lived background activity that never settles).
  await expect(page.getByTestId('event-hero-title')).toBeVisible({ timeout: 15_000 });
}

test.describe('Archive Event Detail', { tag: '@gate' }, () => {
  test('navigates from a card to the event detail page', async ({ page }) => {
    await openFirstArchivedEvent(page);
    await expect(page.getByTestId('back-to-archive')).toBeVisible();
  });

  test('shows the sessions section with at least one session', async ({ page }) => {
    await openFirstArchivedEvent(page);

    const sessions = page.getByTestId('sessions-section');
    await expect(sessions).toBeVisible();

    const firstSession = sessions.getByTestId('session-card').first();
    await expect(firstSession).toBeVisible();
    await expect(firstSession.getByTestId('session-card-title')).toBeVisible();
  });

  test('shows the speaker grid with named speakers', async ({ page }) => {
    await openFirstArchivedEvent(page);

    const grid = page.getByTestId('speaker-grid');
    await expect(grid).toBeVisible();

    const firstSpeaker = grid.getByTestId('speaker-card').first();
    await expect(firstSpeaker).toBeVisible();
    // speaker-name comes from SpeakerDisplay; .first() guards against the rare multi-name card.
    await expect(firstSpeaker.getByTestId('speaker-name').first()).toBeVisible();
  });

  test('returns to the archive list via the back link', async ({ page }) => {
    await openFirstArchivedEvent(page);

    await page.getByTestId('back-to-archive').click();
    await expect(page).toHaveURL(/\/archive(\?.*)?$/);
    await expect(page.getByTestId('event-cards-container')).toBeVisible();
  });

  test('shows a not-found error for a missing event', async ({ page }) => {
    await page.goto('/archive/BAT-NONEXISTENT-999');
    // getEvent 404s; React Query retries (retry: 2) before surfacing the error block.
    await expect(page.getByTestId('event-load-error')).toBeVisible({ timeout: 20_000 });
  });

  test('renders the detail page on a mobile viewport', async ({ page }) => {
    await page.setViewportSize({ width: 375, height: 667 });
    await openFirstArchivedEvent(page);
    await expect(page.getByTestId('sessions-section')).toBeVisible();
  });
});
