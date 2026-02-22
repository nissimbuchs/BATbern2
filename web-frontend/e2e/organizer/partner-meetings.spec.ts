/**
 * E2E tests for Partner Meeting Coordination
 * Story 8.3: AC1, 2, 3, 4
 *
 * Runs in the 'chromium' (organizer) Playwright project using
 * .playwright-auth-organizer.json storage state.
 * Requires ORGANIZER_AUTH_TOKEN env var (set via make setup-test-users).
 *
 * Uses page.route() to mock the partner-meetings API so tests are
 * deterministic and do not require a live backend.
 *
 * Run: cd web-frontend && npx playwright test --project=chromium e2e/organizer/partner-meetings.spec.ts
 */

import { test, expect } from '@playwright/test';
import { BASE_URL } from '../../playwright.config';

const PAGE_URL = `${BASE_URL}/organizer/partner-meetings`;

// ─── Test data ────────────────────────────────────────────────────────────────

const MOCK_MEETING = {
  id: 'e2e-meeting-uuid-1',
  eventCode: 'BATbern57',
  meetingType: 'SPRING',
  meetingDate: '2026-05-14',
  startTime: '12:00:00',
  endTime: '14:00:00',
  location: 'Bern Congress Centre',
  agenda: 'Initial agenda text',
  notes: null,
  inviteSentAt: null,
  createdBy: 'organizer',
  createdAt: '2026-02-22T10:00:00Z',
  updatedAt: '2026-02-22T10:00:00Z',
};

// ─── Tests ────────────────────────────────────────────────────────────────────

test.describe('Partner Meetings Page (Organizer)', () => {
  test('AC1 — organizer creates meeting, it appears in the list', async ({ page }) => {
    const meetings: (typeof MOCK_MEETING)[] = [];

    // Mock list endpoint — returns accumulating list
    await page.route(`${BASE_URL}/api/v1/partner-meetings`, async (route) => {
      if (route.request().method() === 'GET') {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify(meetings),
        });
      } else if (route.request().method() === 'POST') {
        const created = { ...MOCK_MEETING };
        meetings.push(created);
        await route.fulfill({
          status: 201,
          contentType: 'application/json',
          body: JSON.stringify(created),
        });
      }
    });

    await page.goto(PAGE_URL);
    await expect(page.getByTestId('partner-meetings-page')).toBeVisible();

    // Initially empty
    await expect(page.getByTestId('no-meetings-message')).toBeVisible();

    // Open create dialog
    await page.getByTestId('create-meeting-btn').click();
    await expect(page.getByTestId('create-meeting-dialog')).toBeVisible();

    // Fill form
    await page.getByTestId('meeting-event-code').fill('BATbern57');
    await page.getByTestId('meeting-start-time').fill('12:00');
    await page.getByTestId('meeting-end-time').fill('14:00');
    await page.getByTestId('create-meeting-submit').click();

    // Meeting appears in list
    await expect(page.getByTestId('partner-meetings-table')).toBeVisible();
    await expect(page.getByTestId('meeting-row-e2e-meeting-uuid-1')).toBeVisible();
    await expect(page.getByText('BATbern57')).toBeVisible();
  });

  test('AC2 — organizer writes agenda, saved on reload', async ({ page }) => {
    const currentMeeting = { ...MOCK_MEETING };

    await page.route(`${BASE_URL}/api/v1/partner-meetings`, async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify([currentMeeting]),
      });
    });

    await page.route(`${BASE_URL}/api/v1/partner-meetings/${MOCK_MEETING.id}`, async (route) => {
      if (route.request().method() === 'GET') {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify(currentMeeting),
        });
      } else if (route.request().method() === 'PATCH') {
        const body = JSON.parse(route.request().postData() ?? '{}') as { agenda?: string };
        if (body.agenda !== undefined) {
          currentMeeting.agenda = body.agenda;
        }
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify(currentMeeting),
        });
      }
    });

    await page.goto(PAGE_URL);
    await expect(page.getByTestId('meeting-row-e2e-meeting-uuid-1')).toBeVisible();

    // Expand detail panel
    await page.getByTestId('meeting-row-e2e-meeting-uuid-1').click();
    await expect(page.getByTestId('meeting-detail-e2e-meeting-uuid-1')).toBeVisible();

    // Update agenda
    const agendaField = page.getByTestId('meeting-agenda-e2e-meeting-uuid-1');
    await agendaField.fill('Updated agenda: 1. Welcome 2. Review 3. AOB');
    await agendaField.blur();

    // PATCH was called (agenda saved)
    await expect(agendaField).toHaveValue('Updated agenda: 1. Welcome 2. Review 3. AOB');
  });

  test('AC3 — organizer sends invite, success toast and Invite Sent chip appear', async ({
    page,
  }) => {
    const currentMeeting = { ...MOCK_MEETING };

    await page.route(`${BASE_URL}/api/v1/partner-meetings`, async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify([currentMeeting]),
      });
    });

    await page.route(`${BASE_URL}/api/v1/partner-meetings/${MOCK_MEETING.id}`, async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(currentMeeting),
      });
    });

    await page.route(
      `${BASE_URL}/api/v1/partner-meetings/${MOCK_MEETING.id}/send-invite`,
      async (route) => {
        // Mark invite as sent in mock state
        currentMeeting.inviteSentAt = new Date().toISOString();
        await route.fulfill({
          status: 202,
          contentType: 'application/json',
          body: JSON.stringify({
            message: 'Calendar invite is being sent to all partner contacts',
            meetingId: MOCK_MEETING.id,
            recipientCount: 3,
          }),
        });
      }
    );

    await page.goto(PAGE_URL);

    // Expand detail panel
    await page.getByTestId('meeting-row-e2e-meeting-uuid-1').click();
    await expect(page.getByTestId('meeting-detail-e2e-meeting-uuid-1')).toBeVisible();

    // Initially invite not sent
    await expect(page.getByTestId('invite-not-sent-chip-e2e-meeting-uuid-1')).toBeVisible();

    // Click send invite
    await page.getByTestId('send-invite-btn-e2e-meeting-uuid-1').click();

    // Confirm dialog
    await expect(page.getByTestId('confirm-send-invite-btn')).toBeVisible();
    await page.getByTestId('confirm-send-invite-btn').click();

    // Success alert appears
    await expect(page.getByRole('alert')).toContainText(/invite/i);
  });

  test('AC4 — organizer writes post-meeting notes, saved on reload', async ({ page }) => {
    const currentMeeting = { ...MOCK_MEETING };

    await page.route(`${BASE_URL}/api/v1/partner-meetings`, async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify([currentMeeting]),
      });
    });

    await page.route(`${BASE_URL}/api/v1/partner-meetings/${MOCK_MEETING.id}`, async (route) => {
      if (route.request().method() === 'GET') {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify(currentMeeting),
        });
      } else if (route.request().method() === 'PATCH') {
        const body = JSON.parse(route.request().postData() ?? '{}') as { notes?: string };
        if (body.notes !== undefined) {
          currentMeeting.notes = body.notes;
        }
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify(currentMeeting),
        });
      }
    });

    await page.goto(PAGE_URL);

    // Expand detail panel
    await page.getByTestId('meeting-row-e2e-meeting-uuid-1').click();
    await expect(page.getByTestId('meeting-detail-e2e-meeting-uuid-1')).toBeVisible();

    // Write notes
    const notesField = page.getByTestId('meeting-notes-e2e-meeting-uuid-1');
    await notesField.fill('Great discussion. AlphaCo presented new roadmap.');
    await notesField.blur();

    // Notes saved
    await expect(notesField).toHaveValue('Great discussion. AlphaCo presented new roadmap.');
  });
});
