/**
 * Presentation Service
 * Story 10.8a: Moderator Presentation Page — Functional
 *
 * API calls for the fullscreen moderator presentation page.
 * All data sources required by the page are consolidated here.
 */

import apiClient from './api/apiClient';
// events-api split into per-domain specs (API consolidation Phase 6).
import type { components as coreComponents } from '@/types/generated/events-core-api.types';
import type { components as sessionsComponents } from '@/types/generated/event-sessions-api.types';
import type { components as companyComponents } from '@/types/generated/company-api.types';
import type { User } from '@/types/user.types';

export type PresentationEventDetail = coreComponents['schemas']['Event'] & {
  venue?: coreComponents['schemas']['Venue'];
  sessions?: sessionsComponents['schemas']['Session'][];
  topic?: {
    code?: string;
    name?: string;
    description?: string;
    imageUrl?: string;
  } | null;
};

export type PresentationSession = sessionsComponents['schemas']['Session'];
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
 *
 * @param excludeEventCode — the event currently being presented; excluded from
 *   the result so the slide shows only genuinely *future* events, not the
 *   current one (whose date is still in the future).
 */
export const getUpcomingEvents = async (
  excludeEventCode?: string
): Promise<coreComponents['schemas']['Event'][]> => {
  const response = await apiClient.get<{ data: coreComponents['schemas']['Event'][] }>('/events', {
    params: {
      status: 'AGENDA_PUBLISHED,TOPIC_SELECTION_DONE,TOPIC_SELECTION,CREATED',
      limit: 10,
      sort: 'date',
    },
    ...SKIP_AUTH,
  });
  // API returns paginated response; extract data array
  const events =
    response.data.data ?? (response.data as unknown as coreComponents['schemas']['Event'][]);
  // Filter to strictly future events (same pattern as public UpcomingEventsSection),
  // excluding the event currently on screen, then sort by date ascending and take the first 3.
  const now = new Date();
  return events
    .filter((e) => new Date(e.date) > now && e.eventCode !== excludeEventCode)
    .sort((a, b) => new Date(a.date).getTime() - new Date(b.date).getTime())
    .slice(0, 3);
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

/**
 * Fetches all global teaser images (shown on ALL event presentations).
 * Uses _global as reserved eventCode (maps to event_code IS NULL on backend).
 */
export const getGlobalTeaserImages = async (): Promise<
  coreComponents['schemas']['TeaserImageItem'][]
> => {
  const response = await apiClient.get<coreComponents['schemas']['TeaserImageItem'][]>(
    '/events/_global/teaser-images',
    SKIP_AUTH
  );
  return response.data;
};

export const presentationService = {
  getPresentationData,
  getPublicOrganizers,
  getUpcomingEvents,
  getPresentationSettings,
  updatePresentationSettings,
  getGlobalTeaserImages,
};
