/**
 * Task-template fixture helpers — slice 12 / admin tabs (plan §C)
 * docs/plans/playwright-staging-hardening.md
 *
 * Custom task templates have NO prefix-sweep entityType: the deployed
 * `TestFixtureCleanupService` allowlist is CUMS companies/users/additional_emails, EMS
 * events/sessions/topics, PCS partners/meetings — templates are none of these (see
 * `e2e/helpers/test-fixtures-cleanup.ts`). So the Task-Templates `@smoke` tears down by an
 * explicit DELETE of the id captured from `GET /tasks/templates`, exactly the captured-id
 * pattern slice 3 used for users. Both helpers require an ORGANIZER idToken
 * (`@PreAuthorize` on the template endpoints) and are tolerant — cleanup must never throw and
 * fail an otherwise-green test.
 */

import { API_URL } from '../../playwright.config';

interface TaskTemplate {
  id: string;
  name: string;
  isDefault: boolean;
}

async function authedFetch(
  path: string,
  token: string,
  options: RequestInit = {}
): Promise<Response> {
  return fetch(`${API_URL}${path}`, {
    ...options,
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${token}`,
      ...options.headers,
    },
  });
}

/**
 * Resolve a custom template's id by its (unique, factory-generated) name. Returns null when
 * no template carries that name — used both to capture the id the create-form `@smoke` can't
 * know up-front AND to assert the template is gone after the UI delete.
 */
export async function findTaskTemplateByName(token: string, name: string): Promise<string | null> {
  if (!token || !name) return null;
  const res = await authedFetch('/api/v1/tasks/templates', token);
  if (!res.ok) {
    console.warn(`[task-template] ⚠️  GET /tasks/templates → ${res.status}`);
    return null;
  }
  const templates = (await res.json().catch(() => [])) as TaskTemplate[];
  return templates.find((tmpl) => tmpl.name === name)?.id ?? null;
}

/**
 * Explicit DELETE of a custom template by id (`DELETE /tasks/templates/{id}`) — the only
 * teardown path for templates. Accepts 204/404 (404 = already deleted via the UI happy path).
 * Never throws.
 */
export async function deleteTaskTemplate(token: string, id: string): Promise<void> {
  if (!token || !id) return;
  try {
    const res = await authedFetch(`/api/v1/tasks/templates/${encodeURIComponent(id)}`, token, {
      method: 'DELETE',
    });
    if ([200, 204, 404].includes(res.status)) {
      console.log(`[task-template] ✓ delete template ${id} → ${res.status}`);
    } else {
      console.warn(`[task-template] ⚠️  delete template ${id} → unexpected ${res.status}`);
    }
  } catch (error) {
    console.warn(
      `[task-template] ⚠️  delete template ${id} threw:`,
      error instanceof Error ? error.message : String(error)
    );
  }
}
