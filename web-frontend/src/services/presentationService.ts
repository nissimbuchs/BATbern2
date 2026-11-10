/**
 * Presentation Service
 * Story 10.8a: Moderator Presentation Page — Functional
 *
 * API calls for the fullscreen moderator presentation page.
 * All data sources required by the page are consolidated here.
 */

import apiClient from './api/apiClient';
import { updateAdminSetting } from './adminSettingsService';
// events-api split into per-domain specs (API consolidation Phase 6).
import type { components as coreComponents } from '@/types/generated/events-core-api.types';
import type { components as sessionsComponents } from '@/types/generated/event-sessions-api.types';
import type { components as appSettingsComponents } from '@/types/generated/event-app-settings-api.types';
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
export type PresentationSettings = appSettingsComponents['schemas']['PresentationSettingsResponse'];
/** What an organizer saves: the same object, stored as JSON in the app setting `presentation`. */
export type PresentationSettingsRequest = PresentationSettings;

/** The app_settings key the presentation settings are stored under (EMS, Story 4.9). */
export const PRESENTATION_SETTING_KEY = 'presentation';

const SKIP_AUTH = { headers: { 'Skip-Auth': 'true' } };

/**
 * Fetches the event detail including topic, venue and sessions.
 * Public read model — no auth required. Not the organizer endpoint /events/{code}: that one
 * returns no sessions to anonymous callers, which left the agenda slide empty (#1112).
 */
export const getPresentationData = async (eventCode: string): Promise<PresentationEventDetail> => {
  const response = await apiClient.get<PresentationEventDetail>(
    `/public/events/${eventCode}`,
    SKIP_AUTH
  );
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
 * Public read model (`GET /public/events?scope=upcoming`): published events from today on,
 * nearest first, selected server-side. Story 4.9 found that the slide asked the organizer list
 * `/events` with a `status` parameter no spec declares; EMS ignored it and returned the oldest
 * events first, so the slide stayed empty.
 *
 * @param excludeEventCode — the event currently being presented; excluded from
 *   the result so the slide shows only genuinely *future* events, not the
 *   current one (whose date is still in the future).
 */
export const getUpcomingEvents = async (
  excludeEventCode?: string
): Promise<coreComponents['schemas']['Event'][]> => {
  const response = await apiClient.get<{ data: coreComponents['schemas']['Event'][] }>(
    '/public/events',
    { params: { scope: 'upcoming', limit: 10 }, ...SKIP_AUTH }
  );
  // Strictly future (the read model includes today), without the event on screen, nearest first.
  const now = new Date();
  return (response.data.data ?? [])
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
 * Stored by EMS as JSON under the app setting `presentation` (`PUT /admin/settings/presentation`),
 * which validates it (non-blank aboutText, partnerCount >= 0) and answers 400 otherwise.
 */
export const updatePresentationSettings = async (
  data: PresentationSettingsRequest
): Promise<PresentationSettings> => {
  const value = JSON.stringify({ aboutText: data.aboutText, partnerCount: data.partnerCount });
  await updateAdminSetting(PRESENTATION_SETTING_KEY, value);
  return { aboutText: data.aboutText, partnerCount: data.partnerCount };
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
