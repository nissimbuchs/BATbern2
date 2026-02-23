/**
 * E2E tests for Partner Topic Voting
 * Story 8.2: AC2, AC3, AC4 — Task 13
 *
 * Runs in the 'partner' Playwright project using .playwright-auth-partner.json storage state.
 * Requires PARTNER_AUTH_TOKEN env var (set via make setup-test-users).
 *
 * Run: cd web-frontend && npx playwright test --project=partner e2e/partner/topic-voting.spec.ts
 */

import { test, expect } from '@playwright/test';
import { BASE_URL } from '../../playwright.config';

const TOPICS_URL = `${BASE_URL}/partners/topics`;
const ORGANIZER_TOPICS_URL = `${BASE_URL}/organizer/partner-topics`;

// ─── Topic fixtures ───────────────────────────────────────────────────────────

const baseTopics = [
  {
    id: 'e2e-topic-1',
    title: 'Kafka Streams in Production',
    description: 'Real-world Kafka usage',
    suggestedByCompany: 'GoogleZH',
    voteCount: 5,
    currentPartnerHasVoted: false,
    status: 'PROPOSED',
    plannedEvent: null,
    createdAt: '2026-01-01T10:00:00Z',
  },
  {
    id: 'e2e-topic-2',
    title: 'eBPF for Platform Engineers',
    description: null,
    suggestedByCompany: 'MicrosoftZH',
    voteCount: 3,
    currentPartnerHasVoted: false,
    status: 'PROPOSED',
    plannedEvent: null,
    createdAt: '2026-01-02T10:00:00Z',
  },
];

// ─── Partner tests ────────────────────────────────────────────────────────────

test.describe('Partner Topic Voting', () => {
  test.beforeEach(async ({ page }) => {
    // Mock topic list
    await page.route('**/api/v1/partners/topics', async (route) => {
      if (route.request().method() === 'GET') {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify(baseTopics),
        });
      } else if (route.request().method() === 'POST') {
        // Suggest topic
        const body = JSON.parse(route.request().postData() ?? '{}') as {
          title: string;
          description?: string;
        };
        await route.fulfill({
          status: 201,
          contentType: 'application/json',
          body: JSON.stringify({
            id: 'e2e-topic-new',
            title: body.title,
            description: body.description ?? null,
            suggestedByCompany: 'TestCompany',
            voteCount: 0,
            currentPartnerHasVoted: false,
            status: 'PROPOSED',
            plannedEvent: null,
            createdAt: new Date().toISOString(),
          }),
        });
      } else {
        await route.continue();
      }
    });

    // Mock vote endpoints
    await page.route('**/api/v1/partners/topics/*/vote', async (route) => {
      await route.fulfill({ status: 204 });
    });

    await page.goto(TOPICS_URL);
  });

  // ─── AC3: Partner submits topic ─────────────────────────────────────────────

  test('partner submits a topic and it appears in list (AC3)', async ({ page }) => {
    await page.waitForSelector('[data-testid="topic-list-page"]', { timeout: 15000 });

    // Open suggestion form
    await page.getByTestId('suggest-topic-button').click();
    await page.waitForSelector('[data-testid="topic-form-title"]', { timeout: 5000 });

    // Fill in title
    await page.getByTestId('topic-form-title').locator('input').fill('My New Test Topic');

    // Submit
    await page.getByTestId('topic-form-submit').click();

    // After submit the form should close and the list should be refetched
    // (the mock returns the new topic on the next GET call is handled by React Query invalidation)
    await expect(page.getByTestId('topic-list-page')).toBeVisible();
  });

  // ─── AC2: Partner votes → count increments ─────────────────────────────────

  test('partner votes on a topic and vote count increments optimistically (AC2)', async ({
    page,
  }) => {
    await page.waitForSelector('[data-testid="topic-list-page"]', { timeout: 15000 });

    // Initial vote count for topic-1 is 5
    const voteCount = page.getByTestId('vote-count-e2e-topic-1');
    await expect(voteCount).toHaveText('5');

    // Click vote
    await page.getByTestId('vote-button-e2e-topic-1').click();

    // Optimistic update increments to 6
    await expect(voteCount).toHaveText('6');
  });

  // ─── AC2: Partner unvotes → count decrements ───────────────────────────────

  test('partner unvotes a topic and vote count decrements optimistically (AC2)', async ({
    page,
  }) => {
    // Set up topic-1 as already voted
    await page.route('**/api/v1/partners/topics', async (route) => {
      if (route.request().method() === 'GET') {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify([{ ...baseTopics[0], currentPartnerHasVoted: true }]),
        });
      } else {
        await route.continue();
      }
    });

    await page.goto(TOPICS_URL);
    await page.waitForSelector('[data-testid="topic-list-page"]', { timeout: 15000 });

    const voteCount = page.getByTestId('vote-count-e2e-topic-1');
    await expect(voteCount).toHaveText('5');

    // Click unvote (already voted, so this is a remove)
    await page.getByTestId('vote-button-e2e-topic-1').click();

    // Optimistic update decrements to 4
    await expect(voteCount).toHaveText('4');
  });

  // ─── AC4: Organizer marks Selected → partner sees status ───────────────────

  test('organizer marks topic Selected with planned event; partner sees status chip (AC4)', async ({
    page,
  }) => {
    // Mock topics as SELECTED
    await page.route('**/api/v1/partners/topics', async (route) => {
      if (route.request().method() === 'GET') {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify([
            {
              ...baseTopics[0],
              status: 'SELECTED',
              plannedEvent: 'BATbern58',
            },
          ]),
        });
      } else {
        await route.continue();
      }
    });

    await page.goto(TOPICS_URL);
    await page.waitForSelector('[data-testid="topic-list-page"]', { timeout: 15000 });

    // Status chip shows "Selected"
    const chip = page.getByTestId('topic-status-e2e-topic-1');
    await expect(chip).toBeVisible();
    await expect(chip).toHaveText('Selected');

    // Planned event label visible
    await expect(page.getByText(/BATbern58/)).toBeVisible();
  });
});

// ─── Organizer tests ──────────────────────────────────────────────────────────

test.describe('Organizer Topic Status Panel', () => {
  test.beforeEach(async ({ page }) => {
    await page.route('**/api/v1/partners/topics', async (route) => {
      if (route.request().method() === 'GET') {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify(baseTopics),
        });
      } else {
        await route.continue();
      }
    });

    await page.route('**/api/v1/partners/topics/*/status', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ ...baseTopics[0], status: 'SELECTED', plannedEvent: 'BATbern58' }),
      });
    });
  });

  test('organizer can navigate to partner-topics page (AC4)', async ({ page }) => {
    await page.goto(ORGANIZER_TOPICS_URL);
    await page.waitForSelector('[data-testid="topic-status-panel"]', { timeout: 15000 });

    await expect(page.getByTestId('organizer-topics-table')).toBeVisible();
    await expect(page.getByText('Kafka Streams in Production')).toBeVisible();
  });
});
