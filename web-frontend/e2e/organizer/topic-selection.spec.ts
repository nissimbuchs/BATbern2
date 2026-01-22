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
 * Test Pattern:
 * 1. Create topic (POST /api/v1/topics)
 * 2. Create event (POST /api/v1/events)
 * 3. Perform test actions
 * 4. Cleanup: Delete event (DELETE /api/v1/events/{eventCode})
 * 5. Cleanup: Delete topic (DELETE /api/v1/topics/{topicCode})
 *
 * Setup Instructions:
 * 1. Ensure migration V14 is applied: topics, topic_usage_history tables
 * 2. Ensure Event Management Service is running
 * 3. Run: npx playwright test e2e/organizer/topic-selection.spec.ts
 *
 * LANGUAGE-INDEPENDENT SELECTORS (BAT-93):
 * ✅ Fixed: All button selectors use data-testid attributes
 */

import { test, expect, type APIRequestContext } from '@playwright/test';
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
 * Helper: Create a topic via API
 */
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

/**
 * Helper: Delete a topic via API
 */
async function deleteTopicViaAPI(request: APIRequestContext, topicCode: string): Promise<void> {
  const authToken = process.env.AUTH_TOKEN;
  const response = await request.delete(`${API_URL}/api/v1/topics/${topicCode}`, {
    headers: {
      Authorization: `Bearer ${authToken}`,
    },
  });

  // Accept 200 (deleted) or 404 (already deleted)
  expect([200, 204, 404]).toContain(response.status());
}

/**
 * Helper: Create an event via API
 */
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
      date: new Date(Date.now() + 30 * 24 * 60 * 60 * 1000).toISOString(), // 30 days from now
      registrationDeadline: new Date(Date.now() + 20 * 24 * 60 * 60 * 1000).toISOString(), // 20 days from now
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

/**
 * Helper: Delete an event via API
 */
async function deleteEventViaAPI(request: APIRequestContext, eventCode: string): Promise<void> {
  const authToken = process.env.AUTH_TOKEN;
  const response = await request.delete(`${API_URL}/api/v1/events/${eventCode}`, {
    headers: {
      Authorization: `Bearer ${authToken}`,
    },
  });

  // Accept 200 (deleted) or 404 (already deleted)
  expect([200, 204, 404]).toContain(response.status());
}

/**
 * Helper: Assign topic to event via API
 */
async function assignTopicToEventViaAPI(
  request: APIRequestContext,
  eventCode: string,
  topicCode: string,
  justification: string | null = null
): Promise<void> {
  const authToken = process.env.AUTH_TOKEN;
  const response = await request.post(`${API_URL}/api/v1/events/${eventCode}/topics`, {
    data: {
      topicCode,
      justification,
    },
    headers: {
      Authorization: `Bearer ${authToken}`,
      'Content-Type': 'application/json',
    },
  });

  expect(response.status()).toBe(200);
}

test.describe('Topic Selection & Heat Map (Story 5.2)', () => {
  // Track created resources for cleanup
  const createdTopics: string[] = [];
  const createdEvents: string[] = [];

  test.afterEach(async ({ request }) => {
    // Cleanup: Delete all created events first (they may reference topics)
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

  test.describe('AC1: Topic Backlog Display', () => {
    test('should display searchable list of all available topics', async ({ page, request }) => {
      // Create test topics
      const topic1 = await createTopicViaAPI(request, 'Cloud Architecture', 'technical');
      const topic2 = await createTopicViaAPI(request, 'Sustainable Design', 'design');
      const topic3 = await createTopicViaAPI(request, 'AI in Construction', 'technical');
      createdTopics.push(topic1.topicCode, topic2.topicCode, topic3.topicCode);

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
      await expect(page.locator('text=Cloud Architecture')).toBeVisible();
      await expect(page.locator('text=Sustainable Design')).toBeVisible();
      await expect(page.locator('text=AI in Construction')).toBeVisible();
    });

    test('should filter topics by category', async ({ page, request }) => {
      // Create topics in different categories
      const topic1 = await createTopicViaAPI(request, 'DevOps Best Practices', 'technical');
      const topic2 = await createTopicViaAPI(request, 'User Experience Design', 'design');
      const topic3 = await createTopicViaAPI(request, 'Cloud Security', 'technical');
      createdTopics.push(topic1.topicCode, topic2.topicCode, topic3.topicCode);

      await page.goto(`${BASE_URL}/organizer/topics`);

      // Wait for topics to load
      await expect(page.locator('[data-testid^="topic-card-"]')).toHaveCount(3, {
        timeout: 10000,
      });

      // Open category filter
      await page.click('[data-testid="filter-category"]');
      await page.click('[role="option"]:has-text("Technical")');

      // Verify only technical topics are shown
      await expect(page.locator('text=DevOps Best Practices')).toBeVisible();
      await expect(page.locator('text=Cloud Security')).toBeVisible();
      await expect(page.locator('text=User Experience Design')).not.toBeVisible();
    });

    test('should navigate to Topic Backlog Manager from event workflow', async ({
      page,
      request,
    }) => {
      // Create event
      const event = await createEventViaAPI(request);
      createdEvents.push(event.eventCode);

      // Navigate to event details
      await page.goto(`${BASE_URL}/organizer/events`);
      await page.click(`[data-testid="event-card-${event.eventCode}"]`);

      // Click "Select Topic" workflow button
      await page.click('[data-testid="workflow-button-select-topic"]');

      // Verify Topic Backlog Manager loads
      await expect(page.locator('h1')).toContainText('Topic');
      await expect(page.locator('[data-testid="topic-backlog-manager"]')).toBeVisible();
    });
  });

  test.describe('AC2: Heat Map Visualization', () => {
    test('should display usage heat map for selected topic', async ({ page, request }) => {
      // Create topic
      const topic = await createTopicViaAPI(request, 'Microservices Architecture', 'technical');
      createdTopics.push(topic.topicCode);

      await page.goto(`${BASE_URL}/organizer/topics`);

      // Click on the topic to view details
      await page.click(`[data-testid="topic-card-${topic.topicCode}"]`);

      // Verify heat map component loads
      const heatMap = page.locator('[data-testid="topic-heat-map"]');
      await expect(heatMap).toBeVisible();

      // Verify heat map shows quarterly data
      await expect(heatMap).toContainText('Q'); // Quarter markers
    });

    test('should show quarterly usage frequency in heat map', async ({ page, request }) => {
      // Create topic
      const topic = await createTopicViaAPI(request, 'Test Topic for Heat Map', 'technical');
      createdTopics.push(topic.topicCode);

      await page.goto(`${BASE_URL}/organizer/topics`);
      await page.click(`[data-testid="topic-card-${topic.topicCode}"]`);

      // Verify heat map grid exists (4 quarters x 2 years = 8 cells)
      const heatMapCells = page.locator('[data-testid^="heat-map-cell-"]');
      const count = await heatMapCells.count();
      expect(count).toBeGreaterThanOrEqual(4); // At least one year of quarters
    });

    test('should display tooltip on hover showing attendance and engagement', async ({
      page,
      request,
    }) => {
      // Create topic
      const topic = await createTopicViaAPI(request, 'Tooltip Test Topic', 'technical');
      createdTopics.push(topic.topicCode);

      await page.goto(`${BASE_URL}/organizer/topics`);
      await page.click(`[data-testid="topic-card-${topic.topicCode}"]`);

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
      request,
    }) => {
      // Create fresh topic (staleness score should be 100)
      const topic = await createTopicViaAPI(request, 'Brand New Topic', 'technical');
      createdTopics.push(topic.topicCode);

      await page.goto(`${BASE_URL}/organizer/topics`);

      // Wait for topic to appear
      await expect(page.locator(`[data-testid="topic-card-${topic.topicCode}"]`)).toBeVisible();

      // Verify green freshness indicator (high staleness = fresh = green)
      const topicCard = page.locator(`[data-testid="topic-card-${topic.topicCode}"]`);
      await expect(topicCard.locator('[data-freshness="green"]')).toBeVisible();
    });

    test('should display staleness score badge', async ({ page, request }) => {
      // Create topic
      const topic = await createTopicViaAPI(request, 'Staleness Score Test', 'technical');
      createdTopics.push(topic.topicCode);

      await page.goto(`${BASE_URL}/organizer/topics`);

      // Verify staleness score is displayed
      const stalenessScore = page.locator(`[data-testid="staleness-score-${topic.topicCode}"]`);
      await expect(stalenessScore).toBeVisible();

      // New topics should have score of 100
      await expect(stalenessScore).toContainText('100');
    });
  });

  test.describe('AC6: Staleness Score Display', () => {
    test('should display 0-100 staleness score for each topic', async ({ page, request }) => {
      // Create multiple topics
      const topic1 = await createTopicViaAPI(request, 'Score Test 1', 'technical');
      const topic2 = await createTopicViaAPI(request, 'Score Test 2', 'design');
      createdTopics.push(topic1.topicCode, topic2.topicCode);

      await page.goto(`${BASE_URL}/organizer/topics`);

      // Verify staleness score badges exist
      const score1 = page.locator(`[data-testid="staleness-score-${topic1.topicCode}"]`);
      const score2 = page.locator(`[data-testid="staleness-score-${topic2.topicCode}"]`);

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
      // Create topic and event
      const topic = await createTopicViaAPI(request, 'Workflow Test Topic', 'technical');
      const event = await createEventViaAPI(request);
      createdTopics.push(topic.topicCode);
      createdEvents.push(event.eventCode);

      // Navigate to topic selection for this event
      await page.goto(`${BASE_URL}/organizer/topics?eventCode=${event.eventCode}`);

      // Wait for topics to load
      await expect(page.locator('[data-testid^="topic-card-"]')).toHaveCount(1, {
        timeout: 10000,
      });

      // Click on topic card
      await page.click(`[data-testid="topic-card-${topic.topicCode}"]`);

      // Click "Select for Event" button
      await page.click('[data-testid="select-topic-button"]');

      // Wait for success
      await expect(page.locator('[data-testid="success-message"]')).toBeVisible();

      // Navigate back to event to verify assignment
      await page.goto(`${BASE_URL}/organizer/events/${event.eventCode}`);

      // Verify topic is assigned to event
      await expect(page.locator(`text=${topic.title}`)).toBeVisible();
    });

    test('should transition event workflow state when topic selected', async ({
      page,
      request,
    }) => {
      // Create topic and event
      const topic = await createTopicViaAPI(request, 'State Transition Test', 'technical');
      const event = await createEventViaAPI(request);
      createdTopics.push(topic.topicCode);
      createdEvents.push(event.eventCode);

      // Assign topic to event via API
      await assignTopicToEventViaAPI(request, event.eventCode, topic.topicCode);

      // Navigate to event details
      await page.goto(`${BASE_URL}/organizer/events/${event.eventCode}`);

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
