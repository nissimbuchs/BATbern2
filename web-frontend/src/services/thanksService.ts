/**
 * Thanks Service (Story 7.4 — "Thank the Organizers")
 *
 * HTTP client for the public "thank the organizers" endpoints. Submit is anonymous-allowed and
 * Turnstile-guarded (token via X-Turnstile-Token header, mirroring the newsletter widget); the
 * GET returns the public aggregate count (organizer callers additionally receive notes).
 */

import apiClient from '@/services/api/apiClient';
import type { components } from '@/types/generated/events-core-api.types';

export type ThanksCountResponse = components['schemas']['ThanksCountResponse'];
export type ThanksNoteResponse = components['schemas']['ThanksNoteResponse'];

/**
 * Organizer feature-toggle (Story 7.7): mark/un-mark a thank-you note for the public marquee.
 * Organizer-authenticated; featuring an anonymous note is rejected server-side (409).
 */
export async function setThanksFeatured(
  eventCode: string,
  id: string,
  featured: boolean
): Promise<ThanksNoteResponse> {
  const response = await apiClient.patch<ThanksNoteResponse>(
    `/events/${encodeURIComponent(eventCode)}/thanks/${encodeURIComponent(id)}`,
    { featured }
  );
  return response.data;
}

/**
 * Submit a thank-you for an event (no auth required). Returns the new aggregate count.
 * @param turnstileToken Optional Cloudflare Turnstile token — passed via X-Turnstile-Token when present.
 */
export async function submitThanks(
  eventCode: string,
  note?: string | null,
  turnstileToken?: string | null
): Promise<ThanksCountResponse> {
  const response = await apiClient.post<ThanksCountResponse>(
    `/events/${encodeURIComponent(eventCode)}/thanks`,
    note ? { note } : {},
    { headers: turnstileToken ? { 'X-Turnstile-Token': turnstileToken } : {} }
  );
  return response.data;
}

/** Get the public aggregate thank-you count for an event. */
export async function getThanks(eventCode: string): Promise<ThanksCountResponse> {
  const response = await apiClient.get<ThanksCountResponse>(
    `/events/${encodeURIComponent(eventCode)}/thanks`
  );
  return response.data;
}
