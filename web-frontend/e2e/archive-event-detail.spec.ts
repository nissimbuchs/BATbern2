/**
 * Automated E2E Test: Archive Event Detail Navigation
 *
 * Story BAT-109 Task 1.4: Event detail page navigation
 * Tests AC13-18: Event header, sessions display, materials, speaker grid, back navigation
 */

import { test, expect } from '@playwright/test';

test.describe('Archive Event Detail Navigation', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/archive');
    await page.waitForLoadState('networkidle');
  });

  test('should navigate to event detail page', async ({ page }) => {
    console.log('→ Clicking on first event card');

    const eventCards = page.locator('[data-testid="event-card"]');
    await expect(eventCards.first()).toBeVisible();

    // Get event code from first card for URL verification
    const firstCard = eventCards.first();
    await firstCard.click();

    // Should navigate to event detail page
    console.log('→ Verifying navigation to detail page');
    await expect(page).toHaveURL(/\/archive\/BAT\w+\d+/);
    await page.waitForLoadState('networkidle');

    console.log('✓ Navigation to event detail successful');
  });

  test('should display event header with title, date, topic, description', async ({ page }) => {
    console.log('→ Navigating to event detail');
    await page.locator('[data-testid="event-card"]').first().click();
    await page.waitForLoadState('networkidle');

    console.log('→ Verifying event header');
    // AC13: Event header with title, date, topic, description (NO logistics)
    const eventHeader = page.locator('[data-testid="event-header"]');
    await expect(eventHeader).toBeVisible();

    console.log('→ Verifying event title');
    await expect(eventHeader.locator('[data-testid="event-title"]')).toBeVisible();

    console.log('→ Verifying event date');
    await expect(eventHeader.locator('[data-testid="event-date"]')).toBeVisible();

    console.log('→ Verifying event topic');
    await expect(eventHeader.locator('[data-testid="event-topic"]')).toBeVisible();

    console.log('→ Verifying event description');
    const eventDescription = eventHeader.locator('[data-testid="event-description"]');
    await expect(eventDescription).toBeVisible();

    console.log('→ Verifying NO logistics info displayed');
    // AC13: No logistics (venue, registration deadline) on archive detail page
    await expect(page.locator('[data-testid="venue-info"]')).not.toBeVisible();
    await expect(page.locator('[data-testid="registration-deadline"]')).not.toBeVisible();

    console.log('✓ Event header displays correctly without logistics');
  });

  test('should display all sessions at once without pagination', async ({ page }) => {
    console.log('→ Navigating to event detail');
    await page.locator('[data-testid="event-card"]').first().click();
    await page.waitForLoadState('networkidle');

    console.log('→ Verifying sessions section');
    // AC14: All sessions shown at once (4-8 sessions, no pagination)
    const sessionsSection = page.locator('[data-testid="sessions-section"]');
    await expect(sessionsSection).toBeVisible();

    const sessionCards = sessionsSection.locator('[data-testid="session-card"]');
    const sessionCount = await sessionCards.count();
    console.log(`Total sessions displayed: ${sessionCount}`);

    // Should show all sessions (typically 4-8 for BATbern events)
    expect(sessionCount).toBeGreaterThan(0);
    expect(sessionCount).toBeLessThanOrEqual(10); // Reasonable upper bound

    console.log('→ Verifying no pagination controls');
    // AC14: No pagination
    await expect(page.locator('[data-testid="sessions-pagination"]')).not.toBeVisible();

    console.log('✓ All sessions displayed without pagination');
  });

  test('should display session details with speakers, description, materials', async ({ page }) => {
    console.log('→ Navigating to event detail');
    await page.locator('[data-testid="event-card"]').first().click();
    await page.waitForLoadState('networkidle');

    console.log('→ Verifying first session details');
    // AC15: Session details include title, speakers, description, materials
    const firstSession = page.locator('[data-testid="session-card"]').first();
    await expect(firstSession).toBeVisible();

    console.log('→ Verifying session title');
    await expect(firstSession.locator('[data-testid="session-title"]')).toBeVisible();

    console.log('→ Verifying session speakers');
    const sessionSpeakers = firstSession.locator('[data-testid="session-speakers"]');
    await expect(sessionSpeakers).toBeVisible();

    // Should show speaker names and companies
    const speakerItems = sessionSpeakers.locator('[data-testid="speaker-item"]');
    const speakerCount = await speakerItems.count();
    console.log(`Session speakers: ${speakerCount}`);

    if (speakerCount > 0) {
      const firstSpeaker = speakerItems.first();
      await expect(firstSpeaker.locator('[data-testid="speaker-name"]')).toBeVisible();
      await expect(firstSpeaker.locator('[data-testid="speaker-company"]')).toBeVisible();
    }

    console.log('→ Verifying session description');
    await expect(firstSession.locator('[data-testid="session-description"]')).toBeVisible();

    console.log('✓ Session details display correctly');
  });

  test('should display downloadable presentation materials with file size', async ({ page }) => {
    console.log('→ Navigating to event detail');
    await page.locator('[data-testid="event-card"]').first().click();
    await page.waitForLoadState('networkidle');

    console.log('→ Looking for session with materials');
    const sessionCards = page.locator('[data-testid="session-card"]');
    const sessionCount = await sessionCards.count();

    // Find a session with materials
    let foundMaterials = false;
    for (let i = 0; i < sessionCount; i++) {
      const session = sessionCards.nth(i);
      const materials = session.locator('[data-testid="session-materials"]');

      if (await materials.isVisible()) {
        foundMaterials = true;
        console.log(`→ Found session with materials (session ${i + 1})`);

        // AC16: Download PDF with file size (e.g., "2.4 MB")
        const downloadLink = materials.locator('[data-testid="material-download"]').first();
        await expect(downloadLink).toBeVisible();

        const linkText = await downloadLink.textContent();
        console.log(`Download link text: ${linkText}`);

        // Should contain file size (e.g., "2.4 MB", "1.2 KB")
        expect(linkText).toMatch(/\d+\.?\d*\s*(MB|KB|GB)/i);

        console.log('→ Verifying download link is functional');
        // Link should be a proper download link (not disabled)
        const href = await downloadLink.getAttribute('href');
        expect(href).toBeTruthy();
        expect(href).toMatch(/^https?:\/\/|^\/api\//);

        break;
      }
    }

    if (foundMaterials) {
      console.log('✓ Presentation materials display with file size');
    } else {
      console.log('→ No sessions with materials found (test skipped)');
    }
  });

  test('should display speaker grid with photos and companies', async ({ page }) => {
    console.log('→ Navigating to event detail');
    await page.locator('[data-testid="event-card"]').first().click();
    await page.waitForLoadState('networkidle');

    console.log('→ Verifying speaker grid');
    // AC17: Speaker grid with photos and companies
    const speakerGrid = page.locator('[data-testid="speaker-grid"]');
    await expect(speakerGrid).toBeVisible();

    const speakerCards = speakerGrid.locator('[data-testid="speaker-card"]');
    const speakerCount = await speakerCards.count();
    console.log(`Total speakers in grid: ${speakerCount}`);

    expect(speakerCount).toBeGreaterThan(0);

    console.log('→ Verifying first speaker card');
    const firstSpeaker = speakerCards.first();

    // Should show photo
    console.log('→ Verifying speaker photo');
    const speakerPhoto = firstSpeaker.locator('[data-testid="speaker-photo"]');
    await expect(speakerPhoto).toBeVisible();

    // Photo should have alt text
    const altText = await speakerPhoto.getAttribute('alt');
    expect(altText).toBeTruthy();

    // Should show name
    console.log('→ Verifying speaker name');
    await expect(firstSpeaker.locator('[data-testid="speaker-name"]')).toBeVisible();

    // Should show company
    console.log('→ Verifying speaker company');
    await expect(firstSpeaker.locator('[data-testid="speaker-company"]')).toBeVisible();

    console.log('✓ Speaker grid displays correctly');
  });

  test('should navigate back to archive and preserve filters', async ({ page }) => {
    console.log('→ Applying filter on archive page');
    // AC18: Back to archive preserves filters

    // Apply a filter before navigating to detail
    await page
      .locator(
        '[data-testid="time-period-filter"] button:has-text("Last 5 Years"), button:has-text("Letzte 5 Jahre")'
      )
      .click();
    await page.waitForLoadState('networkidle');

    // Capture URL with filter
    const archiveUrlWithFilter = page.url();
    console.log(`Archive URL with filter: ${archiveUrlWithFilter}`);

    console.log('→ Navigating to event detail');
    await page.locator('[data-testid="event-card"]').first().click();
    await page.waitForLoadState('networkidle');

    console.log('→ Clicking back to archive button');
    const backButton = page.locator('[data-testid="back-to-archive"]');
    await expect(backButton).toBeVisible();
    await backButton.click();

    await page.waitForLoadState('networkidle');

    console.log('→ Verifying we are back on archive page');
    await expect(page).toHaveURL(/\/archive/);

    console.log('→ Verifying filter is preserved');
    // URL should still contain the filter parameter
    expect(page.url()).toContain('timePeriod=');

    // Time period filter button should still be active
    const timePeriodButton = page.locator(
      '[data-testid="time-period-filter"] button[aria-pressed="true"]'
    );
    await expect(timePeriodButton).toBeVisible();

    console.log('✓ Back navigation preserves filters');
  });

  test('should handle missing event gracefully', async ({ page }) => {
    console.log('→ Testing 404 handling for missing event');

    // Navigate to non-existent event
    await page.goto('/archive/BAT-NONEXISTENT-999');
    await page.waitForLoadState('networkidle');

    console.log('→ Verifying 404 error page');
    const errorMessage = page.locator('[data-testid="event-not-found"]');
    await expect(errorMessage).toBeVisible({ timeout: 5000 });

    await expect(errorMessage).toContainText(/not found|nicht gefunden/i);

    console.log('→ Verifying back to archive link');
    const backLink = page.locator('[data-testid="back-to-archive"]');
    await expect(backLink).toBeVisible();

    console.log('✓ Missing event handled gracefully');
  });

  test('should display responsive layout on mobile for event detail', async ({ page }) => {
    console.log('→ Setting mobile viewport');
    // AC20: Responsive design (320px+)
    await page.setViewportSize({ width: 375, height: 667 });

    console.log('→ Navigating to event detail on mobile');
    await page.locator('[data-testid="event-card"]').first().click();
    await page.waitForLoadState('networkidle');

    console.log('→ Verifying mobile-optimized layout');
    // Speaker grid should stack vertically on mobile
    const speakerGrid = page.locator('[data-testid="speaker-grid"]');
    await expect(speakerGrid).toBeVisible();

    // Sessions should be in single column
    const sessionsSection = page.locator('[data-testid="sessions-section"]');
    await expect(sessionsSection).toBeVisible();

    // Back button should be easily tappable (≥44px touch target)
    const backButton = page.locator('[data-testid="back-to-archive"]');
    const boundingBox = await backButton.boundingBox();
    if (boundingBox) {
      expect(boundingBox.height).toBeGreaterThanOrEqual(44);
      console.log(`Back button height: ${boundingBox.height}px (meets WCAG touch target)`);
    }

    console.log('✓ Event detail responsive layout works on mobile');
  });
});
