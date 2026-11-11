/**
 * Organizer · AI prompt reset (Story 4.10b): an organizer edits a prompt (`PUT
 * /ai-prompts/{key}`), resets it to the default (`DELETE /ai-prompts/{key}/override`), and both
 * persist after a reload.
 *
 * Guarded to LOCAL DEV (`TEST_ENV=development`, never `@gate`): the prompts are global settings.
 * The exact prompt text read before the test is written back afterwards, also when the test fails.
 */

import { test, expect } from '@playwright/test';
import { readOrganizerToken } from '../helpers/event-fixture';
import { waitForAppShell } from '../helpers/app-shell';

const LOCAL_DEV = (process.env.TEST_ENV ?? 'development') === 'development';
const API_URL = process.env.E2E_API_URL || 'http://localhost:8000';
const describeLocal = LOCAL_DEV ? test.describe : test.describe.skip;
const KEY = 'event_description';

interface Prompt {
  promptKey: string;
  promptText: string;
  defaultText: string;
}

describeLocal('Organizer · AI prompt reset (4.10b)', () => {
  let token: string;
  let original: Prompt;

  const call = (method: string, path: string, body?: unknown) =>
    fetch(`${API_URL}/api/v1/ai-prompts/${KEY}${path}`, {
      method,
      headers: {
        Authorization: `Bearer ${token}`,
        'Content-Type': 'application/json',
        Accept: 'application/json',
      },
      body: body === undefined ? undefined : JSON.stringify(body),
    });

  test.beforeAll(async () => {
    token = readOrganizerToken();
    const res = await call('GET', '');
    expect(res.status).toBe(200);
    original = (await res.json()) as Prompt;
  });

  test.afterAll(async () => {
    const res = await call('PUT', '', { promptText: original.promptText });
    expect(res.status).toBe(200);
    expect(((await (await call('GET', '')).json()) as Prompt).promptText).toBe(original.promptText);
  });

  test('should_persistEditAndReset_when_organizerChangesAPrompt', async ({ page }) => {
    const edited = `${original.defaultText}\n\nE2E 4.10b: Brünnimann’s Zusatz`;

    await page.goto('/organizer/admin?tab=5');
    await waitForAppShell(page);
    const text = page.getByTestId(`ai-prompt-text-${KEY}`);
    await expect(text).toBeVisible();

    await text.fill(edited);
    await page.getByTestId(`ai-prompt-save-${KEY}`).click();
    await expect(page.getByTestId(`ai-prompt-save-${KEY}`)).toBeDisabled();
    await page.reload();
    await expect(page.getByTestId(`ai-prompt-text-${KEY}`)).toHaveValue(edited);

    await page.getByTestId(`ai-prompt-reset-${KEY}`).click();
    await page.getByTestId('ai-prompt-reset-confirm').click();
    await expect(page.getByTestId(`ai-prompt-text-${KEY}`)).toHaveValue(original.defaultText);
    await page.reload();
    await expect(page.getByTestId(`ai-prompt-text-${KEY}`)).toHaveValue(original.defaultText);
    expect(((await (await call('GET', '')).json()) as Prompt).promptText).toBe(
      original.defaultText
    );
  });
});
