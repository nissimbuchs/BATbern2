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
export type EventAgendaConfigResponse = components['schemas']['EventAgendaConfigResponse'];
export type UpdateEventAgendaConfigRequest =
  components['schemas']['UpdateEventAgendaConfigRequest'];

class TimetableService {
  /**
   * Get the authoritative timetable for an event.
   *
   * Returns all slots (MODERATION, BREAK, LUNCH, APERITIF, SPEAKER_SLOT) in chronological order,
   * enriched with DB session slugs and assigned speaker session slugs.
   *
   * GET /api/v1/events/{eventCode}/timetable
   */
  async getTimetable(eventCode: string): Promise<TimetableResponse> {
    const response = await apiClient.get<TimetableResponse>(`/events/${eventCode}/timetable`);
    return response.data;
  }

  /**
   * Get the resolved agenda config for an event (Story 15.2).
   *
   * Returns the per-event override if present, else the shared event-type template
   * (`source` indicates which). GET /api/v1/events/{eventCode}/agenda-config
   */
  async getAgendaConfig(eventCode: string): Promise<EventAgendaConfigResponse> {
    const response = await apiClient.get<EventAgendaConfigResponse>(
      `/events/${eventCode}/agenda-config`
    );
    return response.data;
  }

  /**
   * Upsert the per-event agenda config override (copy-on-edit; never touches the template).
   *
   * PUT /api/v1/events/{eventCode}/agenda-config
   */
  async updateAgendaConfig(
    eventCode: string,
    request: UpdateEventAgendaConfigRequest
  ): Promise<EventAgendaConfigResponse> {
    const response = await apiClient.put<EventAgendaConfigResponse>(
      `/events/${eventCode}/agenda-config`,
      request
    );
    return response.data;
  }
}

export const timetableService = new TimetableService();
