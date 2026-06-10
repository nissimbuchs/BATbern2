/**
 * Q&A Service (Story 7.5 — "The Apéro Continues")
 *
 * HTTP client for per-session Q&A. GET is public; POST is authenticated (JWT attached by the
 * apiClient interceptor); PATCH/DELETE are organizer-only.
 */

import apiClient from '@/services/api/apiClient';
import type { components } from '@/types/generated/events-api.types';

export type QnaWindowResponse = components['schemas']['QnaWindowResponse'];
export type QnaPostResponse = components['schemas']['QnaPostResponse'];

function base(eventCode: string, sessionSlug: string): string {
  return `/events/${encodeURIComponent(eventCode)}/sessions/${encodeURIComponent(sessionSlug)}/qna`;
}

/** Public read of a session's Q&A thread. Throws 404 if no window exists yet. */
export async function getThread(
  eventCode: string,
  sessionSlug: string
): Promise<QnaWindowResponse> {
  const response = await apiClient.get<QnaWindowResponse>(base(eventCode, sessionSlug));
  return response.data;
}

/** Post a question (no parent) or an answer (parentPostId set). Authenticated. */
export async function addPost(
  eventCode: string,
  sessionSlug: string,
  body: string,
  parentPostId?: string | null
): Promise<QnaPostResponse> {
  const response = await apiClient.post<QnaPostResponse>(`${base(eventCode, sessionSlug)}/posts`, {
    body,
    ...(parentPostId ? { parentPostId } : {}),
  });
  return response.data;
}

/** Organizer: extend (new closesAt) or close early (close=true). */
export async function patchWindow(
  eventCode: string,
  sessionSlug: string,
  payload: { closesAt?: string; close?: boolean }
): Promise<QnaWindowResponse> {
  const response = await apiClient.patch<QnaWindowResponse>(base(eventCode, sessionSlug), payload);
  return response.data;
}

/** Organizer: take down a post (soft-delete tombstone). */
export async function removePost(
  eventCode: string,
  sessionSlug: string,
  postId: string
): Promise<void> {
  await apiClient.delete(`${base(eventCode, sessionSlug)}/posts/${encodeURIComponent(postId)}`);
}
