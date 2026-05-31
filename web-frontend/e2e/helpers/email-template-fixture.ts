/**
 * Email-template fixture helpers — slice 12 / admin tabs (plan §C)
 * docs/plans/playwright-staging-hardening.md
 *
 * Email content templates are keyed by (templateKey, locale) and have NO prefix-sweep
 * entityType (the EMS cleanup allowlist is events/sessions/topics only), so the Email
 * Templates CRUD spec captures the key it created and tears down by explicit
 * `DELETE /email-templates/{key}/{locale}` — the same REST endpoint the UI delete hits
 * (`emailTemplateService.deleteTemplate`). Both helpers require an ORGANIZER idToken and are
 * tolerant: cleanup must never throw and fail an otherwise-green test.
 */

import { API_URL } from '../../playwright.config';

export interface EmailTemplate {
  templateKey: string;
  locale: string;
  subject: string | null;
  category: string;
  isSystemTemplate: boolean;
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

/** GET one content template by key+locale → the template, or null if it does not exist (404). */
export async function getEmailTemplate(
  token: string,
  templateKey: string,
  locale: string
): Promise<EmailTemplate | null> {
  if (!token || !templateKey) return null;
  const res = await authedFetch(
    `/api/v1/email-templates/${encodeURIComponent(templateKey)}/${encodeURIComponent(locale)}`,
    token
  );
  if (res.status === 404) return null;
  if (!res.ok) {
    console.warn(`[email-template] ⚠️  GET ${templateKey}/${locale} → ${res.status}`);
    return null;
  }
  return (await res.json().catch(() => null)) as EmailTemplate | null;
}

/**
 * Explicit DELETE of a content template by key+locale. Accepts 200/204/404 (404 = already
 * removed via the UI happy path). Never throws.
 */
export async function deleteEmailTemplate(
  token: string,
  templateKey: string,
  locale: string
): Promise<void> {
  if (!token || !templateKey) return;
  try {
    const res = await authedFetch(
      `/api/v1/email-templates/${encodeURIComponent(templateKey)}/${encodeURIComponent(locale)}`,
      token,
      { method: 'DELETE' }
    );
    if ([200, 204, 404].includes(res.status)) {
      console.log(`[email-template] ✓ delete ${templateKey}/${locale} → ${res.status}`);
    } else {
      console.warn(
        `[email-template] ⚠️  delete ${templateKey}/${locale} → unexpected ${res.status}`
      );
    }
  } catch (error) {
    console.warn(
      `[email-template] ⚠️  delete ${templateKey}/${locale} threw:`,
      error instanceof Error ? error.message : String(error)
    );
  }
}
