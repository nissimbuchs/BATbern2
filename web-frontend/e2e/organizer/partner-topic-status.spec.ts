/**
 * E2E test for the Organizer Partner-Topic Status Panel
 * Story 8.2: AC4 (organizer side)
 *
 * Runs in the 'chromium' (organizer) project — `/organizer/partner-topics` is an ORGANIZER
 * route, so this MUST run as an organizer (it was previously stranded in the partner-project
 * topic-voting spec, where the partner user is guarded out and it could never pass).
 *
 * Fully MOCKED + read-only → `@gate` only.
 *
 * Run: cd web-frontend && npx playwright test --project=chromium \
 *   e2e/organizer/partner-topic-status.spec.ts
 */

import { test, expect } from '@playwright/test';
import { BASE_URL } from '../../playwright.config';

const ORGANIZER_TOPICS_URL = `${BASE_URL}/organizer/partner-topics`;

const TOPICS = [
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
];

test.describe('Organizer Partner-Topic Status Panel @gate', () => {
  test.beforeEach(async ({ page }) => {
    await page.route('**/api/v1/users/me*', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          id: 'org',
          email: 'org@example.com',
          // Roles for local-dev Pattern 3b (JWT may carry no custom:role → AuthContext
          // hydrates roles from /users/me); without these the role is stripped → onboarding redirect.
          roles: ['organizer'],
          currentRole: 'organizer',
          termsAcceptedAt: '2020-01-01T00:00:00Z',
          preferences: { language: 'en' },
        }),
      });
    });

    await page.route('**/api/v1/partners/topics', async (route) => {
      if (route.request().method() === 'GET') {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify(TOPICS),
        });
      } else {
        await route.continue();
      }
    });
  });

  test('organizer can open the partner-topics status panel (AC4)', async ({ page }) => {
    await page.goto(ORGANIZER_TOPICS_URL);

    await expect(page.getByTestId('topic-status-panel')).toBeVisible({ timeout: 15000 });
    await expect(page.getByTestId('organizer-topics-table')).toBeVisible();
    await expect(page.getByTestId('organizer-topic-row-e2e-topic-1')).toBeVisible();
  });
});
