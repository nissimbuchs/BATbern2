/**
 * Presentation Service
 * Story 10.8a: Moderator Presentation Page — Functional
 *
 * API calls for the fullscreen moderator presentation page.
 * All data sources required by the page are consolidated here.
 */

import apiClient from './api/apiClient';
import type { components } from '@/types/generated/events-api.types';
import type { components as companyComponents } from '@/types/generated/company-api.types';
import type { User } from '@/types/user.types';

export type PresentationEventDetail = components['schemas']['Event'] & {
  venue?: components['schemas']['Venue'];
  sessions?: components['schemas']['Session'][];
  topic?: {
    code?: string;
    name?: string;
    description?: string;
    imageUrl?: string;
  } | null;
};

export type PresentationSession = components['schemas']['Session'];
export type PresentationSettings = companyComponents['schemas']['PresentationSettingsResponse'];
export type PresentationSettingsRequest =
  companyComponents['schemas']['PresentationSettingsRequest'];

const SKIP_AUTH = { headers: { 'Skip-Auth': 'true' } };

/**
 * Fetches the event detail including topic, venue and sessions.
 * Public endpoint — no auth required.
 */
export const getPresentationData = async (eventCode: string): Promise<PresentationEventDetail> => {
  const response = await apiClient.get<PresentationEventDetail>(`/events/${eventCode}`, {
    params: { include: 'topics,venue,sessions,speakers' },
    ...SKIP_AUTH,
  });
  return response.data;
};

/**
 * Fetches all active organizers for the Committee slide.
 * Public endpoint — no auth required.
 */
export const getPublicOrganizers = async (): Promise<User[]> => {
  const response = await apiClient.get<User[]>('/public/organizers', SKIP_AUTH);
  return response.data;
};

/**
 * Fetches the next 3 upcoming events for the Upcoming Events slide.
 * Public endpoint — no auth required.
 */
export const getUpcomingEvents = async (): Promise<components['schemas']['Event'][]> => {
  const response = await apiClient.get<{ data: components['schemas']['Event'][] }>('/events', {
    params: {
      status: 'AGENDA_PUBLISHED,TOPIC_SELECTION_DONE,TOPIC_SELECTION,CREATED',
      limit: 3,
      sort: 'date',
    },
    ...SKIP_AUTH,
  });
  // API returns paginated response; extract data array
  return response.data.data ?? (response.data as unknown as components['schemas']['Event'][]);
};

/**
 * Fetches the current presentation settings.
 * Public endpoint — no auth required.
 */
export const getPresentationSettings = async (): Promise<PresentationSettings> => {
  const response = await apiClient.get<PresentationSettings>(
    '/public/settings/presentation',
    SKIP_AUTH
  );
  return response.data;
};

/**
 * Updates the presentation settings. Requires ORGANIZER role.
 */
export const updatePresentationSettings = async (
  data: PresentationSettingsRequest
): Promise<PresentationSettings> => {
  const response = await apiClient.put<PresentationSettings>('/settings/presentation', data);
  return response.data;
};

export const presentationService = {
  getPresentationData,
  getPublicOrganizers,
  getUpcomingEvents,
  getPresentationSettings,
  updatePresentationSettings,
};
