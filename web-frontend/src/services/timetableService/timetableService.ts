/**
 * Timetable Service
 *
 * HTTP client for the timetable endpoint — the single authoritative timeline
 * for an event, served by TimetableService (backend).
 */

import apiClient from '@/services/api/apiClient';
import type { components } from '@/types/generated/events-api.types';

export type TimetableSlot = components['schemas']['TimetableSlot'];
export type TimetableResponse = components['schemas']['TimetableResponse'];

class TimetableService {
  /**
   * Get the authoritative timetable for an event.
   *
   * Returns all slots (MODERATION, BREAK, LUNCH, SPEAKER_SLOT) in chronological order,
   * enriched with DB session slugs and assigned speaker session slugs.
   *
   * GET /api/v1/events/{eventCode}/timetable
   */
  async getTimetable(eventCode: string): Promise<TimetableResponse> {
    const response = await apiClient.get<TimetableResponse>(`/events/${eventCode}/timetable`);
    return response.data;
  }
}

export const timetableService = new TimetableService();
