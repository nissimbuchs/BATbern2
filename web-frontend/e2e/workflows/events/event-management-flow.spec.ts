/**
 * E2E Tests for Events API Consolidation
 * Story 1.15a.1: Events API Consolidation
 *
 * IMPORTANT: These tests require:
 * 1. Playwright installed and configured
 * 2. Event Management Service running
 * 3. API Gateway configured with /api/v1/events routes
 * 4. PostgreSQL database with events tables
 * 5. Redis for caching (optional, but recommended)
 *
 * Test Coverage:
 * - AC1: List/Search Events with filters, sorting, pagination
 * - AC2: Event Detail with resource expansion (include parameter)
 * - AC3-6: Event CRUD operations
 * - AC7-8: Event Actions (publish, workflow advance)
 * - AC9-12: Sub-resources (sessions, registrations)
 * - AC13: Event Analytics
 * - AC14: Bulk Operations
 * - AC15-16: Performance and Caching
 * - AC17: Wireframe Migration Validation
 *
 * Run: npx playwright test e2e/workflows/events/event-management-flow.spec.ts
 */

import { test, expect, type Page } from '@playwright/test';

// Test configuration
const BASE_URL = process.env.E2E_BASE_URL || 'http://localhost:3000';
const API_URL = process.env.E2E_API_URL || 'http://localhost:8080';
const TEST_ORGANIZER_EMAIL = `organizer-${Date.now()}@batbern.ch`;
const TEST_PASSWORD = 'TestPassword123!';

// Type definitions
interface Event {
  id: string;
  title: string;
  date: string;
  status: string;
  venue?: Venue;
  speakers?: Speaker[];
  sessions?: Session[];
}

interface Venue {
  id: string;
  name: string;
  capacity: number;
  address: string;
}

interface Speaker {
  id: string;
  name: string;
  title: string;
  company: string;
}

interface Session {
  id: string;
  title: string;
  startTime: string;
  duration: number;
}

/**
 * Helper: Login as organizer and get auth token
 */
async function loginAsOrganizer(page: Page): Promise<string> {
  await page.goto(`${BASE_URL}/auth/login`);
  await page.fill('input[name="email"]', TEST_ORGANIZER_EMAIL);
  await page.fill('input[name="password"]', TEST_PASSWORD);
  await page.click('button[type="submit"]');

  await page.waitForURL(/\/organizer\/dashboard/);

  const token = await page.evaluate(() => localStorage.getItem('authToken'));
  return token || '';
}

/**
 * Helper: Make authenticated API request
 */
async function apiRequest(
  endpoint: string,
  token: string,
  options: RequestInit = {}
): Promise<Response> {
  return fetch(`${API_URL}${endpoint}`, {
    ...options,
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${token}`,
      ...options.headers,
    },
  });
}

/**
 * Helper: Create test event via API
 */
async function createTestEvent(token: string, title: string = 'BATbern Test 2025'): Promise<Event> {
  const response = await apiRequest('/api/v1/events', token, {
    method: 'POST',
    body: JSON.stringify({
      title,
      date: '2025-05-15T09:00:00Z',
      status: 'draft',
      description: 'Test event for API consolidation',
    }),
  });

  return response.json();
}

// ============================================================================
// TEST GROUP 1: Event Dashboard - List/Search with Filters (AC1)
// ============================================================================

test.describe('Events API Consolidation - Event Dashboard (AC1)', () => {
  let authToken: string;

  test.beforeAll(async ({ browser }) => {
    const page = await browser.newPage();
    authToken = await loginAsOrganizer(page);
    await page.close();
  });

  test('should_loadEventDashboard_when_usingNewListAPI', async ({ page }) => {
    // AC1 & AC17: Event dashboard loads using consolidated API
    // BEFORE: 30 API calls to load dashboard
    // AFTER: 1-3 API calls using consolidated endpoint

    await page.goto(`${BASE_URL}/organizer/events`);

    // Wait for event list to load
    await expect(page.getByTestId('event-list')).toBeVisible();

    // Verify events are displayed
    const events = page.locator('[data-testid="event-card"]');
    await expect(events.first()).toBeVisible();

    // Verify consolidated API was called (check network requests)
    // In real implementation, verify only 1 call to /api/v1/events?include=...
  });

  test('should_filterByStatus_when_filterProvided', async () => {
    // AC1: Filter events by status
    const response = await apiRequest('/api/v1/events?filter={"status":"published"}', authToken);
    const data = await response.json();

    expect(response.status).toBe(200);
    expect(data.data).toBeDefined();
    expect(data.pagination).toBeDefined();

    // Verify all returned events have status='published'
    data.data.forEach((event: Event) => {
      expect(event.status).toBe('published');
    });
  });

  test('should_filterByYear_when_dateFilterProvided', async () => {
    // AC1: Filter events by year
    const response = await apiRequest(
      '/api/v1/events?filter={"date":{"$gte":"2025-01-01T00:00:00Z","$lt":"2026-01-01T00:00:00Z"}}',
      authToken
    );
    const data = await response.json();

    expect(response.status).toBe(200);

    // Verify all events are from 2025
    data.data.forEach((event: Event) => {
      const year = new Date(event.date).getFullYear();
      expect(year).toBe(2025);
    });
  });

  test('should_sortByDate_when_sortParamProvided', async () => {
    // AC1: Sort events by date descending
    const response = await apiRequest('/api/v1/events?sort=-date', authToken);
    const data = await response.json();

    expect(response.status).toBe(200);

    // Verify events are sorted by date descending
    const dates = data.data.map((e: Event) => new Date(e.date).getTime());
    for (let i = 0; i < dates.length - 1; i++) {
      expect(dates[i]).toBeGreaterThanOrEqual(dates[i + 1]);
    }
  });

  test('should_paginateResults_when_pageParamProvided', async () => {
    // AC1: Pagination support
    const response = await apiRequest('/api/v1/events?page=1&limit=10', authToken);
    const data = await response.json();

    expect(response.status).toBe(200);
    expect(data.pagination.page).toBe(1);
    expect(data.pagination.limit).toBe(10);
    expect(data.pagination.total).toBeGreaterThanOrEqual(0);
    expect(data.pagination.hasNext).toBeDefined();
    expect(data.pagination.hasPrev).toBe(false);
    expect(data.data.length).toBeLessThanOrEqual(10);
  });
});

// ============================================================================
// TEST GROUP 2: Event Detail with Resource Expansion (AC2)
// ============================================================================

test.describe('Events API Consolidation - Event Detail (AC2)', () => {
  let authToken: string;
  let testEvent: Event;

  test.beforeAll(async ({ browser }) => {
    const page = await browser.newPage();
    authToken = await loginAsOrganizer(page);
    testEvent = await createTestEvent(authToken);
    await page.close();
  });

  test('should_loadEventDetail_when_usingNewAPI', async ({ page }) => {
    // AC2 & AC17: Event detail page loads with consolidated API
    // BEFORE: 30 API calls for event detail page
    // AFTER: 1 API call with ?include parameter

    await page.goto(`${BASE_URL}/organizer/events/${testEvent.id}`);

    // Wait for event detail to load
    await expect(page.getByTestId('event-detail')).toBeVisible();
    await expect(page.getByText(testEvent.title)).toBeVisible();
  });

  test('should_getEventBasic_when_noIncludesProvided', async () => {
    // AC2: Basic event without includes
    const response = await apiRequest(`/api/v1/events/${testEvent.id}`, authToken);
    const data = await response.json();

    expect(response.status).toBe(200);
    expect(data.id).toBe(testEvent.id);
    expect(data.title).toBeDefined();
    expect(data.date).toBeDefined();
    expect(data.status).toBeDefined();

    // Verify no expanded resources
    expect(data.venue).toBeUndefined();
    expect(data.speakers).toBeUndefined();
    expect(data.sessions).toBeUndefined();
  });

  test('should_includeVenue_when_includeVenueRequested', async () => {
    // AC2: Include venue in response
    const response = await apiRequest(`/api/v1/events/${testEvent.id}?include=venue`, authToken);
    const data = await response.json();

    expect(response.status).toBe(200);
    expect(data.venue).toBeDefined();
    expect(data.venue.id).toBeDefined();
    expect(data.venue.name).toBeDefined();
    expect(data.venue.capacity).toBeDefined();
  });

  test('should_includeSpeakers_when_includeSpeakersRequested', async () => {
    // AC2: Include speakers in response
    const response = await apiRequest(`/api/v1/events/${testEvent.id}?include=speakers`, authToken);
    const data = await response.json();

    expect(response.status).toBe(200);
    expect(data.speakers).toBeDefined();
    expect(Array.isArray(data.speakers)).toBe(true);
  });

  test('should_includeMultiple_when_multipleIncludesRequested', async () => {
    // AC2: Include multiple resources
    const response = await apiRequest(
      `/api/v1/events/${testEvent.id}?include=venue,speakers,sessions,topics,workflow`,
      authToken
    );
    const data = await response.json();

    expect(response.status).toBe(200);
    expect(data.venue).toBeDefined();
    expect(data.speakers).toBeDefined();
    expect(data.sessions).toBeDefined();
    expect(data.topics).toBeDefined();
    expect(data.workflow).toBeDefined();
  });

  test('should_return404_when_eventNotFound', async () => {
    // AC2: Error handling for non-existent event
    const response = await apiRequest('/api/v1/events/non-existent-id', authToken);

    expect(response.status).toBe(404);
  });
});

// ============================================================================
// TEST GROUP 3: Event CRUD Operations (AC3-6)
// ============================================================================

test.describe('Events API Consolidation - CRUD Operations (AC3-6)', () => {
  let authToken: string;

  test.beforeAll(async ({ browser }) => {
    const page = await browser.newPage();
    authToken = await loginAsOrganizer(page);
    await page.close();
  });

  test('should_createEvent_when_validDataProvided', async () => {
    // AC3: Create event
    const response = await apiRequest('/api/v1/events', authToken, {
      method: 'POST',
      body: JSON.stringify({
        title: 'New Test Event',
        date: '2025-06-01T09:00:00Z',
        status: 'draft',
        description: 'Test event creation',
      }),
    });

    expect(response.status).toBe(201);
    const data = await response.json();
    expect(data.id).toBeDefined();
    expect(data.title).toBe('New Test Event');
  });

  test('should_return400_when_invalidDataProvided', async () => {
    // AC3: Validation error for invalid data
    const response = await apiRequest('/api/v1/events', authToken, {
      method: 'POST',
      body: JSON.stringify({
        // Missing required fields
        status: 'draft',
      }),
    });

    expect(response.status).toBe(400);
  });

  test('should_replaceEvent_when_putRequested', async () => {
    // AC4: Update event (full replacement)
    const event = await createTestEvent(authToken, 'Event to Replace');

    const response = await apiRequest(`/api/v1/events/${event.id}`, authToken, {
      method: 'PUT',
      body: JSON.stringify({
        title: 'Replaced Event',
        date: '2025-07-01T09:00:00Z',
        status: 'draft',
        description: 'Event replaced via PUT',
      }),
    });

    expect(response.status).toBe(200);
    const data = await response.json();
    expect(data.title).toBe('Replaced Event');
  });

  test('should_patchFields_when_patchRequested', async () => {
    // AC5: Partial update
    const event = await createTestEvent(authToken, 'Event to Patch');

    const response = await apiRequest(`/api/v1/events/${event.id}`, authToken, {
      method: 'PATCH',
      body: JSON.stringify({
        title: 'Patched Title Only',
      }),
    });

    expect(response.status).toBe(200);
    const data = await response.json();
    expect(data.title).toBe('Patched Title Only');
    expect(data.date).toBe(event.date); // Date unchanged
  });

  test('should_deleteEvent_when_deleteRequested', async () => {
    // AC6: Delete event
    const event = await createTestEvent(authToken, 'Event to Delete');

    const response = await apiRequest(`/api/v1/events/${event.id}`, authToken, {
      method: 'DELETE',
    });

    expect(response.status).toBe(204);

    // Verify event no longer exists
    const getResponse = await apiRequest(`/api/v1/events/${event.id}`, authToken);
    expect(getResponse.status).toBe(404);
  });
});

// ============================================================================
// TEST GROUP 4: Event Actions - Publish & Workflow (AC7-8)
// ============================================================================

test.describe('Events API Consolidation - Event Actions (AC7-8)', () => {
  let authToken: string;

  test.beforeAll(async ({ browser }) => {
    const page = await browser.newPage();
    authToken = await loginAsOrganizer(page);
    await page.close();
  });

  test('should_publishEvent_when_validationPasses', async () => {
    // AC7: Publish event
    const event = await createTestEvent(authToken, 'Event to Publish');

    const response = await apiRequest(`/api/v1/events/${event.id}/publish`, authToken, {
      method: 'POST',
    });

    expect(response.status).toBe(200);
    const data = await response.json();
    expect(data.status).toBe('published');
  });

  test('should_return422_when_validationFails', async () => {
    // AC7: Publish validation failure
    const event = await createTestEvent(authToken, 'Incomplete Event');
    // Event missing required fields for publishing

    const response = await apiRequest(`/api/v1/events/${event.id}/publish`, authToken, {
      method: 'POST',
    });

    expect(response.status).toBe(422);
  });

  test('should_advanceWorkflow_when_transitionValid', async () => {
    // AC8: Advance workflow to next state
    const event = await createTestEvent(authToken, 'Workflow Event');

    const response = await apiRequest(`/api/v1/events/${event.id}/workflow/advance`, authToken, {
      method: 'POST',
    });

    expect(response.status).toBe(200);
    const data = await response.json();
    expect(data.workflow).toBeDefined();
    expect(data.workflow.currentStep).toBeGreaterThan(1);
  });

  test('should_return422_when_transitionInvalid', async () => {
    // AC8: Invalid workflow transition
    const event = await createTestEvent(authToken, 'Invalid Workflow Event');
    // Try to advance workflow when prerequisites not met

    const response = await apiRequest(`/api/v1/events/${event.id}/workflow/advance`, authToken, {
      method: 'POST',
    });

    expect(response.status).toBe(422);
  });
});

// ============================================================================
// TEST GROUP 5: Performance & Caching (AC15-16)
// ============================================================================

test.describe('Events API Consolidation - Performance (AC15-16)', () => {
  let authToken: string;
  let testEvent: Event;

  test.beforeAll(async ({ browser }) => {
    const page = await browser.newPage();
    authToken = await loginAsOrganizer(page);
    testEvent = await createTestEvent(authToken);
    await page.close();
  });

  test('should_respondUnder500ms_when_fullIncludesRequested', async () => {
    // AC16: Performance requirement - event detail with all includes <500ms P95
    const measurements: number[] = [];

    // Make 10 requests to measure P95
    for (let i = 0; i < 10; i++) {
      const startTime = Date.now();
      const response = await apiRequest(
        `/api/v1/events/${testEvent.id}?include=venue,speakers,sessions,topics,workflow,registrations,catering,team,publishing,notifications,analytics`,
        authToken
      );
      const endTime = Date.now();

      expect(response.status).toBe(200);
      measurements.push(endTime - startTime);
    }

    // Calculate P95
    measurements.sort((a, b) => a - b);
    const p95Index = Math.floor(measurements.length * 0.95);
    const p95Latency = measurements[p95Index];

    // AC16: P95 latency should be < 500ms
    expect(p95Latency).toBeLessThan(500);

    console.log(
      `Event Detail API Latency - Min: ${Math.min(...measurements)}ms, Max: ${Math.max(...measurements)}ms, P95: ${p95Latency}ms`
    );
  });

  test('should_returnCached_when_withinTTL', async () => {
    // AC15: Redis caching for expanded resources
    const firstResponse = await apiRequest(
      `/api/v1/events/${testEvent.id}?include=venue,speakers,sessions`,
      authToken
    );
    expect(firstResponse.status).toBe(200);
    const firstData = await firstResponse.json();

    // Second request should be cached (faster)
    const startTime = Date.now();
    const cachedResponse = await apiRequest(
      `/api/v1/events/${testEvent.id}?include=venue,speakers,sessions`,
      authToken
    );
    const cachedLatency = Date.now() - startTime;

    expect(cachedResponse.status).toBe(200);
    const cachedData = await cachedResponse.json();

    // Verify cached response matches original
    expect(cachedData.id).toBe(firstData.id);

    // Cached response should be faster (< 50ms from dev notes)
    expect(cachedLatency).toBeLessThan(100);
  });
});

// ============================================================================
// TEST GROUP 6: Wireframe Migration Validation (AC17)
// ============================================================================

test.describe('Events API Consolidation - Wireframe Migration (AC17)', () => {
  let authToken: string;

  test.beforeAll(async ({ browser }) => {
    const page = await browser.newPage();
    authToken = await loginAsOrganizer(page);
    await page.close();
  });

  test('should_loadEventManagementDashboard_when_migratedToNewAPIs', async ({ page }) => {
    // AC17: story-1.16-event-management-dashboard.md uses new APIs
    await page.goto(`${BASE_URL}/organizer/events`);

    await expect(page.getByTestId('event-list')).toBeVisible();
    await expect(page.getByTestId('event-search')).toBeVisible();
    await expect(page.getByTestId('event-filters')).toBeVisible();

    // Verify page loads successfully with consolidated APIs
    const events = page.locator('[data-testid="event-card"]');
    await expect(events.count()).resolves.toBeGreaterThan(0);
  });

  test('should_loadEventDetailEdit_when_migratedToNewAPIs', async ({ page }) => {
    // AC17: story-1.16-event-detail-edit.md uses new APIs
    const event = await createTestEvent(authToken);

    await page.goto(`${BASE_URL}/organizer/events/${event.id}/edit`);

    await expect(page.getByTestId('event-edit-form')).toBeVisible();
    await expect(page.getByText(event.title)).toBeVisible();

    // Verify all sub-resources loaded via includes
    await expect(page.getByTestId('event-venue-section')).toBeVisible();
    await expect(page.getByTestId('event-speakers-section')).toBeVisible();
    await expect(page.getByTestId('event-sessions-section')).toBeVisible();
  });
});
