/**
 * Featured Thanks Service (Story 7.7 — curated thank-you marquee)
 *
 * HTTP client for the PUBLIC curated featured thank-you notes. Returns up to `limit` (capped at 9
 * server-side) random organizer-featured, logged-in notes across all events, enriched with the
 * author's first name + company logo. No auth required.
 */

import apiClient from '@/services/api/apiClient';
import type { components } from '@/types/generated/events-core-api.types';

export type FeaturedThanksResponse = components['schemas']['FeaturedThanksResponse'];

/** Get up to `limit` random featured thank-you notes (public, cross-event). */
export async function getFeaturedThanks(limit = 9): Promise<FeaturedThanksResponse[]> {
  const response = await apiClient.get<FeaturedThanksResponse[]>('/thanks/featured', {
    params: { limit },
  });
  return response.data;
}
