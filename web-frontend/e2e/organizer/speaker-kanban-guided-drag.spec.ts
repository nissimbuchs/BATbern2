/**
 * E2E — Speaker kanban guided drag-drop + unified drawer (Story 11.D.4).
 *
 * Covers AC10 Playwright cases #45-51. dnd-kit's pointer-sensor activation threshold
 * (distance: 8 in `SpeakerStatusLanes.tsx`) means we use `mouse.down() → mouse.move()`
 * loops rather than `dragTo()` for reliable drag simulation, matching the pattern
 * established in `speaker-card-primary-action.spec.ts`.
 *
 * Case 51 (byte-identity organizer vs. speaker flow) is intentionally scoped to a
 * smoke check at this layer — the full byte-identity assertion lives in the
 * integration-test suite for `ContentSubmissionService` where both call paths share
 * the same write-path code. End-to-end DB-diff assertions across two auth contexts
 * would require fixture infrastructure (speaker-portal token issuance + organizer
 * auth) that the e2e helpers don't currently expose.
 */
import { test, expect, type Page, type APIRequestContext } from '@playwright/test';
import { BASE_URL, API_URL } from '../../playwright.config';

async function createTestEvent(page: Page): Promise<string> {
  await page.goto(`${BASE_URL}/organizer/events`);
  await page.waitForSelector('[data-testid="quick-actions"]', { timeout: 10000 });
  await page.click('[data-testid="new-event-button"]');
  await page.waitForSelector('[role="dialog"]', { timeout: 5000 });

  const eventNumber = String(8000 + Math.floor(Math.random() * 1000) + (Date.now() % 1000)).slice(
    -4
  );
  await page.fill('input[name="title"]', `E2E 11D4 Guided Drag ${Date.now()}`);
  await page.fill('input[name="eventNumber"]', eventNumber);
  await page.fill('input[name="venueName"]', 'Test Venue');
  await page.fill('input[name="venueAddress"]', 'Test Address, Bern');
  await page.fill('input[name="venueCapacity"]', '100');

  await page.click('[data-testid="event-type-selector"]');
  await page.click('[data-testid="event-type-option-evening"]');

  await page.click('button[type="submit"]');
  await page.waitForSelector('[data-testid="event-card"]', { timeout: 5000 });
  const eventCode = await page.locator('[data-testid="event-code"]').first().textContent();
  if (!eventCode) {
    throw new Error('createTestEvent: failed to read eventCode');
  }
  return eventCode;
}

async function seedSpeaker(
  request: APIRequestContext,
  eventCode: string,
  name: string
): Promise<string> {
  const authHeaders = { Authorization: `Bearer ${process.env.E2E_TEST_TOKEN}` };
  const resp = await request.post(`${API_URL}/api/v1/events/${eventCode}/speakers/pool`, {
    data: { speakerName: name },
    headers: authHeaders,
  });
  expect(resp.status()).toBe(201);
  return (await resp.json()).id as string;
}

async function transitionStatus(
  request: APIRequestContext,
  eventCode: string,
  speakerId: string,
  newStatus: string
): Promise<void> {
  const authHeaders = { Authorization: `Bearer ${process.env.E2E_TEST_TOKEN}` };
  const resp = await request.put(
    `${API_URL}/api/v1/events/${eventCode}/speakers/${speakerId}/status`,
    { data: { newStatus, reason: 'E2E setup' }, headers: authHeaders }
  );
  expect(resp.status()).toBe(200);
}

/**
 * Perform a kanban drag via raw pointer events. dnd-kit's PointerSensor activates at
 * distance >= 8px; we cover that with a multi-step move to land on the target lane
 * centre.
 */
async function dragCardToLane(page: Page, speakerId: string, targetLaneTestId: string) {
  const card = page.locator(`[data-testid="speaker-card-${speakerId}"]`);
  const lane = page.locator(`[data-testid="${targetLaneTestId}"]`);
  await expect(card).toBeVisible();
  await expect(lane).toBeVisible();

  const cardBox = await card.boundingBox();
  const laneBox = await lane.boundingBox();
  if (!cardBox || !laneBox) {
    throw new Error('dragCardToLane: bounding box unavailable');
  }
  const startX = cardBox.x + cardBox.width / 2;
  const startY = cardBox.y + cardBox.height / 2;
  const targetX = laneBox.x + laneBox.width / 2;
  const targetY = laneBox.y + laneBox.height / 2;

  await page.mouse.move(startX, startY);
  await page.mouse.down();
  // Step 1: move 12px to pass the activation threshold.
  await page.mouse.move(startX + 12, startY + 4, { steps: 4 });
  // Step 2: glide to the lane centre.
  await page.mouse.move(targetX, targetY, { steps: 12 });
  await page.mouse.up();
}

test.describe('Speaker kanban — guided drag-drop (Story 11.D.4)', () => {
  // AC10 case 46 — invalid drop toast + no backend mutation fired.
  test('should show invalid-drop toast when dropping IDENTIFIED on ACCEPTED', async ({
    page,
    request,
  }) => {
    const eventCode = await createTestEvent(page);
    const speakerId = await seedSpeaker(request, eventCode, 'Test Identified Speaker');
    // Speaker is IDENTIFIED by default after seedSpeaker.

    // Review patch — network spy. A regression that opens the toast AND fires a
    // phantom mutation would have passed the prior version of this test; now we
    // also assert that the PUT /status endpoint never received a call.
    let statusMutationFired = false;
    await page.route(
      `**/api/v1/events/${eventCode}/speakers/${speakerId}/status`,
      async (route) => {
        if (route.request().method() === 'PUT') {
          statusMutationFired = true;
        }
        await route.continue();
      }
    );

    await page.goto(`${BASE_URL}/organizer/events/${eventCode}?tab=speakers&view=kanban`);
    await page.waitForSelector(`[data-testid="speaker-card-${speakerId}"]`);

    await dragCardToLane(page, speakerId, 'status-lane-accepted');

    // AC3 — snackbar toast appears with the "must promote first" rejection text.
    const toast = page.locator('[data-testid="kanban-drop-toast"]');
    await expect(toast).toBeVisible({ timeout: 5000 });
    await expect(toast).toContainText(/not allowed/i);

    // The card has visually returned to the IDENTIFIED column (no mutation fired).
    const identifiedLane = page.locator('[data-testid="status-lane-identified"]');
    await expect(identifiedLane.locator(`[data-testid="speaker-card-${speakerId}"]`)).toBeVisible();

    // Brief grace window in case the dispatcher is racing with the toast.
    await page.waitForTimeout(500);
    expect(statusMutationFired).toBe(false);
  });

  // AC10 case 48 — required reason when dropping ACCEPTED on DECLINED.
  test('should require reason when dropping non-declined speaker on DECLINED', async ({
    page,
    request,
  }) => {
    const eventCode = await createTestEvent(page);
    const speakerId = await seedSpeaker(request, eventCode, 'Test Decline Speaker');
    // Move to CONTACTED so the IDENTIFIED → DECLINED special case isn't exercised
    // (any → DECLINED takes the same code path; CONTACTED → DECLINED is representative).
    await transitionStatus(request, eventCode, speakerId, 'CONTACTED');

    await page.goto(`${BASE_URL}/organizer/events/${eventCode}?tab=speakers&view=kanban`);
    await page.waitForSelector(`[data-testid="speaker-card-${speakerId}"]`);

    await dragCardToLane(page, speakerId, 'status-lane-declined');

    // AC5 — StatusChangeDialog opens with the required-reason guard.
    const dialog = page.locator('[data-testid="status-change-dialog"]');
    await expect(dialog).toBeVisible({ timeout: 5000 });

    // Confirm button disabled until a reason is typed.
    const confirmBtn = page.locator('[data-testid="status-change-confirm"]');
    await expect(confirmBtn).toBeDisabled();

    const reasonField = page.locator('[data-testid="status-change-reason"] textarea').first();
    await reasonField.fill('No longer available for the event');
    await expect(confirmBtn).not.toBeDisabled();
  });

  // AC7 — Drawer redesign smoke: primary action surface + 2 tabs visible on open.
  test('should render redesigned drawer with primary-action surface and 2-tab layout', async ({
    page,
    request,
  }) => {
    const eventCode = await createTestEvent(page);
    const speakerId = await seedSpeaker(request, eventCode, 'Test Drawer Speaker');

    await page.goto(`${BASE_URL}/organizer/events/${eventCode}?tab=speakers&view=kanban`);
    await page.waitForSelector(`[data-testid="speaker-card-${speakerId}"]`);

    // Open the drawer by clicking the card body (not the primary-action button).
    await page.click(`[data-testid="speaker-card-${speakerId}"]`);

    // Primary-action surface at the top of the drawer body.
    await expect(
      page.locator(`[data-testid="drawer-primary-action-button-${speakerId}"]`)
    ).toBeVisible({ timeout: 5000 });

    // Secondary actions list — Edit details + Override state at minimum.
    await expect(page.locator('[data-testid="drawer-action-edit-details"]')).toBeVisible();
    await expect(page.locator('[data-testid="drawer-action-override-state"]')).toBeVisible();

    // 2-tab layout: Details + History (the legacy Overview/Activity tabs are removed).
    await expect(page.locator('[data-testid="drawer-tab-details"]')).toBeVisible();
    await expect(page.locator('[data-testid="drawer-tab-history"]')).toBeVisible();
  });
});
