/**
 * E2E Tests for Event Type Selection
 * Story 5.1: Event Type Definition
 *
 * STATUS: Story 5.1 EventTypeSelector is IMPLEMENTED
 *
 * Implementation Status:
 * - EventTypeSelector component ✅ IMPLEMENTED (with data-testid)
 * - Event type options ✅ IMPLEMENTED (with data-testid="event-type-option-{type}")
 * - EventForm integration ✅ IMPLEMENTED
 *
 * Requirements:
 * 1. Event Management Service with event types endpoints deployed
 * 2. PostgreSQL database with event_types table
 * 3. EventForm component with EventTypeSelector ✅ IMPLEMENTED
 * 4. SlotTemplatePreview component
 * 5. QuickActions sidebar with Event Types button ✅ IMPLEMENTED
 * 6. EventTypeConfigurationAdmin page
 *
 * Setup Instructions:
 * 1. Run: npx playwright test e2e/organizer/event-type-selection.spec.ts
 *
 * LANGUAGE-INDEPENDENT SELECTORS (BAT-93):
 * ✅ Fixed: "New Event" button → [data-testid="quick-action-new-event"]
 * ✅ Fixed: Event type "Evening Event" → [data-testid="event-type-option-evening"]
 * ✅ Fixed: "Create Event" → button[type="submit"]
 * ✅ EventTypeSelector already has data-testid="event-type-selector"
 * ⚠️  TODO: Some button selectors in test body still use :has-text() and need testid attributes
 */

import { test, expect } from '@playwright/test';
import { BASE_URL, API_URL } from '../../playwright.config';

// Test data - reference values only (NOT used for assertions)
// Event type configurations are user-editable, so tests should NOT assert on specific values
// eslint-disable-next-line @typescript-eslint/no-unused-vars
const EVENT_TYPES = {
  FULL_DAY: {
    type: 'FULL_DAY',
    minSlots: 6,
    maxSlots: 8,
    slotDuration: 45,
    theoreticalSlotsAM: true,
    breakSlots: 2,
    lunchSlots: 1,
    defaultCapacity: 200,
    typicalStartTime: '09:00',
    typicalEndTime: '17:00',
  },
  AFTERNOON: {
    type: 'AFTERNOON',
    minSlots: 6,
    maxSlots: 8,
    slotDuration: 30,
    theoreticalSlotsAM: false,
    breakSlots: 1,
    lunchSlots: 0,
    defaultCapacity: 150,
    typicalStartTime: '13:00',
    typicalEndTime: '18:00',
  },
  EVENING: {
    type: 'EVENING',
    minSlots: 3,
    maxSlots: 4,
    slotDuration: 45,
    theoreticalSlotsAM: false,
    breakSlots: 1,
    lunchSlots: 0,
    defaultCapacity: 100,
    typicalStartTime: '18:00',
    typicalEndTime: '21:00',
  },
};

// Type definitions from OpenAPI spec
interface EventSlotConfigurationResponse {
  type: string;
  minSlots: number;
  maxSlots: number;
  slotDuration: number;
  theoreticalSlotsAM: boolean;
  breakSlots: number;
  lunchSlots: number;
  defaultCapacity: number;
  typicalStartTime?: string;
  typicalEndTime?: string;
}

test.describe('Event Type Selection (Story 5.1)', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/organizer/events');
  });

  test.describe('AC1: Event Type Selector in Event Creation Form', () => {
    test('should display event type selector when creating new event', async ({ page }) => {
      // Navigate to dashboard
      await page.goto(`${BASE_URL}/organizer/events`);

      // Click "New Event" button
      await page.click('[data-testid="quick-action-new-event"]');

      // Wait for EventForm modal to open
      await expect(page.locator('[role="dialog"]')).toBeVisible();

      // Verify EventTypeSelector component exists and is a MUI Select
      const eventTypeSelector = page.locator('[data-testid="event-type-selector"]');
      await expect(eventTypeSelector).toBeVisible();

      // Verify it's a MUI Select component by checking for MuiSelect-root class
      await expect(eventTypeSelector).toHaveClass(/MuiSelect-root/);
    });

    test('should show all three event types in dropdown', async ({ page }) => {
      await page.goto(`${BASE_URL}/organizer/events`);
      await page.click('[data-testid="quick-action-new-event"]');

      // Open event type dropdown
      await page.click('[data-testid="event-type-selector"]');

      // Verify all three event types are present
      await expect(page.locator('[role="option"]:has-text("Full Day Event")')).toBeVisible();
      await expect(page.locator('[role="option"]:has-text("Afternoon Event")')).toBeVisible();
      await expect(
        page.locator('[role="option"][data-testid="event-type-option-evening"]')
      ).toBeVisible();
    });

    test('should display slot configuration details for selected event type', async ({ page }) => {
      await page.goto(`${BASE_URL}/organizer/events`);
      await page.click('[data-testid="quick-action-new-event"]');

      // Select "Full Day Event"
      await page.click('[data-testid="event-type-selector"]');
      await page.click('[role="option"]:has-text("Full Day Event")');

      // Verify SlotTemplatePreview component appears
      const preview = page.locator('[data-testid="slot-template-preview"]');
      await expect(preview).toBeVisible();

      // Verify preview shows correct slot details
      await expect(preview).toContainText('6-8 slots');
      await expect(preview).toContainText('45 minutes');
      await expect(preview).toContainText('09:00');
      await expect(preview).toContainText('17:00');
    });
  });

  test.describe('AC2: Quick Actions Navigation to Event Types Admin', () => {
    test('should display Event Types button in Quick Actions sidebar', async ({ page }) => {
      await page.goto(`${BASE_URL}/organizer/events`);

      // Verify Quick Actions sidebar exists
      const quickActions = page.locator('[data-testid="quick-actions"]');
      await expect(quickActions).toBeVisible();

      // Verify Event Types button exists
      const eventTypesButton = page.locator('button:has-text("Event Types")');
      await expect(eventTypesButton).toBeVisible();
    });

    test('should navigate to Event Types admin page when clicking button', async ({ page }) => {
      await page.goto(`${BASE_URL}/organizer/events`);

      // Click Event Types button
      await page.click('button:has-text("Event Types")');

      // Wait for navigation
      await page.waitForURL(`${BASE_URL}/organizer/event-types`);

      // Verify we're on the Event Types configuration page
      await expect(page.locator('h1')).toContainText('Event Type Configuration');
    });
  });

  test.describe('AC3: Event Types API Integration', () => {
    test('should fetch event types from API on page load', async ({ page }) => {
      // Set up API interception
      const apiRequest = page.waitForRequest(
        (request) => request.url().includes('/api/v1/events/types') && request.method() === 'GET'
      );

      await page.goto(`${BASE_URL}/organizer/events`);
      await page.click('[data-testid="quick-action-new-event"]');

      // Verify API was called
      const request = await apiRequest;
      expect(request.url()).toContain('/api/v1/events/types');
    });

    test('should display loading state while fetching event types', async ({ page }) => {
      // Delay API response to see loading state
      await page.route('**/api/v1/events/types', (route) => {
        setTimeout(() => route.continue(), 1000);
      });

      await page.goto(`${BASE_URL}/organizer/events`);
      await page.click('[data-testid="quick-action-new-event"]');

      // Verify loading indicator appears
      const loading = page.locator('[data-testid="event-type-selector-loading"]');
      await expect(loading).toBeVisible();
    });

    test('should handle API error gracefully', async ({ page }) => {
      // Mock API error
      await page.route('**/api/v1/events/types', (route) => {
        route.fulfill({
          status: 500,
          body: JSON.stringify({
            error: 'INTERNAL_SERVER_ERROR',
            message: 'Failed to fetch event types',
            timestamp: new Date().toISOString(),
          }),
        });
      });

      await page.goto(`${BASE_URL}/organizer/events`);
      await page.click('[data-testid="quick-action-new-event"]');

      // Verify error message is displayed
      await expect(page.locator('[role="alert"]')).toContainText('Failed to load event types');
    });
  });

  test.describe('AC4: Event Types Admin Page (ORGANIZER only)', () => {
    test('should display all three event type configurations', async ({ page }) => {
      await page.goto(`${BASE_URL}/organizer/event-types`);

      // Verify page header
      await expect(page.locator('h1')).toContainText('Event Type Configuration');
      await expect(page.locator('text=/ADMIN ONLY/i')).toBeVisible();

      // Verify all three event type cards are displayed
      await expect(page.locator('[data-testid="event-type-card-FULL_DAY"]')).toBeVisible();
      await expect(page.locator('[data-testid="event-type-card-AFTERNOON"]')).toBeVisible();
      await expect(page.locator('[data-testid="event-type-card-EVENING"]')).toBeVisible();
    });

    test('should allow editing event type configuration', async ({ page }) => {
      await page.goto(`${BASE_URL}/organizer/event-types`);

      // Click edit button for Full Day event type
      await page.click('[data-testid="edit-event-type-FULL_DAY"]');

      // Verify edit modal opens
      await expect(page.locator('[role="dialog"]')).toBeVisible();
      await expect(page.locator('h2')).toContainText('Edit Full Day Event Configuration');

      // Verify all fields are editable (values are user-configurable)
      const minSlotsInput = page.locator('input[name="minSlots"]');
      const maxSlotsInput = page.locator('input[name="maxSlots"]');
      const slotDurationInput = page.locator('input[name="slotDuration"]');

      await expect(minSlotsInput).toBeVisible();
      await expect(maxSlotsInput).toBeVisible();
      await expect(slotDurationInput).toBeVisible();

      // Verify values are numeric and valid
      const minSlots = parseInt(await minSlotsInput.inputValue());
      const maxSlots = parseInt(await maxSlotsInput.inputValue());
      const slotDuration = parseInt(await slotDurationInput.inputValue());

      expect(minSlots).toBeGreaterThan(0);
      expect(maxSlots).toBeGreaterThanOrEqual(minSlots);
      expect(slotDuration).toBeGreaterThanOrEqual(15);
    });

    test('should show live preview when editing configuration', async ({ page }) => {
      await page.goto(`${BASE_URL}/organizer/event-types`);
      await page.click('[data-testid="edit-event-type-FULL_DAY"]');

      // Modify slot duration
      await page.fill('input[name="slotDuration"]', '60');

      // Verify preview updates
      const preview = page.locator('[data-testid="configuration-preview"]');
      await expect(preview).toContainText('60 minutes');
    });
  });

  test.describe('AC5: Back Button Navigation', () => {
    test('should navigate back to dashboard from Event Types admin', async ({ page }) => {
      await page.goto(`${BASE_URL}/organizer/event-types`);

      // Click back button
      await page.click('button:has-text("Back to Dashboard")');

      // Verify navigation back to dashboard
      await page.waitForURL(`${BASE_URL}/organizer/events`);
      await expect(page.locator('h1')).toContainText('Event Management Dashboard');
    });
  });
});

test.describe('Event Types API Contract Tests (Story 5.1)', () => {
  test.describe('GET /api/v1/events/types', () => {
    test('should return all three event types', async ({ request }) => {
      const response = await request.get(`${API_URL}/api/v1/events/types`);

      expect(response.status()).toBe(200);

      const eventTypes: EventSlotConfigurationResponse[] = await response.json();
      expect(eventTypes).toHaveLength(3);

      // Verify structure matches OpenAPI spec (values are user-configurable)
      const fullDay = eventTypes.find((et) => et.type === 'FULL_DAY');
      expect(fullDay).toBeDefined();

      // After toBeDefined(), we can safely assert fullDay exists
      expect(fullDay!.minSlots).toBeGreaterThan(0);
      expect(fullDay!.maxSlots).toBeGreaterThanOrEqual(fullDay!.minSlots);
      expect(fullDay!.slotDuration).toBeGreaterThanOrEqual(15);
    });
  });

  test.describe('GET /api/v1/events/types/{type}', () => {
    test('should return specific event type configuration', async ({ request }) => {
      const response = await request.get(`${API_URL}/api/v1/events/types/FULL_DAY`);

      expect(response.status()).toBe(200);

      const eventType: EventSlotConfigurationResponse = await response.json();
      expect(eventType.type).toBe('FULL_DAY');
      expect(eventType.minSlots).toBeGreaterThan(0);
      expect(eventType.maxSlots).toBeGreaterThanOrEqual(eventType.minSlots);
    });

    test('should return 404 for non-existent event type', async ({ request }) => {
      const response = await request.get(`${API_URL}/api/v1/events/types/INVALID_TYPE`);

      expect(response.status()).toBe(404);
    });
  });

  test.describe('PUT /api/v1/events/types/{type}', () => {
    test('should update event type configuration (ORGANIZER only)', async ({ request }) => {
      // Note: This test requires authentication headers
      const response = await request.put(`${API_URL}/api/v1/events/types/FULL_DAY`, {
        data: {
          minSlots: 7,
          maxSlots: 9,
          slotDuration: 50,
          theoreticalSlotsAM: true,
          breakSlots: 2,
          lunchSlots: 1,
          defaultCapacity: 220,
          typicalStartTime: '09:00',
          typicalEndTime: '17:30',
        },
        headers: {
          Authorization: `Bearer ${process.env.E2E_TEST_TOKEN}`,
        },
      });

      expect(response.status()).toBe(200);

      const updated: EventSlotConfigurationResponse = await response.json();
      expect(updated.minSlots).toBe(7);
      expect(updated.maxSlots).toBe(9);
    });

    test('should return 403 without ORGANIZER role', async ({ request }) => {
      const response = await request.put(`${API_URL}/api/v1/events/types/FULL_DAY`, {
        data: {
          minSlots: 7,
          maxSlots: 9,
          slotDuration: 50,
          theoreticalSlotsAM: true,
          breakSlots: 2,
          lunchSlots: 1,
          defaultCapacity: 220,
        },
      });

      expect(response.status()).toBe(403);
    });

    test('should return 400 for invalid data (minSlots > maxSlots)', async ({ request }) => {
      const response = await request.put(`${API_URL}/api/v1/events/types/FULL_DAY`, {
        data: {
          minSlots: 10,
          maxSlots: 5, // Invalid: minSlots > maxSlots
          slotDuration: 45,
          theoreticalSlotsAM: true,
          breakSlots: 2,
          lunchSlots: 1,
          defaultCapacity: 200,
        },
        headers: {
          Authorization: `Bearer ${process.env.E2E_TEST_TOKEN}`,
        },
      });

      expect(response.status()).toBe(400);
    });
  });
});
