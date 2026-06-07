/**
 * E2E: Admin · Email Content Templates — full UI CRUD (slice 12 / admin tabs, plan §C)
 * docs/plans/playwright-staging-hardening.md
 *
 * Real Create → Read → Update → Delete of a content template, driven through the frontend
 * (Admin page → Email Templates tab). Content templates are keyed by (templateKey, locale) and
 * are freely creatable+deletable (non-system), so they gate per-deploy → `@smoke`.
 *
 * Why CREATE goes through the DUPLICATE flow (reliability, not a shortcut):
 *   The new-template editor body is **TinyMCE** (a contenteditable iframe) — driving it in
 *   Playwright is flaky and wrong for a blocking gate. The Duplicate action opens the same
 *   create modal but pre-fills subject + htmlBody from an existing template (`cloneFrom`), so a
 *   real create only needs a fresh, unique templateKey — no editor interaction, the body is
 *   already valid. UPDATE edits the plain MUI Subject field (also no TinyMCE). This exercises
 *   the genuine create/update/delete endpoints end-to-end without the iframe flake.
 *
 * Cleanup: email templates have NO prefix-sweep entityType, so the test captures the key it
 * created and the UI Delete step IS the teardown; the afterAll `DELETE /email-templates/{key}/
 * {locale}` is the backstop if an earlier step failed.
 */

import { test, expect, type Page } from '@playwright/test';
import * as factory from '../helpers/test-data-factory';
import { readOrganizerToken } from '../helpers/event-fixture';
import { getEmailTemplate, deleteEmailTemplate } from '../helpers/email-template-fixture';

const ADMIN_URL = '/organizer/admin';
// The Email Templates tab defaults its filters to category=SPEAKER, locale=de; the seeded
// content templates there are the duplicate source, and a duplicate inherits that locale.
const LOCALE = 'de';

test.describe('AdminTabs · Email Templates CRUD', { tag: ['@smoke', '@gate'] }, () => {
  test.describe.configure({ mode: 'serial' });

  let token: string;
  let createdKey: string | null = null;

  test.beforeAll(() => {
    token = readOrganizerToken();
  });

  test.afterAll(async () => {
    if (createdKey) await deleteEmailTemplate(token, createdKey, LOCALE);
  });

  async function openEmailTemplatesTab(page: Page) {
    await page.goto(`${ADMIN_URL}?tab=3`);
    await expect(page.getByTestId('email-templates-tab')).toBeVisible({ timeout: 20_000 });
  }

  test('should_createReadUpdateDelete_contentTemplate_via UI', async ({ page }) => {
    const key = factory.emailTemplateKey();
    await openEmailTemplatesTab(page);

    // ── CREATE (via Duplicate → unique key; body pre-filled, no TinyMCE interaction) ────────
    // Duplicate the first seeded SPEAKER/de content template. The create modal opens with
    // subject + htmlBody pre-filled from it; we only supply a fresh templateKey.
    const firstDuplicate = page.locator('[data-testid^="duplicate-email-template-"]').first();
    await expect(firstDuplicate).toBeVisible({ timeout: 15_000 });
    await firstDuplicate.click();

    await expect(page.getByTestId('email-template-modal')).toBeVisible();
    await page.getByTestId('email-template-key-input').fill(key);
    await page.getByTestId('email-template-save').click();
    await expect(page.getByTestId('email-template-modal')).toBeHidden({ timeout: 20_000 });

    // Capture + record the created subject (from the cloned source) for the UPDATE assertion.
    const created = await getEmailTemplate(token, key, LOCALE);
    expect(created, 'created content template should exist via API').not.toBeNull();
    createdKey = key;

    // ── READ ──────────────────────────────────────────────────────────────────────────────
    // The new row renders (same SPEAKER/de filter) with key-keyed edit + delete actions.
    await expect(page.getByTestId(`edit-email-template-${key}`)).toBeVisible({ timeout: 10_000 });
    await expect(page.getByTestId(`delete-email-template-${key}`)).toBeVisible();

    // ── UPDATE (edit the plain Subject field — no TinyMCE) ──────────────────────────────────
    const newSubject = `BATPW-E2E edited ${key}`;
    await page.getByTestId(`edit-email-template-${key}`).click();
    await expect(page.getByTestId('email-template-modal')).toBeVisible();
    await page.getByTestId('email-template-subject-input').fill(newSubject);
    await page.getByTestId('email-template-save').click();
    await expect(page.getByTestId('email-template-modal')).toBeHidden({ timeout: 20_000 });
    // Authoritative verify: the subject persisted server-side.
    const updated = await getEmailTemplate(token, key, LOCALE);
    expect(updated?.subject).toBe(newSubject);

    // ── DELETE ────────────────────────────────────────────────────────────────────────────
    const deleteBtn = page.getByTestId(`delete-email-template-${key}`);
    page.once('dialog', (dialog) => void dialog.accept());
    await deleteBtn.click();
    await expect(deleteBtn).toBeHidden({ timeout: 10_000 });
    // Authoritative verify: gone server-side.
    expect(await getEmailTemplate(token, key, LOCALE)).toBeNull();
    createdKey = null; // cleaned up via UI → afterAll backstop is a no-op
  });
});
