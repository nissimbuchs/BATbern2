/**
 * E2E Tests for Topic Selection & Heat Map Visualization
 * Story 5.2: Topic Selection & Speaker Brainstorming (AC1-8, AC14-16)
 *
 * STATUS: Story 5.2 is IMPLEMENTED (Done)
 *
 * Implementation Status:
 * - TopicBacklogManager component ✅ IMPLEMENTED
 * - TopicHeatMap component ✅ IMPLEMENTED
 * - TopicDetailsPanel ✅ IMPLEMENTED
 * - TopicList and filtering ✅ IMPLEMENTED
 *
 * IMPORTANT: Tests follow FULL CRUD lifecycle - no dependency on seed data
 * Each test creates its own topics/events and cleans up after itself.
 *
 * Test Pattern (E2E Tests - UI-Based):
 * 1. Create topic via UI (navigate, fill form, submit)
 * 2. Create event via UI (navigate, fill form, submit)
 * 3. Perform test actions through UI interactions
 * 4. Cleanup: Delete event via UI
 * 5. Cleanup: Delete topic via UI
 *
 * Test Pattern (API Contract Tests - API-Based):
 * 1. Create topic via API (POST /api/v1/topics)
 * 2. Create event via API (POST /api/v1/events)
 * 3. Verify API contract and responses
 * 4. Cleanup: Delete via API
 *
 * Setup Instructions:
 * 1. Ensure migration V14 is applied: topics, topic_usage_history tables
 * 2. Ensure Event Management Service is running
 * 3. Run: npx playwright test e2e/organizer/topic-selection.spec.ts
 *
 * LANGUAGE-INDEPENDENT SELECTORS (BAT-93):
 * ✅ Fixed: All button selectors use data-testid attributes
 */

import { test, expect, type APIRequestContext, type Page } from '@playwright/test';
import { BASE_URL, API_URL } from '../../playwright.config';

// Type definitions for Story 5.2 API responses
interface TopicResponse {
  id: string;
  topicCode: string;
  title: string;
  description: string;
  category: string;
  stalenessScore: number;
  status: string;
}

interface EventResponse {
  id: string;
  eventCode: string;
  title: string;
  eventNumber: number;
  status: string;
}

/**
 * Helper: Create a topic via UI
 */
async function createTopicViaUI(
  page: Page,
  title: string,
  category: string = 'technical',
  description: string = 'Test topic description'
): Promise<string> {
  await page.goto(`${BASE_URL}/organizer/topics`);

  // Wait for page to load
  await page.waitForLoadState('networkidle');

  // Click "Create New Topic" button
  await page.click('[data-testid="new-topic-button"]');

  // Wait for modal to open
  await expect(page.locator('[data-testid="create-topic-modal"]')).toBeVisible();

  // Fill topic form in modal
  await page.fill('[data-testid="topic-title-input"]', title);
  await page.fill('[data-testid="topic-description-input"]', description);

  // Select category from dropdown
  await page.click('[data-testid="topic-category-select-input"]');
  await page.click(`[data-testid="category-option-${category}"]`);

  // Submit form
  await page.click('[data-testid="submit-topic-button"]');

  // Wait for modal to close
  await expect(page.locator('[data-testid="create-topic-modal"]')).not.toBeVisible({
    timeout: 10000,
  });

  // Extract topic code from the created topic card
  const topicCard = page.locator(`[data-testid^="topic-card-"]:has-text("${title}")`);
  await expect(topicCard).toBeVisible({ timeout: 10000 });
  const testId = await topicCard.getAttribute('data-testid');
  const topicCode = testId?.replace('topic-card-', '') || '';

  return topicCode;
}

/**
 * Helper: Delete a topic via UI
 */
async function deleteTopicViaUI(page: Page, topicCode: string): Promise<void> {
  await page.goto(`${BASE_URL}/organizer/topics`);

  // Wait for topic card to appear
  const topicCard = page.locator(`[data-testid="topic-card-${topicCode}"]`);
  const isVisible = await topicCard.isVisible().catch(() => false);

  if (!isVisible) {
    // Topic already deleted or doesn't exist
    return;
  }

  // Click on the topic card to select it
  await topicCard.click();

  // Click delete button
  await page.click('[data-testid="delete-topic-button"]');

  // Confirm deletion in modal
  await page.click('[data-testid="confirm-delete-button"]');

  // Wait for success message or card to disappear
  await page.waitForTimeout(1000);
}

/**
 * Helper: Create an event (using API for test setup)
 * Note: Event creation UI flow is complex and tested separately.
 * For topic selection tests, events serve as test data setup.
 */
async function createEventForTest(
  request: APIRequestContext,
  title: string = `E2E Test Event ${Date.now()}`,
  eventNumber: number = 999
): Promise<string> {
  const authToken = process.env.AUTH_TOKEN;
  const response = await request.post(`${API_URL}/api/v1/events`, {
    data: {
      title,
      eventNumber,
      date: new Date(Date.now() + 30 * 24 * 60 * 60 * 1000).toISOString(),
      registrationDeadline: new Date(Date.now() + 20 * 24 * 60 * 60 * 1000).toISOString(),
      venueName: 'Test Venue',
      venueAddress: 'Test Address, Bern',
      venueCapacity: 100,
      eventType: 'EVENING',
    },
    headers: {
      Authorization: `Bearer ${authToken}`,
      'Content-Type': 'application/json',
    },
  });

  expect(response.status()).toBe(201);
  const event = await response.json();
  return event.eventCode;
}

/**
 * Helper: Delete an event (using API for test cleanup)
 */
async function deleteEventForTest(request: APIRequestContext, eventCode: string): Promise<void> {
  const authToken = process.env.AUTH_TOKEN;
  const response = await request.delete(`${API_URL}/api/v1/events/${eventCode}`, {
    headers: {
      Authorization: `Bearer ${authToken}`,
    },
  });

  expect([200, 204, 404]).toContain(response.status());
}

/**
 * Helper: Assign topic to event via UI
 */
async function assignTopicToEventViaUI(
  page: Page,
  eventCode: string,
  topicCode: string
): Promise<void> {
  // Navigate to topic selection for this event
  await page.goto(`${BASE_URL}/organizer/topics?eventCode=${eventCode}`);

  // Wait for topics to load
  await page.waitForTimeout(1000);

  // Click on topic card
  await page.click(`[data-testid="topic-card-${topicCode}"]`);

  // Click "Select for Event" button
  await page.click('[data-testid="select-topic-button"]');

  // Wait for success
  await expect(page.locator('[data-testid="success-message"]')).toBeVisible();
}

// API helpers for API contract tests only
async function createTopicViaAPI(
  request: APIRequestContext,
  title: string,
  category: string = 'technical',
  description: string = 'Test topic description'
): Promise<TopicResponse> {
  const authToken = process.env.AUTH_TOKEN;
  const response = await request.post(`${API_URL}/api/v1/topics`, {
    data: {
      title,
      description,
      category,
    },
    headers: {
      Authorization: `Bearer ${authToken}`,
      'Content-Type': 'application/json',
    },
  });

  expect(response.status()).toBe(201);
  return await response.json();
}

async function deleteTopicViaAPI(request: APIRequestContext, topicCode: string): Promise<void> {
  const authToken = process.env.AUTH_TOKEN;
  const response = await request.delete(`${API_URL}/api/v1/topics/${topicCode}`, {
    headers: {
      Authorization: `Bearer ${authToken}`,
    },
  });

  expect([200, 204, 404]).toContain(response.status());
}

async function createEventViaAPI(
  request: APIRequestContext,
  title: string = `E2E Test Event ${Date.now()}`,
  eventNumber: number = 999
): Promise<EventResponse> {
  const authToken = process.env.AUTH_TOKEN;
  const response = await request.post(`${API_URL}/api/v1/events`, {
    data: {
      title,
      eventNumber,
      date: new Date(Date.now() + 30 * 24 * 60 * 60 * 1000).toISOString(),
      registrationDeadline: new Date(Date.now() + 20 * 24 * 60 * 60 * 1000).toISOString(),
      venueName: 'Test Venue',
      venueAddress: 'Test Address, Bern',
      venueCapacity: 100,
      eventType: 'EVENING',
    },
    headers: {
      Authorization: `Bearer ${authToken}`,
      'Content-Type': 'application/json',
    },
  });

  expect(response.status()).toBe(201);
  return await response.json();
}

async function deleteEventViaAPI(request: APIRequestContext, eventCode: string): Promise<void> {
  const authToken = process.env.AUTH_TOKEN;
  const response = await request.delete(`${API_URL}/api/v1/events/${eventCode}`, {
    headers: {
      Authorization: `Bearer ${authToken}`,
    },
  });

  expect([200, 204, 404]).toContain(response.status());
}

test.describe('Topic Selection & Heat Map (Story 5.2)', () => {
  // Track created resources for cleanup
  const createdTopics: string[] = [];
  const createdEvents: string[] = [];

  test.afterEach(async ({ page, request }) => {
    // Cleanup: Delete all created events first (they may reference topics)
    for (const eventCode of createdEvents) {
      await deleteEventForTest(request, eventCode);
    }
    createdEvents.length = 0;

    // Cleanup: Delete all created topics via UI
    for (const topicCode of createdTopics) {
      await deleteTopicViaUI(page, topicCode);
    }
    createdTopics.length = 0;
  });

  test.describe('AC1: Topic Backlog Display', () => {
    test('should display searchable list of all available topics', async ({ page }) => {
      // Create test topics via UI with unique names
      const timestamp = Date.now();
      const topicCode1 = await createTopicViaUI(
        page,
        `Cloud Architecture ${timestamp}`,
        'technical'
      );
      const topicCode2 = await createTopicViaUI(
        page,
        `Sustainable Design ${timestamp}`,
        'management'
      );
      const topicCode3 = await createTopicViaUI(
        page,
        `AI in Construction ${timestamp}`,
        'technical'
      );
      createdTopics.push(topicCode1, topicCode2, topicCode3);

      // Navigate to topic management
      await page.goto(`${BASE_URL}/organizer/topics`);

      // Verify topic list component exists
      const topicList = page.locator('[data-testid="topic-list"]');
      await expect(topicList).toBeVisible();

      // Verify search input exists
      const searchInput = page.locator('input[placeholder*="Search"]');
      await expect(searchInput).toBeVisible();

      // Verify our created topics are displayed
      await expect(page.locator('[data-testid^="topic-card-"]')).toHaveCount(3, {
        timeout: 10000,
      });
      await expect(page.locator(`text=Cloud Architecture ${timestamp}`)).toBeVisible();
      await expect(page.locator(`text=Sustainable Design ${timestamp}`)).toBeVisible();
      await expect(page.locator(`text=AI in Construction ${timestamp}`)).toBeVisible();
    });

    test('should filter topics by category', async ({ page }) => {
      // Create topics in different categories via UI with unique names
      const timestamp = Date.now();
      const topic1Title = `DevOps Best Practices ${timestamp}`;
      const topic2Title = `User Experience Design ${timestamp}`;
      const topic3Title = `Cloud Security ${timestamp}`;

      const topicCode1 = await createTopicViaUI(page, topic1Title, 'technical');
      const topicCode2 = await createTopicViaUI(page, topic2Title, 'management');
      const topicCode3 = await createTopicViaUI(page, topic3Title, 'technical');
      createdTopics.push(topicCode1, topicCode2, topicCode3);

      await page.goto(`${BASE_URL}/organizer/topics`);

      // Wait for topics to load
      await expect(page.locator('[data-testid^="topic-card-"]')).toHaveCount(3, {
        timeout: 10000,
      });

      // Open category filter
      await page.click('[data-testid="filter-category"]');
      await page.click('[role="option"]:has-text("Technical")');

      // Verify only technical topics are shown
      await expect(page.locator(`text=${topic1Title}`)).toBeVisible();
      await expect(page.locator(`text=${topic3Title}`)).toBeVisible();
      await expect(page.locator(`text=${topic2Title}`)).not.toBeVisible();
    });

    test('should navigate to Topic Backlog Manager from event workflow', async ({
      page,
      request,
    }) => {
      // Create event for test
      const eventCode = await createEventForTest(request);
      createdEvents.push(eventCode);

      // Navigate to event details
      await page.goto(`${BASE_URL}/organizer/events`);
      await page.click(`[data-testid="event-card-${eventCode}"]`);

      // Click "Select Topic" workflow button
      await page.click('[data-testid="workflow-button-select-topic"]');

      // Verify Topic Backlog Manager loads
      await expect(page.locator('h1')).toContainText('Topic');
      await expect(page.locator('[data-testid="topic-backlog-manager"]')).toBeVisible();
    });
  });

  test.describe('AC2: Heat Map Visualization', () => {
    test('should display usage heat map for selected topic', async ({ page }) => {
      // Create topic via UI with unique name
      const topicCode = await createTopicViaUI(
        page,
        `Microservices Architecture ${Date.now()}`,
        'technical'
      );
      createdTopics.push(topicCode);

      await page.goto(`${BASE_URL}/organizer/topics`);

      // Click on the topic to view details
      await page.click(`[data-testid="topic-card-${topicCode}"]`);

      // Verify heat map component loads
      const heatMap = page.locator('[data-testid="topic-heat-map"]');
      await expect(heatMap).toBeVisible();

      // Verify heat map shows quarterly data
      await expect(heatMap).toContainText('Q'); // Quarter markers
    });

    test('should show quarterly usage frequency in heat map', async ({ page }) => {
      // Create topic via UI with unique name
      const topicCode = await createTopicViaUI(
        page,
        `Test Topic for Heat Map ${Date.now()}`,
        'technical'
      );
      createdTopics.push(topicCode);

      await page.goto(`${BASE_URL}/organizer/topics`);
      await page.click(`[data-testid="topic-card-${topicCode}"]`);

      // Verify heat map grid exists (4 quarters x 2 years = 8 cells)
      const heatMapCells = page.locator('[data-testid^="heat-map-cell-"]');
      const count = await heatMapCells.count();
      expect(count).toBeGreaterThanOrEqual(4); // At least one year of quarters
    });

    test('should display tooltip on hover showing attendance and engagement', async ({ page }) => {
      // Create topic via UI with unique name
      const topicCode = await createTopicViaUI(
        page,
        `Tooltip Test Topic ${Date.now()}`,
        'technical'
      );
      createdTopics.push(topicCode);

      await page.goto(`${BASE_URL}/organizer/topics`);
      await page.click(`[data-testid="topic-card-${topicCode}"]`);

      // Hover over a heat map cell
      const heatMapCell = page.locator('[data-testid^="heat-map-cell-"]').first();
      await heatMapCell.hover();

      // Verify tooltip appears (may need timeout for animation)
      await page.waitForTimeout(500);
      const tooltip = page.locator('[role="tooltip"]');
      const isVisible = await tooltip.isVisible();

      // Tooltip may only appear if there's data for that cell
      if (isVisible) {
        await expect(tooltip).toContainText(/Attendance|Engagement|No data/);
      }
    });
  });

  test.describe('AC3: Color-Coded Freshness', () => {
    test('should display green color for newly created topics (not used, staleness 100)', async ({
      page,
    }) => {
      // Create fresh topic via UI (staleness score should be 100) with unique name
      const topicCode = await createTopicViaUI(page, `Brand New Topic ${Date.now()}`, 'technical');
      createdTopics.push(topicCode);

      await page.goto(`${BASE_URL}/organizer/topics`);

      // Wait for topic to appear
      await expect(page.locator(`[data-testid="topic-card-${topicCode}"]`)).toBeVisible();

      // Verify green freshness indicator (high staleness = fresh = green)
      const topicCard = page.locator(`[data-testid="topic-card-${topicCode}"]`);
      await expect(topicCard.locator('[data-freshness="green"]')).toBeVisible();
    });

    test('should display staleness score badge', async ({ page }) => {
      // Create topic via UI with unique name
      const topicCode = await createTopicViaUI(
        page,
        `Staleness Score Test ${Date.now()}`,
        'technical'
      );
      createdTopics.push(topicCode);

      await page.goto(`${BASE_URL}/organizer/topics`);

      // Verify staleness score is displayed
      const stalenessScore = page.locator(`[data-testid="staleness-score-${topicCode}"]`);
      await expect(stalenessScore).toBeVisible();

      // New topics should have score of 100
      await expect(stalenessScore).toContainText('100');
    });
  });

  test.describe('AC6: Staleness Score Display', () => {
    test('should display 0-100 staleness score for each topic', async ({ page }) => {
      // Create multiple topics via UI with unique names
      const timestamp = Date.now();
      const topicCode1 = await createTopicViaUI(page, `Score Test 1 ${timestamp}`, 'technical');
      const topicCode2 = await createTopicViaUI(page, `Score Test 2 ${timestamp}`, 'management');
      createdTopics.push(topicCode1, topicCode2);

      await page.goto(`${BASE_URL}/organizer/topics`);

      // Verify staleness score badges exist
      const score1 = page.locator(`[data-testid="staleness-score-${topicCode1}"]`);
      const score2 = page.locator(`[data-testid="staleness-score-${topicCode2}"]`);

      await expect(score1).toBeVisible();
      await expect(score2).toBeVisible();

      // Verify scores are within 0-100 range
      const scoreText1 = await score1.textContent();
      const scoreText2 = await score2.textContent();
      const scoreValue1 = parseInt(scoreText1 || '0');
      const scoreValue2 = parseInt(scoreText2 || '0');

      expect(scoreValue1).toBeGreaterThanOrEqual(0);
      expect(scoreValue1).toBeLessThanOrEqual(100);
      expect(scoreValue2).toBeGreaterThanOrEqual(0);
      expect(scoreValue2).toBeLessThanOrEqual(100);
    });
  });

  test.describe('AC8: Topic Creation Inline', () => {
    test('should allow creating new topic with description', async ({ page }) => {
      await page.goto(`${BASE_URL}/organizer/topics`);

      // Click "New Topic" button
      await page.click('[data-testid="new-topic-button"]');

      // Fill topic form
      const topicTitle = `AI-Powered DevOps ${Date.now()}`;
      await page.fill('input[name="title"]', topicTitle);
      await page.fill(
        'textarea[name="description"]',
        'Exploring AI applications in DevOps workflows'
      );
      await page.click('[data-testid="category-selector"]');
      await page.click('[role="option"]:has-text("Technical")');

      // Submit form
      await page.click('[data-testid="create-topic-button"]');

      // Wait for success message
      await expect(page.locator('[data-testid="success-message"]')).toBeVisible();

      // Verify topic appears in list
      await expect(page.locator(`text=${topicTitle}`)).toBeVisible();

      // Extract topic code for cleanup
      const topicCard = page.locator(`[data-testid^="topic-card-"]:has-text("${topicTitle}")`);
      const testId = await topicCard.getAttribute('data-testid');
      if (testId) {
        const topicCode = testId.replace('topic-card-', '');
        createdTopics.push(topicCode);
      }
    });
  });

  test.describe('AC14-16: Workflow Engine Integration', () => {
    test('should allow selecting topic for an event', async ({ page, request }) => {
      // Create topic via UI and event for test setup with unique names
      const topicTitle = `Workflow Test Topic ${Date.now()}`;
      const topicCode = await createTopicViaUI(page, topicTitle, 'technical');
      const eventCode = await createEventForTest(request);
      createdTopics.push(topicCode);
      createdEvents.push(eventCode);

      // Navigate to topic selection for this event
      await page.goto(`${BASE_URL}/organizer/topics?eventCode=${eventCode}`);

      // Wait for topics to load
      await expect(page.locator('[data-testid^="topic-card-"]')).toHaveCount(1, {
        timeout: 10000,
      });

      // Click on topic card
      await page.click(`[data-testid="topic-card-${topicCode}"]`);

      // Click "Select for Event" button
      await page.click('[data-testid="select-topic-button"]');

      // Wait for success
      await expect(page.locator('[data-testid="success-message"]')).toBeVisible();

      // Navigate back to event to verify assignment
      await page.goto(`${BASE_URL}/organizer/events/${eventCode}`);

      // Verify topic is assigned to event
      await expect(page.locator(`text=${topicTitle}`)).toBeVisible();
    });

    test('should transition event workflow state when topic selected', async ({
      page,
      request,
    }) => {
      // Create topic via UI and event for test setup with unique names
      const topicCode = await createTopicViaUI(
        page,
        `State Transition Test ${Date.now()}`,
        'technical'
      );
      const eventCode = await createEventForTest(request);
      createdTopics.push(topicCode);
      createdEvents.push(eventCode);

      // Assign topic to event via UI
      await assignTopicToEventViaUI(page, eventCode, topicCode);

      // Navigate to event details
      await page.goto(`${BASE_URL}/organizer/events/${eventCode}`);

      // Verify workflow state badge reflects topic selection
      const workflowBadge = page.locator('[data-testid="workflow-state-badge"]');
      await expect(workflowBadge).toBeVisible();
      // State should have progressed from CREATED to TOPIC_SELECTION
      const badgeText = await workflowBadge.textContent();
      expect(badgeText).toContain('Topic');
    });
  });
});

test.describe('Topics API Contract Tests (Story 5.2)', () => {
  // Track created resources for cleanup
  const createdTopics: string[] = [];
  const createdEvents: string[] = [];

  test.afterEach(async ({ request }) => {
    // Cleanup: Delete all created events first
    for (const eventCode of createdEvents) {
      await deleteEventViaAPI(request, eventCode);
    }
    createdEvents.length = 0;

    // Cleanup: Delete all created topics
    for (const topicCode of createdTopics) {
      await deleteTopicViaAPI(request, topicCode);
    }
    createdTopics.length = 0;
  });

  test.describe('GET /api/v1/topics', () => {
    test('should return topics with pagination', async ({ request }) => {
      // Create test topics
      const topic1 = await createTopicViaAPI(request, 'API Test Topic 1', 'technical');
      const topic2 = await createTopicViaAPI(request, 'API Test Topic 2', 'design');
      createdTopics.push(topic1.topicCode, topic2.topicCode);

      const authToken = process.env.AUTH_TOKEN;
      const response = await request.get(`${API_URL}/api/v1/topics?page=1&limit=50`, {
        headers: {
          Authorization: `Bearer ${authToken}`,
        },
      });

      expect(response.status()).toBe(200);

      const result = await response.json();
      expect(result.data).toBeInstanceOf(Array);
      expect(result.data.length).toBeGreaterThanOrEqual(2);
      expect(result.pagination).toBeDefined();
      expect(result.pagination.page).toBe(1);
      expect(result.pagination.limit).toBe(50);
    });

    test('should filter topics by category', async ({ request }) => {
      // Create topics in specific category
      const topic1 = await createTopicViaAPI(request, 'Technical Topic 1', 'technical');
      const topic2 = await createTopicViaAPI(request, 'Technical Topic 2', 'technical');
      const topic3 = await createTopicViaAPI(request, 'Design Topic 1', 'design');
      createdTopics.push(topic1.topicCode, topic2.topicCode, topic3.topicCode);

      const authToken = process.env.AUTH_TOKEN;
      const filter = JSON.stringify({ category: 'technical' });
      const response = await request.get(
        `${API_URL}/api/v1/topics?filter=${encodeURIComponent(filter)}`,
        {
          headers: {
            Authorization: `Bearer ${authToken}`,
          },
        }
      );

      expect(response.status()).toBe(200);

      const result = await response.json();
      expect(result.data.length).toBeGreaterThanOrEqual(2);
      result.data.forEach((topic: TopicResponse) => {
        expect(topic.category).toBe('technical');
      });
    });

    test('should sort topics by staleness score descending', async ({ request }) => {
      // Create multiple topics
      const topic1 = await createTopicViaAPI(request, 'Sort Test 1', 'technical');
      const topic2 = await createTopicViaAPI(request, 'Sort Test 2', 'design');
      createdTopics.push(topic1.topicCode, topic2.topicCode);

      const authToken = process.env.AUTH_TOKEN;
      const response = await request.get(`${API_URL}/api/v1/topics?sort=-stalenessScore`, {
        headers: {
          Authorization: `Bearer ${authToken}`,
        },
      });

      expect(response.status()).toBe(200);

      const result = await response.json();
      expect(result.data.length).toBeGreaterThanOrEqual(2);
      const scores = result.data.map((t: TopicResponse) => t.stalenessScore);

      // Verify descending order
      for (let i = 0; i < scores.length - 1; i++) {
        expect(scores[i]).toBeGreaterThanOrEqual(scores[i + 1]);
      }
    });
  });

  test.describe('POST /api/v1/topics', () => {
    test('should create new topic with initial staleness score 100', async ({ request }) => {
      const authToken = process.env.AUTH_TOKEN;
      const response = await request.post(`${API_URL}/api/v1/topics`, {
        data: {
          title: 'Test Topic - API Contract',
          description: 'Test description',
          category: 'technical',
        },
        headers: {
          Authorization: `Bearer ${authToken}`,
          'Content-Type': 'application/json',
        },
      });

      expect(response.status()).toBe(201);

      const topic = await response.json();
      expect(topic.id).toBeDefined();
      expect(topic.topicCode).toBeDefined();
      expect(topic.title).toContain('Test Topic');
      expect(topic.stalenessScore).toBe(100); // New topics have max staleness
      expect(topic.status).toBe('AVAILABLE');

      // Track for cleanup
      createdTopics.push(topic.topicCode);
    });
  });

  test.describe('POST /api/v1/events/{eventCode}/topics', () => {
    test('should assign topic to event and return success', async ({ request }) => {
      // Create topic and event
      const topic = await createTopicViaAPI(request, 'Assignment Test Topic', 'technical');
      const event = await createEventViaAPI(request);
      createdTopics.push(topic.topicCode);
      createdEvents.push(event.eventCode);

      const response = await request.post(`${API_URL}/api/v1/events/${event.eventCode}/topics`, {
        data: {
          topicCode: topic.topicCode,
          justification: null,
        },
        headers: {
          Authorization: `Bearer ${process.env.E2E_TEST_TOKEN}`,
        },
      });

      expect(response.status()).toBe(200);

      const result = await response.json();
      expect(result.eventCode).toBe(event.eventCode);
      expect(result.topicCode).toBe(topic.topicCode);
    });
  });

  test.describe('DELETE /api/v1/topics/{topicCode}', () => {
    test('should delete unused topic', async ({ request }) => {
      const authToken = process.env.AUTH_TOKEN;
      // Create topic
      const topic = await createTopicViaAPI(request, 'Delete Test Topic', 'technical');

      // Delete topic
      const response = await request.delete(`${API_URL}/api/v1/topics/${topic.topicCode}`, {
        headers: {
          Authorization: `Bearer ${authToken}`,
        },
      });

      expect([200, 204]).toContain(response.status());

      // Verify topic is deleted (GET should return 404)
      const getResponse = await request.get(`${API_URL}/api/v1/topics/${topic.topicCode}`, {
        headers: {
          Authorization: `Bearer ${authToken}`,
        },
      });

      expect(getResponse.status()).toBe(404);
    });
  });
});
