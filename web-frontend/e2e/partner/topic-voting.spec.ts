/**
 * E2E tests for Partner Topic Voting (partner side)
 * Story 8.2: AC2, AC3, AC4
 *
 * Runs in the 'partner' Playwright project using .playwright-auth-partner.json storage state.
 * Requires PARTNER_AUTH_TOKEN. Fully MOCKED + read-only → `@gate` only.
 *
 * Reality / reliability fixes (slice 11, plan §C):
 * - The topic list mock is now STATEFUL: a vote POST/DELETE mutates the in-memory topic, so
 *   the component's optimistic update AND its `onSuccess` invalidate→refetch BOTH settle on
 *   the same count. The old static mock returned the original count on refetch, which
 *   instantly reverted the optimistic value — the assertion raced and lost.
 * - `/api/v1/users/me` is mocked to `language: en` so LanguageSync does not flip the UI to
 *   the partner test user's backend German preference (the status-chip text assertion needs EN).
 * - The old "Organizer Topic Status Panel" describe lived in THIS partner-project file but
 *   navigated to the ORGANIZER route `/organizer/partner-topics` — a partner user is guarded
 *   out, so it could never pass here. It moved to e2e/organizer/partner-topic-status.spec.ts
 *   (chromium project).
 *
 * Run: cd web-frontend && PARTNER_AUTH_TOKEN=$(jq -r .idToken ~/.batbern/staging-partner.json) \
 *   npx playwright test --project=partner e2e/partner/topic-voting.spec.ts
 */

import { test, expect, type Page } from '@playwright/test';
import { BASE_URL } from '../../playwright.config';

const TOPICS_URL = `${BASE_URL}/partners/topics`;

interface MockTopic {
  id: string;
  title: string;
  description: string | null;
  suggestedByCompany: string;
  voteCount: number;
  currentPartnerHasVoted: boolean;
  status: 'PROPOSED' | 'SELECTED' | 'DECLINED';
  plannedEvent: string | null;
  createdAt: string;
}

function seedTopics(): MockTopic[] {
  return [
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
}

/**
 * Wire a stateful topic API onto the page. `mutate` lets a test pre-adjust the seed
 * (e.g. mark a topic SELECTED, or pre-voted) before the page loads.
 */
async function mockTopicApi(page: Page, mutate?: (topics: MockTopic[]) => void): Promise<void> {
  const topics = seedTopics();
  mutate?.(topics);

  // Force EN so LanguageSync doesn't switch to the user's backend (German) preference.
  await page.route('**/api/v1/users/me*', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        id: 'test-partner',
        email: 'partner@example.com',
        // Roles for local-dev Pattern 3b (JWT may carry no custom:role → AuthContext
        // hydrates roles from /users/me); without these the role is stripped → onboarding redirect.
        roles: ['partner'],
        currentRole: 'partner',
        termsAcceptedAt: '2020-01-01T00:00:00Z',
        preferences: { language: 'en' },
      }),
    });
  });

  // Vote toggle (POST cast / DELETE remove) — mutate state then 204.
  await page.route('**/api/v1/partners/topics/*/vote', async (route) => {
    const url = route.request().url();
    const id = url.match(/topics\/([^/]+)\/vote/)?.[1];
    const topic = topics.find((t) => t.id === id);
    if (topic) {
      if (route.request().method() === 'POST') {
        topic.voteCount += 1;
        topic.currentPartnerHasVoted = true;
      } else if (route.request().method() === 'DELETE') {
        topic.voteCount = Math.max(0, topic.voteCount - 1);
        topic.currentPartnerHasVoted = false;
      }
    }
    await route.fulfill({ status: 204 });
  });

  // List + suggest.
  await page.route('**/api/v1/partners/topics', async (route) => {
    const method = route.request().method();
    if (method === 'GET') {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(topics),
      });
    } else if (method === 'POST') {
      const body = JSON.parse(route.request().postData() ?? '{}') as {
        title: string;
        description?: string;
      };
      const created: MockTopic = {
        id: 'e2e-topic-new',
        title: body.title,
        description: body.description ?? null,
        suggestedByCompany: 'TestCompany',
        voteCount: 0,
        currentPartnerHasVoted: false,
        status: 'PROPOSED',
        plannedEvent: null,
        createdAt: '2026-01-03T10:00:00Z',
      };
      topics.push(created);
      await route.fulfill({
        status: 201,
        contentType: 'application/json',
        body: JSON.stringify(created),
      });
    } else {
      await route.continue();
    }
  });
}

test.describe('Partner Topic Voting @gate', () => {
  // ─── AC3: Partner submits a topic ───────────────────────────────────────────

  test('partner submits a topic and the form closes (AC3)', async ({ page }) => {
    await mockTopicApi(page);
    await page.goto(TOPICS_URL);
    await expect(page.getByTestId('topic-list-page')).toBeVisible({ timeout: 15000 });

    await page.getByTestId('suggest-topic-button').click();
    await expect(page.getByTestId('topic-form-title')).toBeVisible();

    await page.getByTestId('topic-form-title').locator('input').fill('My New Test Topic');
    await page.getByTestId('topic-form-submit').click();

    // Form closes (the submit dialog is dismissed) and the list is still shown.
    await expect(page.getByTestId('topic-form-title')).toBeHidden();
    await expect(page.getByTestId('topic-list-page')).toBeVisible();
  });

  // ─── AC2: Vote increments (optimistic + refetch agree via stateful mock) ────

  test('partner votes on a topic and the count increments (AC2)', async ({ page }) => {
    await mockTopicApi(page);
    await page.goto(TOPICS_URL);
    await expect(page.getByTestId('topic-list-page')).toBeVisible({ timeout: 15000 });

    const voteCount = page.getByTestId('vote-count-e2e-topic-1');
    await expect(voteCount).toHaveText('5');

    await page.getByTestId('vote-button-e2e-topic-1').click();
    await expect(voteCount).toHaveText('6');
  });

  // ─── AC2: Vote decrements when already voted ────────────────────────────────

  test('partner unvotes a topic and the count decrements (AC2)', async ({ page }) => {
    await mockTopicApi(page, (topics) => {
      topics[0].currentPartnerHasVoted = true;
    });
    await page.goto(TOPICS_URL);
    await expect(page.getByTestId('topic-list-page')).toBeVisible({ timeout: 15000 });

    const voteCount = page.getByTestId('vote-count-e2e-topic-1');
    await expect(voteCount).toHaveText('5');

    await page.getByTestId('vote-button-e2e-topic-1').click();
    await expect(voteCount).toHaveText('4');
  });

  // ─── AC4: Partner sees Selected status + planned event ──────────────────────

  test('partner sees the Selected status chip and planned event (AC4)', async ({ page }) => {
    await mockTopicApi(page, (topics) => {
      topics[0].status = 'SELECTED';
      topics[0].plannedEvent = 'BATbern58';
    });
    await page.goto(TOPICS_URL);
    await expect(page.getByTestId('topic-list-page')).toBeVisible({ timeout: 15000 });

    const chip = page.getByTestId('topic-status-e2e-topic-1');
    await expect(chip).toBeVisible();
    await expect(chip).toHaveText('Selected');

    await expect(page.getByTestId('topic-planned-event-e2e-topic-1')).toContainText('BATbern58');
  });
});
