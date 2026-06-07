/**
 * Global teaser-image fixture helpers — slice 12 / admin tabs (plan §C)
 * docs/plans/playwright-staging-hardening.md
 *
 * Global teaser images (event_code IS NULL) live under `/events/_global/teaser-images`
 * (eventApiClient.teaserBasePath maps a null/empty eventCode to the `_global` segment). They
 * have NO prefix-sweep entityType, so the Global Images CRUD spec captures the id assigned at
 * upload-confirm (by diffing the gallery before/after) and tears it down by explicit DELETE —
 * the same endpoint the UI remove button hits. Helpers require an ORGANIZER idToken; the
 * delete is tolerant (cleanup must never throw).
 */

import { API_URL } from '../../playwright.config';

const GLOBAL_TEASER_PATH = '/api/v1/events/_global/teaser-images';

export interface TeaserImage {
  id: string;
  presentationPosition: string;
  displayOrder: number;
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

/** List all global teaser images (full objects). */
export async function listGlobalImages(token: string): Promise<TeaserImage[]> {
  if (!token) return [];
  const res = await authedFetch(GLOBAL_TEASER_PATH, token);
  if (!res.ok) {
    console.warn(`[global-image] ⚠️  GET ${GLOBAL_TEASER_PATH} → ${res.status}`);
    return [];
  }
  return (await res.json().catch(() => [])) as TeaserImage[];
}

/** Convenience: just the ids (used to diff before/after an upload to find the new one). */
export async function listGlobalImageIds(token: string): Promise<string[]> {
  return (await listGlobalImages(token)).map((img) => img.id);
}

/** GET one global image by id → the image, or null if absent. */
export async function getGlobalImage(token: string, id: string): Promise<TeaserImage | null> {
  if (!token || !id) return null;
  return (await listGlobalImages(token)).find((img) => img.id === id) ?? null;
}

/**
 * Explicit DELETE of a global teaser image by id. Accepts 200/204/404 (404 = already removed
 * via the UI happy path). Never throws.
 */
export async function deleteGlobalImage(token: string, id: string): Promise<void> {
  if (!token || !id) return;
  try {
    const res = await authedFetch(`${GLOBAL_TEASER_PATH}/${encodeURIComponent(id)}`, token, {
      method: 'DELETE',
    });
    if ([200, 204, 404].includes(res.status)) {
      console.log(`[global-image] ✓ delete ${id} → ${res.status}`);
    } else {
      console.warn(`[global-image] ⚠️  delete ${id} → unexpected ${res.status}`);
    }
  } catch (error) {
    console.warn(
      `[global-image] ⚠️  delete ${id} threw:`,
      error instanceof Error ? error.message : String(error)
    );
  }
}
