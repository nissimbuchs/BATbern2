/**
 * Live Timing Service
 * Story 15.1: REST polling replacement for the STOMP live-agenda surface.
 *
 * - getLiveTiming polls GET /events/{eventCode}/live-timing with If-None-Match so an
 *   unchanged snapshot costs a 304 (no body). Returns the ETag for the next poll.
 * - postLiveTimingAction POSTs an END/EXTEND/DELAY action and returns the post-cascade
 *   snapshot with the bumped version.
 *
 * The organizer live-control hook calls these authenticated (apiClient adds the bearer
 * token), which also refreshes server-side presence. The public presenter passes
 * skipAuth so it polls anonymously.
 */

import apiClient from './api/apiClient';
import type { components } from '@/types/generated/event-watch-api.types';

export type LiveTimingResponse = components['schemas']['LiveTimingResponse'];
export type LiveTimingActionRequest = components['schemas']['LiveTimingActionRequest'];

export interface LiveTimingPollResult {
  /** 200 when the snapshot changed (data present), 304 when unchanged (data null). */
  status: 200 | 304;
  data: LiveTimingResponse | null;
  /** ETag to send back as If-None-Match on the next poll. */
  etag: string | null;
}

export interface LiveTimingActionResult {
  data: LiveTimingResponse;
  etag: string | null;
}

const LIVE_TIMING_PATH = (eventCode: string) => `/events/${eventCode}/live-timing`;

export const liveTimingService = {
  /**
   * Polls the live-timing snapshot. When `etag` is supplied it is sent as If-None-Match;
   * an unchanged version yields a 304 (status 304, data null).
   *
   * @param eventCode the event to poll
   * @param etag      ETag from the previous poll, or null on first poll
   * @param skipAuth  true for the public presenter (anonymous poll; no presence heartbeat)
   */
  async getLiveTiming(
    eventCode: string,
    etag?: string | null,
    skipAuth = false
  ): Promise<LiveTimingPollResult> {
    const headers: Record<string, string> = {};
    if (etag) {
      headers['If-None-Match'] = etag;
    }
    if (skipAuth) {
      headers['Skip-Auth'] = 'true';
    }
    const res = await apiClient.get<LiveTimingResponse>(LIVE_TIMING_PATH(eventCode), {
      headers,
      validateStatus: (s) => s === 200 || s === 304,
    });
    const responseEtag = (res.headers?.etag as string | undefined) ?? null;
    if (res.status === 304) {
      return { status: 304, data: null, etag: etag ?? responseEtag };
    }
    return { status: 200, data: res.data, etag: responseEtag };
  },

  /**
   * Applies a timing action and returns the post-cascade snapshot with the bumped version.
   *
   * @param eventCode the event
   * @param request   the END/EXTEND/DELAY action
   */
  async postLiveTimingAction(
    eventCode: string,
    request: LiveTimingActionRequest
  ): Promise<LiveTimingActionResult> {
    const res = await apiClient.post<LiveTimingResponse>(
      `${LIVE_TIMING_PATH(eventCode)}/actions`,
      request
    );
    return { data: res.data, etag: (res.headers?.etag as string | undefined) ?? null };
  },
};
