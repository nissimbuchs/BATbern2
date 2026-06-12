/**
 * Speaker Self-Nomination API Client — Story 7.2 "I Could Speak on That".
 *
 * A logged-in attendee raises their hand with a proposed talk for a specific upcoming event
 * (the event card supplies the eventCode). Flows into the existing speaker pool at IDENTIFIED
 * tagged source=self_nomination — see event-management-service SelfNominationController.
 *
 * CRITICAL: paths are relative to baseURL (which already includes /api/v1).
 */

import apiClient from '@/services/api/apiClient';
import type { SpeakerPoolResponse } from '@/types/speakerPool.types';

export interface SelfNominateRequest {
  /** The proposed talk title. */
  sessionTitle: string;
  /** The proposed talk abstract (stored raw). */
  abstract: string;
}

/**
 * Self-nominate as a speaker for {@code eventCode}. The attendee's name + company are
 * auto-filled server-side from their profile — the body carries only the proposed talk.
 *
 * @throws on 409 if the event's topic is unset/unpublished, or the attendee already nominated.
 */
export const selfNominateSpeaker = async (
  eventCode: string,
  req: SelfNominateRequest
): Promise<SpeakerPoolResponse> => {
  const response = await apiClient.post<SpeakerPoolResponse>(
    `/events/${eventCode}/speakers/self-nominate`,
    req
  );
  return response.data;
};
