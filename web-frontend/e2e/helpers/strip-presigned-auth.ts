/**
 * Strip the global Authorization header from presigned object-store requests (plan §C slice 1).
 * docs/plans/playwright-staging-hardening.md
 *
 * ── Why this exists (a real gate blocker, not a dev quirk) ──────────────────────────────
 * `playwright.config.ts` injects a global `Authorization: Bearer <JWT>` via `extraHTTPHeaders`
 * whenever `AUTH_TOKEN` is set (the runner sets it). That header is harmless on same-origin
 * API calls — the app's axios client sets its own from storageState — but it is FATAL on a
 * presigned S3/MinIO upload: the object store receives BOTH the query-string SigV4 auth
 * (`X-Amz-Signature=…`) AND the Authorization header and rejects the request as having two
 * auth mechanisms. Verified failure modes:
 *   • dev MinIO  → HTTP 400  <Code>InvalidRequest</Code>
 *                 "request has multiple authentication types, please use one"
 *   • AWS S3     → HTTP 400  (only one of query-auth | Authorization header may be present)
 * curl with NO Authorization header against the very same presigned URL returns 200, which
 * isolates the cause to the injected header. Real users never hit this — their browser sends
 * no global Authorization on the cross-origin PUT (axios attaches it only to API calls).
 *
 * Any UI flow that uploads through a presigned URL (profile photo, company logo, speaker
 * materials, …) must call this before triggering the upload. We match on the `X-Amz-Signature`
 * query marker so it targets dev MinIO and prod S3 alike — and nothing else (no app/API
 * request carries that marker). Scoped to the calling page only: the global config stays
 * untouched, so the other ~410 specs are unaffected.
 */

import type { Page } from '@playwright/test';

export async function stripPresignedAuthHeader(page: Page): Promise<void> {
  await page.route(/X-Amz-Signature=/, async (route) => {
    // Playwright lowercases header names in request().headers().
    const headers = { ...route.request().headers() };
    delete headers.authorization;
    await route.continue({ headers });
  });
}
