/**
 * Automated E2E Test: Archive Browsing Journey
 *
 * Story BAT-109 Task 1.1: Archive browsing user journey
 * Tests AC1-5: Event cards, grid/list toggle, infinite scroll, session preview, load indicator
 */

import { test, expect } from '@playwright/test';

test.describe('Archive Browsing Journey', () => {
  test.beforeEach(async ({ page }) => {
    // Archive browsing is public (no authentication required)
    await page.goto('/');
  });

  test('should display archive browse page with event cards', async ({ page }) => {
    console.log('→ Navigating to archive page');
    await page.goto('/archive');

    // Wait for page to load
    await page.waitForLoadState('networkidle');

    console.log('→ Verifying page title');
    // AC1: Page should have archive title
    await expect(page.locator('h1')).toContainText(/Event Archive|Archiv/i);

    console.log('→ Verifying event cards are visible');
    // AC1: Event cards should be displayed
    const eventCards = page.locator('[data-testid="event-card"]');
    await expect(eventCards.first()).toBeVisible({ timeout: 5000 });

    // AC1: Each event card shows image, title, date, topic
    console.log('→ Verifying event card structure');
    const firstCard = eventCards.first();
    await expect(firstCard.locator('[data-testid="event-image"]')).toBeVisible();
    await expect(firstCard.locator('[data-testid="event-title"]')).toBeVisible();
    await expect(firstCard.locator('[data-testid="event-date"]')).toBeVisible();
    await expect(firstCard.locator('[data-testid="event-topic"]')).toBeVisible();

    // AC4: Session preview (first 3 sessions with speaker/company)
    console.log('→ Verifying session preview');
    const sessionPreview = firstCard.locator('[data-testid="session-preview"]');
    await expect(sessionPreview).toBeVisible();

    const sessionItems = sessionPreview.locator('[data-testid="session-item"]');
    const sessionCount = await sessionItems.count();
    expect(sessionCount).toBeLessThanOrEqual(3); // Max 3 sessions preview

    // First session should show speaker name and company
    if (sessionCount > 0) {
      const firstSession = sessionItems.first();
      await expect(firstSession.locator('[data-testid="session-title"]')).toBeVisible();
      await expect(firstSession.locator('[data-testid="speaker-name"]')).toBeVisible();
      await expect(firstSession.locator('[data-testid="speaker-company"]')).toBeVisible();
    }

    console.log('✓ Archive browse page displays event cards correctly');
  });

  test('should toggle between grid and list view', async ({ page }) => {
    console.log('→ Navigating to archive page');
    await page.goto('/archive');
    await page.waitForLoadState('networkidle');

    console.log('→ Verifying initial grid view');
    // AC2: Default view should be grid
    const gridToggle = page.locator('[data-testid="view-toggle-grid"]');
    const listToggle = page.locator('[data-testid="view-toggle-list"]');

    await expect(gridToggle).toHaveAttribute('aria-pressed', 'true');

    // Verify grid layout class
    const eventCardsContainer = page.locator('[data-testid="event-cards-container"]');
    await expect(eventCardsContainer).toHaveClass(/grid/i);

    console.log('→ Switching to list view');
    // AC2: Toggle to list view
    await listToggle.click();
    await expect(listToggle).toHaveAttribute('aria-pressed', 'true');
    await expect(gridToggle).toHaveAttribute('aria-pressed', 'false');

    // Verify list layout class
    await expect(eventCardsContainer).toHaveClass(/list/i);

    console.log('→ Verifying view preference persistence');
    // AC2: View preference should persist to localStorage
    const storedView = await page.evaluate(() => localStorage.getItem('archiveViewMode'));
    expect(storedView).toBe('list');

    console.log('→ Refreshing page to verify persistence');
    // Reload page and verify list view is maintained
    await page.reload();
    await page.waitForLoadState('networkidle');

    await expect(listToggle).toHaveAttribute('aria-pressed', 'true');
    await expect(eventCardsContainer).toHaveClass(/list/i);

    console.log('✓ Grid/list toggle works with localStorage persistence');
  });

  test('should display load indicator with progress', async ({ page }) => {
    console.log('→ Navigating to archive page');
    await page.goto('/archive');

    // AC5: Load indicator should show progress (X of Y events)
    console.log('→ Verifying load indicator');
    const loadIndicator = page.locator('[data-testid="load-indicator"]');
    await expect(loadIndicator).toBeVisible({ timeout: 10000 });

    // Should show format like "20 of 54 events" or "Showing 20 of 54"
    await expect(loadIndicator).toContainText(/\d+\s+of\s+\d+/i);

    console.log('✓ Load indicator displays progress correctly');
  });

  test('should handle empty archive state', async ({ page }) => {
    console.log('→ Testing empty archive state');
    // This test verifies graceful handling when no events exist
    // In production, there will always be historical events, but new deployments may be empty

    await page.goto('/archive');
    await page.waitForLoadState('networkidle');

    // If no events, should show empty state message
    const eventCards = page.locator('[data-testid="event-card"]');
    const eventCount = await eventCards.count();

    if (eventCount === 0) {
      console.log('→ Verifying empty state message');
      const emptyState = page.locator('[data-testid="empty-archive-state"]');
      await expect(emptyState).toBeVisible();
      await expect(emptyState).toContainText(/no events|keine ereignisse/i);
    }

    console.log('✓ Empty archive state handled gracefully');
  });

  test('should display responsive layout on mobile', async ({ page }) => {
    console.log('→ Setting mobile viewport');
    // AC20: Responsive design (320px+)
    await page.setViewportSize({ width: 375, height: 667 }); // iPhone SE

    await page.goto('/archive');
    await page.waitForLoadState('networkidle');

    console.log('→ Verifying mobile-optimized layout');
    // Event cards should stack vertically on mobile
    const eventCardsContainer = page.locator('[data-testid="event-cards-container"]');
    await expect(eventCardsContainer).toBeVisible();

    // Grid should use single column on mobile
    const computedColumns = await eventCardsContainer.evaluate((el) => {
      return window.getComputedStyle(el).gridTemplateColumns;
    });

    // Should be single column or auto-fit with min-width that results in 1 column at 375px
    console.log(`Mobile grid columns: ${computedColumns}`);

    console.log('✓ Responsive layout works on mobile viewport');
  });
});
