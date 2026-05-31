/**
 * E2E: Admin · Task Templates — full UI CRUD (slice 12 / admin tabs, plan §C)
 * docs/plans/playwright-staging-hardening.md
 *
 * Real Create → Read → Update → Delete of a CUSTOM task template, all driven through the
 * frontend (Admin page → Task Templates tab). Custom templates are the safe, fully-reversible
 * admin entity (NOT a global singleton): created via CustomTaskModal in template mode, edited
 * via TaskTemplateEditModal, deleted via the row action. PO decision 2026-05-31: collections
 * like this gate per-deploy → `@smoke`.
 *
 * Cleanup: task templates have NO prefix-sweep entityType (EMS sweep = events/sessions/topics
 * only), so the test captures the server-assigned id (`GET /tasks/templates`) and the UI Delete
 * step IS the teardown; the afterAll API delete is the backstop if an earlier step failed.
 */

import { test, expect, type Page } from '@playwright/test';
import * as factory from '../helpers/test-data-factory';
import { readOrganizerToken } from '../helpers/event-fixture';
import { findTaskTemplateByName, deleteTaskTemplate } from '../helpers/task-template-fixture';

const ADMIN_URL = '/organizer/admin';

test.describe('AdminTabs · Task Templates CRUD', { tag: ['@smoke', '@gate'] }, () => {
  test.describe.configure({ mode: 'serial' });

  let token: string;
  let createdId: string | null = null;

  test.beforeAll(() => {
    token = readOrganizerToken();
  });

  test.afterAll(async () => {
    // Backstop: only fires if the UI Delete step didn't run (test failed earlier). The happy
    // path sets createdId=null after deleting, so this is a no-op there.
    if (createdId) await deleteTaskTemplate(token, createdId);
  });

  async function openTaskTemplatesTab(page: Page) {
    await page.goto(`${ADMIN_URL}?tab=2`);
    await expect(page.getByTestId('task-templates-tab')).toBeVisible({ timeout: 20_000 });
  }

  test('should_createReadUpdateDelete_customTaskTemplate_via UI', async ({ page }) => {
    const name = factory.taskTemplateName();
    const renamed = `${name}-edited`;
    await openTaskTemplatesTab(page);

    // ── CREATE ────────────────────────────────────────────────────────────────────────────
    // "+ Add Template" → CustomTaskModal. With eventId=null and no event selected, checking
    // "Save as reusable template" is REQUIRED (else the form demands an event); it routes the
    // submit to createTemplate (template mode). Defaults (triggerState, relative due date) are
    // valid, so name + the checkbox are the only inputs needed.
    await page.getByTestId('add-template-btn').click();
    await expect(page.getByTestId('custom-task-modal')).toBeVisible();
    await page.getByTestId('custom-task-name-input').fill(name);
    await page.getByTestId('custom-task-save-as-template').click();
    await page.getByTestId('custom-task-submit').click();
    // Modal closes only after the create mutation resolves (success signal).
    await expect(page.getByTestId('custom-task-modal')).toBeHidden({ timeout: 15_000 });

    // Capture the server-assigned id (cleanup is by exact id — no prefix sweep for templates).
    createdId = await findTaskTemplateByName(token, name);
    expect(createdId, 'created template should be found via API').not.toBeNull();

    // ── READ ──────────────────────────────────────────────────────────────────────────────
    // The new row renders in the Custom Templates list with id-keyed edit + delete actions.
    await expect(page.getByTestId(`edit-template-${createdId}`)).toBeVisible({ timeout: 10_000 });
    await expect(page.getByTestId(`delete-template-${createdId}`)).toBeVisible();

    // ── UPDATE ────────────────────────────────────────────────────────────────────────────
    // Open the edit modal, rename, save. Modal closes on success; the list refetches.
    await page.getByTestId(`edit-template-${createdId}`).click();
    await expect(page.getByTestId('task-template-edit-modal')).toBeVisible();
    await page.getByTestId('template-name-input').fill(renamed);
    await page.getByTestId('template-edit-save').click();
    await expect(page.getByTestId('task-template-edit-modal')).toBeHidden({ timeout: 15_000 });
    // Authoritative verify: the SAME id now carries the new name (update persisted server-side).
    expect(await findTaskTemplateByName(token, renamed)).toBe(createdId);
    expect(await findTaskTemplateByName(token, name)).toBeNull();

    // ── DELETE ────────────────────────────────────────────────────────────────────────────
    // Native window.confirm — accept it; the row unmounts on refetch.
    const deleteBtn = page.getByTestId(`delete-template-${createdId}`);
    page.once('dialog', (dialog) => void dialog.accept());
    await deleteBtn.click();
    await expect(deleteBtn).toBeHidden({ timeout: 10_000 });
    // Authoritative verify: gone server-side, not just from the rendered list.
    expect(await findTaskTemplateByName(token, renamed)).toBeNull();
    createdId = null; // cleaned up via UI → afterAll backstop is a no-op
  });
});
