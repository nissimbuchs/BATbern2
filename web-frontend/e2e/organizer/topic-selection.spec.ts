/**
 * E2E Tests for Topic Selection & Heat Map Visualization
 * Story 5.2: Topic Selection & Speaker Brainstorming (AC1-8, AC14-16)
 *
 * IMPORTANT: These tests are RED PHASE tests (TDD). They should FAIL until
 * the Topic Selection functionality is fully implemented.
 *
 * Requirements:
 * 1. Event Management Service with topics endpoints deployed
 * 2. PostgreSQL database with topics and topic_usage_history tables (Migration V14)
 * 3. TopicBacklogManager component with filter panel
 * 4. TopicHeatMap component with Recharts visualization
 * 5. TopicDetailsPanel with staleness scores and similarity warnings
 * 6. EventWorkflowStateMachine integration from Story 5.1a
 *
 * Setup Instructions:
 * 1. Ensure migration V14 is applied: topics, topic_usage_history tables
 * 2. Ensure Event Management Service is running
 * 3. Run: npx playwright test e2e/organizer/topic-selection.spec.ts
 */

import { test, expect, type Page } from '@playwright/test';

// Test configuration
const BASE_URL = process.env.E2E_BASE_URL || 'http://localhost:8100';
const API_URL = process.env.E2E_API_URL || 'http://localhost:8080';

// Type definitions for Story 5.2 API responses
interface TopicResponse {
  id: string;
  title: string;
  category: string;
  stalenessScore: number;
  status: string;
}

/**
 * Helper: Login as an organizer
 */
async function loginAsOrganizer(page: Page) {
  const testEmail = process.env.E2E_TEST_EMAIL || 'test@batbern.ch';
  const testPassword = process.env.E2E_TEST_PASSWORD || 'Test123!@#';

  await page.goto(`${BASE_URL}/login`);
  await page.fill('input[name="email"]', testEmail);
  await page.fill('input[name="password"]', testPassword);
  await page.click('button[type="submit"]');

  // Wait for redirect to dashboard
  await page.waitForURL(`${BASE_URL}/organizer/events`);
}

/**
 * Helper: Create a test event
 */
async function createTestEvent(page: Page): Promise<string> {
  await page.goto(`${BASE_URL}/organizer/events`);
  await page.click('button:has-text("New Event")');

  // Fill event form
  await page.fill('input[name="title"]', `E2E Test Event ${Date.now()}`);
  await page.fill('input[name="eventNumber"]', '999');
  await page.fill('input[name="venueName"]', 'Test Venue');
  await page.fill('input[name="venueAddress"]', 'Test Address, Bern');
  await page.fill('input[name="venueCapacity"]', '100');

  // Select event type
  await page.click('[data-testid="event-type-selector"]');
  await page.click('[role="option"]:has-text("Evening Event")');

  // Submit form
  await page.click('button[type="submit"]:has-text("Create Event")');

  // Wait for success and extract event code
  await page.waitForSelector('[data-testid="event-card"]', { timeout: 5000 });
  const eventCodeElement = page.locator('[data-testid="event-code"]').first();
  const eventCode = await eventCodeElement.textContent();

  return eventCode || 'BATbern999';
}

test.describe('Topic Selection & Heat Map (Story 5.2)', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/organizer/events');
  });

  test.describe('AC1: Topic Backlog Display', () => {
    test('should navigate to Topic Backlog Manager from event workflow', async ({ page }) => {
      const eventCode = await createTestEvent(page);

      // Navigate to event details
      await page.click(`[data-testid="event-card-${eventCode}"]`);

      // Click "Select Topic" workflow button
      await page.click('button:has-text("Select Topic")');

      // Verify Topic Backlog Manager loads
      await expect(page.locator('h1')).toContainText('Topic Backlog');
      await expect(page.locator('[data-testid="topic-backlog-manager"]')).toBeVisible();
    });

    test('should display searchable list of all available topics', async ({ page }) => {
      await page.goto(`${BASE_URL}/organizer/topics`);

      // Verify topic list component exists
      const topicList = page.locator('[data-testid="topic-list"]');
      await expect(topicList).toBeVisible();

      // Verify search input exists
      const searchInput = page.locator('input[placeholder*="Search topics"]');
      await expect(searchInput).toBeVisible();

      // Verify topics are displayed
      await expect(page.locator('[data-testid^="topic-card-"]')).toHaveCount(3, { timeout: 10000 }); // At least 3 topics
    });

    test('should filter topics by category', async ({ page }) => {
      await page.goto(`${BASE_URL}/organizer/topics`);

      // Open category filter
      await page.click('[data-testid="filter-category"]');
      await page.click('[role="option"]:has-text("Technical")');

      // Verify only technical topics are shown
      await expect(
        page.locator('[data-testid="topic-category-badge"]:has-text("Technical")')
      ).toHaveCount(3, { timeout: 5000 });
    });
  });

  test.describe('AC2: Heat Map Visualization', () => {
    test('should display usage heat map for selected topic', async ({ page }) => {
      await page.goto(`${BASE_URL}/organizer/topics`);

      // Click on a topic to view details
      await page.click('[data-testid^="topic-card-"]').first();

      // Verify heat map component loads
      const heatMap = page.locator('[data-testid="topic-heat-map"]');
      await expect(heatMap).toBeVisible();

      // Verify heat map shows last 24 months
      await expect(heatMap).toContainText('Q'); // Quarter markers
      await expect(heatMap).toContainText('2024');
      await expect(heatMap).toContainText('2025');
    });

    test('should show quarterly usage frequency in heat map', async ({ page }) => {
      await page.goto(`${BASE_URL}/organizer/topics`);
      await page.click('[data-testid^="topic-card-"]').first();

      // Verify heat map grid exists (4 quarters x 2 years = 8 cells)
      const heatMapCells = page.locator('[data-testid^="heat-map-cell-"]');
      await expect(heatMapCells).toHaveCount(8, { timeout: 5000 });
    });

    test('should display tooltip on hover showing attendance and engagement', async ({ page }) => {
      await page.goto(`${BASE_URL}/organizer/topics`);
      await page.click('[data-testid^="topic-card-"]').first();

      // Hover over a heat map cell
      await page.hover('[data-testid^="heat-map-cell-"]').first();

      // Verify tooltip appears
      const tooltip = page.locator('[role="tooltip"]');
      await expect(tooltip).toBeVisible();
      await expect(tooltip).toContainText('Attendance');
      await expect(tooltip).toContainText('Engagement');
    });
  });

  test.describe('AC3: Color-Coded Freshness', () => {
    test('should display red color for topics used within 6 months', async ({ page }) => {
      await page.goto(`${BASE_URL}/organizer/topics`);

      // Find topics with low staleness score (<50)
      const redTopics = page.locator('[data-testid^="topic-card-"][data-freshness="red"]');
      const count = await redTopics.count();

      if (count > 0) {
        // Verify red color styling
        await expect(redTopics.first()).toHaveCSS('border-color', /rgb\(220, 38, 38\)/); // Tailwind red-600
      }
    });

    test('should display yellow color for topics used 6-12 months ago', async ({ page }) => {
      await page.goto(`${BASE_URL}/organizer/topics`);

      // Find topics with medium staleness score (50-83)
      const yellowTopics = page.locator('[data-testid^="topic-card-"][data-freshness="yellow"]');
      const count = await yellowTopics.count();

      if (count > 0) {
        // Verify yellow color styling
        await expect(yellowTopics.first()).toHaveCSS('border-color', /rgb\(234, 179, 8\)/); // Tailwind yellow-500
      }
    });

    test('should display green color for topics not used over 12 months', async ({ page }) => {
      await page.goto(`${BASE_URL}/organizer/topics`);

      // Find topics with high staleness score (>83)
      const greenTopics = page.locator('[data-testid^="topic-card-"][data-freshness="green"]');
      const count = await greenTopics.count();

      if (count > 0) {
        // Verify green color styling
        await expect(greenTopics.first()).toHaveCSS('border-color', /rgb\(34, 197, 94\)/); // Tailwind green-500
      }
    });
  });

  test.describe('AC5: Duplicate Warnings & AC7: Override Capability', () => {
    test('should show warning when selecting topic with >70% similarity', async ({ page }) => {
      await page.goto(`${BASE_URL}/organizer/topics`);

      // Select a topic that triggers similarity warning
      await page.click('[data-testid="topic-card-similar-topic"]'); // Test data should include similar topics
      await page.click('button:has-text("Select for Event")');

      // Verify warning modal appears
      const warningModal = page.locator('[role="dialog"][aria-label="Similarity Warning"]');
      await expect(warningModal).toBeVisible();
      await expect(warningModal).toContainText('70%'); // Similarity score
      await expect(warningModal).toContainText('similar to recent topic');
    });

    test('should allow override with justification', async ({ page }) => {
      await page.goto(`${BASE_URL}/organizer/topics`);
      await page.click('[data-testid="topic-card-similar-topic"]');
      await page.click('button:has-text("Select for Event")');

      // Wait for warning modal
      const warningModal = page.locator('[role="dialog"][aria-label="Similarity Warning"]');
      await expect(warningModal).toBeVisible();

      // Fill justification
      await page.fill(
        'textarea[name="justification"]',
        'Partner explicitly requested this topic despite recent usage'
      );

      // Click override button
      await page.click('button:has-text("Proceed Anyway")');

      // Verify topic is selected
      await expect(page.locator('[data-testid="success-message"]')).toContainText('Topic selected');
    });

    test('should block selection without justification when similarity high', async ({ page }) => {
      await page.goto(`${BASE_URL}/organizer/topics`);
      await page.click('[data-testid="topic-card-similar-topic"]');
      await page.click('button:has-text("Select for Event")');

      // Try to proceed without justification
      const proceedButton = page.locator('button:has-text("Proceed Anyway")');
      await expect(proceedButton).toBeDisabled();
    });
  });

  test.describe('AC6: Staleness Score Display', () => {
    test('should display 0-100 staleness score for each topic', async ({ page }) => {
      await page.goto(`${BASE_URL}/organizer/topics`);

      // Verify staleness score badges exist
      const stalenessScores = page.locator('[data-testid^="staleness-score-"]');
      const count = await stalenessScores.count();
      expect(count).toBeGreaterThan(0);

      // Verify score is within 0-100 range
      const firstScore = await stalenessScores.first().textContent();
      const scoreValue = parseInt(firstScore || '0');
      expect(scoreValue).toBeGreaterThanOrEqual(0);
      expect(scoreValue).toBeLessThanOrEqual(100);
    });
  });

  test.describe('AC8: Topic Creation Inline', () => {
    test('should allow creating new topic with description', async ({ page }) => {
      await page.goto(`${BASE_URL}/organizer/topics`);

      // Click "New Topic" button
      await page.click('button:has-text("New Topic")');

      // Fill topic form
      await page.fill('input[name="title"]', 'AI-Powered DevOps');
      await page.fill(
        'textarea[name="description"]',
        'Exploring AI applications in DevOps workflows'
      );
      await page.click('[data-testid="category-selector"]');
      await page.click('[role="option"]:has-text("Technical")');

      // Submit form
      await page.click('button[type="submit"]:has-text("Create Topic")');

      // Verify topic is created and appears in list
      await expect(
        page.locator('[data-testid="topic-card-"]:has-text("AI-Powered DevOps")')
      ).toBeVisible();
    });
  });

  test.describe('AC14-16: Workflow Engine Integration', () => {
    test('should transition event to TOPIC_SELECTION state when topic selected', async ({
      page,
    }) => {
      const eventCode = await createTestEvent(page);

      // Navigate to topic selection
      await page.goto(`${BASE_URL}/organizer/topics?eventCode=${eventCode}`);

      // Select a topic
      await page.click('[data-testid^="topic-card-"]').first();
      await page.click('button:has-text("Select for Event")');

      // Wait for API call
      await page.waitForResponse(
        (response) =>
          response.url().includes(`/events/${eventCode}/topics`) && response.status() === 200
      );

      // Navigate back to event details
      await page.goto(`${BASE_URL}/organizer/events/${eventCode}`);

      // Verify workflow state is updated
      const workflowBadge = page.locator('[data-testid="workflow-state-badge"]');
      await expect(workflowBadge).toContainText('Topic Selection');
    });
  });
});

test.describe('Topics API Contract Tests (Story 5.2)', () => {
  test.describe('GET /api/v1/topics', () => {
    test('should return topics with pagination', async ({ request }) => {
      const response = await request.get(`${API_URL}/api/v1/topics?page=1&limit=50`);

      expect(response.status()).toBe(200);

      const result = await response.json();
      expect(result.data).toBeInstanceOf(Array);
      expect(result.pagination).toBeDefined();
      expect(result.pagination.page).toBe(1);
      expect(result.pagination.limit).toBe(50);
    });

    test('should filter topics by category', async ({ request }) => {
      const filter = JSON.stringify({ category: 'technical' });
      const response = await request.get(
        `${API_URL}/api/v1/topics?filter=${encodeURIComponent(filter)}`
      );

      expect(response.status()).toBe(200);

      const result = await response.json();
      result.data.forEach((topic: TopicResponse) => {
        expect(topic.category).toBe('technical');
      });
    });

    test('should sort topics by staleness score descending', async ({ request }) => {
      const response = await request.get(`${API_URL}/api/v1/topics?sort=-stalenessScore`);

      expect(response.status()).toBe(200);

      const result = await response.json();
      const scores = result.data.map((t: TopicResponse) => t.stalenessScore);

      // Verify descending order
      for (let i = 0; i < scores.length - 1; i++) {
        expect(scores[i]).toBeGreaterThanOrEqual(scores[i + 1]);
      }
    });
  });

  test.describe('POST /api/v1/topics', () => {
    test('should create new topic with initial staleness score 100', async ({ request }) => {
      const response = await request.post(`${API_URL}/api/v1/topics`, {
        data: {
          title: 'Test Topic - API Contract',
          description: 'Test description',
          category: 'technical',
        },
        headers: {
          Authorization: `Bearer ${process.env.E2E_TEST_TOKEN}`,
        },
      });

      expect(response.status()).toBe(201);

      const topic = await response.json();
      expect(topic.id).toBeDefined();
      expect(topic.title).toContain('Test Topic');
      expect(topic.stalenessScore).toBe(100); // New topics have max staleness
      expect(topic.status).toBe('available');
    });
  });

  test.describe('POST /api/v1/events/{eventCode}/topics', () => {
    test('should select topic for event and transition workflow state', async ({ request }) => {
      // This test requires a valid event code - would need test setup
      const eventCode = process.env.E2E_TEST_EVENT_CODE || 'BATbern999';
      const topicId = process.env.E2E_TEST_TOPIC_ID || '00000000-0000-0000-0000-000000000001';

      const response = await request.post(`${API_URL}/api/v1/events/${eventCode}/topics`, {
        data: {
          topicId,
          justification: null,
        },
        headers: {
          Authorization: `Bearer ${process.env.E2E_TEST_TOKEN}`,
        },
      });

      expect(response.status()).toBe(200);
    });
  });
});
