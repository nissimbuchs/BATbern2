/**
 * E2E: Event Tasks — slice 5 / tasks (plan §C)
 * docs/plans/playwright-staging-hardening.md
 *
 * Rewritten 2026-05-30 to reality + the quality bar (testid-only locators, factory/API
 * fixture data, mandatory cleanup, no empty tests). Story 5.5 (EventTasksTab, AC21-22).
 *
 * What this replaces (rewrite-to-reality + a prod-residue fix):
 *   • BOTH old task specs (`test-task-creation-from-templates` + `test-task-assignment`)
 *     created a REAL event through the UI EventWorkflowPage (`BATbern${9000+random}`, an
 *     `E2E Test - …` title — neither swept) and **never cleaned it up** → a leaked event +
 *     its tasks on every run. They drove the Tasks tab with role/text locators
 *     (`input[type=checkbox]`, `getByRole('combobox')`, `getByRole('listitem')`,
 *     `getByRole('option',{name:'Nissim Buchs'})`) and HARDCODED organizer display names
 *     ("Nissim Buchs"/"Daniel Kühni"/"Andreas Grütter") — environment-specific + fragile —
 *     plus pervasive `waitForTimeout`. `test-task-assignment.spec.ts` was DELETED (its
 *     three-named-assignee flow is fully covered by the `@smoke` below, deterministically).
 *
 * Reality (verified in EventTasksTab.tsx / EventForm.tsx / event-management-service):
 *   • The Tasks tab content (`event-tasks-tab-content`) lists the global task-template catalog
 *     (7 seeded default templates + any custom). Editing an event with NO existing tasks
 *     PRE-SELECTS all default templates (enabled, not disabled — EventForm.tsx:230-240), so an
 *     API-created throwaway event shows assignable rows immediately (no UI event-create needed).
 *   • Per-row testids added this PR (same commit): `task-template-<templateId>` (ListItem) +
 *     `task-assignee-<templateId>` (the OrganizerSelect, which already forwards data-testid to
 *     its inner Select). Assignee OPTIONS already carry `organizer-option-<username>`.
 *   • Save in edit mode (`save-event-button`) calls `POST /events/{code}/tasks/from-templates`
 *     for the selected templates + assignees even with no event-field change
 *     (EventForm.tsx:589), then closes the dialog — so dialog-close is the success signal.
 *
 * Cleanup contract: tasks have NO prefix-sweep entityType (EMS sweep is events/sessions/topics
 * only). `event_tasks.event_id` is `ON DELETE CASCADE` (V22), so deleting the throwaway fixture
 * event (`cleanupByCode`) removes its tasks. Each run creates its own event via the API fixture
 * (captured `BATbern{N}` code) and tears it down in afterAll — never touches a real event.
 *
 * Prod-safety (plan risk #1 + #2): the `@smoke` mutates ONLY its own throwaway event (created +
 * deleted in this run); the server-generated `BATbern{N}` code is captured and explicit-deleted.
 */

import { test, expect } from '@playwright/test';
import { API_URL } from '../../playwright.config';
import {
  readOrganizerToken,
  organizerUsername,
  createRegistrationEvent,
  type RegistrationEvent,
} from '../helpers/event-fixture';
import { cleanupByCode } from '../helpers/test-fixtures-cleanup';

interface EventTask {
  templateId: string | null;
  taskName: string;
  assignedOrganizerUsername: string | null;
}

test.describe('Event Tasks (Story 5.5)', { tag: '@gate' }, () => {
  // Shared throwaway event for the whole file (serial) — created once, torn down once. Tasks
  // ride its FK cascade. Serial so the @smoke's task-create doesn't race the @gate render.
  test.describe.configure({ mode: 'serial' });

  let token: string;
  let username: string;
  let fixtureEvent: RegistrationEvent;

  test.beforeAll(async () => {
    token = readOrganizerToken();
    username = organizerUsername(token);
    // A generic CREATED fixture event (the helper is registration-named but creates a plain
    // CREATED event); editable + task-template-eligible, with a captured BATbern{N} code.
    fixtureEvent = await createRegistrationEvent(token);
  });

  test.afterAll(async () => {
    if (fixtureEvent?.eventCode) {
      await cleanupByCode(token, fixtureEvent.eventCode); // cascade removes event_tasks
    }
  });

  /** Open the fixture event's edit modal and switch to the (loaded) Tasks tab. */
  async function openTasksTab(page: import('@playwright/test').Page) {
    await page.goto(`/organizer/events/${fixtureEvent.eventCode}`);
    await page.getByTestId('edit-event-button').click();
    await page.getByTestId('tasks-tab').click();
    await expect(page.getByTestId('event-tasks-tab-content')).toBeVisible();
  }

  test('should_listTaskTemplates_when_tasksTabOpened', async ({ page }) => {
    await openTasksTab(page);

    // The default-template rows render (pre-selected) with per-row assignee selects.
    await expect(page.locator('[data-testid^="task-template-"]').first()).toBeVisible();
    await expect(page.locator('[data-testid^="task-assignee-"]').first()).toBeVisible();
  });

  test(
    'should_createAndAssignTasks_when_savedWithAssignee',
    { tag: ['@smoke', '@gate'] },
    async ({ page, request }) => {
      await openTasksTab(page);

      // Assign the first (pre-selected, enabled) default template to the current organizer.
      // The select is the OrganizerSelect combobox; options render in a portal and carry
      // `organizer-option-<username>` testids.
      const firstRow = page.locator('[data-testid^="task-template-"]').first();
      await firstRow.locator('[data-testid^="task-assignee-"]').click();
      await page.getByTestId(`organizer-option-${username}`).click();

      // Save with only the task assignment changed → createTasksFromTemplates fires, then the
      // dialog closes (success signal; a failed save keeps the modal open).
      await page.getByTestId('save-event-button').click();
      await expect(page.getByRole('dialog')).toBeHidden({ timeout: 15_000 });

      // Verify the feature did what it claims: template tasks were instantiated for the event
      // and at least one carries our assignee. This is the real AC (task creation from
      // templates WITH assignees), stronger than dialog-close alone.
      const res = await request.get(`${API_URL}/api/v1/events/${fixtureEvent.eventCode}/tasks`);
      expect(res.status()).toBe(200);
      const tasks = (await res.json()) as EventTask[];
      expect(tasks.length).toBeGreaterThan(0);
      expect(tasks.some((t) => t.assignedOrganizerUsername === username)).toBe(true);
    }
  );
});
