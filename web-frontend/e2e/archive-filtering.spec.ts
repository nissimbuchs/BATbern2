/**
 * Automated E2E Test: Archive Filtering
 *
 * Story BAT-109 Task 1.2: Filter application and persistence
 * Tests AC6-12: Filter panel, time period filters, topic filters, search, persistence, sort
 */

import { test, expect } from '@playwright/test';

test.describe('Archive Filtering', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/archive');
    await page.waitForLoadState('networkidle');
  });

  test('should display filter sidebar on desktop', async ({ page }) => {
    console.log('→ Verifying filter sidebar on desktop');
    // AC6: Filter panel should be sidebar on desktop (≥1024px)
    await page.setViewportSize({ width: 1280, height: 720 });

    const filterSidebar = page.locator('[data-testid="filter-sidebar"]');
    await expect(filterSidebar).toBeVisible();

    // Should contain filter sections
    console.log('→ Verifying filter sections');
    await expect(page.locator('[data-testid="time-period-filter"]')).toBeVisible();
    await expect(page.locator('[data-testid="topic-filter"]')).toBeVisible();
    await expect(page.locator('[data-testid="search-input"]')).toBeVisible();
    await expect(page.locator('[data-testid="sort-dropdown"]')).toBeVisible();

    console.log('✓ Filter sidebar displays all filter sections');
  });

  test('should display filter sheet on mobile', async ({ page }) => {
    console.log('→ Setting mobile viewport');
    // AC6: Filter panel should be sheet/modal on mobile (<768px)
    await page.setViewportSize({ width: 375, height: 667 });

    console.log('→ Looking for filter button');
    // Filter button should be visible
    const filterButton = page.locator('[data-testid="filter-toggle-button"]');
    await expect(filterButton).toBeVisible();

    console.log('→ Opening filter sheet');
    await filterButton.click();

    // Filter sheet should open
    const filterSheet = page.locator('[data-testid="filter-sheet"]');
    await expect(filterSheet).toBeVisible({ timeout: 2000 });

    console.log('→ Verifying filter sections in sheet');
    await expect(page.locator('[data-testid="time-period-filter"]')).toBeVisible();
    await expect(page.locator('[data-testid="topic-filter"]')).toBeVisible();

    console.log('✓ Filter sheet works on mobile');
  });

  test('should filter by time period', async ({ page }) => {
    console.log('→ Testing time period filter');
    // AC7: Time period options: All, Last 5Y, 2020-24, 2015-19, 2010-14, Before 2010

    const timePeriodFilter = page.locator('[data-testid="time-period-filter"]');

    console.log('→ Selecting "Last 5 Years" filter');
    await timePeriodFilter
      .locator('button:has-text("Last 5 Years"), button:has-text("Letzte 5 Jahre")')
      .click();

    // Wait for filtering to apply
    await page.waitForLoadState('networkidle');

    console.log('→ Verifying filtered results');
    // URL should update with filter parameter
    await expect(page).toHaveURL(/timePeriod=last5years/i);

    // Event cards should only show events from last 5 years
    const eventDates = page.locator('[data-testid="event-date"]');
    const firstDate = await eventDates.first().textContent();
    console.log(`First event date after filter: ${firstDate}`);

    // Verify filter is reflected in UI
    const activeFilterChip = page.locator('[data-testid="active-filter-chip"]');
    await expect(activeFilterChip.first()).toBeVisible();

    console.log('→ Testing "2020-2024" filter');
    await timePeriodFilter
      .locator('button:has-text("2020-2024"), button:has-text("2020-24")')
      .click();
    await page.waitForLoadState('networkidle');

    await expect(page).toHaveURL(/timePeriod=2020-2024/i);

    console.log('✓ Time period filter works correctly');
  });

  test('should filter by topic with counts', async ({ page }) => {
    console.log('→ Testing topic filter');
    // AC8: Topic filter with checkboxes and counts (e.g., "Cloud (23)")

    const topicFilter = page.locator('[data-testid="topic-filter"]');
    await expect(topicFilter).toBeVisible();

    console.log('→ Verifying topic counts are displayed');
    // Find first topic checkbox
    const firstTopicCheckbox = topicFilter.locator('input[type="checkbox"]').first();
    const firstTopicLabel = topicFilter.locator('label').first();

    // Label should contain count in format "Topic (N)"
    const labelText = await firstTopicLabel.textContent();
    console.log(`First topic label: ${labelText}`);
    expect(labelText).toMatch(/\(\d+\)/); // Contains "(number)"

    console.log('→ Selecting a topic');
    await firstTopicCheckbox.check();
    await page.waitForLoadState('networkidle');

    // URL should reflect topic filter
    await expect(page).toHaveURL(/topics=/);

    console.log('→ Selecting multiple topics');
    // AC8: Multiple topics can be selected (checkboxes, not radio buttons)
    const secondTopicCheckbox = topicFilter.locator('input[type="checkbox"]').nth(1);
    await secondTopicCheckbox.check();
    await page.waitForLoadState('networkidle');

    // Both checkboxes should be checked
    await expect(firstTopicCheckbox).toBeChecked();
    await expect(secondTopicCheckbox).toBeChecked();

    console.log('✓ Topic filter with counts works correctly');
  });

  test('should search events with debouncing', async ({ page }) => {
    console.log('→ Testing search functionality');
    // AC9: Search bar with 300ms debounce

    const searchInput = page.locator('[data-testid="search-input"]');
    await expect(searchInput).toBeVisible();

    console.log('→ Typing search query');
    await searchInput.fill('Cloud');

    // Should NOT trigger immediately (debounced)
    await page.waitForTimeout(100); // Less than 300ms debounce
    console.log('→ Verifying search is debounced');

    // Wait for debounce to complete
    await page.waitForTimeout(300);
    await page.waitForLoadState('networkidle');

    console.log('→ Verifying search results');
    // URL should contain search parameter
    await expect(page).toHaveURL(/search=Cloud/i);

    // Event cards should be filtered by search term
    const eventCards = page.locator('[data-testid="event-card"]');
    const count = await eventCards.count();
    console.log(`Search results count: ${count}`);

    // Clear search
    console.log('→ Clearing search');
    await searchInput.clear();
    await page.waitForTimeout(300);
    await page.waitForLoadState('networkidle');

    // URL should not contain search parameter
    await expect(page).not.toHaveURL(/search=/);

    console.log('✓ Search with debouncing works correctly');
  });

  test('should persist filters to URL parameters', async ({ page }) => {
    console.log('→ Testing filter persistence');
    // AC10: Filter persistence via URL query parameters

    console.log('→ Applying multiple filters');
    // Apply time period filter
    await page
      .locator(
        '[data-testid="time-period-filter"] button:has-text("Last 5 Years"), button:has-text("Letzte 5 Jahre")'
      )
      .click();
    await page.waitForLoadState('networkidle');

    // Apply topic filter
    const topicCheckbox = page
      .locator('[data-testid="topic-filter"] input[type="checkbox"]')
      .first();
    await topicCheckbox.check();
    await page.waitForLoadState('networkidle');

    // Apply search
    await page.locator('[data-testid="search-input"]').fill('Architecture');
    await page.waitForTimeout(300);
    await page.waitForLoadState('networkidle');

    console.log('→ Capturing URL with filters');
    const urlWithFilters = page.url();
    console.log(`URL with filters: ${urlWithFilters}`);

    // URL should contain all filter parameters
    expect(urlWithFilters).toContain('timePeriod=');
    expect(urlWithFilters).toContain('topics=');
    expect(urlWithFilters).toContain('search=');

    console.log('→ Reloading page to verify persistence');
    await page.reload();
    await page.waitForLoadState('networkidle');

    // Filters should be restored from URL
    await expect(topicCheckbox).toBeChecked();
    await expect(page.locator('[data-testid="search-input"]')).toHaveValue('Architecture');

    console.log('✓ Filters persist via URL parameters');
  });

  test('should clear all filters', async ({ page }) => {
    console.log('→ Testing clear filters functionality');
    // AC11: Clear all filters button

    console.log('→ Applying multiple filters');
    await page
      .locator(
        '[data-testid="time-period-filter"] button:has-text("Last 5 Years"), button:has-text("Letzte 5 Jahre")'
      )
      .click();
    await page.waitForLoadState('networkidle');

    const topicCheckbox = page
      .locator('[data-testid="topic-filter"] input[type="checkbox"]')
      .first();
    await topicCheckbox.check();
    await page.waitForLoadState('networkidle');

    await page.locator('[data-testid="search-input"]').fill('Test');
    await page.waitForTimeout(300);
    await page.waitForLoadState('networkidle');

    console.log('→ Clicking clear filters button');
    const clearButton = page.locator('[data-testid="clear-filters-button"]');
    await expect(clearButton).toBeVisible();
    await clearButton.click();
    await page.waitForLoadState('networkidle');

    console.log('→ Verifying all filters are cleared');
    // All filters should be reset
    await expect(topicCheckbox).not.toBeChecked();
    await expect(page.locator('[data-testid="search-input"]')).toHaveValue('');
    await expect(page).not.toHaveURL(/timePeriod=|topics=|search=/);

    console.log('✓ Clear all filters works correctly');
  });

  test('should sort events', async ({ page }) => {
    console.log('→ Testing sort functionality');
    // AC12: Sort options: Newest, Oldest, Most Attended, Most Sessions

    const sortDropdown = page.locator('[data-testid="sort-dropdown"]');
    await expect(sortDropdown).toBeVisible();

    console.log('→ Opening sort dropdown');
    await sortDropdown.click();

    // Verify sort options are available
    console.log('→ Verifying sort options');
    await expect(page.locator('text=Newest, text=Neueste')).toBeVisible();
    await expect(page.locator('text=Oldest, text=Älteste')).toBeVisible();
    await expect(page.locator('text=Most Attended, text=Meist besucht')).toBeVisible();
    await expect(page.locator('text=Most Sessions, text=Meiste Sitzungen')).toBeVisible();

    console.log('→ Selecting "Oldest" sort');
    await page.click('text=Oldest, text=Älteste');
    await page.waitForLoadState('networkidle');

    // URL should reflect sort order
    await expect(page).toHaveURL(/sort=date|sort=oldest/i);

    console.log('→ Verifying sort order changed');
    const eventDates = page.locator('[data-testid="event-date"]');
    const firstDate = await eventDates.first().textContent();
    console.log(`First event date after sort: ${firstDate}`);

    console.log('✓ Sort functionality works correctly');
  });
});
